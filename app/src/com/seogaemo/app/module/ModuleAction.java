package com.seogaemo.app.module;

public final class ModuleAction {
    public enum Type { ENABLE_MODULE, OPEN_STOCK_WIFI_SETTINGS }

    public final int labelResId;
    public final Type type;
    public final String target;

    public ModuleAction(int labelResId, Type type, String target) {
        this.labelResId = labelResId;
        this.type = type;
        this.target = target;
    }
}
