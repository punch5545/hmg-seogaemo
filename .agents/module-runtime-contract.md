# Module runtime contract (parallel-work interface)

Fixed interface between the shared module runtime and individual modules, so that
`modules/*`, the runtime and the updater can be built in parallel worktrees.
Change it only through the runtime owner; record any change here.

Ownership:

| Area | Owner worktree |
|---|---|
| `app/src/com/seogaemo/app/module/` (RootClient, ModuleManager, RootBackedModule), `nemesis/package/lib/common.sh`, `seogaemo-root` dependency/exclusion enforcement, payload packaging in `Makefile`, generic module rows in Settings UI | module runtime |
| `modules/wifi-client/**` | wifi-client |
| `modules/wireless-adb/**` | wireless-adb |
| `modules/wireless-carplay-and-aa/**` | wireless-carplay-and-aa |
| `app/src/com/seogaemo/app/core/update/**`, `nemesis/package/core/update.sh`, the `update` verb in `seogaemo-root`, `.github/workflows/release.yml` OTA parts | updater |

Module worktrees touch only their own `modules/<id>/` directory (plus their own `.agents/<id>.md`
notes). Anything outside needs a note in the final report instead of an edit.

## Root side

`seogaemo-root <id> apply|revert|status|precheck` runs `modules/<id>.sh <verb>` as root with:

- `SEOGAEMO_BASE`: installed package root (contains `lib/`, `modules/`, `modules.list`, `modules.graph`).
- `SEOGAEMO_MODULE_DIR`: `$SEOGAEMO_BASE/modules/<id>.d` = packaged copy of `modules/<id>/payload/`
  (extra files: jars, `.so`, XML, APK patches). Absent if the module has no payload.
- `SEOGAEMO_STATE`: `/data/nemesis/backup/seogaemo/modules/<id>` (mode 0700, survives updates,
  removed only after a verified uninstall restore).
- `umask 077`, Android 4.4 mksh/toolbox only (no bash-isms, no `tail`, no `readlink -f`).

Dependencies and exclusions are enforced by `seogaemo-root`/`ModuleManager`, never inside a
module script. A module script must assume its dependencies are already applied.

Module scripts start with `. "$SEOGAEMO_BASE/lib/common.sh"` and use only these functions:

| Function | Meaning |
|---|---|
| `sg_log <msg>` | append to the module log (bounded) |
| `sg_prop_baseline <key>` | first call per enable session records live value + `/data/property/<key>` presence/content; later calls are no-ops |
| `sg_prop_set <key> <value>` | `setprop` + readback check; non-zero on mismatch |
| `sg_prop_restore <key>` | restore the recorded baseline exactly (incl. "was absent"); if the live value differs from what the module set, refuse with conflict instead of overwriting |
| `sg_file_baseline <path>` | first call records bytes, existence, mode, owner |
| `sg_file_mark_applied <path>` | after a module writes `<path>`, record a copy of the bytes it wrote |
| `sg_file_restore <path>` | restore the recorded file exactly (incl. "was absent") **only if** the current bytes equal the marked applied copy (or already equal the baseline → no-op); otherwise refuse with conflict and keep the baseline (firmware OTA or another tool changed it) |
| `sg_remount rw\|ro` | `/system` remount through `nemesis remount` |
| `sg_baseline_clear` | drop this module's baselines after a verified revert |
| `sg_owner_present <name>` | 0 if a known external owner is installed: `nemesis-adb-tcp`, `hmgapi`, `leafia` |
| `sg_status <state> <restart> [detail]` | print the status block below and return the matching exit code |

Status block on stdout (one `key=value` per line, ASCII, detail ≤ 200 chars):

```
state=off|on|pending-restart|conflict|incompatible|failed|not-implemented
restart=none|adbd|head_unit
detail=<short reason>
```

Optional extra lines `info.<key>=<value>` (`[a-z0-9_]` keys, ASCII values ≤ 200 chars) carry
read-only facts the module wants shown in its Settings pane (e.g. `info.hotspot_ssid`,
`info.hotspot_ip`, `info.adb_endpoint`). `status` must be cheap and side-effect free.

