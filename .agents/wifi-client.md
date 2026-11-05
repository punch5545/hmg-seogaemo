Status: current — implemented off-car; STA behavior unverified on the car.

# wifi-client module

Paths in `HMG-API/`, `HMG-Leafia-Plus/`, `hmg_nemesis/` citations are relative to `tmp/`.
"Wi-Fi off" has three meanings: the chip/radio, the Android client (STA) and its Settings menu, and the
hotspot (AP) used by wireless CarPlay/Android Auto.

## Design

- Off by default. Owns `/system/etc/permissions/android.hardware.wifi.xml` (`owns_files`, `sg_file_put`) and
  `persist.wifi.state` (`owns_props`, `sg_prop_set ... 3`). Claims no `featurexml.cfg` line.
- precheck, status and apply refuse an existing XML or a non-empty `persist.wifi.state` the module did not write.
  HMG-API presence alone is not a conflict (its wireless mod owns neither target), so this can be tested while
  HMG-API still provides AP and adb.
- `status` reports `info.client_ip` and `info.client_adb_endpoint` for any non-AP IPv4 address on `wlan0` (parser uses
  mksh builtins; no `awk`). The PackageManager `android.hardware.wifi` feature separates "applied, awaiting reboot"
  from "observed"; it cannot show actual Settings menu visibility.
- Pre-enable warning uses the sibling module's root `status` (`on`/`pending-restart`) or a cheap AP check
  (`init.svc.hostapd=running` or `wlan0` at `192.168.0.1`). Read-only indicators; can miss a session on other setups.
- Mutually exclusive with `wireless-carplay-and-aa`: enabling either prompts with the modules to be disabled, including
  dependents. `wireless-adb` is independent and offers "Connect to another Wi-Fi". Network selection uses the stock menu;
  no HMG-Seogaemo picker.
- Enable warning `carplay_active`: `precheck` reports `warn.carplay_active=1` when `wireless-carplay-and-aa` is enabled or a wireless projection session/AP is active; the exclusion still turns projection off. Once applied (and after the head-unit restart) the pane offers `OPEN_STOCK_WIFI_SETTINGS`. Gate also requires NEMESIS 1.16.4+ (not runtime-probed), and `restart=HEAD_UNIT`. `persist.wifi.state=3` is set with the XML; its effect on this firmware is unverified.
- Superseded alternative (rejected): a core "Wi-Fi hotspot support" feature outside `modules/` with
  `hmg-seogaemo-root wifi-enable` verbs, gated on a validated hardware sample. Replaced by this module once the car
  showed no removal line to edit.

## Compatibility gate

- `ro.build.product` glob `NEEV.KOR.*.260902`, `tcc.wifi.vendor=broadcom`, and nonempty regular
  `/system/wifi/bcmdhd.ko` and `/system/wifi/fw_bcmdhd.bin`. Missing/wrong vendor or missing/empty driver/firmware fails
  incompatible before any write, even with live SDIO/WLAN entries.
- Not required: `wlan.driver.status`, a current interface, SDIO enumeration, current firmware mode. Owner's 2026-11-04
  (F6) check found empty `/sys/bus/sdio/devices/` and no `wlan*` while the radio was off on a working unit, so live
  enumeration cannot gate enable. This proves stock software capability only, not a healthy radio.
