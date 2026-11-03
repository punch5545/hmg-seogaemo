package com.seogaemo.hmg.module;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Bounded root status data; transport is not implemented yet. */
public final class RootResult {
    public final String state;
    public final String restart;
    public final String detail;
    public final int exitCode;
    public final Map<String, String> info;
    public final Map<String, String> warnings;

    public RootResult(String state, String restart, String detail, int exitCode,
            Map<String, String> values) {
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
}
