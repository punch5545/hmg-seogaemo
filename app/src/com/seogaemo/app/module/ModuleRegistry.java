package com.seogaemo.app.module;

/** Only the build-generated list defines built-in modules. */
public final class ModuleRegistry {
    private ModuleRegistry() { }

    public static Module[] all() {
        return GeneratedModules.ALL.clone();
    }

    public static String[] depends(String id) {
        return relation(id, GeneratedModules.DEPENDS);
    }

    public static String[] excludes(String id) {
        return relation(id, GeneratedModules.EXCLUDES);
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
