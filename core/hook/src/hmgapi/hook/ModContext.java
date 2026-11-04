package hmgapi.hook;

// HMG-Seogaemo core hook host. AGPL-3.0-or-later; engine API derived from HMG-API (see UPSTREAM.md).
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** What one installed hook module sees: its id, package files, settings and the hook API. */
public final class ModContext {
    public final String id;
    /** The installed package (module.properties, files/), readable in the zygote only. */
    public final File dir;
    private final Map<String, String> settings = new HashMap<String, String>();

    ModContext(String id) {
        this.id = id;
        this.dir = new File(HmgApi.ROOT + "/installed/" + id);
        File[] saved = new File(HmgApi.ROOT + "/settings/" + id).listFiles();
        if (saved != null) for (File file : saved) {
            try { if (file.getName().matches("[a-z0-9_]+")) settings.put(file.getName(), HmgApi.read(file, 256).trim()); }
            catch (Throwable ignored) {}
        }
    }

    public java.lang.reflect.Member hook(ClassLoader cl, String point, MethodHook hook) { return Points.hook(cl, point, hook); }
    /** Saved value, read in the zygote; null means the declared default applies. */
    public String settingString(String key, String fallback) { String v = settings.get(key); return v == null ? fallback : v; }
    public boolean settingBoolean(String key, boolean fallback) {
        String v = settings.get(key);
        return v == null ? fallback : "on".equals(v) || "true".equals(v);
    }
    public void log(String message) { Log.i(HmgApi.TAG, "[" + id + "] " + message); }
    public void error(String message, Throwable t) { Log.e(HmgApi.TAG, "[" + id + "] " + message, t); }

    static String md5(String path) {
        try {
            FileInputStream in = new FileInputStream(path); MessageDigest d = MessageDigest.getInstance("MD5");
            byte[] b = new byte[65536]; int n;
            try { while ((n = in.read(b)) > 0) d.update(b, 0, n); } finally { in.close(); }
            StringBuilder out = new StringBuilder();
            for (byte v : d.digest()) out.append(String.format("%02x", v & 255));
            return out.toString();
        } catch (Exception e) { return null; }
    }
}
