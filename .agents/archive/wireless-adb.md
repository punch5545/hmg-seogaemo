Source: wireless-adb.md. Historical: 2026-11-04 investigation of TCP ADB being unavailable after restart; not current state.

## Restart symptom (2026-11-04)

Owner: TCP ADB unavailable after restart until off/on. Firmware dump `tmp/car-sessions/2026-11-04-devdump/runtime/root`:
- `rootfs_init.daudio.rc.txt:342–354`: only the stock enable=1 trigger runs `set_adb_ip`, starts async `set_adb_port`
  (`service.adb.tcp.port=5555`), then stops/starts adbd. Our enable=0 avoids this; persistent port changes have no init
  trigger to restart a running daemon.
- `rootfs_init.usb.rc.txt:13–32,268–270`: USB boot config sets `none` (stops adbd) then the desired config (starts adbd);
  `init.rc:488` defines adbd disabled. No inspected init rule resets either TCP port.
- `runtime/cmd/getprop.txt:75,140,147,250,418`: adbd running, persistent port 5555, enable=0, USB config `adb`, no transient port.

Likely cause: adbd picks its port at process start while the old apply compared only before/after property strings; at boot
both can be 5555 with a running process lacking TCP transport, so reapply skipped the restart. Off/on changes the effective
property and forces one. Not proven: vendor reset or startup ordering (needs stock adbd trace plus pre-toggle export). No
Wi-Fi-up wait is needed for an INADDR_ANY listener. Test models props=5555, adbd running without LISTEN → failed → apply
restarts → healthy LISTEN (also IPv6).

