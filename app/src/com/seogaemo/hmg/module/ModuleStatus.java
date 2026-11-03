package com.seogaemo.hmg.module;

/** Immutable status payload; fields are not populated from a device yet. */
public final class ModuleStatus {
    public final boolean desired;
    public final boolean observed;
    public final boolean pendingRestart;
    public final String errorCode;
    public final long lastOperationTime;

    public ModuleStatus(boolean desired, boolean observed, boolean pendingRestart,
            String errorCode, long lastOperationTime) {
        this.desired = desired;
        this.observed = observed;
        this.pendingRestart = pendingRestart;
        this.errorCode = errorCode;
        this.lastOperationTime = lastOperationTime;
    }
}
