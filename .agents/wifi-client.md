Status: current — implemented off-car; physical-car behavior remains unverified.

# Wi-Fi client module implementation

The feature XML has exclusive `owns_files` ownership. The exact stock removal
line in `featurexml.cfg` is declared in `owns_lines.1`; the shared runtime
composes it from one stock baseline with other modules' distinct line patches.
The projection exclusion remains for STA versus AP on one Wi-Fi radio, not for
file sharing. The build and runtime reject overlapping stock-line claims.

Implemented against `.agents/module-runtime-contract.md`, including the
2026-11-03 package mutation API. This is an independent implementation
of the observed feature-file behavior; no upstream source was copied.

On `NEEV.KOR.*.260902`, apply changes exactly one active line in
`/system/onebin/neev_kr/featurexml.cfg` from
`-/system/etc/permissions/android.hardware.wifi.xml` to
`#-/system/etc/permissions/android.hardware.wifi.xml`. It adds
`/system/etc/permissions/android.hardware.wifi.xml` with the standard
`android.hardware.wifi` feature declaration. No property changes are made.
The XML ships in `modules/wifi-client/payload/`; the module calls
`sg_file_put` and `sg_file_replace_line`, and `sg_revert_all` performs
guarded exact restore. The module reads files directly but never writes
system files itself.
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
`wpa_state=COMPLETED` and `ifconfig wlan0` or `netcfg` has a non-AP IPv4
address. The parser uses mksh builtins because `awk` is absent on the unit.
PackageManager's `android.hardware.wifi` feature distinguishes applied files
awaiting a reboot from an observed feature. The module cannot infer actual
Settings menu visibility from the feature list.

`modules/wifi-client/test/run.sh` uses a private fake `common.sh`, fake
`getprop`/`setprop`/`pm`/`ifconfig`/`netcfg`/`wpa_cli`, and fake system/sys roots. It covers
off/apply/reapply/on/revert, exact config restore and XML absence, IP,
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
