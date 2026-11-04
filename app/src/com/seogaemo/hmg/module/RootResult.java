package com.seogaemo.hmg.module;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Bounded root status data and, for a plan, the ordered operation receipts. */
public final class RootResult {
    public static final class StepOutcome {
        public final String id;
        public final boolean enable;
        public final RootResult result;
        public StepOutcome(String id, boolean enable, RootResult result) {
            this.id = id; this.enable = enable; this.result = result;
        }
    }
    public final List<StepOutcome> outcomes;
    public final String state;
    public final String restart;
    public final String detail;
    public final int exitCode;
    public final Map<String, String> info;
    public final Map<String, String> warnings;

    public RootResult(String state, String restart, String detail, int exitCode,
            Map<String, String> values) {
        this(state, restart, detail, exitCode, values, Collections.<StepOutcome>emptyList());
    }

    private RootResult(String state, String restart, String detail, int exitCode,
            Map<String, String> values, List<StepOutcome> outcomes) {
        this.outcomes = Collections.unmodifiableList(new ArrayList<StepOutcome>(outcomes));
        this.state = state;
        this.restart = restart;
        this.detail = detail;
        this.exitCode = exitCode;
        Map<String, String> foundInfo = new HashMap<String, String>();
        Map<String, String> foundWarnings = new HashMap<String, String>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("info.")) {
                    foundInfo.put(key.substring(5), entry.getValue());
                } else if (key.startsWith("warn.")) {
                    foundWarnings.put(key.substring(5), entry.getValue());
                }
            }
        }
        info = Collections.unmodifiableMap(foundInfo);
        warnings = Collections.unmodifiableMap(foundWarnings);
    }

    public static RootResult notImplemented() {
        return new RootResult("not-implemented", "none", "not implemented", 1,
                Collections.<String, String>emptyMap());
    }

    public static RootResult combined(RootResult last, String restart, List<StepOutcome> outcomes) {
        Map<String, String> values = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : last.info.entrySet())
            values.put("info." + entry.getKey(), entry.getValue());
        for (Map.Entry<String, String> entry : last.warnings.entrySet())
            values.put("warn." + entry.getKey(), entry.getValue());
        String state = last.exitCode == 0 && "head_unit".equals(restart)
                ? "pending-restart" : last.state;
        return new RootResult(state, restart, last.detail, last.exitCode, values, outcomes);
    }

    /** Saved intent is authoritative even when live state has drifted. */
    public boolean desiredEnabled() {
        String desired = info.get("desired");
        return desired == null ? "on".equals(state) || "pending-restart".equals(state)
                : "on".equals(desired);
    }
}
