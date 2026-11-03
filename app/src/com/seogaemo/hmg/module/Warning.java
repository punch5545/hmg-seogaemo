package com.seogaemo.hmg.module;

public final class Warning {
    public final String checkKey;
    public final int messageResId;

    public Warning(String checkKey, int messageResId) {
        this.checkKey = checkKey;
        this.messageResId = messageResId;
    }
}
