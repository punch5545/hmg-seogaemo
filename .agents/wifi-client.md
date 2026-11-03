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
warning, firmware/chip/owner gates, and drift conflict. It runs module
commands with a restricted PATH that supplies only the documented head-unit
tools and failing stubs for unavailable tools. `make test` discovers and runs `modules/*/test/run.sh`, including this test.
`tools/test-package.py` checks package metadata and layout without the former
shared `not-implemented` assertion.

On the car: capture both original files (including mode/owner),
`pm list features`, `getprop ro.build.product`, and SDIO/WLAN evidence.
Enable, inspect the one-line diff and XML, reboot fully, confirm the stock
Wi-Fi Settings menu appears, then scan, join a test network, and verify
DHCP/DNS and `info.client_ip`. Disable and reboot; compare the config byte
for byte, verify XML is absent if it was absent originally, and confirm the
stock menu/AP behavior. Repeat with CarPlay active and with an unrelated
file drift to verify warnings and restore refusal. The feature flags and
STA behavior remain unverified on the physical head unit.
