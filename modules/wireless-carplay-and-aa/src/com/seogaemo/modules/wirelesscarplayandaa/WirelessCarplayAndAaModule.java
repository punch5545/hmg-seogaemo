package com.seogaemo.modules.wirelesscarplayandaa;

import com.seogaemo.app.R;
import com.seogaemo.app.module.Module;
import com.seogaemo.app.module.RootBackedModule;
import com.seogaemo.app.module.InfoRow;
import com.seogaemo.app.module.ModuleAction;
import com.seogaemo.app.module.HomeTile;
import com.seogaemo.app.module.Warning;

/** Placeholder only: wireless projection requires separate research. */
public final class WirelessCarplayAndAaModule extends RootBackedModule {
    public String id() { return "wireless-carplay-and-aa"; }
    public int nameResId() { return R.string.wireless_carplay_and_aa_name; }
    public int descriptionResId() { return R.string.wireless_carplay_and_aa_description; }
    public String settingsCategory() { return "wireless"; }
    public boolean hasHomeAction() { return false; }
    public boolean requiresRoot() { return true; }
    public Setting[] settings() { return new Setting[0]; }
    public String[] ownedProperties() { return new String[0]; }
    public String[] conflicts() {
        return new String[] { "hmgapi.wireless.carplay.aa", "leafia.wireless.carplay.aa", "nemesis.wireless.projection" };
    }
    public RestartRequirement restartRequirement() { return RestartRequirement.HEAD_UNIT; }
    public String checkCompatibility() { return "not implemented"; }
    public String checkConflicts() { return "not implemented"; }
    public String[] ownedFiles() { return new String[0]; }
    public InfoRow[] infoRows() { return new InfoRow[0]; }
    public int[] notes() { return new int[0]; }
    public ModuleAction[] actions() { return new ModuleAction[0]; }
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
    public Warning[] enableWarnings() { return new Warning[0]; }
}
