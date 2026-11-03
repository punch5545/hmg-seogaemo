package com.seogaemo.hmg.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure Java ordering calculation, independent of Android and root transport. */
public final class ModulePlanner {
    private ModulePlanner() { }

    public static ModulePlan plan(String id, boolean enable, String[] order,
            Map<String, String[]> dependencies, Map<String, String[]> exclusions,
            Set<String> on) {
        if (!dependencies.containsKey(id)) throw new IllegalArgumentException(id);
        Set<String> toEnable = new HashSet<String>();
        Set<String> toDisable = new HashSet<String>();
        if (enable) {
            addDependencies(id, dependencies, toEnable);
            for (String candidate : toEnable) {
                for (String excluded : exclusions.get(candidate)) {
                    if (on.contains(excluded))
                        addDependents(excluded, dependencies, on, toDisable, order);
                }
            }
        } else addDependents(id, dependencies, on, toDisable, order);
        List<ModulePlan.Step> steps = new ArrayList<ModulePlan.Step>();
        for (int i = order.length - 1; i >= 0; i--) {
            if (toDisable.contains(order[i]) && on.contains(order[i]))
                steps.add(new ModulePlan.Step(order[i], false));
        }
        for (String candidate : order) {
            if (toEnable.contains(candidate) && !on.contains(candidate))
                steps.add(new ModulePlan.Step(candidate, true));
        }
        return new ModulePlan(steps, new ArrayList<Warning>());
    }

    private static void addDependencies(String id, Map<String, String[]> dependencies,
            Set<String> found) {
        if (!found.add(id)) return;
        for (String dependency : dependencies.get(id))
            addDependencies(dependency, dependencies, found);
    }

    private static void addDependents(String id, Map<String, String[]> dependencies,
            Set<String> on, Set<String> found, String[] order) {
        if (!found.add(id)) return;
        for (String candidate : order) {
            if (!on.contains(candidate)) continue;
            for (String dependency : dependencies.get(candidate)) {
                if (dependency.equals(id))
                    addDependents(candidate, dependencies, on, found, order);
            }
        }
    }
}
