package com.seogaemo.hmg.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Runs one read-only root call per module at once. Reads of different modules take no shared
 * lock, so a screen waits for the slowest module instead of the sum; each id is read once.
 */
public final class ParallelReads {
    public interface Read { RootResult read(String id); }

    private ParallelReads() { }

    public static Map<String, RootResult> each(Collection<String> ids, final Read read) {
        final Map<String, RootResult> results = Collections.synchronizedMap(new HashMap<String, RootResult>());
        List<Thread> threads = new ArrayList<Thread>();
        for (final String id : new LinkedHashSet<String>(ids)) {
            Thread thread = new Thread(new Runnable() { public void run() {
                RootResult result;
                try { result = read.read(id); }
                catch (RuntimeException error) { result = new RootResult("failed", "none", "read failed", 1, null); }
                results.put(id, result);
            } }, "hmg-seogaemo-read");
            threads.add(thread);
            thread.start();
        }
        boolean interrupted = false;
        for (Thread thread : threads) {
            while (true) {
                try { thread.join(); break; }
                catch (InterruptedException error) { interrupted = true; }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return new HashMap<String, RootResult>(results);
    }
}
