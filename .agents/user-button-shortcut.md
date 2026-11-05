Status: current — implemented off-car; physical KOR button delivery untested.

# User button shortcuts

Owner chose the no-hook alternative on 2026-11-04: default OFF, no hooks, no map requirements, no new root verbs. Two independent modules (no dependency or exclusion; either or both may be on) share one fixed receiver/action. Stock settings lists get no new entry. Two modules fit the fixed `<id> apply|revert|status|precheck` channel without per-button root arguments, settings persistence or runtime APIs.

| Module ID | Physical button | Sole owned property | Stock baseline (KOR) |
| --- | --- | --- | --- |
| `user-button-shortcut` | Steering-wheel user button | `persist.sys.key.swrccustom` | `gohome` |
| `user-button-head-unit` | Head-unit user button | `persist.sys.key.custom` | `com.mobis.intent.action.CONNECTIVITY_SHORTCUT` |

The steering ID is kept to preserve existing desired state and baselines. Baseline: `tmp/car-sessions/2026-11-03-baseline/props/getprop.txt:204-206` (also `persist.sys.key.mode`=`54:116:115`). Each module preserves the other button's property and `persist.sys.key.mode`; each has its own state dir and uses the common exact-restore API (`sg_prop_set` apply, `sg_revert_all` restore). Live values plus backing bytes/absence are baselined. Existing HMG-API/Leafia ownership and incompatible product/API are refused.

Planning shows ConfirmDialog and `warn.assignment` (EN/KO) naming the physical button: replaces the current assignment, exact original restore, and automatic reassertion overriding stock settings while ON; turn the module OFF to select another function. Navigation from a received button action changes no persistent settings.

## Receiver

Exported receiver accepts exactly `com.seogaemo.hmg.USER_BUTTON_SHORTCUT`, ignores extras, launches only `com.seogaemo.hmg.MainActivity` with MAIN/NEW_TASK. It never receives the stock None action (which would launch the app while off). No arbitrary target or command.

## Upstream reference

HMG-API commit `4fab4376adae65b67025046539963a68fb37f76f`, `core/runtime/java/hmgapi/hook/StarKey.java`: `:64-77` dynamic launcher receiver for `hmgapi.star.<mod>.<id>`; `:97-103` explicit activity with NEW_TASK; `:115-204` injects stock settings rows and suppresses unknown-action reset (omitted by owner decision); `:80-90` and `install/common/uninstall.sh:67-71` reset removed actions to None (we restore the exact prior assignment). Independently written, no upstream source copied; upstream-diff found no drift from the pin.

## KOR evidence (private dump; do not commit)

Dex/odex inspection of `files/system/framework/automotive-service.odex` (offsets are code units within each method):

- `com.hkmc.micom.ModeKeyDispatcher.getSwrcCustomKeyAction` 0000/0002/0004: `SystemProperties.get("persist.sys.key.swrccustom", fallback "com.mobis.intent.action.SWRC_CUSTOM_SHORTCUT")`. `getCustomKeyAction` 0000/0002/0004: `persist.sys.key.custom`, fallback `com.mobis.intent.action.CUSTOM_SHORTCUT`. `sendBroadcastKeyEvent` calls getCustomKeyAction at 06b6.
- Head `processCustomKey` calls `requestBroadcastShortcutEvent` at 00b6; steering `processSwrcCustomKey` at 00b8; both invoke-virtual-quick index 50, resolved from the dumped ModeKeyDispatcher/Object vtables to `requestBroadcastShortcutEvent(String,int,int)`. Both buttons share one path; no head-specific receiver or extra needed.
- `requestBroadcastShortcutEvent` forwards to ModeManagerService. `ModeManagerService$ModeChanger.generateShortcutIntent` builds the Intent from the action (00e8); extras `com.hkmc.mode.EXTRA_RUNNING_STATE`, `EXTRA_SHORTCUT_REASON`, `EXTRA_RESTOREMODE_REASON`, `TIMESTAMP`, `EXTRA_SAME_MODE` (branches vary by action/reason). `broadcastShortcutEvent` calls generateShortcutIntent (03d6), startServiceByShortcut (03de), then the ordered-broadcast Context overload (03f1, index 98, resolved from this dump's vtables; Intent, permission, receiver, handler, result code/data/extras). Intent indices 73/74/82 = int/long/boolean putExtra.
- Stock restore code references `/oem_data/mode/` and per-user assignments; we do not write those. Boot/profile changes can replace the property; actual persistence and delivery are on-car checks.

## Reassertion (owner-approved)

Owner saw stock reset the steering assignment after a head-unit restart and chose package precedence while ON. Both manifests declare `reassert_props` (their sole property) plus `watch=boot,screen_on`.

- Apply/reconcile reasserts our action over a stock reset or user choice, keeping the ORIGINAL first-apply live value and backing bytes/absence across repeats. Watch requires desired ON, an existing baseline, compatibility and matching product; it never re-baselines, and never adopts our action without a baseline.
- Read-only status reports drift as `pending-restart` with `info.desired=on`, not conflict (existing status protocol); the watcher repairs it without another restart.
- Disable / stock restore / uninstall deliberately override drift for these declared properties and restore the product-bound original, even over a user-selected value (owner accepted). Firmware mismatch still conflicts, keeps backups and blocks both reassertion and restore. Other restore guards unchanged. Keep globbing enabled: package restore enumerates and clears baselines with globs.
- Ignition/QuickBoot: QUICKBOOT_POWERON can retain the kernel boot ID and dumpsys power can stay Awake through ignition/display toggles, so boot-once and off-to-on detection are insufficient. The watcher also compares declared reasserted properties to saved applied values on each active poll (normally 5 s), dispatching the fixed `watch screen_on` handler only on mismatch, under the root lock and desired-ON / stock-paused guards. Matching values cause no write or ledger event; no new verb or free-form argument. Drift can persist until the next poll, and the watcher must be running. Idle watcher interval is 300 s, so enabling the first watch module while it sleeps can delay the first active poll up to that long.
- The runtime contract documents the manifest policy.

## Tests

Real package API with fake properties: repeat apply, read-only status, warning emission, exact live/backing restore, absent-backing restore, firmware mismatch, drift reassertion, pending read-only status, drifted revert restoring the original, orphan action, missing-baseline watch, failed write. The shared lifecycle suite runs per module and checks both can be on, disabling one preserves the other, reasserting one leaves the other. Runtime tests keep the boot ID and an always-Awake display while drifting a property, then check fixed screen_on dispatch, no matching-value writes, no dispatch when OFF/paused/no snapshot. Stock-restore tests: declared drift permits original restore and uninstall, unrelated drift still blocks global preflight. Receiver tests compile production source against stubs (null/other action rejected, fixed target/flags, launch failure, manifest action). Builds against Android 19. Passed under /bin/sh and mksh R59c plus full `make test` on 2026-11-04 (built from origin/main `eb67343`, VERSION 0.1.20); no car writes.

## On-car checks

Each button alone and both together: confirm/cancel; press in foreground/background and after update/reboot; no shortcut after disable; other button and mode list unchanged by each operation; stock settings select is overridden while ON; profile/boot restoration; exact original backing bytes on drifted revert/uninstall; backups kept on firmware mismatch; physical ignition-cycle timing. Force-stopped APK delivery is not promised (stock include-stopped handling varies by action); open the app normally before testing. Host tests do not establish car behavior.
