Status: current — implemented off-car; physical-car behavior remains unverified.

# Wireless ADB module findings

The module owns only `persist.service.adb.tcp.enable` and
`persist.adb.tcp.port`. Apply calls `sg_prop_set` to force the stock switch to
`0` and set port `5555`, then calls `sg_service_restart adbd` when the
effective port changes or init.svc.adbd is not running. A nonempty `service.adb.tcp.port` overrides the
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
the test fails if any stub is invoked. `make test` now discovers and runs `modules/*/test/run.sh`, including this test.
`tools/test-package.py` verifies package metadata and layout without the old
`not-implemented` assertion.

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

Status distinguishes overridden (conflict), stopped (failed), and configured
with adbd running. EN/KO info rows expose effective port and daemon state.
Readable /proc/net/tcp and tcp6 tables are checked for local :15B3 LISTEN
state 0A. An absent listener reports failed and reapply restarts adbd through
the package API, allowing up to four seconds for asynchronous listen startup.
Unreadable tables report unknown. LISTEN does not prove remote reachability.
HMG-API adb.c lines 1352–1358 confirms transient-before-persistent precedence;
this matches stock KitKat adbd property selection.

## Restart symptom investigation (2026-11-04)

Owner reports TCP ADB unavailable after restart until off/on. The firmware dump
in main tmp/car-sessions/2026-11-04-devdump/runtime/root shows:
- rootfs_init.daudio.rc.txt:342–354: only the stock enable=1 trigger runs
  set_adb_ip (eth0 192.168.0.123), starts an asynchronous set_adb_port service
  (service.adb.tcp.port=5555), then stops/starts adbd. Our enable=0 intentionally
  avoids this address-changing trigger; persistent port changes have no init
  trigger here to restart an already running daemon.
- rootfs_init.usb.rc.txt:13–32,268–270: USB boot configuration sets none (stops
  adbd) and then its desired configuration (adb starts adbd). init.rc:488
  defines adbd disabled. No inspected init rule resets either TCP port.
- runtime/cmd/getprop.txt:75,140,147,250,418: adbd running, persistent port
  5555, stock enable=0, persistent/live USB config=adb; transient port absent.

Most likely mechanism is a daemon lifetime/readiness mismatch: adbd selects
its transport port at process startup, while our old apply compared only
before/after property strings. At boot both can already be 5555 despite the
running process lacking TCP transport; reapply then skipped the restart.
Off/on changes the effective property and forces restart, explaining recovery.
The dump does not prove a vendor reset or the precise startup ordering, and
current absent transient state is not evidence of an overriding boot value.
A stock adbd binary trace plus pre-toggle export is needed to establish that.
No Wi-Fi-up wait is required for an INADDR_ANY listener. Apply waits at most
four seconds for the restarted listener. Test explicitly models persistent
props=5555, adbd running without LISTEN, status failed, apply service restart,
then healthy LISTEN (also checked in IPv6).
