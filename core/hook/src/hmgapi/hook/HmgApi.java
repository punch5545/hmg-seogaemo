package hmgapi.hook;

// HMG-Seogaemo core hook host. AGPL-3.0-or-later; engine API derived from HMG-API (see UPSTREAM.md).
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import dalvik.system.DexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/**
 * Zygote entry of the core hook host. Loads every installed hook module registered under
 * core/hook/mods/<id>/ (hook.jar, entry, processes) with its own class loader; a module that
 * fails is turned off alone. Results go to core/hook/status for `hmg-seogaemo-root` status.
 */
final class HmgApi {
    static final String ROOT = "/data/nemesis/backup/hmg-seogaemo";
    static final String TAG = "HMG-Seogaemo-Hook";

    static final class Loaded {
        final ModContext ctx; final ModEntry entry; final Set<String> processes;
        Loaded(ModContext ctx, ModEntry entry, Set<String> processes) { this.ctx = ctx; this.entry = entry; this.processes = processes; }
    }
    static final List<Loaded> LOADED = new ArrayList<Loaded>();

    private HmgApi() {}
    static void i(String msg) { RuntimeResult.log("I", msg, null); Log.i(TAG, msg); }
    static void e(String msg, Throwable t) {
        HostStatus.put("last_error", msg + " " + t);
        RuntimeResult.put("event.last_error", msg + " " + t);
        RuntimeResult.log("E", msg, t);
        Log.e(TAG, msg, t);
    }

    static String read(File file, int limit) throws Exception {
        FileInputStream in = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        try {
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > limit) throw new IllegalStateException(file + " too large");
                out.write(buf, 0, n);
            }
        } finally { in.close(); }
        return out.toString("UTF-8");
    }

    private static String property(Class<?> provider, String key) throws Exception {
        return (String) provider.getMethod("get", String.class).invoke(null, key);
    }

    private static void gateOff(String reason) {
        HostStatus.put("gate", "off: " + reason);
        i("hooks off: " + reason);
        HostStatus.write();
    }

    static void zygoteInit() {
        HostStatus.put("gate", "initializing");
        try {
            if (!new File(ROOT + "/modules/core-hook/enabled").isFile()) { gateOff("core hook host disabled"); return; }
            Class<?> prop = Class.forName("android.os.SystemProperties");
            String product = property(prop, "ro.build.product");
            File root = new File(ROOT + "/car-maps");
            String relative = read(new File(root, "active"), 4096).trim();
            if (!relative.matches("[A-Za-z0-9._-]+/[0-9a-f]{32}/[0-9a-f]{32}")) { gateOff("active map pointer invalid"); return; }
            File selected = new File(root, relative);
            JSONObject car = new JSONObject(read(new File(selected, "car.json"), 4194304));
            if (!product.equals(car.getString("product")) ||
                    !property(prop, "ro.mobis.onebindir").equals(car.getString("onebin")) ||
                    !property(prop, "ro.build.version.release").equals(car.getString("android"))) {
                gateOff("car identity changed"); return;
            }
            String seal = product + "\n" + read(new File(selected, "hash-id"), 4096).trim() + "\n";
            if (!read(new File(selected, "verified"), 4096).startsWith(seal)) { gateOff("map not verified for this stock build"); return; }
            JSONObject files = car.getJSONObject("files");
            Iterator<String> paths = files.keys();
            while (paths.hasNext()) {
                String file = paths.next();
                if (!file.matches("/system/(?:app|framework)/[A-Za-z0-9_.-]+") ||
                        !files.getString(file).equals(ModContext.md5(file))) { gateOff("stock hash mismatch " + file); return; }
            }
            Points.use(new JSONObject(read(new File(selected, "map.json"), 4194304)));
            String missing = Points.missing("framework.activity_thread.handle_bind_application",
                    "framework.app_bind_data.app_info", "framework.app_bind_data.process_name",
                    "framework.activity_thread.initial_application");
            if (missing != null && missing.length() > 0) { gateOff("missing framework points " + missing); return; }
            File[] mods = new File(ROOT + "/core/hook/mods").listFiles();
            if (mods != null) {
                Arrays.sort(mods);
                for (File dir : mods) load(dir);
            }
            if (LOADED.isEmpty()) { gateOff("no hook module loaded"); return; }
            Points.hook(null, "framework.activity_thread.handle_bind_application", new MethodHook() {
                @Override protected void afterHookedMethod(Param p) {
                    String pkg = null, process = null;
                    ClassLoader loader = null; Context context = null;
                    try {
                        Object data = p.args[0];
                        pkg = ((ApplicationInfo) Points.get(null, "framework.app_bind_data.app_info", data)).packageName;
                        process = (String) Points.get(null, "framework.app_bind_data.process_name", data);
                        Application app = (Application) Points.get(null, "framework.activity_thread.initial_application", p.thisObject);
                        if (app == null) return;
                        loader = app.getClassLoader(); context = app;
                    } catch (Throwable t) { e("app bind", t); return; }
                    for (Loaded mod : LOADED) {
                        if (!mod.processes.contains(process) && !mod.processes.contains(pkg)) continue;
                        try { mod.entry.appLoaded(mod.ctx, new LoadedApp(pkg, process, loader, context)); }
                        catch (Throwable t) { mod.ctx.error("appLoaded in " + process, t); }
                    }
                }
            });
            HostStatus.put("gate", "ready " + LOADED.size());
            i("core hook host ready with " + LOADED.size() + " module(s)");
        } catch (Throwable t) { e("core hook setup", t); gateOff("setup: " + t); return; }
        HostStatus.write();
    }

    private static void load(File dir) {
        String id = dir.getName();
        if (!id.matches("[a-z0-9]+(-[a-z0-9]+)*")) return;
        try {
            String entry = read(new File(dir, "entry"), 512).trim();
            if (!entry.matches("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)+")) throw new IllegalStateException("bad entry class");
            Set<String> processes = new HashSet<String>();
            for (String line : read(new File(dir, "processes"), 8192).split("\n")) {
                if (line.trim().length() > 0) processes.add(line.trim());
            }
            File jar = new File(dir, "hook.jar");
            File optimized = new File(ROOT + "/core/hook/dex/" + id);
            if (!optimized.isDirectory() && !optimized.mkdirs()) throw new IllegalStateException("dex directory unavailable");
            DexClassLoader loader = new DexClassLoader(jar.getPath(), optimized.getPath(), null, HmgApi.class.getClassLoader());
            Class<?> cls = loader.loadClass(entry);
            if (!ModEntry.class.isAssignableFrom(cls)) throw new IllegalStateException(entry + " does not extend hmgapi.hook.ModEntry");
            ModEntry instance = (ModEntry) cls.newInstance();
            ModContext ctx = new ModContext(id);
            instance.zygoteInit(ctx);
            LOADED.add(new Loaded(ctx, instance, processes));
            HostStatus.put("mod." + id, "loaded " + processes.size() + " process(es)");
            i("loaded " + id + " (" + entry + ")");
        } catch (Throwable t) {
            HostStatus.put("mod." + id, "failed: " + t);
            e("load " + id, t);
        }
    }
}
