package com.seogaemo.modules.wificlient;

import com.seogaemo.app.R;
import com.seogaemo.app.module.Module;
import com.seogaemo.app.module.RootBackedModule;
import com.seogaemo.app.module.InfoRow;
import com.seogaemo.app.module.ModuleAction;
import com.seogaemo.app.module.Warning;

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
}
