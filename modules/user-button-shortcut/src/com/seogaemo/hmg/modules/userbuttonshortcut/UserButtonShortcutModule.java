package com.seogaemo.hmg.modules.userbuttonshortcut;

import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.RootBackedModule;
import com.seogaemo.hmg.module.InfoRow;
import com.seogaemo.hmg.module.ModuleAction;
import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.Warning;

/** Steering user button assignment through the guarded property API. */
public final class UserButtonShortcutModule extends RootBackedModule {
    public String id() { return "user-button-shortcut"; }
    public int nameResId() { return R.string.user_button_shortcut_name; }
    public int descriptionResId() { return R.string.user_button_shortcut_description; }
    public String icon() { return "button"; }
    public String settingsCategory() { return "general"; }
    public boolean hasHomeAction() { return false; }
    public boolean requiresRoot() { return true; }
    public Setting[] settings() { return new Setting[0]; }
    public String[] ownedProperties() { return new String[] { "persist.sys.key.swrccustom" }; }
    public String[] ownedFiles() { return new String[0]; }
    public String[] conflicts() { return new String[] { "hmgapi", "leafia" }; }
    public InfoRow[] infoRows() { return new InfoRow[0]; }
    public int[] notes() { return new int[] { R.string.user_button_shortcut_note }; }
    public ModuleAction[] actions() { return new ModuleAction[0]; }
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
    public Warning[] enableWarnings() { return new Warning[] { new Warning("assignment", R.string.user_button_shortcut_note) }; }
    public RestartRequirement restartRequirement() { return RestartRequirement.NONE; }
    public boolean rebootRestore() { return false; }
    public String checkCompatibility() { return null; }
    public String checkConflicts() { return null; }
}
