package com.seogaemo.hmg.modules.wirelesscarplayandaa;

import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.Module;
import com.seogaemo.hmg.module.RootBackedModule;
import com.seogaemo.hmg.module.InfoRow;
import com.seogaemo.hmg.module.ModuleAction;
import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.Warning;

/** Wireless projection on the verified NEEV.KOR.260902 firmware. */
public final class WirelessCarplayAndAaModule extends RootBackedModule {
    public String id() { return "wireless-carplay-and-aa"; }
    public int nameResId() { return R.string.wireless_carplay_and_aa_name; }
    public int descriptionResId() { return R.string.wireless_carplay_and_aa_description; }
    public String settingsCategory() { return "wireless"; }
    public boolean hasHomeAction() { return false; }
    public boolean requiresRoot() { return true; }
    public Setting[] settings() { return new Setting[0]; }
    public String[] ownedProperties() { return new String[] { "persist.ium.support", "persist.sys.iap2.wifi", "persist.iap2.iden.wireless" }; }
    public String[] conflicts() {
        return new String[] { "hmgapi.wireless.carplay.aa", "leafia.wireless.carplay.aa", "nemesis.wireless.projection" };
    }
    public RestartRequirement restartRequirement() { return RestartRequirement.HEAD_UNIT; }
    public String checkCompatibility() { return null; }
    public String checkConflicts() { return null; }
    public String[] ownedFiles() { return new String[] {
        "/system/bin/app_process",
        "/system/bin/app_process.seogaemo-stock",
        "/system/lib/libseogaemo_wireless_hook.so",
        "/system/framework/seogaemo-wireless-hook.jar",
        "/system/lib/libleafiaaac.so",
        "/system/onebin/neev_kr/featurexml.cfg",
        "/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml",
        "/system/etc/permissions/com.hkmc.software.connectivity.androidauto_wireless.xml",
        "/system/etc/permissions/com.hkmc.software.wifihotspot.xml",
        "/system/etc/seogaemo/wireless-carplay-and-aa/car-map.json",
        "/system/etc/seogaemo/wireless-carplay-and-aa/recipes/aa-tbt-48ee793a.recipe",
        "/system/etc/seogaemo/wireless-carplay-and-aa/recipes/aa-tbt-a0bea1c4.recipe",
        "/system/etc/seogaemo/wireless-carplay-and-aa/recipes/carplay-core-wifi.recipe",
        "/system/etc/seogaemo/wireless-carplay-and-aa/recipes/carplay-audio-formats.recipe",
        "/system/etc/seogaemo/wireless-carplay-and-aa/recipes/carplay-utils-converter.recipe",
        "/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/enable",
        "/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/enabled",
        "/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/starts",
        "/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/disable"
    }; }
    public InfoRow[] infoRows() { return new InfoRow[0]; }
    public int[] notes() { return new int[0]; }
    public ModuleAction[] actions() { return new ModuleAction[0]; }
    public HomeTile[] homeTiles() { return new HomeTile[0]; }
    public Warning[] enableWarnings() { return new Warning[0]; }
}
