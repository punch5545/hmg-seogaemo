package com.seogaemo.hmg.core.update;

import java.io.IOException;
import java.io.InputStream;

/**
 * Runs a child process with a hard deadline. Output is drained by a daemon thread so a
 * chatty or stuck child cannot fill the pipe and deadlock the caller; on timeout the child
 * is destroyed. Call from a worker thread only.
 */
public final class BoundedProcess {
    private BoundedProcess() { }

    public static final class Result {
        public final int exit;
        public final byte[] output;
        public final boolean truncated;
        public final boolean timedOut;
        Result(int exit, byte[] output, boolean truncated, boolean timedOut) {
            this.exit = exit; this.output = output; this.truncated = truncated; this.timedOut = timedOut;
        }
    }

    /** Merged stdout/stderr is kept up to {@code maxBytes}; the rest is drained and discarded. */
    public static Result run(ProcessBuilder builder, long timeoutMs, int maxBytes)
            throws IOException, InterruptedException {
        final Process process = builder.redirectErrorStream(true).start();
        final InputStream input = process.getInputStream();
        final byte[] kept = new byte[maxBytes];
        final int[] used = {0};
        final boolean[] overflow = {false};
        Thread reader = new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] chunk = new byte[512];
                    int n;
                    while ((n = input.read(chunk)) != -1) {
                        synchronized (used) {
                            int keep = Math.min(n, kept.length - used[0]);
                            if (keep > 0) { System.arraycopy(chunk, 0, kept, used[0], keep); used[0] += keep; }
                            if (keep < n) overflow[0] = true;
                        }
                    }
                } catch (IOException ignored) { }
            }
        }, "hmg-seogaemo-bounded-output");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.currentTimeMillis() + timeoutMs;
        int exit;
        try {
            while (true) {
                try { exit = process.exitValue(); break; }
                catch (IllegalThreadStateException running) {
                    if (System.currentTimeMillis() >= deadline) {
                        process.destroy();
                        return new Result(-1, new byte[0], false, true);
                    }
                    Thread.sleep(50);
                }
            }
        } catch (InterruptedException interrupted) {
            process.destroy();
            throw interrupted;
        }
        reader.join(2000);
        synchronized (used) {
            byte[] copy = new byte[used[0]];
            System.arraycopy(kept, 0, copy, 0, used[0]);
            return new Result(exit, copy, overflow[0], false);
        }
    }
}
