package com.seogaemo.app.module;

import com.seogaemo.app.ui.settings.SettingsPage;

/** Shared compile-only adapter for built-in root modules. */
public abstract class RootBackedModule implements Module {
    public void buildSettingsPage(SettingsPage page) {
        page.statusHeader();
        for (InfoRow row : infoRows()) page.info(row.labelResId, row.infoKey);
        for (int note : notes()) page.note(note);
        for (ModuleAction action : actions()) page.action(action.labelResId, action);
    }
    public ModuleStatus apply() { return map(RootClient.run(id(), "apply")); }
    public ModuleStatus revert() { return map(RootClient.run(id(), "revert")); }
    public ModuleStatus status() { return map(RootClient.run(id(), "status")); }

    private ModuleStatus map(RootResult result) {
        boolean on = "on".equals(result.state) || "pending-restart".equals(result.state);
        boolean pending = "pending-restart".equals(result.state);
        String error = result.exitCode == 0 ? null : result.state;
        return new ModuleStatus(on, on, pending, error, 0L);
    }
}
