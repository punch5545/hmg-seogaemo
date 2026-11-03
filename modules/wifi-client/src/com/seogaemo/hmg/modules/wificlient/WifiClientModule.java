package com.seogaemo.hmg.modules.wificlient;

import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.Module;
import com.seogaemo.hmg.module.RootBackedModule;
import com.seogaemo.hmg.module.InfoRow;
import com.seogaemo.hmg.module.ModuleAction;
import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.Warning;

/** Stock Wi-Fi client feature for supported NEEV Korean firmware. */
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
    public String checkCompatibility() { return null; }
    public String checkConflicts() { return null; }
    public String[] ownedFiles() {
        return new String[] {
            "/system/etc/permissions/android.hardware.wifi.xml",
            "/system/onebin/neev_kr/featurexml.cfg"
        };
    }
    public InfoRow[] infoRows() {
        return new InfoRow[] { new InfoRow(R.string.wifi_client_ip, "client_ip") };
    }
    public int[] notes() { return new int[] { R.string.wifi_client_note }; }
    public ModuleAction[] actions() {
        return new ModuleAction[] {
            new ModuleAction(R.string.wifi_client_open_settings,
                ModuleAction.Type.OPEN_STOCK_WIFI_SETTINGS, null)
        };
    }
    public Warning[] enableWarnings() {
        return new Warning[] { new Warning("carplay_active", R.string.wifi_client_carplay_warning) };
    }
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
}
