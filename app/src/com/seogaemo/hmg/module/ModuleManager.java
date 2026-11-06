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
    public interface StepListener {
        void started(int index, ModulePlan.Step step);
        void finished(int index, ModulePlan.Step step, RootResult result);
    }

    /** A related module whose status is transiently unreadable (adbd restart) is re-read this often. */
    static final int STATUS_ATTEMPTS = 4;
    static final long STATUS_RETRY_MS = 1500L;

    private final Context context;
    public ModuleManager() { this(null); }
    public ModuleManager(Context context) { this.context = context; }

    /** MainActivity.applyLocale() sets the default locale to the chosen UI language. */
    private static String language() {
        String language = java.util.Locale.getDefault().getLanguage();
        return language != null && language.matches("[a-z]{2}") ? language : "en";
    }

    private static boolean readable(RootResult status) {
        return status.exitCode == 0 && ("off".equals(status.state) ||
                "on".equals(status.state) || "pending-restart".equals(status.state));
    }

    private static RootResult readStatus(String id, boolean related) {
        RootResult status = RootClient.run(id, "status");
        for (int attempt = 1; related && attempt < STATUS_ATTEMPTS && !readable(status); attempt++) {
            boolean settled = ("conflict".equals(status.state) && status.exitCode == 3) ||
                    ("incompatible".equals(status.state) && status.exitCode == 4) ||
                    "not-implemented".equals(status.state);
            if (settled) break;
            try { Thread.sleep(STATUS_RETRY_MS); } catch (InterruptedException interrupted) { break; }
            status = RootClient.run(id, "status");
        }
        return status;
    }

    public ModulePlan plan(String id, boolean enable) {
        // Installed packages take part in ordering and exclusions even before screens show them.
        // Refresh in the UI language: this list is shared with the screens that show module text.
        ModuleRegistry.refreshInstalled(language());
        ModuleRegistry.depends(id);
        String[] order = ModuleRegistry.orderedIds();
        Map<String, String[]> dependencies = new HashMap<String, String[]>();
        Map<String, String[]> exclusions = new HashMap<String, String[]>();
        for (String candidate : order) {
            dependencies.put(candidate, ModuleRegistry.depends(candidate));
            exclusions.put(candidate, ModuleRegistry.excludes(candidate));
        }
        final Set<String> related = ModulePlanner.related(id, order, dependencies, exclusions);
        Map<String, RootResult> statuses = ParallelReads.each(java.util.Arrays.asList(order), new ParallelReads.Read() {
            public RootResult read(String candidate) { return readStatus(candidate, related.contains(candidate)); }
        });
        Set<String> on = new HashSet<String>();
        Set<String> desired = new HashSet<String>();
        RootResult wifi = null;
        for (String candidate : order) {
            boolean linked = related.contains(candidate);
            RootResult status = statuses.get(candidate);
            if (WIFI.equals(candidate)) wifi = status;
            if (status.desiredEnabled()) desired.add(candidate);
            if (!readable(status)) {
                // Only modules linked by depends/excludes take part in this plan, so an unrelated
                // module whose state is blocked or momentarily unreadable cannot change it.
                if (!linked) continue;
                throw new IllegalStateException("Could not read " + candidate + " state: " + status.detail);
            }
            if ("on".equals(status.state) ||
                    ("pending-restart".equals(status.state) && !"off".equals(status.info.get("desired"))))
                on.add(candidate);
        }
        ModulePlan plan = ModulePlanner.plan(id, enable, order, dependencies, exclusions, on, desired);
        String other = hotspotConflict(id, enable, wifi, on);
        if (other == null) return plan;
        // Turn the other module (and its dependents) off first, then follow the requested plan.
        ModulePlan off = ModulePlanner.plan(other, false, order, dependencies, exclusions, on, desired);
        List<ModulePlan.Step> steps = new ArrayList<ModulePlan.Step>(off.steps);
        for (ModulePlan.Step step : plan.steps) {
            boolean planned = false;
            for (ModulePlan.Step existing : steps) planned |= existing.id.equals(step.id);
            if (!planned) steps.add(step);
        }
        List<Warning> warnings = new ArrayList<Warning>(off.warnings);
        warnings.addAll(plan.warnings);
        return new ModulePlan(steps, warnings);
    }

    static final String WIFI = "wifi";
    static final String CARPLAY = "wireless-carplay-and-aa";

    /**
     * Wi-Fi with the built-in radio as a client (and no USB CarPlay hotspot) leaves wireless CarPlay
     * without a hotspot: enabling either one turns the other off, as the former wifi-client exclusion did.
     * The roles come from the wifi status (info.builtin_role, info.usb_role).
     */
    public static String hotspotConflict(String id, boolean enable, RootResult wifi, Set<String> on) {
        if (!enable || wifi == null || !"client".equals(wifi.info.get("builtin_role")) ||
                "carplay_ap".equals(wifi.info.get("usb_role"))) return null;
        if (WIFI.equals(id) && on.contains(CARPLAY)) return CARPLAY;
        if (CARPLAY.equals(id) && on.contains(WIFI)) return WIFI;
        return null;
    }

    public RootResult execute(ModulePlan plan) { return execute(plan, null); }

    public RootResult execute(ModulePlan plan, StepListener listener) {
        RootResult last = new RootResult("off", "none", "", 0, null);
        String restart = "none";
        List<RootResult.StepOutcome> outcomes = new ArrayList<RootResult.StepOutcome>();
        for (int i = 0; i < plan.steps.size(); i++) {
            ModulePlan.Step step = plan.steps.get(i);
            if (listener != null) listener.started(i, step);
            last = RootClient.run(step.id, step.enable ? "apply" : "revert");
            outcomes.add(new RootResult.StepOutcome(step.id, step.enable, last));
            if (listener != null) listener.finished(i, step, last);
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
        return requestEnable(id, resumeIntent, plan, null);
    }

    public RootResult requestEnable(String id, String resumeIntent, ModulePlan plan, StepListener listener) {
        ModuleRegistry.depends(id);
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("module_resume", Context.MODE_PRIVATE);
            if (!prefs.edit().putString("module", id).putString("intent", resumeIntent)
                    .putString("boot", ModuleResume.bootId()).commit())
                return new RootResult("failed", "none", "Could not save resume intent", 1, null);
        }
        RootResult result;
        try {
            result = plan.steps.isEmpty() ? RootClient.run(id, "status") : execute(plan, listener);
        }
        catch (IllegalStateException error) {
            result = new RootResult("failed", "none", error.getMessage(), 1, null);
        }
        if (context != null && (result.exitCode != 0 || !"head_unit".equals(result.restart)))
            context.getSharedPreferences("module_resume", Context.MODE_PRIVATE).edit().clear().commit();
        return result;
    }
}
