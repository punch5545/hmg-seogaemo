package com.seogaemo.modules.wificlient;

import com.seogaemo.app.R;
import com.seogaemo.app.module.Module;
import com.seogaemo.app.module.RootBackedModule;
import com.seogaemo.app.module.InfoRow;
import com.seogaemo.app.module.ModuleAction;
import com.seogaemo.app.module.Warning;

/** Placeholder only: no file, property, or NEMESIS operation. */
public final class WifiClientModule extends RootBackedModule {
    public String id() { return "wifi-client"; }
    public int nameResId() { return R.string.wifi_client_name; }
    public int descriptionResId() { return R.string.wifi_client_description; }
    public String settingsCategory() { return "wireless"; }
    public boolean hasHomeAction() { return false; }
    public boolean requiresRoot() { return true; }
    public Setting[] settings() { return new Setting[0]; }
    public String[] ownedProperties() { return new String[0]; }
    public String[] conflicts() {
        return new String[] { "hmgapi.wifi", "leafia.wireless.carplay.aa", "nemesis.wireless.projection" };
    }
    public RestartRequirement restartRequirement() { return RestartRequirement.HEAD_UNIT; }
    public String checkCompatibility() { return "not implemented"; }
    public String checkConflicts() { return "not implemented"; }
    public String[] ownedFiles() { return new String[0]; }
    public InfoRow[] infoRows() { return new InfoRow[0]; }
    public int[] notes() { return new int[0]; }
    public ModuleAction[] actions() { return new ModuleAction[0]; }
    public Warning[] enableWarnings() { return new Warning[0]; }
}
