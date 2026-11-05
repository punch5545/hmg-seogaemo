Status: current — implemented and tested off-car; physical head-unit validation pending.

# Stock restore and app removal

Settings → General provides Restore stock state (순정 상태로 되돌리기) and
Uninstall HMG-Seogaemo. Both require the shared confirmation dialog. Cancel
makes no changes. After confirmation, progress cannot be cancelled; saved OTA
resume preferences are cleared so a later app launch cannot re-enable modules.

`hmg-seogaemo-root stock restore` retains the installation and runs a serialized
reverse dependency restore. All baselines are checked before any module revert.
The persistent `stock/paused` marker prevents watcher calls and boot reapply,
even if a failed module retains desired on. An explicit USB install is still
permitted while paused: postinstall clears the OTA payload selection first (so
the paused restore retries against the freshly installed tree), the pending APK
install ignores the pause, and the pause itself is never cleared by install. Only verified successful modules
commit desired off. Failure stops immediately; successful earlier restores stay
off and the failed/unvisited desired states remain unchanged. An operation copy
under `stock/recovery.<time>.<pid>/` preserves recovery data for a late failure
after baseline consumption. These copies are diagnostic material, never a
license to force old stock bytes over a changed firmware.

A module that owns `/system/bin/app_process` is never reported "already off" on
the strength of an empty ledger: if the swap is unrecorded (our stock backup, our hook
`.so`/`.jar` exist, or `app_process` is not the declared stock MD5) the preflight
returns conflict, reverts nothing, keeps desired states and blocks
`package uninstall` and the uninstall hook (`sg_zygote_foreign`, see the runtime
contract); all offenders are named on one line. The wrapper's guard files
(`starts`, `enable`, `enabled`, `disable`) are not evidence: the live wrapper
recreates `starts` after a revert and `touch disable` is its manual off switch, so
restore sweeps them (ledger row each) and reports pending-restart while the revert
marker of this boot says the wrapper zygote is still running. Resolve a real
conflict by hand, then retry.

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

## State after a completed uninstall

