package com.seogaemo.hmg.module;

import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Fixed NEMESIS daemon command, invoked only from a worker thread. */
public final class RootClient {
    /** Writes also wait on operation.lock, which a boot map verify can hold for 30 s. */
    static final long WRITE_TIMEOUT_MS = 150000L;
    static final long READ_TIMEOUT_MS = 30000L;
    static final long STOCK_STATUS_TIMEOUT_MS = 30000L;
    static final long STOCK_TIMEOUT_MS = 180000L;
    /** Thousands of ledger files need copying, diagnostics, sync and USB verification. */
    static final long EXPORT_TIMEOUT_MS = 600000L;

    private RootClient() { }

    /** Timeout for a module verb: apply/revert wait on the lock and write; status/precheck only read. */
    static long moduleTimeoutMs(String verb) {
        return "apply".equals(verb) || "revert".equals(verb) ? WRITE_TIMEOUT_MS : READ_TIMEOUT_MS;
    }

    private static final String ROOT = "/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root ";

    /** Same shape the root dispatcher accepts; root checks that the id is installed. */
    public static boolean validId(String id) {
        return id != null && id.length() <= 48 && id.matches("[a-z0-9]+(-[a-z0-9]+)*");
    }

    public static RootResult run(String moduleId, String verb) {
        if (!validId(moduleId)) return failure("unknown module");
        if (!("apply".equals(verb) || "revert".equals(verb) ||
                "status".equals(verb) || "precheck".equals(verb))) return failure("invalid verb");
        return invoke(ROOT + moduleId + " " + verb, moduleTimeoutMs(verb));
    }

    /** Installed packages only; a running module is reverted and applied again. */
    public static RootResult moduleSet(String moduleId, String key, String value) {
        if (!validId(moduleId) || key == null || !key.matches("[a-z0-9_]{1,40}") ||
                value == null || !value.matches("[a-z0-9_.-]{1,40}")) return failure("invalid setting");
        return invoke(ROOT + moduleId + " set " + key + " " + value, 2 * WRITE_TIMEOUT_MS);
    }

    public static RootResult moduleList() {
        return invoke(ROOT + "module list", READ_TIMEOUT_MS);
    }

    public static RootResult moduleDescribe(String moduleId, String locale) {
        if (!validId(moduleId) || locale == null || !locale.matches("[a-z]{2}")) return failure("invalid module");
        return invoke(ROOT + "module describe " + moduleId + " " + locale, READ_TIMEOUT_MS);
    }

    /** The package must already be at files/hsm/<sha256>.hsm in the app's private storage. */
    public static boolean validActionName(String name) { return name != null && name.matches("[a-z0-9_]{1,40}"); }

    /** Runs an installed module's declared `ui.txt` button; root re-checks the name and that the module is on. */
    public static RootResult moduleAction(String moduleId, String name) {
        if (!validId(moduleId) || !validActionName(name)) return failure("invalid action");
        return invoke(ROOT + moduleId + " action " + name, WRITE_TIMEOUT_MS);
    }

    /** Copies USB packages into the app-readable inbox; changes no stock state. */
    public static RootResult moduleUsbImport() {
        return invoke(ROOT + "module usb-import", WRITE_TIMEOUT_MS);
    }

