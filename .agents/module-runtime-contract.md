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

Module scripts start with `. "$SEOGAEMO_BASE/lib/common.sh"`.

**Modules never modify the system themselves.** Every write goes through the package API below,
which baselines, writes, records the applied state and appends to the change ledger in one step.
Module scripts may only *read* directly (`getprop`, `cat`, `ls`, `grep`, `pm list features`,
`ifconfig`/`netcfg`, `ps`, `dumpsys`). `make test` lints module root scripts and fails on direct
mutations: `setprop`, `mount`/`umount`, `cp`, `mv`, `rm`, `dd`, `ln`, `chmod`, `chown`, `sed -i`,
output redirection to absolute paths outside `$SEOGAEMO_STATE`, `nemesis remount`,
`pm install|uninstall|enable|disable`, `settings put`, `start`/`stop`, `iptables`, `am`.

Mutation API (each call: validate target against the module's `owns_props`/`owns_files`,
baseline on first touch, perform, verify, mark applied, append ledger; non-zero on failure):

| Function | Meaning |
|---|---|
| `sg_prop_set <key> <value>` | set a persistent/live property (must be in `owns_props`) |
| `sg_file_put <path> <payload-rel-src> [mode] [owner:group]` | create/replace a file from `$SEOGAEMO_MODULE_DIR/<src>` atomically (path in `owns_files`); handles `/system` remount |
| `sg_file_replace_line <path> <exact-old-line> <new-line>` | replace exactly one line that must match exactly once (e.g. comment a `featurexml.cfg` entry) |
| `sg_file_delete <path>` | remove a file (baseline keeps its bytes) |
| `sg_bind_mount <target> <payload-rel-src>` | non-persistent bind mount, recorded; reapplied by reconcile at boot |
| `sg_service_restart <name>` | `stop`/`start` an init service (allow-listed per module in `module.properties` `services=`) |
| `sg_revert_all` | undo every change this module made, newest first, with the guarded restore rules below; clears baselines only after every item verified |

Read/report helpers: `sg_log <msg>`, `sg_owner_present <name>` (`nemesis-adb-tcp`, `hmgapi`,
`leafia`), `sg_applied <path|key>` (0 if our applied state is current), `sg_status <state> <restart> [detail]`.
Internal (not for modules): baseline capture, applied-copy marking, remount, ledger writes.

**Change ledger.** `/data/nemesis/backup/seogaemo/ledger/` holds an append-only `ledger.tsv`
(`time, module, op, target, before-ref, after-ref, result`) plus, per change, the original bytes
(`before/`), the bytes we wrote (`after/`), file metadata, and property before/after values. It is
never pruned while any change is active; after a verified uninstall it is kept as a final
export-able record until the package directory is removed.

**Export.** `seogaemo-root export usb|adb` copies the ledger, all original and modified files,
property before/after values, `ro.build.product`, package version and module desired states into
`HMG-Seogaemo-export/<timestamp>/` on the first mounted USB drive (`usb`) or into
`/data/local/tmp/hmg-seogaemo-export/<timestamp>/` readable by the adb shell user (`adb pull`).
Read-only for the system; bounded size; refuses if no target. Exposed in Settings (Logs) as
"Export changes to USB" and documented for adb:
`adb shell /data/local/tmp/nemesis-d root -c '/data/nemesis/apps/seogaemo/bin/seogaemo-root export adb'`.

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

## Home screen tiles

- Home is a horizontally paged row of tall vertical cards (ccNC style), NEMESIS-scale touch targets.
- `Module.homeTiles()` → `HomeTile[]` (`id`, `labelResId`, `iconResId` or glyph, `ModuleAction action`,
  `int order`). Tiles appear only while their module is enabled (or always, if `alwaysVisible`).
- The runtime builds the tile list: the built-in **Settings tile is always first on page 1**; module
  tiles follow, sorted by `order` then module id; overflow continues on the next page with page dots
  and swipe/arrow navigation. Tiles per page is a layout constant (not per module).

## Shell tools available on the head unit (hard rule)

Stock userland is **toolbox + mksh only** (`tmp/hmg_nemesis/01-DSP-OTA-troubleShooter.md:121`,
`05-Run-Scripts.md:101`). Root-side scripts may use: mksh builtins (`case`, `read`, `${var#…}`,
`${var%…}`, `printf`, `echo`, `test`, arithmetic), toolbox commands (`cat`, `cmp`, `cp`, `dd`,
`grep`, `ls`, `mkdir`, `mv`, `rm`, `rmdir`, `ln`, `chmod`, `chown`, `touch`, `sleep`, `ps`,
`kill`, `md5`, `mount`, `umount`, `sync`, `date`, `id`, `ifconfig`, `netcfg`, `route`,
`getprop`, `setprop`, `start`, `stop`, `log`, `dmesg`), and system binaries `pm`, `am`,
`settings`, `logcat`, `dumpsys`, `wpa_cli`, `/system/xbin/nemesis`.
**Not available:** `awk`, `sed`, `tail`, `head`, `cut`, `tr`, `wc`, `sort`, `uniq`, `xargs`,
`find`, `stat`, `readlink -f`, `sha256sum`/`sha1sum`, `tar`, `unzip`, `busybox`, `toybox`.
Parse with `while read`, `case` and parameter expansion. Hashing on the unit: `md5` only.
`make test` lints root scripts (package and modules) for the unavailable tools.

## Settings layout and module pages

- Sidebar, top to bottom: core categories (General first, then e.g. "Modules", Logs, About),
  a divider, then one entry per module ("module settings"), in generated module order.
- **Modules** (working name; KO "모듈 관리"): one large row per module with ON/OFF toggle, observed
  state and restart banner; toggling goes through `ModuleManager.plan` + confirmation dialog.
- **Module settings pages**: each module gets its own page. Reusable components live in the core app
  (`com.seogaemo.app.ui.settings`): `SettingsPage` builder with `section(titleRes)`,
  `info(labelRes, infoKey)`, `toggle(labelRes, settingKey)`, `choice(labelRes, settingKey, valuesRes)`,
  `action(labelRes, ModuleAction)`, `note(textRes)`, `warning(textRes)`, `statusHeader()`.
  Modules compose their page in `Module.buildSettingsPage(SettingsPage page)`; they never create
  Android views themselves. Default implementation (in `RootBackedModule`) renders
  `statusHeader()` + `infoRows()` + `notes()` + `actions()`, so declarative modules need no code.
- Components follow `ui-guidelines.md` (NEMESIS-scale rows, EN/KO).
