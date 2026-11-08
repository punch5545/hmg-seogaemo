package com.seogaemo.hmg.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** A bundled module the owner removed, as `module list-removed` reports it; it can be added back from the Add screen. */
public final class RemovedModule {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z.+-]{0,40}");
    private static final Pattern ICON = Pattern.compile("[a-z]{1,20}");
    private static final int MAX = 16;

    public final String id;
    public final String name;
    public final String summary;
    public final String version;
    public final String icon;

    private RemovedModule(String id, String name, String summary, String version, String icon) {
        this.id = id;
        this.name = name;
        this.summary = summary;
        this.version = version;
        this.icon = icon;
    }

    /** Entries in root order; malformed lines are skipped. */
    public static List<RemovedModule> parse(RootResult result) {
        List<RemovedModule> found = new ArrayList<RemovedModule>();
        if (result == null || result.exitCode != 0) return found;
        for (int n = 1; n <= MAX; n++) {
            String line = result.info.get("removed_" + n);
            if (line == null) break;
            String[] parts = line.split(",", -1);
            if (parts.length != 3 || !ID.matcher(parts[0]).matches() || !VERSION.matcher(parts[1]).matches()) continue;
            String name = result.info.get("removed_" + n + "_name");
            String summary = result.info.get("removed_" + n + "_summary");
            found.add(new RemovedModule(parts[0], name != null && name.length() > 0 ? name : parts[0],
                    summary == null ? "" : summary, parts[1], ICON.matcher(parts[2]).matches() ? parts[2] : "box"));
        }
        return Collections.unmodifiableList(found);
    }
}
