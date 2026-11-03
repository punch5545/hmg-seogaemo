package com.seogaemo.app.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModulePlan {
    public static final class Step {
        public final String id;
        public final boolean enable;

        public Step(String id, boolean enable) {
            this.id = id;
            this.enable = enable;
        }
    }

    public final List<Step> steps;
    public final List<Warning> warnings;

    public ModulePlan(List<Step> steps, List<Warning> warnings) {
        this.steps = Collections.unmodifiableList(new ArrayList<Step>(steps));
        this.warnings = Collections.unmodifiableList(new ArrayList<Warning>(warnings));
    }
}
