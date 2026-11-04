Status: current — redesigned after the 2026-11-03 on-car read-only checks; STA behavior remains unverified.

# Wi-Fi client module implementation

On-car facts (NEEV.KOR.0000.V039.001.260902, 2026-11-03): stock
`/system/onebin/neev_kr/featurexml.cfg` has no `android.hardware.wifi.xml`
removal line, `/system/etc/permissions/android.hardware.wifi.xml` is absent,
and `pm list features` lacks `android.hardware.wifi`. Stock
`HKMC_WifiSettings` contains `WifiClientSettings`/`WifiClientModeEnabler` and
calls `hasSystemFeature` with `android.hardware.wifi`. No file under `/system`
(lib, bin, xbin, app, priv-app, framework, etc) or the init scripts contains
`persist.wifi` or `wifi.state`. The unit has no `wpa_cli`. adbd listens on
`0.0.0.0:5555`; iptables INPUT policy is ACCEPT with an empty `fw_wlan_INPUT`
chain while the AP is up (STA-mode rules unverified).

The module owns `/system/etc/permissions/android.hardware.wifi.xml`
(`owns_files`, `sg_file_put`) and `persist.wifi.state` (`owns_props`,
`sg_prop_set ... 3`). It claims no `featurexml.cfg` line. precheck, status and
apply all refuse an existing XML or a non-empty `persist.wifi.state` that the
module did not write. HMG-API's presence alone is not a conflict (its bundled
wireless mod owns neither target), so the module can be tested while HMG-API
still provides the AP and adb. `status` reports `info.client_ip` and
`info.client_adb_endpoint` for any non-AP IPv4 address on `wlan0`.

The pre-enable warning uses the sibling module's root `status` (`on` or
`pending-restart`) or a cheap AP check: `init.svc.hostapd=running` or
`wlan0` at `192.168.0.1`. These are read-only indicators and can miss a
projection session on other interface/address setups. The IP parser uses mksh builtins because `awk` is absent on the unit.
PackageManager's `android.hardware.wifi` feature distinguishes applied files
awaiting a reboot from an observed feature. The module cannot infer actual
Settings menu visibility from the feature list.

`modules/wifi-client/test/run.sh` uses a private fake `common.sh`, fake
`getprop`/`setprop`/`pm`/`ifconfig`/`netcfg`, and fake system/sys roots. It covers
off/apply/reapply/on/revert, property restore, XML absence, untouched featurexml.cfg, IP,
warning, firmware/capability/owner gates, and drift conflict. It runs module
commands with a restricted PATH that supplies only the documented head-unit
tools and failing stubs for unavailable tools. `make test` discovers and runs `modules/*/test/run.sh`, including this test.
`tools/test-package.py` checks package metadata and layout without the former
shared `not-implemented` assertion.

On the car: capture both original files (including mode/owner),
`pm list features`, `getprop ro.build.product`, `getprop tcc.wifi.vendor`,
the driver/firmware file presence, and SDIO/WLAN evidence.
Enable, inspect the one-line diff and XML, reboot fully, confirm the stock
Wi-Fi Settings menu appears, then scan, join a test network, and verify
DHCP/DNS and `info.client_ip`. Disable and reboot; compare the config byte
for byte, verify XML is absent if it was absent originally, and confirm the
stock menu/AP behavior. Repeat with CarPlay active and with an unrelated
file drift to verify warnings and restore refusal. The feature flags and
STA behavior remain unverified on the physical head unit.

## Powered-off capability check (2026-11-04, F6)

The owner's live checks found empty `/sys/bus/sdio/devices/` and no
`/sys/class/net/wlan*` while the radio was off on an otherwise working unit.
That enumeration describes current power/driver state, so it cannot gate
initial enable. The firmware glob remains `NEEV.KOR.*.260902`. Both precheck
and apply now require `tcc.wifi.vendor=broadcom`, plus nonempty regular files
`/system/wifi/bcmdhd.ko` and `/system/wifi/fw_bcmdhd.bin`. They do not require
`wlan.driver.status`, a current interface, or the current firmware mode. Missing
vendor/configuration or missing/empty driver/firmware fails incompatible before
any writes, even with live SDIO/WLAN entries. This establishes the supported
stock software capability; it cannot diagnose a physically failed radio.

Read-only provenance in the original checkout's gitignored car dumps:

- `tmp/car-sessions/2026-11-04-devdump/runtime/root/rootfs_init.tcc803x.rc.txt`
  imports the Broadcom init file (line 4) and writes
  `/system/wifi/fw_bcmdhd.bin` to `/data/misc/wifi/firmware_path` (lines 105/203).
- `runtime/root/rootfs_init.tcc803x.wifi.broadcom.rc.txt` sets
  `tcc.wifi.vendor=broadcom` on boot (line 2), declares disabled one-shot
  `wpa_supplicant`/`p2p_supplicant` services for `wlan0`, and does not insert
  the driver during boot.
- Strings in `system/lib/libhardware_legacy.so` name
  `/system/wifi/bcmdhd.ko`, driver insertion messages, and the ordinary
  `iface_name=wlan0 firmware_path=/data/misc/wifi/fw_bcmdhd.bin ...` arguments.
  The dump contains the nonempty driver and normal firmware at those stock
  system paths.
- `runtime/cmd/getprop.txt:516` and
  `tmp/car-sessions/2026-11-03-baseline/props/getprop.txt:516` both report
  `tcc.wifi.vendor=broadcom`; `tcc.wifi.firmware.mode=softap` and
  `wlan.driver.status=ok` describe the captured active state, not capability.

The regression starts with both fake enumeration roots empty and verifies
precheck/apply succeed, then tests missing/empty driver and firmware and
missing/wrong vendor with live entries present. It asserts rejection causes
no XML/property writes. The powered-off fixture fails on the former probe.
No private dump content or device identifiers were copied into fixtures.
