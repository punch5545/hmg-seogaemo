package com.seogaemo.modules.wirelessadb;

import com.seogaemo.app.R;
import com.seogaemo.app.module.RootBackedModule;
import com.seogaemo.app.module.RootClient;
import com.seogaemo.app.module.RootResult;
import com.seogaemo.app.module.InfoRow;
import com.seogaemo.app.module.ModuleAction;
import com.seogaemo.app.module.HomeTile;
import com.seogaemo.app.module.Warning;

/** Property-only TCP ADB module. The runtime renders its wireless pane. */
public final class WirelessAdbModule extends RootBackedModule {
    public String id() { return "wireless-adb"; }
    public int nameResId() { return R.string.wireless_adb_name; }
    public int descriptionResId() { return R.string.wireless_adb_description; }
    public String settingsCategory() { return "wireless"; }
    public boolean hasHomeAction() { return false; }
    public boolean requiresRoot() { return true; }
    public Setting[] settings() { return new Setting[0]; }
    public String[] ownedProperties() {
        return new String[] { "persist.service.adb.tcp.enable", "persist.adb.tcp.port" };
    }
    public String[] conflicts() {
        return new String[] { "adb.tcp", "hmgapi.adb", "leafia.wireless.carplay.aa" };
    }
    public RestartRequirement restartRequirement() { return RestartRequirement.ADBD; }
    public String checkCompatibility() { return null; }
    public String checkConflicts() { return null; }
    public String[] ownedFiles() { return new String[0]; }
    public InfoRow[] infoRows() {
        InfoRow[] hotspot = new InfoRow[] {
            new InfoRow(R.string.wireless_adb_hotspot_ssid, "hotspot_ssid"),
            new InfoRow(R.string.wireless_adb_hotspot_ip, "hotspot_ip"),
            new InfoRow(R.string.wireless_adb_adb_endpoint, "adb_endpoint")
        };
        RootResult wifi = RootClient.run("wifi-client", "status");
        if (!"on".equals(wifi.state) && !"pending-restart".equals(wifi.state)) {
            return hotspot;
        }
        return new InfoRow[] {
            hotspot[0], hotspot[1], hotspot[2],
            new InfoRow(R.string.wireless_adb_client_ip, "client_ip"),
            new InfoRow(R.string.wireless_adb_client_adb_endpoint, "client_adb_endpoint")
        };
    }
    public int[] notes() {
        return new int[] {
            R.string.wireless_adb_connect_note,
            R.string.wireless_adb_security_note,
            R.string.wireless_adb_restore_note
        };
    }
    public ModuleAction[] actions() {
        return new ModuleAction[] {
            new ModuleAction(R.string.wireless_adb_connect_other_wifi,
                    ModuleAction.Type.ENABLE_MODULE, "wifi-client")
        };
    }
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
    public Warning[] enableWarnings() { return new Warning[0]; }
}
