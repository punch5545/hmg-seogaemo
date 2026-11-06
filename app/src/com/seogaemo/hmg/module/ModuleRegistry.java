package com.seogaemo.hmg.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Built-in modules come from the build-generated list; installed packages are read from
 * root and follow them in install order. Screens still show built-in modules only.
 */
public final class ModuleRegistry {
    private static volatile List<InstalledModule> installed = Collections.emptyList();

    private ModuleRegistry() { }

    public static Module[] all() {
        return GeneratedModules.ALL.clone();
    }

    public static List<InstalledModule> installed() {
        return installed;
    }

    public static void setInstalled(List<InstalledModule> modules) {
        installed = Collections.unmodifiableList(new ArrayList<InstalledModule>(modules));
    }

    /** Reads `module list` and describes every installed package; keeps the old list on failure. */
    public static boolean refreshInstalled(String locale) {
        RootResult list = RootClient.moduleList();
        if (list.exitCode != 0 || !"on".equals(list.state)) return false;
        TreeMap<Integer, String> order = new TreeMap<Integer, String>();
        for (Map.Entry<String, String> entry : list.info.entrySet()) {
            if (!entry.getKey().startsWith("module_")) continue;
            String[] fields = entry.getValue().split(",", -1);
            if (fields.length != 4 || !("direct".equals(fields[2]) || "bundled".equals(fields[2])) || !RootClient.validId(fields[0])) continue;
            try { order.put(Integer.valueOf(fields[3]), fields[0]); }
            catch (NumberFormatException bad) { return false; }
        }
        final String language = locale;
        Map<String, RootResult> described = ParallelReads.each(order.values(), new ParallelReads.Read() {
            public RootResult read(String id) { return RootClient.moduleDescribe(id, language); }
        });
        List<InstalledModule> found = new ArrayList<InstalledModule>();
        for (String id : order.values()) {
            InstalledModule module = InstalledModule.parse(id, described.get(id));
            if (module == null) return false;
            found.add(module);
        }
        setInstalled(found);
        return true;
    }

    public static String[] orderedIds() {
        List<String> ids = new ArrayList<String>();
        Collections.addAll(ids, GeneratedModules.IDS);
        for (InstalledModule module : installed) ids.add(module.id);
        return ids.toArray(new String[0]);
    }

    public static Module find(String id) {
        Module[] modules = all();
        for (Module module : modules) if (module.id().equals(id)) return module;
        throw new IllegalArgumentException("Unknown module: " + id);
    }

    public static InstalledModule findInstalled(String id) {
        for (InstalledModule module : installed) if (module.id.equals(id)) return module;
        return null;
    }

    public static String[] depends(String id) {
        InstalledModule module = findInstalled(id);
        if (module != null) return module.depends.clone();
        return relation(id, GeneratedModules.DEPENDS);
    }

    /** Symmetric, like the root dispatcher: an installed package may name a built-in. */
    public static String[] excludes(String id) {
        List<String> result = new ArrayList<String>();
        InstalledModule self = findInstalled(id);
        if (self != null) Collections.addAll(result, self.excludes);
        else Collections.addAll(result, relation(id, GeneratedModules.EXCLUDES));
        for (InstalledModule other : installed) {
            if (other.id.equals(id) || result.contains(other.id)) continue;
            for (String excluded : other.excludes) if (excluded.equals(id)) result.add(other.id);
        }
        return result.toArray(new String[0]);
    }

    private static String[] relation(String id, String[][] values) {
        for (int i = 0; i < GeneratedModules.IDS.length; i++) {
            if (GeneratedModules.IDS[i].equals(id)) {
                return values[i].clone();
            }
        }
        throw new IllegalArgumentException("Unknown module: " + id);
    }
}
