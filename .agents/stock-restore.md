Status: current — implemented and tested off-car; physical head-unit validation pending.

# Stock restore and app removal

Settings → General provides Restore stock state (순정 상태로 되돌리기) and
Uninstall HMG-Seogaemo. Both require the shared confirmation dialog. Cancel
makes no changes. After confirmation, progress cannot be cancelled; saved OTA
resume preferences are cleared so a later app launch cannot re-enable modules.

`hmg-seogaemo-root stock restore` retains the installation and runs a serialized
reverse dependency restore. All baselines are checked before any module revert.
The persistent `stock/paused` marker prevents watcher calls and boot reapply,
even if a failed module retains desired on. Only verified successful modules
commit desired off. Failure stops immediately; successful earlier restores stay
off and the failed/unvisited desired states remain unchanged. An operation copy
under `stock/recovery.<time>.<pid>/` preserves recovery data for a late failure
after baseline consumption. These copies are diagnostic material, never a
license to force old stock bytes over a changed firmware.

`stock status` reads the last per-module receipt. An outstanding head-unit
restart remains pending through repeated restore, including the uninstall hook,
and clears on a different kernel boot. After failure, export the ledger, resolve
the conflict and retry. A successful explicitly requested module apply resumes
normal automatic dispatch and invalidates the old stock receipt. Maps and OTA
recovery payloads are retained; modules' bind mounts and tracked dex caches are
handled through their normal guarded runtime restore.

`hmg-seogaemo-root package uninstall` first performs the same restore. On any
failure it never calls NEMESIS. Success queues the fixed command
`nemesis uninstall hmg-seogaemo`, without force. NEMESIS invokes the same restore
again through `scripts/preuninstall.sh`; the staged-update exception still
preserves state. The hook removes and verifies the APK before allowing package
removal. A framework restart may still be required after removal.

## Detached lifecycle

The runner and its helper copy live in the root-only retained directory
`/data/nemesis/backup/hmg-seogaemo/uninstall/`. Before forking, HUP is ignored;
the runner changes to `/`, reads `/dev/null`, and writes its own log. Its files,
stdio, and cwd survive package/APK removal. No extra installed daemon is needed.
The caller writes a PID readiness handoff only after publishing the receipt and
releasing the operation lock. The runner waits for its exact PID, expiring after
30 seconds without dispatch if publication fails. A pending marker blocks new
applies; `result`, `exit-code`, and `log` record final NEMESIS success or failure.
Queued is an acceptance receipt, not proof of removal. If power is lost during
the job, retry uninstall after reboot; stale pending state never authorizes
automatic removal or automatic module reapply.

Upstream evidence, read-only:

- `tmp/hmg_nemesis/Sources/native/main.c` routes the uninstall CLI.
- `pkg.c:629-680,893-910`: normal uninstall stops package services, runs the hook,
  aborts and restarts services on hook failure, then removes package files.
- `su.c:145-214,261-275`: daemon workers run root commands independently of the
  unprivileged app UID; response collection waits for command stdout to close.
- `svc.c:115-132`: service stop targets the registered service PID. The copied
  uninstall runner is not a package service or an app-UID child.

## Validation and on-car checks

`tools/test-stock.py` exercises the actual dispatcher and preuninstall hook with
fake properties, APK and NEMESIS. It checks reverse dependency order, desired
off, all-module preflight conflict retention, late failures, paused watch and
reconcile, read-only receipts, reboot status, export, fixed arguments, and both
detached success and failure. Fake NEMESIS removes the caller's package and APK;
the copied runner survives HUP and records its final result. `make test` runs
the suite under both sh and mksh when available, plus confirmation routing and
package inclusion checks. Existing module/runtime suites prove the underlying
bind/dex/zygote restore guards.

Before approving on a car:

1. Use a release-signed build and keep a full-root USB ADB recovery path. Record
   firmware, original files/metadata, live properties/backing files and ledger.
2. Enable modules one at a time, then compatible combinations. Cancel each new
   action first and verify no state changed. Confirm restore and compare exact
   originals, desired off, released bind mounts and tracked dex-cache cleanup.
3. Wake/sleep and reboot: verify no Factory82/watch call after restore, no boot
   reapply, and the stock consent behavior returns after the requested restart.
4. Introduce a safe owned-target drift in a controlled test. Verify conflict,
   retained backups/desired state, installed app/package, no NEMESIS dispatch,
   no automatic reapply, and successful ledger export. Resolve drift and retry.
5. Confirm app uninstall with modules active. Check the app closes only as the
   APK is removed, NEMESIS registration/package and APK both disappear, and
   retained result/log/ledger record success. Verify exact stock state again
   after restart. Separately exercise a refused hook/removal and power loss.
6. Check EN/KO layout and scrolling on 1920×720 hdpi, Cancel focus and destructive
   styling, progress, per-module results, and restart text.
