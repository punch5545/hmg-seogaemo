# Wi-Fi client module implementation

Implemented against `.agents/module-runtime-contract.md`, including the
2026-11-03 applied-file marker revision. This is an independent implementation
of the observed feature-file behavior; no upstream source was copied.

On `NEEV.KOR.*.260902`, apply changes exactly one active line in
`/system/onebin/neev_kr/featurexml.cfg` from
`-/system/etc/permissions/android.hardware.wifi.xml` to
`#-/system/etc/permissions/android.hardware.wifi.xml`. It adds
`/system/etc/permissions/android.hardware.wifi.xml` with the standard
`android.hardware.wifi` feature declaration. No property changes are made.
In particular, `persist.wifi.state=3` remains a later experiment; see
`.agents/research-wifi-client.md` and `.agents/research-wifi-enable.md`.

Apply requires the expected single stock line, a physical SDIO device or
`wlan*` interface, no known HMG-API/Leafia/NEMESIS wireless projection owner,
and a compatible product string. `wlan0` alone is not required: on the
pre-mod report it was absent and can appear only after boot/service startup.
The NEMESIS wireless projection package's own installed descriptor is also
checked because the current shared `sg_owner_present` contract only names
`nemesis-adb-tcp`, HMG-API, and Leafia. Runtime also needs to enforce
its general NEMESIS 1.16.4+ minimum.

The pre-enable warning uses the sibling module's root `status` (`on` or
`pending-restart`) or a cheap AP check: `init.svc.hostapd=running` or
`wlan0` at `192.168.0.1`. These are read-only indicators and can miss a
projection session on other interface/address setups. `status` reports
`info.client_ip` only if `wpa_cli -i wlan0 status` reports
`wpa_state=COMPLETED` and `ifconfig wlan0` has a non-AP IPv4 address.
PackageManager's `android.hardware.wifi` feature distinguishes applied files
awaiting a reboot from an observed feature. The module cannot infer actual
Settings menu visibility from the feature list.

`modules/wifi-client/test/run.sh` uses a private fake `common.sh`, fake
`getprop`/`pm`/`ifconfig`/`wpa_cli`, and fake system/sys roots. It covers
off/apply/reapply/on/revert, exact config restore and XML absence, IP,
warning, firmware/chip/owner gates, and drift conflict. The current top-level
`make test` does not discover module tests; the runtime owner should add
`sh modules/wifi-client/test/run.sh` to that target (or general discovery).
The current `tools/test-package.py:63-76` also assumes every module is still
`not-implemented`; it fails when this module correctly returns an observed
state. The runtime owner needs to replace that stub assertion with module
tests or a generic status-block check.

On the car: capture both original files (including mode/owner),
`pm list features`, `getprop ro.build.product`, and SDIO/WLAN evidence.
Enable, inspect the one-line diff and XML, reboot fully, confirm the stock
Wi-Fi Settings menu appears, then scan, join a test network, and verify
DHCP/DNS and `info.client_ip`. Disable and reboot; compare the config byte
for byte, verify XML is absent if it was absent originally, and confirm the
stock menu/AP behavior. Repeat with CarPlay active and with an unrelated
file drift to verify warnings and restore refusal. The feature flags and
STA behavior remain unverified on the physical head unit.
