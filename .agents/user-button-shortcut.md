Status: current — implemented off-car; physical KOR button delivery remains untested.

# User button shortcuts

Owner selected the no-hook alternative on 2026-11-04. Default OFF; no runtime
default-on support, hooks, map requirements or new root verbs. Two independent
modules share the same fixed receiver/action; the stock settings lists have no new entry.

| Module ID | Physical button | Sole owned property |
| --- | --- | --- |
| `user-button-shortcut` | Steering-wheel user button | `persist.sys.key.swrccustom` |
| `user-button-head-unit` | Head-unit user button | `persist.sys.key.custom` |

The steering ID is retained to preserve existing desired state and baselines.
Both default OFF, with no dependency or exclusion between them. Two modules fit
the fixed `<id> apply|revert|status|precheck` channel without adding per-button
root arguments, settings persistence, or runtime APIs. Each uses its own state
directory and the common exact-restore API. Either or both may be enabled.

Normal module planning shows ConfirmDialog and `warn.assignment` before apply.
Each EN/KO warning names its physical button and explains replacement of the current
assignment (steering: `gohome`; head: `com.mobis.intent.action.CONNECTIVITY_SHORTCUT`),
guarded exact restore, and stock settings overriding this shortcut. Navigation
from a received button action changes no persistent settings.

## Upstream reference

HMG-API commit `4fab4376adae65b67025046539963a68fb37f76f`:
`core/runtime/java/hmgapi/hook/StarKey.java:64-77` registers a dynamic launcher
receiver for `hmgapi.star.<mod>.<id>`; `:97-103` opens an explicit activity with
NEW_TASK. `:115-204` injects stock settings rows and suppresses unknown-action
reset; this hook behavior is omitted by owner decision. `:80-90` and
`install/common/uninstall.sh:67-71` reset removed actions to None; our guarded
runtime restores the exact prior assignment instead. Independently written code,
no upstream source copied. upstream-diff found no drift from the pin.

## KOR evidence (private dump; do not commit)

Baseline `tmp/car-sessions/2026-11-03-baseline/props/getprop.txt:204-206`:
head custom=`com.mobis.intent.action.CONNECTIVITY_SHORTCUT`, mode=`54:116:115`,
steering custom=`gohome`. Each module preserves the other button property and
`persist.sys.key.mode`.

Direct Dex/odex inspection of `files/system/framework/automotive-service.odex`:

- `com.hkmc.micom.ModeKeyDispatcher.getSwrcCustomKeyAction` code-unit offsets
  0000/0002/0004 read `persist.sys.key.swrccustom` with fallback
  `com.mobis.intent.action.SWRC_CUSTOM_SHORTCUT` via SystemProperties.get.
  `getCustomKeyAction` offsets 0000/0002/0004 likewise read
  `persist.sys.key.custom` with fallback `com.mobis.intent.action.CUSTOM_SHORTCUT`.
  `sendBroadcastKeyEvent` calls getCustomKeyAction at 06b6.
- Head `ModeKeyDispatcher.processCustomKey` passes the generic action through
  requestBroadcastShortcutEvent at 00b6 (invoke-virtual-quick index 50).
  Steering `processSwrcCustomKey` calls the same method at 00b8, also index 50.
  Index 50 resolves from the dumped ModeKeyDispatcher/Object virtual tables to
  `requestBroadcastShortcutEvent(String,int,int)`. Thus both feed the same
  generateShortcutIntent / ordered-broadcast path described below; no head-specific
  receiver or extra is needed. These are code-unit offsets within each method.
- `ModeKeyDispatcher.processSwrcCustomKey` processes the action;
  `requestBroadcastShortcutEvent` forwards to ModeManagerService.
  `ModeManagerService$ModeChanger.generateShortcutIntent` constructs the Intent
  from the supplied action (00e8). Extra keys include
  `com.hkmc.mode.EXTRA_RUNNING_STATE`, `com.hkmc.mode.EXTRA_SHORTCUT_REASON`,
  `com.hkmc.mode.EXTRA_RESTOREMODE_REASON`, `com.hkmc.mode.TIMESTAMP`,
  `com.hkmc.mode.EXTRA_SAME_MODE`. Branches vary by action/reason.
- `ModeManagerService$ModeChanger.broadcastShortcutEvent` calls
  generateShortcutIntent (03d6), startServiceByShortcut (03de), then Context's
  ordered-broadcast overload (03f1, invoke-virtual-quick index 98). Index 98 was
  resolved from this dump's Context/Object virtual tables; it takes Intent,
  permission, receiver, handler, result code/data/extras. Intent indices 73/74/82
  resolve to integer/long/boolean putExtra overloads.
- Stock restore code references `/oem_data/mode/` and user-specific assignments.
  We do not write those files. Boot/profile changes can replace the property;
  actual persistence and delivery remain on-car checks.

Each module owns only its tabled property. Apply uses `sg_prop_set`, restore uses
`sg_revert_all`. Live values plus backing bytes/absence are baselined. Existing
HMG-API/Leafia ownership and incompatible product/API are refused. Reapply checks
our baseline/applied value first, including boot reconcile; external choices
produce conflict and retained backups, never automatic reassignment. Disable also
fails closed on external change. Returning the property to the recorded applied
action permits a guarded retry. Our action without a baseline is never adopted.
Keep globbing enabled: package restore enumerates and clears baselines with globs.

The exported receiver accepts exactly `com.seogaemo.hmg.USER_BUTTON_SHORTCUT`,
ignores extras and launches only `com.seogaemo.hmg.MainActivity` with MAIN/NEW_TASK.
It does not receive the stock None action, which would launch the app while off.
No arbitrary target or command is accepted.

Tests exercise the real package API with fake properties: repeat apply, read-only
status, warning emission, exact live/backing restore, absent backing restore,
firmware mismatch, external-change status/apply/revert conflict, orphan action,
failed write. The shared lifecycle suite runs for each module and also verifies
that both can be enabled, disabling either preserves the other, and a conflict in
one does not prevent guarded restore of the other. Receiver tests compile
production source against stubs, checking
null/other action rejection, fixed target/flags, launch failure and manifest action.
The build compiles against Android 19.

On-car checks for each button independently and both together: confirm/cancel;
press foreground/background and after update/reboot; no shortcut from that button
after disable; the other button and mode list unchanged by each operation; stock settings
open/select can reset/override unknown action, with conflict and no reapply;
profile/boot restoration; exact backing bytes on revert/uninstall; backups retained
on conflict. Force-stopped APK delivery is not promised (stock include-stopped
handling varies by action); open the app normally before testing. Host tests do
not establish physical-car behavior.

Validation on 2026-11-04: new module + receiver tests passed under /bin/sh and
mksh R59c; standalone runtime and generator suites passed; `make test` passed
(build/package, API 19 compilation and v1 signature verification, mapper,
confirmation routing, root/stock/line suites, every module suite, shell/naming
lints, planner/home tiles, OTA and CA trust). VERSION unchanged; no car writes.

Two-button follow-up validation (2026-11-04): both lifecycle/independence suites
and the shared receiver suite passed under /bin/sh and mksh R59c. `make test`
passed once after the changes, including the Android 19 build/package, generated
module ownership checks, confirmation routing, runtime/restore suites, every
module test, shell/naming lints, planner/home tiles, OTA and trust checks.
No VERSION bump, push, or head-unit changes.