Exit codes: `0` ok, `1` failed, `3` conflict, `4` incompatible, `2` usage.

Module tests (`modules/<id>/test/*.sh`, run by `make test`) use a fake `common.sh` and fake
`getprop`/`setprop`/file roots under a temp dir; they never touch the host system.

## Java side

- `RootClient.run(String moduleId, String verb)` → `RootResult` (state, restart, detail, exit code).
  Fixed argv, bounded output, no shell strings from callers.
- `abstract class RootBackedModule implements Module`: implements `apply/revert/status` through
  `RootClient`; a module class extends it and only supplies metadata (`id`, string res ids,
  settings category, owned props/files, restart requirement) and optional pure-Java
  compatibility hints.
- `ModuleManager.plan(String id, boolean enable)` → `ModulePlan` (ordered enable/disable list incl.
  dependencies, dependents and exclusions, for the confirmation dialog); `ModuleManager.execute(ModulePlan)`.
  `ModuleManager.requestEnable(String id, String resumeIntent)` persists an intent that survives the
  head-unit reboot (used by the updater for `wifi-client`).
- Generic Settings rows (toggle, observed state, restart banner, confirmation dialog) are built by
  the runtime from module metadata. Modules do not edit UI code.
- Module-specific pane content is declarative:
  - `Module.infoRows()` → `InfoRow[]` (`labelResId`, `infoKey`): runtime shows the value of
    `info.<infoKey>` from the last `status`, or "—".
  - `Module.notes()` → string res ids shown as plain text under the rows.
  - `Module.actions()` → `ModuleAction[]` (`labelResId`, `type`, `target`). v1 types:
    `ENABLE_MODULE <id>` (goes through `ModuleManager.plan` + confirmation),
    `OPEN_STOCK_WIFI_SETTINGS` (starts the stock Settings Wi-Fi screen).
- Pre-enable warnings belong to the module being enabled: `Module.enableWarnings()` returns
  `Warning[]` (`checkKey`, `messageResId`); the runtime asks the root side
  `seogaemo-root <id> precheck` and shows each warning whose `warn.<checkKey>=1` line is present,
  inside the same confirmation dialog. `precheck` is read-only and uses the status-block format.

## Current module decisions

- `wireless-adb` does **not** depend on `wifi-client`. Its pane shows the car hotspot
  (`info.hotspot_ssid`, `info.hotspot_ip`, `info.adb_endpoint` = `<hotspot ip>:5555`) with a note
  that ADB works for a computer joined to that hotspot, plus an action
  "Connect to another Wi-Fi" = `ENABLE_MODULE wifi-client`. When `wifi-client` is on, it also
  shows the client IP (`info.client_ip`, `info.client_adb_endpoint`).
- `wifi-client` declares an enable warning `carplay_active`: its `precheck` reports
  `warn.carplay_active=1` when `wireless-carplay-and-aa` is enabled or a wireless projection
  session/AP is active. The exclusion still turns `wireless-carplay-and-aa` off. Once applied (and
  after the head-unit restart), its pane offers `OPEN_STOCK_WIFI_SETTINGS` to pick a network.

Strings: modules add only `<snake_id>_*` resources in their own `res/values{,-ko}/strings.xml`.

## Restore-on-uninstall guarantees (owner priority)

- Every `/system` or `/data` change made by a module is baselined before the first write and
  restored byte-exactly on revert/uninstall. Nothing outside baselined paths/props may be written.
- Restore is guarded: only undo what we wrote. If the current content is neither our applied copy
  nor the baseline (e.g. an official firmware update replaced `/system`), do not write stock bytes
  from an older firmware; report conflict, keep backups, and let the uninstall fail closed.
- Baselines are bound to `ro.build.product`; a different firmware build invalidates them (conflict).
- Uninstall aborts (non-zero preuninstall) rather than leaving a half-restored system.
