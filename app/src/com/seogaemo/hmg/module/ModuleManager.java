package com.seogaemo.hmg.module;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Orders built-in operations; the root dispatcher checks the same relations. */
public final class ModuleManager {
    private final Context context;
    public ModuleManager() { this(null); }
    public ModuleManager(Context context) { this.context = context; }

    public ModulePlan plan(String id, boolean enable) {
        ModuleRegistry.depends(id);
        String[] order = ModuleRegistry.orderedIds();
        Map<String, String[]> dependencies = new HashMap<String, String[]>();
        Map<String, String[]> exclusions = new HashMap<String, String[]>();
        for (String candidate : order) {
            dependencies.put(candidate, ModuleRegistry.depends(candidate));
            exclusions.put(candidate, ModuleRegistry.excludes(candidate));
        }
        Set<String> related = ModulePlanner.related(id, order, dependencies, exclusions);
        Set<String> on = new HashSet<String>();
        Set<String> desired = new HashSet<String>();
        for (String candidate : order) {
            RootResult status = RootClient.run(candidate, "status");
            if (status.desiredEnabled()) desired.add(candidate);
            if (status.exitCode != 0 || !("off".equals(status.state) ||
                    "on".equals(status.state) || "pending-restart".equals(status.state))) {
                // An unrelated module blocked by its own conflict cannot change this plan.
                boolean blocked = ("conflict".equals(status.state) && status.exitCode == 3) ||
                        ("incompatible".equals(status.state) && status.exitCode == 4) ||
                        "not-implemented".equals(status.state);
                if (blocked && !related.contains(candidate)) continue;
                throw new IllegalStateException("Could not read " + candidate + " state: " + status.detail);
            }
            if ("on".equals(status.state) ||
                    ("pending-restart".equals(status.state) && !"off".equals(status.info.get("desired"))))
                on.add(candidate);
        }
        return ModulePlanner.plan(id, enable, order, dependencies, exclusions, on, desired);
    }

    public RootResult execute(ModulePlan plan) {
        RootResult last = new RootResult("off", "none", "", 0, null);
        String restart = "none";
        List<RootResult.StepOutcome> outcomes = new ArrayList<RootResult.StepOutcome>();
        for (ModulePlan.Step step : plan.steps) {
            last = RootClient.run(step.id, step.enable ? "apply" : "revert");
            outcomes.add(new RootResult.StepOutcome(step.id, step.enable, last));
            if ("head_unit".equals(last.restart) ||
                    ("adbd".equals(last.restart) && "none".equals(restart))) restart = last.restart;
            if (last.exitCode != 0 || "not-implemented".equals(last.state)) break;
        }
        return RootResult.combined(last, restart, outcomes);
    }

    public RootResult requestEnable(String id, String resumeIntent) {
        try { return requestEnable(id, resumeIntent, plan(id, true)); }
        catch (IllegalStateException error) {
            return new RootResult("failed", "none", error.getMessage(), 1, null);
        }
    }

    public RootResult requestEnable(String id, String resumeIntent, ModulePlan plan) {
        ModuleRegistry.depends(id);
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("module_resume", Context.MODE_PRIVATE);
            if (!prefs.edit().putString("module", id).putString("intent", resumeIntent)
                    .putString("boot", ModuleResume.bootId()).commit())
                return new RootResult("failed", "none", "Could not save resume intent", 1, null);
        }
        RootResult result;
        try {
            result = plan.steps.isEmpty() ? RootClient.run(id, "status") : execute(plan);
        }
        catch (IllegalStateException error) {
            result = new RootResult("failed", "none", error.getMessage(), 1, null);
        }
        if (context != null && (result.exitCode != 0 || !"head_unit".equals(result.restart)))
            context.getSharedPreferences("module_resume", Context.MODE_PRIVATE).edit().clear().commit();
        return result;
    }
}
