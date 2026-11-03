package com.seogaemo.app.module;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Orders built-in operations; the root dispatcher checks the same relations. */
public final class ModuleManager {
    private final Context context;
    public ModuleManager() { this(null); }
    public ModuleManager(Context context) { this.context = context; }

    public ModulePlan plan(String id, boolean enable) {
        ModuleRegistry.depends(id);
        String[] order = ModuleRegistry.orderedIds();
        Set<String> on = new HashSet<String>();
        for (String candidate : order) {
            RootResult status = RootClient.run(candidate, "status");
            if (status.exitCode != 0 || !("off".equals(status.state) ||
                    "on".equals(status.state) || "pending-restart".equals(status.state)))
                throw new IllegalStateException("Could not read " + candidate + " state: " + status.detail);
            if ("on".equals(status.state) || "pending-restart".equals(status.state)) on.add(candidate);
        }
        Map<String, String[]> dependencies = new HashMap<String, String[]>();
        Map<String, String[]> exclusions = new HashMap<String, String[]>();
        for (String candidate : order) {
            dependencies.put(candidate, ModuleRegistry.depends(candidate));
            exclusions.put(candidate, ModuleRegistry.excludes(candidate));
        }
        return ModulePlanner.plan(id, enable, order, dependencies, exclusions, on);
    }

    public RootResult execute(ModulePlan plan) {
        RootResult last = new RootResult("off", "none", "", 0, null);
        for (ModulePlan.Step step : plan.steps) {
            last = RootClient.run(step.id, step.enable ? "apply" : "revert");
            if (last.exitCode != 0 || "not-implemented".equals(last.state)) return last;
        }
        return last;
    }

    public RootResult requestEnable(String id, String resumeIntent) {
        ModuleRegistry.depends(id);
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("module_resume", Context.MODE_PRIVATE);
            if (!prefs.edit().putString("module", id).putString("intent", resumeIntent).commit())
                return new RootResult("failed", "none", "Could not save resume intent", 1, null);
        }
        RootResult result;
        try { result = execute(plan(id, true)); }
        catch (IllegalStateException error) {
            result = new RootResult("failed", "none", error.getMessage(), 1, null);
        }
        if (result.exitCode != 0 && context != null)
            context.getSharedPreferences("module_resume", Context.MODE_PRIVATE).edit().clear().commit();
        return result;
    }
}
