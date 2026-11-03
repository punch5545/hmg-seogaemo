package com.seogaemo.app.module;

/** Shared compile-only adapter for built-in root modules. */
public abstract class RootBackedModule implements Module {
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
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
