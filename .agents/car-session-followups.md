# Car session follow-ups (2026-11-04)

NEMESIS 1.16.1/1.16.2 `usb.c:do_install` runs standalone uninstall then
install for USB reinstalls while Android is up. The hook cannot distinguish
that uninstall from an intentional removal, and no replacement has been staged.
Never infer an update from stale staging files or USB presence.
NEMESIS 1.16.3 (`dc08358`) and 1.16.4 use `install --update` instead.
`pkg.c` stages `/data/local/tmp/nemesis/stage/hmg-seogaemo/` before invoking
the old hook; `util.c:run_script` forks and execs sh with the install process
as its parent. The existing guard preserves state on that route.

The USB wrapper now uses confirmed `type=execute` and a fixed script that calls
`nemesis install --update` for fresh installs and replacements. NEMESIS runs
it with the stick root as cwd. `type=update` is reserved for NEMESIS core ZIPs.
Real uninstall retains guarded stock restore and APK removal. This also works
when updating the older installed HMG-Seogaemo hook. No upstream code copied.

Wi-Fi feature removal returned pending-restart without info.desired=off, and
the dispatcher accepted that receipt only for restore=reboot. Wi-Fi now reports
pending disable explicitly on status/revert, the dispatcher accepts that bounded
receipt for exact restores too, and EN/KO restart text distinguishes direction.

A missing module build baseline now returns false without logging or creating
state. A recorded different product still logs conflict and blocks restore.

ADB export previously chmodded the timestamp subtree but left its parent 0700.
ADB now opens the export root for shell traversal after copying; USB is unchanged.
The current manifests/ledger sources contain feature XML, runtime properties,
executables, hook payloads, call receipts and map metadata, not Wi-Fi password
stores (no wpa_supplicant or credential settings are captured). No redaction is
needed for these current sources; exports remain an intentional diagnostic copy.
