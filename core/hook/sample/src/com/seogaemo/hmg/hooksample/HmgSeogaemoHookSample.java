package com.seogaemo.hmg.hooksample;

import hmgapi.hook.LoadedApp;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;

/** Smallest hook module: logs from the zygote and from each listed app process (logcat tag HMG-Seogaemo-Hook). */
public final class HmgSeogaemoHookSample extends ModEntry {
    @Override public void zygoteInit(ModContext mod) {
        mod.log("zygote init, greeting=" + mod.settingString("greeting", "hello"));
    }

    @Override public void appLoaded(ModContext mod, LoadedApp app) {
        mod.log(mod.settingString("greeting", "hello") + " from " + app.processName + " (" + app.packageName + ")");
    }
}
