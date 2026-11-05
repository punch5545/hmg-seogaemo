Status: current — implemented off-car; STA behavior unverified on the car.

# wifi-client module

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

## On-car facts (NEEV.KOR.0000.V039.001.260902, verified 2026-11-03)

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

## Client and AP limits

- A working client needs more than flags: `wifi_on` requests active STA; supplicant edits concern P2P and may not be required for STA. It also needs the supplicant service/config and STA-capable driver firmware/NVRAM. A reboot is needed to validate feature changes; a live property may not reload PackageManager or restart WifiService.
- AP concurrency: the CarPlay bridge uses the head unit's `WifiManager` AP APIs. In AOSP, enabling STA while AP is on stops the AP first and a running AP rejects supplicant start. Hyundai's vendor mode 3 may differ; simultaneous operation is unverified. Enabling STA may stop the CarPlay AP, hence the mutual exclusion. Record AP state, `wlan0`/`p2p0`, driver interface combinations, channel and Wi-Fi logs around a STA toggle.
- Hotspot is `wlan0` `192.168.0.1/24`; the stock ADB switch can assign `eth0` `192.168.0.123`, breaking hotspot DHCP (see [wireless-adb.md](wireless-adb.md)).

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
