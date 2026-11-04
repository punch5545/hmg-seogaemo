package com.seogaemo.hmg.module;

import java.io.FileReader;

/** Read-only gate for an already confirmed update continuation. */
public final class ModuleResume {
    private ModuleResume() { }

    public static boolean ready(RootResult status, boolean usableNetwork) {
        return usableNetwork && status.exitCode == 0 && status.desiredEnabled() &&
                "on".equals(status.state) && !"head_unit".equals(status.restart);
    }

    public static boolean restarted(String previousBoot, String currentBoot) {
        return previousBoot.length() > 0 && currentBoot.length() > 0 && !previousBoot.equals(currentBoot);
    }

    public static String bootId() {
        try {
            FileReader reader = new FileReader("/proc/sys/kernel/random/boot_id");
            try {
                char[] value = new char[80];
                int count = reader.read(value);
                return count > 0 ? new String(value, 0, count).trim() : "";
            } finally { reader.close(); }
        } catch (Exception ignored) { return ""; }
    }
}