- Provenance (original checkout's gitignored dumps): `tmp/car-sessions/2026-11-04-devdump/runtime/root/rootfs_init.tcc803x.rc.txt`
  imports the Broadcom init file (line 4), writes `/system/wifi/fw_bcmdhd.bin` to `/data/misc/wifi/firmware_path`
  (lines 105/203); `rootfs_init.tcc803x.wifi.broadcom.rc.txt` sets `tcc.wifi.vendor=broadcom` (line 2), declares disabled
  one-shot `wpa_supplicant`/`p2p_supplicant` for `wlan0`, does not insert the driver at boot; strings in
  `system/lib/libhardware_legacy.so` name `/system/wifi/bcmdhd.ko` and
  `iface_name=wlan0 firmware_path=/data/misc/wifi/fw_bcmdhd.bin`; dump holds the nonempty driver and firmware;
  `runtime/cmd/getprop.txt:516` and `tmp/car-sessions/2026-11-03-baseline/props/getprop.txt:516` report
  `tcc.wifi.vendor=broadcom` (`tcc.wifi.firmware.mode=softap`, `wlan.driver.status=ok` are active state, not capability).

## On-car facts (NEEV.KOR.0000.V039.001.260902, 2026-11-03)

- Stock `featurexml.cfg` has no `android.hardware.wifi.xml` removal line; the XML is absent;
  `pm list features` lacks `android.hardware.wifi`.
- Stock `HKMC_WifiSettings` has `WifiClientSettings`/`WifiClientModeEnabler` and gates on `hasSystemFeature`
  `android.hardware.wifi`.
- No file under `/system` or init scripts contains `persist.wifi` or `wifi.state`. No `wpa_cli`.
- adbd listens on `0.0.0.0:5555`; iptables INPUT policy ACCEPT, empty `fw_wlan_INPUT` while the AP is up (STA rules
  unverified).
- Owner: a prior HMG-API install gave the stock hotspot menu and wireless CarPlay after reboot, but no client menu.
- Pre-install Car Report (`car-reports/car-report-neev-kor-260902-20261102-2115.json:5-16,64-96,110-118,2196-2236`):
  `wifi_chip:false`, 90 features incl. `com.hkmc.software.wifi.module`, no `android.hardware.wifi`, no `wlan0`/`p2p0`,
  `load_iptrules`, zero HMG-API rules. HMG-API detects chips via SDIO or a remembered marker
  (`HMG-API/core/runtime/java/hmgapi/engine/SystemCheck.java:136-152`), so `false` is a probe false negative, not
  evidence of no Wi-Fi (the owner's wireless CarPlay worked later).
- IPv4/IPv6 filter policies were `INPUT ACCEPT`/`OUTPUT ACCEPT`, `fw_wlan_INPUT` and `oem_out` empty; STA DHCP/DNS should
  need no new rules, but no connected-STA packet test exists. If post-install rules differ, trace counters and add only
  the demonstrated rule.

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

## Tests

`modules/wifi-client/test/run.sh`: private fake `common.sh`, fake `getprop`/`setprop`/`pm`/`ifconfig`/`netcfg`, fake
system/sys roots, restricted PATH with failing stubs for tools absent on the unit. Covers off/apply/reapply/on/revert,
property restore, XML absence, untouched `featurexml.cfg`, IP, warning, firmware/capability/owner gates, drift conflict.
The powered-off regression starts with empty enumeration roots and checks precheck/apply succeed, then missing/empty
driver/firmware and missing/wrong vendor with live entries are rejected without writes. No private dump content copied
into fixtures.

## On-car checklist

1. Read-only baseline over adb (full-root NEMESIS if needed): `getprop ro.build.product ro.mobis.targetdir
   persist.wifi.state tcc.wifi.vendor`, `settings get global wifi_on`, `ls -l` the wifi permission XMLs and
   `featurexml.cfg`, `/data/misc/wifi`, supplicant/hostapd/dhcp processes, `ifconfig`/`netcfg`/`ip route`, `iptables -S`,
   `ip6tables -S`, `logcat -d -s WifiStateMachine WifiService WifiController wpa_supplicant WifiHW`, `dumpsys wifi`,
   `pm list features`, driver/firmware presence, SDIO/WLAN evidence, exact bytes/mode/owner of both files, AP/CarPlay and
   DHCP baseline. Toolbox may lack `ip`/`dumpsys wifi`; fall back to `netcfg`; never assume `tail`.
2. Check no leftover HMG-API/Leafia/NEMESIS owner claims the files.
3. Enable, inspect the planned diff and XML, reboot fully, confirm the stock Wi-Fi menu appears, scan/join a test network,
   verify DHCP/DNS and `info.client_ip`. Log interfaces, routes and firewall counters on any failure.
4. Confirm the UI lists `wireless-carplay-and-aa` and its dependents when switching to client, and `wifi-client` when
   switching back; `wireless-adb` is independent.
5. Repeat apply+reboot, disable, update, uninstall; compare restored files byte for byte (XML absent if originally absent);
   verify menu/AP/CarPlay behavior. Test with CarPlay active and with unrelated file drift (warning, restore refusal). Keep
   backups on restore failure.
