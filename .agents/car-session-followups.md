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
