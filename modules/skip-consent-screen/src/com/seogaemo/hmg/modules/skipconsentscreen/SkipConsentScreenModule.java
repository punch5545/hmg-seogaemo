package com.seogaemo.hmg.modules.skipconsentscreen;

import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.RootBackedModule;
import com.seogaemo.hmg.module.InfoRow;
import com.seogaemo.hmg.module.ModuleAction;
import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.Warning;

/** Startup notice behavior through the package-owned Binder call and watcher. */
public final class SkipConsentScreenModule extends RootBackedModule {
    public String id() { return "skip-consent-screen"; }
    public int nameResId() { return R.string.skip_consent_screen_name; }
    public int descriptionResId() { return R.string.skip_consent_screen_description; }
    public String icon() { return "skip"; }
    public String settingsCategory() { return "general"; }
    public boolean hasHomeAction() { return false; }
    public boolean requiresRoot() { return true; }
    public Setting[] settings() { return new Setting[0]; }
    public String[] ownedProperties() { return new String[0]; }
    public String[] ownedFiles() { return new String[0]; }
    public String[] conflicts() { return new String[] { "local.hkmc.factory82" }; }
    public InfoRow[] infoRows() { return new InfoRow[0]; }
    public int[] notes() { return new int[] { R.string.skip_consent_screen_note, R.string.skip_consent_screen_restore_note }; }
    public ModuleAction[] actions() { return new ModuleAction[0]; }
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
    public Warning[] enableWarnings() { return new Warning[0]; }
    public RestartRequirement restartRequirement() { return RestartRequirement.HEAD_UNIT; }
    public boolean rebootRestore() { return true; }
    public String checkCompatibility() { return null; }
    public String checkConflicts() { return null; }
}
