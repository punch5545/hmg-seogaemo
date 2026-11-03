package com.seogaemo.app.module;

import java.util.Collections;

/** Planning and execution entry points; behavior awaits the runtime implementation. */
public final class ModuleManager {
    public ModulePlan plan(String id, boolean enable) {
        ModuleRegistry.depends(id); // Reject unknown IDs using the generated registry.
        return new ModulePlan(Collections.singletonList(new ModulePlan.Step(id, enable)),
                Collections.<Warning>emptyList());
    }

    public RootResult execute(ModulePlan plan) {
        return RootResult.notImplemented();
    }

    public RootResult requestEnable(String id, String resumeIntent) {
        return RootResult.notImplemented();
    }
}
