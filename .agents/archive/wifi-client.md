Source: wifi-client.md. Historical: upstream Leafia/HMG-API Wi-Fi research and the rejected core-feature alternative; not current state.

## Upstream research

**Identity.** `NEEV.KOR.260902` parses as Hyundai Ioniq 5 KOR firmware 260902 (`HMG-API/.../SystemCheck.java:49-55,76-90`).
Leafia added the car in `be8118e4708910037540edc09b64435514ffdfad` (v1.0.0) but keeps `patches/neev_kr/` outside
`patches/cars`; its README says no Korean unit checked showed working Wi-Fi hardware
(`HMG-Leafia-Plus/patches/README.md:9-15`, `README.md:72-76`). Leafia checks `/sys/class/net/wlan*` or the DHD
power-failure message (`.../packages/wireless-carplay-aa/scripts/common.sh:192-210`).

**Leafia hotspot correction** (`fc88c0770eb94ca44a3ca2ff4a6aa8d761c67883`, tags v1.0.1+,
`.../comp/1_projection/scripts/common.sh:350-373,441-445,548-554`): comments out an active stock
`-/system/etc/permissions/com.hkmc.software.wifihotspot.xml` line and adds missing hotspot and Wi-Fi feature XMLs, only if
the removal line was active and the files absent; conditional on observed files, not KOR-specific. v1.0.0 already left
`persist.wifi.state`, `wifi_on`, `wpa_supplicant` and overlay untouched (tag `v1.0.0`, same path, lines 498-517),
dropping the client setup inherited from NEMESIS `wireless.projection`
(`hmg_nemesis/Sources/packages/wireless-projection/scripts/common.sh:534-557`: `persist.wifi.state=3`, `wifi_on=1`, client
XML, P2P config, `wpa_supplicant.conf` `p2p_disabled=0`, overlay `p2p_no_go_freq` removed; common.sh:394-469,519-585).
Commit `b29aa3fec7e5bda669c41f10a6d443babdf3868b` (v1.0.3) added the `LEAFIA-WIFI-CLIENT` opt-in: `persist.wifi.state=3` in
`/system/build.prop` and live, plus `android.hardware.wifi.xml` (`.../common.sh:447-459,541-546`); Leafia README labels
simultaneous client+hotspot untested (`README.md:29-31,50`). Leafia snapshots/restores build.prop, feature config, permission
files, properties and `wifi_on` (`.../common.sh:466-512,567-615`) and reboots after install (`scripts/post.sh:43-61`,
`comp/1_projection/scripts/post.sh:5-17`). No reviewed correction loads a kernel module, edits `hostapd`, starts
`wpa_supplicant`, or calls an init Wi-Fi service.

**HMG-API.** Generic mod request `"wifi":{"client":true}` = `android.hardware.wifi.xml` + commented removal line in
`/system/onebin/{onebin}/featurexml.cfg` + `persist.wifi.state=3` (`Engine.java:444-504`; `docs/mod-reference.md:278-284`).
The bundled `wireless-carplay-aa` mod does not request client mode (its `test.sh:47-57` asserts none), comments the
hotspot removal line, and lists only USA/CAN (`mod.json:11-55,68-125`; `patches.json:2-45`). So HMG-API gives a
generic mechanism, not a shipped NEEV.KOR.260902 fix. Its engine rebuilds shared feature files from stock and restores
properties on release (`Engine.java:446-504,1691-1725`), so independent edits to the same files can be lost on its
apply/restore; Leafia and NEMESIS also own these paths. Wireless mod owns `persist.ium.support`, bridge parts own
`persist.sys.iap2.wifi` and `persist.iap2.iden.wireless`. Its `network` declaration/`hmgapi net` keeper
(`docs/mod-reference.md:221-275`) is unused by the wireless mod.

**Client needs more than flags.** `wifi_on` requests active STA; supplicant edits concern P2P and may not be required for
STA. A working client also needs the supplicant service/config and STA-capable driver firmware/NVRAM. Reboot is needed to
validate feature changes; a live property may not reload PackageManager or restart WifiService.

**AP concurrency.** The CarPlay bridge uses the head unit's `WifiManager` AP APIs incl. `setWifiApEnabledForConnectivity`
(`HMG-API/mods/wireless-carplay-aa/src/hmgapi/mods/wireless/dipo/Bridge.java:438-462`). In AOSP, enabling STA while AP is
on calls `setHostApRunning(..., false)` first and a running AP rejects supplicant start
([WifiController](https://android.googlesource.com/platform/frameworks/opt/net/wifi/+/478dd44/service/java/com/android/server/wifi/WifiController.java),
[WifiStateMachine](https://android.googlesource.com/platform/frameworks/opt/net/wifi/+/dba342b115f9b2fedf6a42afd4586f17c1eddbe7/service/java/com/android/server/wifi/WifiStateMachine.java)).
Hyundai's vendor mode 3 may differ; simultaneous operation is unverified. Enabling STA may stop the CarPlay AP, hence the
mutual exclusion. Record AP state, `wlan0`/`p2p0`, driver interface combinations, channel and Wi-Fi logs around a STA toggle.

**Interactions.** Hotspot is `wlan0` `192.168.0.1/24`; the stock ADB switch can assign `eth0` `192.168.0.123`, breaking
hotspot DHCP (see [wireless-adb.md](wireless-adb.md)). The hotspot fix alone does not guarantee wireless CarPlay/AA
(`HMG-Leafia-Plus/README.md:21-38`). Licensing: NEMESIS-derived Leafia scripts are AGPL-3.0, Leafia original GPL-3.0,
HMG-API AGPL-3.0 (`HMG-Leafia-Plus/NOTICE.md:5-15`; `HMG-API/README.md:100`); reimplement observed behavior
independently.


**Rejected alternative.** A core "Wi-Fi hotspot support" feature outside `modules/` with `hmg-seogaemo-root wifi-enable` verbs, gated on a validated hardware sample; replaced by this module once the car showed no removal line to edit.
