package com.seogaemo.hmg.module;

/** Metadata for a module shortcut on Home. Actions are dispatched by the UI runtime. */
public final class HomeTile {
    public final String id;
    public final int labelResId;
    public final String glyph;
    public final ModuleAction action;
    public final int order;
    public final boolean alwaysVisible;

    public HomeTile(String id, int labelResId, String glyph, ModuleAction action,
            int order, boolean alwaysVisible) {
        this.id = id;
        this.labelResId = labelResId;
        this.glyph = glyph;
        this.action = action;
        this.order = order;
        this.alwaysVisible = alwaysVisible;
    }
}