    public static RootResult moduleInstall(String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) return failure("invalid package digest");
        return invoke(ROOT + "module install " + sha256, WRITE_TIMEOUT_MS);
    }

    public static RootResult moduleRemove(String moduleId) {
        if (!validId(moduleId)) return failure("invalid module");
        return invoke(ROOT + "module remove " + moduleId, WRITE_TIMEOUT_MS);
    }

    /** Deletes the wireless module's two generated turn-by-turn observation files (fixed verb, no arguments). */
    public static RootResult tbtReset() {
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root wireless-carplay-and-aa tbt-reset", WRITE_TIMEOUT_MS);
    }

    public static RootResult rebootDevice() {
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root device reboot", READ_TIMEOUT_MS);
    }

    /** Problem report: everything `export usb` writes plus Android/NEMESIS logs; logs is all or ours. */
    public static RootResult exportReport(boolean allLogs) {
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export usb "
                + (allLogs ? "all" : "ours"), EXPORT_TIMEOUT_MS);
    }

    public static RootResult stock(String verb) {
        if (!("restore".equals(verb) || "status".equals(verb))) return failure("invalid stock verb");
        // status is a read-only probe run at app start; only restore needs the long window.
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root stock " + verb,
                "status".equals(verb) ? STOCK_STATUS_TIMEOUT_MS : STOCK_TIMEOUT_MS);
    }

    public static RootResult uninstallPackage() {
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root package uninstall", STOCK_TIMEOUT_MS);
    }

    /** Other NEMESIS packages that overlap ours: read-only status, or clean one catalogue item. */
    public static RootResult foreignStatus() {
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root foreign status", READ_TIMEOUT_MS);
    }

    public static RootResult foreignClean(String item) {
        if (item == null || !item.matches("[a-z0-9-]{1,40}")) return failure("invalid item");
        return invoke("/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root foreign clean " + item,
                STOCK_TIMEOUT_MS);
    }

    public static RootResult map(String verb) {
        if (!("status".equals(verb) || "run".equals(verb) ||
                "verify".equals(verb) || "export".equals(verb))) return failure("invalid map verb");
        String command = "/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root map " + verb;
        return invoke(command, "export".equals(verb) ? EXPORT_TIMEOUT_MS :
                "run".equals(verb) ? 600000 : "verify".equals(verb) ? 180000 : 30000);
    }

    private static RootResult invoke(String command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "/data/nemesis/apps/hmg-seogaemo/bin/nemesis-client", "root", "-c", command)
                    .redirectErrorStream(true).start();
            final InputStream input = process.getInputStream();
            final byte[] bytes = new byte[8192];
            final int[] length = {0};
            final IOException[] readError = {null};
            Thread reader = new Thread(new Runnable() {
                public void run() {
                    try {
                        byte[] chunk = new byte[512];
                        int n;
                        while ((n = input.read(chunk)) != -1) {
                            int keep = Math.min(n, bytes.length - length[0]);
                            if (keep > 0) {
                                System.arraycopy(chunk, 0, bytes, length[0], keep);
                                length[0] += keep;
                            }
                        }
                    } catch (IOException error) { readError[0] = error; }
                }
            }, "hmg-seogaemo-root-output");
            reader.setDaemon(true);
            reader.start();
            long deadline = System.currentTimeMillis() + timeoutMs;
            int exit;
            while (true) {
                try { exit = process.exitValue(); break; }
                catch (IllegalThreadStateException running) {
                    if (System.currentTimeMillis() >= deadline) {
                        process.destroy();
                        return failure("root command timed out");
                    }
                    Thread.sleep(50);
                }
            }
            reader.join(1000);
            if (reader.isAlive() || readError[0] != null) return failure("root output incomplete");
            return parse(new String(bytes, 0, length[0], "UTF-8"), exit);
        } catch (Exception error) {
            if (process != null) process.destroy();
            return failure(error.getClass().getSimpleName());
        }
    }

    private static RootResult parse(String output, int exit) {
        String state = null, restart = null, detail = "";
        Map<String, String> extras = new HashMap<String, String>();
        for (String line : output.split("\n")) {
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            String key = line.substring(0, equals);
            String value = line.substring(equals + 1).replace("\r", "");
            if (value.length() > 200) value = value.substring(0, 200);
            if ("state".equals(key)) state = value;
            else if ("restart".equals(key)) restart = value;
            else if ("detail".equals(key)) detail = value;
            else if (key.matches("(info|warn)\\.[a-z0-9_]+")) extras.put(key, value);
        }
        if (state == null || restart == null) return failure("invalid root response");
        return new RootResult(state, restart, detail, exit, extras);
    }

    private static RootResult failure(String reason) {
        return new RootResult("failed", "none", reason, 1, null);
    }
}
