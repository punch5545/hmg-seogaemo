# Wireless ADB module findings

The module owns only `persist.service.adb.tcp.enable` and
`persist.adb.tcp.port`. Apply calls `sg_prop_set` to force the stock switch to
`0` and set port `5555`, then calls `sg_service_restart adbd` only when the
effective port changes. A nonempty `service.adb.tcp.port` overrides the
persistent port; an override other than `5555` is a conflict. Revert calls
`sg_revert_all` and keeps the baseline if live property drift or firmware
change prevents a guarded restore. It never writes `eth0` or Wi-Fi state.

The service restart is delegated to the shared package API. Android 4.4's
`ctl.restart` control property is used by NEMESIS's ADB TCP package
(`tmp/hmg_nemesis/Sources/packages/adb-tcp/scripts/common.sh:300`).
The module reports `pending-restart` for the head unit only while `eth0` has
`192.168.0.123`; a successful adbd restart does not clear that address.

For network facts, `status` reads `ifconfig wlan0` and falls back to `netcfg`.
It validates an IPv4 address before printing it. It classifies `wlan0` as the
hotspot when the address is `192.168.0.1` or `init.svc.hostapd=running`;
otherwise it offers it as a client address. An address is not proof that ADB
port 5555 is reachable. The hotspot SSID is stored in the binary
`/data/misc/wifi/softap.conf` (`DataOutputStream.writeUTF` in AOSP's
`WifiApConfigStore`); the shell module does not decode that format. It omits
`info.hotspot_ssid`, so the runtime renders `—`. This avoids printing a
truncated or invalid SSID in the ASCII status protocol.

Run the off-car tests with `sh modules/wireless-adb/test/run.sh`. The fake
helpers, properties and interface tools live under a disposable directory in
`modules/wireless-adb/test/`. The module runs with a restricted PATH containing
only head-unit-available commands, plus failing stubs for unavailable tools;
the test fails if any stub is invoked. The shared `Makefile` currently calls
`tools/test-package.py`, which assumes every module's `status` and `precheck`
still return `not-implemented`. The runtime owner should replace that
assertion with contract validation and add
`sh modules/wireless-adb/test/run.sh` to `make test` (or discover all
`modules/*/test/*.sh`), without changing this module's status behavior.

On the car, save the original two live properties and their `/data/property/`
backing file presence/content, plus `ro.build.product`. Check that the
hotspot gives a computer DHCP service and CarPlay works. Enable Wireless ADB;
confirm the old switch is `0`, port is `5555`, `eth0` is not assigned
`192.168.0.123` after any required full reboot, and `adb connect
192.168.0.1:5555` works from that computer while CarPlay and hotspot DHCP
continue working. Repeat apply, then disable and compare both properties and
backing files byte for byte. Also test an existing old `eth0` address, an
external ADB owner, a failed adbd restart, a firmware change, and package
uninstall. If the saved stock switch was `1`, restoring it can reintroduce
the hotspot routing problem; the UI should warn before disable/uninstall.
