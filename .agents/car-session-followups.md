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

## Module-switch incident recovery (2026-11-04)

CarPlay disk revert reports desired off even when a head-unit restart is still
needed. The dispatcher persists that receipt before the next plan step; the app
continues successful pending-restart steps and presents one combined result with
the strongest restart requirement. Saved intent participates in exclusion
planning even when the live module is off. Structured refusals expose the reason.

A snapshot-free module revert is idempotent, including stale desired on after
an earlier completed restore. Remaining restore material retains firmware,
content and metadata guards. Failed restore diagnostics identify stage/target
in the root receipt and app. A regression uses the shipped CarPlay script with
no build/baselines, saved desired on and dispatch paused; stock restore succeeds.
The Java planner/real dispatcher test covers an explicit Wi-Fi switch from that
paused stale-intent state and verifies dispatch resumes.

Old installed stable dispatchers need one fixed NEMESIS USB package refresh.
New stable entries delegate to selected OTA logic, but USB postinstall always
uses its fresh tree. Successful APK install (or Android down) retires stale OTA
selection/rollback pointers and journal while retaining version directories,
baselines and ledger. Failed APK install preserves selection. Host fixtures
cover both Android-up outcomes.

Recommended owner recovery: install the release-signed fixed NEMESIS USB ZIP via
the wrapper's nemesis install --update path. In the app confirm Restore stock
state, restart if requested, then confirm Wi-Fi client enable and restart if
requested. Connect through stock Wi-Fi settings and reopen the app. Direct
Wi-Fi enable also reverts stale CarPlay intent and clears the failed restore
pause. A previously accepted update check continues after the required new boot,
Wi-Fi readiness and usable network; the app must be opened because there is no
Android boot receiver. If no continuation was saved, confirm a new update check.
Genuine drift still blocks restore: export the retained ledger instead of
forcing old bytes.

The ASCII semicolon issue came from sg_clean's whitelist, which omitted ';'.
All module root text was audited as ASCII; preserving the character at source
fixes the reported ADB detail without changing transport encoding.

Physical reboot, stock Wi-Fi menu visibility, TLS/network and OTA continuation
remain to be validated on the car. Static Broadcom capability checks establish
stock software support, not physical radio health.
