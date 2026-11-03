package com.seogaemo.hmg.module;

import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Fixed NEMESIS daemon command, invoked only from a worker thread. */
public final class RootClient {
    private RootClient() { }

    public static RootResult run(String moduleId, String verb) {
        try { ModuleRegistry.depends(moduleId); }
        catch (IllegalArgumentException bad) { return failure("unknown module"); }
        if (!("apply".equals(verb) || "revert".equals(verb) ||
                "status".equals(verb) || "precheck".equals(verb))) return failure("invalid verb");
        final String command = "/data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root " + moduleId + " " + verb;
        return invoke(command);
    }

    public static RootResult exportUsb() {
        return invoke("/data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export usb");
    }

    private static RootResult invoke(String command) {
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
            long deadline = System.currentTimeMillis() + 15000;
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
            return parse(new String(bytes, 0, length[0], "US-ASCII"), exit);
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
