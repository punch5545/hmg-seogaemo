package com.seogaemo.hmg.module;

import java.io.FileReader;

/** Read-only gate for an already confirmed update continuation. */
public final class ModuleResume {
    /** A saved automatic update resume is only honoured for this long. */
    public static final long RESUME_TTL_MS = 24L * 60L * 60L * 1000L;

    private ModuleResume() { }

    /** Wall-clock stamps before this are an unsynced clock (the unit has no time before it syncs). */
    private static final long PLAUSIBLE_WALL_MS = 1735689600000L; // 2025-01-01

    /**
     * True once the resume state is older than the TTL by wall clock. A clock that moved
     * backwards or a stamp taken before the clock was valid cannot show the age, so those
     * are never treated as expired.
     */
    public static boolean expired(long savedAtMs, long nowMs) {
        return savedAtMs >= PLAUSIBLE_WALL_MS && nowMs > savedAtMs && nowMs - savedAtMs > RESUME_TTL_MS;
    }

    /**
     * Expiry that does not trust the wall clock. Within the boot that saved the stamp the
     * monotonic elapsed-realtime clock (which keeps counting in sleep) is authoritative.
     * After a restart it is meaningless, so fall back to {@link #expired(long, long)}, which
     * only expires when it can positively show the TTL has passed.
     */
    public static boolean expired(String savedBootId, long savedElapsedMs, long savedWallMs,
            String currentBootId, long nowElapsedMs, long nowWallMs) {
        boolean sameBoot = savedBootId != null && savedBootId.length() > 0 && savedBootId.equals(currentBootId);
        if (sameBoot && savedElapsedMs > 0 && nowElapsedMs >= savedElapsedMs)
            return nowElapsedMs - savedElapsedMs > RESUME_TTL_MS;
        return expired(savedWallMs, nowWallMs);
    }

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