The NEMESIS pre-uninstall hook (reached from the app's `package uninstall`, from the
NEMESIS UI and from the staged-update exception alike, the last of which exits early
and changes nothing) ends a successful removal by deleting `desired/`, `modules/`,
`journal`, `reconcile.log`, `app-install/`, `stock/` and `restart-cleanup/`. `stock/`
holds `paused`, `result`, `product`, `boot`, `outcome.*` and any `recovery.*` copies; none
of it describes a later installation, and a surviving `stock/paused` would silently
keep a fresh install from reapplying modules, so it must not survive. Kept on purpose:
`uninstall/` (result, exit code, log, the copied runner), the `ledger/`, `car-maps/`
and the OTA version directories. The restore that precedes it also removes `core/vm`
(the mapper's private Dalvik cache) and any `core/vm.status.*` scratch cache; `map status`
does not recreate it (it only uses an existing cache or a scratch one removed on exit).
A failed hook or a failed removal deletes nothing: the pause, receipt and backups remain
for the retry. Generated app directories that the still-running hooks rewrote after the
restore are not retried after an uninstall (nothing is left to run the retry); they hold
only diagnostic files and no stock state.

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

- The Android 4.4 shell profile permits the fixed uninstall command based on
  `Sources/native/main.c` at its pinned NEMESIS commit
  `da77eef6654e1f321ef3b979704681a0abc398f0` (usage and `cmd_uninstall` dispatch).
  Receipt replacement uses plain toolbox `mv`; no additional lint waiver is needed.
- `tmp/hmg_nemesis/Sources/native/main.c` routes the uninstall CLI.
- `pkg.c:629-680,893-910`: normal uninstall stops package services, runs the hook,
  aborts and restarts services on hook failure, then removes package files.
- `su.c:145-214,261-275`: daemon workers run root commands independently of the
  unprivileged app UID; response collection waits for command stdout to close.
- `svc.c:115-132`: service stop targets the registered service PID. The copied
  uninstall runner is not a package service or an app-UID child.

## Command surface and receipt

- `hmg-seogaemo-root stock restore|status` and `package uninstall` take exactly two arguments. `core/stock.sh` holds the operation
  lock across preflight, reverse-dependency reverts, verification, desired-state commits and result publication, and visits every
  module including interrupted applies with snapshots but desired off. The real `preuninstall` hook runs the same transaction
  (the staged-update exception preserves state). Preflight uses the existing runtime guards (zygote counters, derived dex-cache
  tracking). Successful restore writes desired off for every module; maps, ledger and OTA recovery payloads stay for diagnostics.
- Receipt (bounded): `state=off|pending-restart|conflict|failed`, `restart=none|head_unit`, `detail`, and
  `info.module_<snake_id>` per module (`reverted`, `conflict`, `failed`, `not_attempted`). Pending restart = successful guarded
  restore, no baseline left, desired off; it never claims transient framework behavior is restored before a kernel boot. `stock
  status` is read-only, returns the last receipt, clears the displayed restart requirement after a different boot ID; no receipt =
  no operation. Start, module outcomes and completion/failure are in the change ledger; an interrupted receipt fails closed.
- `package uninstall`: restore first (any failure prevents NEMESIS dispatch), then queue a fixed runner copied to
  `/data/nemesis/backup/hmg-seogaemo/uninstall/` that invokes only `nemesis uninstall hmg-seogaemo` (never `--force`). The runner
  does not use a fixed delay to decide safety; the root caller is a NEMESIS daemon child independent of the app UID and package
  services. A persistent pending marker blocks new applies until removal finishes or fails.
- `device reboot`: `hmg-seogaemo-root device reboot` (exactly two arguments) runs toolbox `sync` then KitKat `reboot`, stopping if
  sync fails; `RootClient.rebootDevice()` exposes only that literal command. Settings → General → Restart head unit (KO 헤드 유닛
  재시작) uses the destructive ConfirmDialog, Cancel focused left; failure is localized. AOSP `android-4.4.2_r1` implements reboot in
  `system/core/reboot/reboot.c` (standalone binary, not a toolbox applet); the no-argument form requests the normal init reboot.

## Module-switch reporting and recovery

- Wi-Fi feature removal reports pending disable explicitly on status/revert (`info.desired=off` with pending-restart). The dispatcher accepts that bounded receipt for exact restores as well as `restore=reboot`; EN/KO restart text distinguishes direction. CarPlay disk revert reports desired off even while a restart is still needed; the dispatcher persists that receipt before the next plan step, the app continues successful pending-restart steps and shows one combined result with the strongest restart requirement. Saved intent participates in exclusion planning even when the live module is off. Structured refusals expose the reason.
- A missing module build baseline returns false without logging or creating state; a recorded different product still logs conflict and blocks restore. A snapshot-free module revert is idempotent, including stale desired-on after an earlier completed restore; remaining restore material keeps firmware, content and metadata guards. Failed restore diagnostics name stage/target in the root receipt and app. Regression coverage: shipped CarPlay script with no build/baselines, saved desired on, dispatch paused, stock restore succeeds; the Java planner/dispatcher test covers an explicit Wi-Fi switch from that paused stale-intent state and verifies dispatch resumes.
- ADB export opens the export root for shell traversal after copying (USB unchanged). Manifests and ledger sources hold feature XML, properties, executables, hook payloads, call receipts and map metadata, not Wi-Fi credentials; no redaction is needed, and exports stay an intentional diagnostic copy.
- Recovery from a stuck module switch: install the release-signed NEMESIS USB ZIP (wrapper `install --update` path; see `research-nemesis-packaging.md`); in the app confirm Restore stock state, restart if requested, then confirm Wi-Fi client enable and restart if requested; connect via stock Wi-Fi settings and reopen the app. Direct Wi-Fi enable also reverts stale CarPlay intent and clears the failed-restore pause. An accepted update check continues after the new boot, Wi-Fi readiness and usable network (app must be opened); otherwise confirm a new update check. Genuine drift still blocks restore: export the retained ledger instead of forcing old bytes.
- Still to validate on the car: physical reboot, stock Wi-Fi menu visibility, TLS/network, OTA continuation. Static Broadcom capability checks show stock software support, not physical radio health.

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
