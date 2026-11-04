package com.seogaemo.hmg.module;

import java.util.List;
import java.util.Map;

/**
 * Read-only decision for a saved post-update module restore found at app start.
 * Uses only the existing {@code stock status} and per-module {@code status} results.
 */
public final class RestoreGate {
    private RestoreGate() { }

    public enum Decision {
        /** Safe to offer the restore behind a confirmation. */
        PROCEED,
        /** A stock restore has run (automatic calls are paused); the saved intent is obsolete. */
        PAUSED,
        /** A head-unit restart is outstanding; ask again after it. */
        PENDING_RESTART,
        /** Root state could not be read; change nothing. */
        UNAVAILABLE
    }

    public static Decision decide(RootResult stock, List<RootResult> modules) {
        if (stock == null || (stock.exitCode != 0 && stock.info.isEmpty() && "failed".equals(stock.state)))
            return Decision.UNAVAILABLE;
        // stock/result exists exactly while stock/paused does; it carries info.module_* receipts.
        if (isPaused(stock)) return Decision.PAUSED;
        if ("pending-restart".equals(stock.state) || "head_unit".equals(stock.restart))
            return Decision.PENDING_RESTART;
        for (RootResult module : modules) {
            if (module == null) return Decision.UNAVAILABLE;
            if ("pending-restart".equals(module.state) || "head_unit".equals(module.restart))
                return Decision.PENDING_RESTART;
        }
        return Decision.PROCEED;
    }

    private static boolean isPaused(RootResult stock) {
        for (Map.Entry<String, String> entry : stock.info.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("module_")) return true;
            if ("paused".equals(key) && !"0".equals(entry.getValue()) && !"false".equals(entry.getValue()))
                return true;
        }
        return false;
    }
}
