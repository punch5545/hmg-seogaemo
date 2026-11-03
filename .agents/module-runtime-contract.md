Status: partly superseded — the former parallel-work ownership table is historical; runtime APIs, Home tile wiring, and module decisions reflect current code.

# Module runtime contract (parallel-work interface)

Implemented interface between the shared module runtime, modules, and updater.
The ownership table records the original parallel-work split; it no longer assigns active worktrees.

Ownership:

| Area | Owner worktree |
|---|---|
| `app/src/com/seogaemo/hmg/module/` (RootClient, ModuleManager, RootBackedModule), `nemesis/package/lib/common.sh`, `hmg-seogaemo-root` dependency/exclusion enforcement, payload packaging in `Makefile`, generic module rows in Settings UI | module runtime |
| `modules/wifi-client/**` | wifi-client |
| `modules/wireless-adb/**` | wireless-adb |
| `modules/wireless-carplay-and-aa/**` | wireless-carplay-and-aa |
| `app/src/com/seogaemo/hmg/core/update/**`, `nemesis/package/core/update.sh`, the `update` verb in `hmg-seogaemo-root`, `.github/workflows/release.yml` OTA parts | updater |

Module worktrees touch only their own `modules/<id>/` directory (plus their own `.agents/<id>.md`
notes). Anything outside needs a note in the final report instead of an edit.

## Root side

`hmg-seogaemo-root <id> apply|revert|status|precheck` runs `modules/<id>.sh <verb>` as root with:

- `HMG_SEOGAEMO_BASE`: installed package root (contains `lib/`, `modules/`, `modules.list`, `modules.graph`).
- `HMG_SEOGAEMO_MODULE_DIR`: `$HMG_SEOGAEMO_BASE/modules/<id>.d` = packaged copy of `modules/<id>/payload/`
  (extra files: jars, `.so`, XML, APK patches). Absent if the module has no payload.
- `HMG_SEOGAEMO_STATE`: `/data/nemesis/backup/hmg-seogaemo/modules/<id>` (mode 0700, survives updates,
  removed only after a verified uninstall restore).
- `umask 077`, Android 4.4 mksh/toolbox only (no Bash-only features or `tail`;
  `readlink -f` is supported, but shell parameter trimming avoids spawning it).

Dependencies and exclusions are enforced by `hmg-seogaemo-root`/`ModuleManager`, never inside a
module script. A module script must assume its dependencies are already applied.

Module scripts start with `. "$HMG_SEOGAEMO_BASE/lib/common.sh"`.

**Modules never modify the system themselves.** Every write goes through the package API below,
which baselines, writes, records the applied state and appends to the change ledger in one step.
Module scripts may only *read* directly (`getprop`, `cat`, `ls`, `grep`, `pm list features`,
`ifconfig`/`netcfg`, `ps`, `dumpsys`). `make test` lints module root scripts and fails on direct
mutations: `setprop`, `mount`/`umount`, `cp`, `mv`, `rm`, `dd`, `ln`, `chmod`, `chown`, `sed -i`,
output redirection to absolute paths outside `$HMG_SEOGAEMO_STATE`, `nemesis remount`,
`pm install|uninstall|enable|disable`, `settings put`, `start`/`stop`, `iptables`, `am`.

Mutation API (each call: validate target against the module's `owns_props`/`owns_files`,
baseline on first touch, perform, verify, mark applied, append ledger; non-zero on failure):

| Function | Meaning |
|---|---|
| `sg_prop_set <key> <value>` | set a persistent/live property (must be in `owns_props`) |
| `sg_file_put <path> <payload-rel-src> [mode] [owner:group]` | create/replace a file from `$HMG_SEOGAEMO_MODULE_DIR/<src>` atomically (path in `owns_files`); handles `/system` remount |
| `sg_file_replace_line <path> <exact-old-line> <new-line>` | replace exactly one line that must match exactly once (e.g. comment a `featurexml.cfg` entry) |
| `sg_file_delete <path>` | remove a file (baseline keeps its bytes) |
| `sg_bind_mount <target> <payload-rel-src>` | non-persistent bind mount, recorded; reapplied by reconcile at boot |
| `sg_service_restart <name>` | `stop`/`start` an init service (allow-listed per module in `module.properties` `services=`) |
| `sg_service_call <service> <code>` | fixed Binder call, only if the exact `service:code` pair is listed in `service_calls=`; validates both arguments, enforces a 15-second timeout and records every attempted result in the ledger |
| `sg_zygote_install <wrapper-rel-src> <loader-rel-src> <jar-rel-src>` | guarded transaction for `/system/bin/app_process`, its `app_process.hmg-seogaemo-stock` copy, the wireless loader and JAR, and `enable`/`enabled`/`starts`/`disable` state; all eight paths must be in `owns_files` |
| `sg_dex_cache_track <system-jar> <app-package>` | track the Dalvik cache entry for an owned `/system/framework/*.jar` and the package's `app_hmg-seogaemo-wireless-dex` files; these two derived cache namespaces are the sole exception to explicit `owns_files` listing |
| `sg_revert_all` | undo every change this module made, newest first, with the guarded restore rules below; clears baselines only after every item verified |

Read/report helpers: `sg_log <msg>`, `sg_owner_present <name>` (`nemesis-adb-tcp`, `hmgapi`,
`leafia`, `local-hkmc-factory82`), `sg_applied <path|key>` (0 if our applied state is current), `sg_status <state> <restart> [detail]`.
`sg_print <line>...` writes each argument literally with a newline; `sg_write <text>`
writes exactly one argument without adding a newline. Both preserve backslashes and
option-looking text. They use mksh `print -r --` / `print -rn --`; host POSIX sh
uses a bounded here-document through `dd` for exact bytes, with `echo` for newlines.
Never route binary files or property backing files through shell variables: copy them.

Internal (not for modules): baseline capture, applied-copy marking, remount, ledger writes.

`watch=boot,screen_on` may declare only these fixed events. The package's single
watcher service (`svc/watcher.sh`, `mode=restart`, separate from the `mode=once`
`reconcile` service, and never calling apply/revert) invokes `root/<id>.sh watch
<event>` only while that module's desired state is on, under the root operation
lock. It waits for `sys.boot_completed=1` with a slow sleep and no hard exit, and
re-reads desired state every loop. With no enabled watch module it idles on a
300-second sleep. The boot event is dispatched once per module per kernel boot
(marker `watch/boot.<id>` holding `/proc/sys/kernel/random/boot_id`), so a watcher
restart does not repeat it. Screen-on dispatch uses a display off-to-on
transition read from `dumpsys power` every 5 seconds (Android 4.4 keys
`mWakefulness=`, `mDisplayPowerRequest.policy=`; `mScreenOn=`/`mInteractive=` are
also accepted); uncertain readings do not trigger calls. The watcher never passes
arbitrary event names or arguments.

`restore=reboot` is reserved for modules whose only non-reversible effect is
transient until a head-unit restart. Disable stops future watch calls. Until the
next kernel boot, status is `pending-restart`, `restart=head_unit`, with
`info.desired=off`; after reboot it is `off`. Apply/revert writes its desired
state through the root dispatcher. The UI treats `info.desired=off` as off for
toggle planning and explicitly tells the owner in the confirmation dialog that
disabling requires a head-unit restart to restore stock behavior. This class
must not own persistent stock-file or property changes.

**Change ledger.** `/data/nemesis/backup/hmg-seogaemo/ledger/` holds an append-only `ledger.tsv`
(`time, module, op, target, before-ref, after-ref, result`) plus, per change, the original bytes
(`before/`), the bytes we wrote (`after/`), file metadata, and property before/after values. It is
never pruned while any change is active; after a verified uninstall it is kept as a final
export-able record until the package directory is removed.
Binder calls record the exact allow-listed pair, time, bounded Parcel summary,
and outcome in this same ledger; export includes them.

The zygote transaction copies the original executable to `app_process.hmg-seogaemo-stock` and
verifies that backup before replacing `app_process`. The wrapper must always exec that stock
path, including when its 3-start/120-second preload guard trips. A failure rolls back via the
same full preflight and guarded restore as uninstall. `starts` is a dynamic decimal counter;
its final value is captured in the ledger before restore. Dalvik cache entries in the two
derived namespaces are captured before activation and again at revert, then restored to their
original bytes or absence. `sg_revert_all` checks every owned path for drift before its first
restore write. `make test` also runs optional `modules/<id>/build/build.sh` hooks before APK
packaging and every `modules/<id>/test/run.sh`.

**Export.** `hmg-seogaemo-root export usb|adb` copies the ledger, all original and modified files,
property before/after values, `ro.build.product`, package version and module desired states into
`HMG-Seogaemo-export/<timestamp>/` on the first mounted USB drive (`usb`) or into
`/data/local/tmp/hmg-seogaemo-export/<timestamp>/` readable by the adb shell user (`adb pull`).
Read-only for the system; bounded size; refuses if no target. Exposed in Settings (Logs) as
"Export changes to USB" and documented for adb:
`adb shell /data/local/tmp/nemesis-d root -c '/data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export adb'`.

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
- Every module write, including `ENABLE_MODULE` from a declarative Settings action or Home tile,
  passes through the shared `ui.ConfirmDialog` after `ModuleManager.plan` and read-only `precheck`.
  The dialog lists ordered steps and active warnings; it disables Confirm on submission and shows
  progress until the operation finishes. Navigation-only actions remain direct.
- Module-specific pane content is declarative:
  - `Module.infoRows()` → `InfoRow[]` (`labelResId`, `infoKey`): runtime shows the value of
    `info.<infoKey>` from the last `status`, or "—".
  - `Module.notes()` → string res ids shown as plain text under the rows.
  - `Module.actions()` → `ModuleAction[]` (`labelResId`, `type`, `target`). v1 types:
    `ENABLE_MODULE <id>` (goes through `ModuleManager.plan` + confirmation),
    `OPEN_STOCK_WIFI_SETTINGS` (starts the stock Settings Wi-Fi screen).
- Pre-enable warnings belong to the module being enabled: `Module.enableWarnings()` returns
  `Warning[]` (`checkKey`, `messageResId`); the runtime asks the root side
  `hmg-seogaemo-root <id> precheck` and shows each warning whose `warn.<checkKey>=1` line is present,
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

`HomeView` renders module-provided tiles selected by the runtime. Settings is always first; currently no built-in module supplies a Home tile.

- Home is a horizontally paged row of tall vertical cards (ccNC style), NEMESIS-scale touch targets.
- `Module.homeTiles()` → `HomeTile[]` (`id`, `labelResId`, `iconResId` or glyph, `ModuleAction action`,
  `int order`). Tiles appear only while their module is enabled (or always, if `alwaysVisible`).
- The runtime builds the tile list: the built-in **Settings tile is always first on page 1**; module
  tiles follow, sorted by `order` then module id; overflow continues on the next page with page dots
  and swipe/arrow navigation. Tiles per page is a layout constant (not per module).

## Shell tools available on the head unit (hard rule)

The source-derived compatibility authority is `tools/android44-shell-profile.json`.
`tools/lint-android44-shell.py` checks commands, paths, combined flags, option
arguments and shell syntax during `make test`. See `android44-shell-audit.md`
for pinned AOSP evidence and limits. Edge cases require an inline
`# android44-shell: allow -- <specific reason>`; the lint reports all waivers.
Toolbox `mv` has no options: plain rename already replaces existing files.
Toolbox `md5` requires filenames; hash a text pipe with `/proc/self/fd/0`
explicitly and check its exit status. Never store binary baselines in variables.

Stock userland is **toolbox + mksh only** (`tmp/hmg_nemesis/01-DSP-OTA-troubleShooter.md:121`,
`05-Run-Scripts.md:101`). Root-side scripts may use: mksh builtins (`case`, `read`, `${var#…}`,
`${var%…}`, `print`, `echo`, `test`, arithmetic), toolbox commands (`cat`, `cmp`, `cp`, `dd`,
`grep`, `ls`, `mkdir`, `mv`, `rm`, `rmdir`, `ln`, `chmod`, `chown`, `touch`, `sleep`, `ps`,
`kill`, `md5`, `mount`, `umount`, `sync`, `date`, `id`, `ifconfig`, `netcfg`, `route`,
`getprop`, `setprop`, `start`, `stop`, `log`, `dmesg`), and system binaries `pm`, `am`,
`settings`, `service`, `app_process`, `logcat`, `dumpsys`, `wpa_cli`, `/system/xbin/nemesis`.
**Not available:** `printf` (neither a builtin nor a toolbox binary), `awk`, `sed`, `tail`, `head`, `cut`, `tr`, `wc`, `sort`, `uniq`, `xargs`,
`find`, `stat`, `timeout`, `which`, `seq`, `basename`, `dirname`,
`sha256sum`/`sha1sum`, `tar`, `unzip`, `busybox`, `toybox`.
KitKat does have `cp`, BSD `grep` (including `-E -F -q -x -c`), and `readlink -f`;
the latter uses `realpath` and is unused by current scripts. `cat` and `sleep`
resolve to mksh builtins first (Android enables `HAVE_SELECT`); the profile uses
the supported builtin forms and limits sleeps to integer seconds in this project.
R43 has no `set -o pipefail`. Do not infer features from the newer host mksh.
Parse with `while read`, `case` and parameter expansion. Hashing on the unit: `md5` only.
The profile is a conservative reviewed subset, not a promise that every unlisted
AOSP tool is absent. Host-only payload build helpers are excluded from archives;
every shipped shell member, including generated modules, is checked. Dynamic
arguments must be data, paths, or values of literal options, never option bundles
or command text. Fixed project helpers are checked at their definitions; literal
`sh -c` and trap bodies and command substitutions are checked recursively.
Waivers apply to one physical line and require manual review of the whole line.
The lexer does not prove data flow, service responses, kernel behavior, or exact
vendor binaries. Keep byte-exact restore tests and on-car validation separate.

## Settings layout and module pages

- Sidebar, top to bottom: core categories (General first, then e.g. "Modules", Logs, About),
  a divider, then one entry per module ("module settings"), in generated module order.
- **Modules** (working name; KO "모듈 관리"): one large row per module with ON/OFF toggle, observed
  state and restart banner; toggling goes through `ModuleManager.plan` + confirmation dialog.
- **Module settings pages**: each module gets its own page. Reusable components live in the core app
  (`com.seogaemo.hmg.ui.settings`): `SettingsPage` builder with `section(titleRes)`,
  `info(labelRes, infoKey)`, `toggle(labelRes, settingKey)`, `choice(labelRes, settingKey, valuesRes)`,
  `action(labelRes, ModuleAction)`, `note(textRes)`, `warning(textRes)`, `statusHeader()`.
  Modules compose their page in `Module.buildSettingsPage(SettingsPage page)`; they never create
  Android views themselves. Default implementation (in `RootBackedModule`) renders
  `statusHeader()` + `infoRows()` + `notes()` + `actions()`, so declarative modules need no code.
- Components follow `ui-guidelines.md` (NEMESIS-scale rows, EN/KO).

## Applied rename (2026-11-03)

The application ID and Java base package are `com.seogaemo.hmg`, built-in modules
use `com.seogaemo.hmg.modules.*`, the NEMESIS package ID is `hmg-seogaemo`, and
the root dispatcher is `hmg-seogaemo-root`. Installed files live under
`/data/nemesis/apps/hmg-seogaemo`, state under
`/data/nemesis/backup/hmg-seogaemo`, and the USB wrapper contains
`packages/hmg-seogaemo.zip`. For example:
`adb shell /data/local/tmp/nemesis-d root -c '/data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export adb'`.

## Naming and install boundary (2026-11-03)

The root environment variables are `HMG_SEOGAEMO_BASE`, `HMG_SEOGAEMO_STATE`,
and `HMG_SEOGAEMO_MODULE_DIR`. The `sg_*` function prefix remains the stable
module runtime API. Package and OTA APK entries are `app/hmg-seogaemo.apk`;
wireless assets use `/system/etc/hmg-seogaemo/`,
`/system/bin/app_process.hmg-seogaemo-stock`,
`/system/lib/libhmg_seogaemo_wireless_hook.so`, and
`/system/framework/hmg-seogaemo-wireless-hook.jar`. The derived Dalvik cache
names follow the new JAR and `app_hmg-seogaemo-wireless-dex` directory. These
are changes to the planned initial installation paths; no build has been
deployed on a car, so there is no migration from the earlier names.

## Core car mapping (2026-11-03)

`hmg-seogaemo-root map status|run|verify|export` accepts exactly these fixed verbs.
`status` hashes the catalogue's stock files and compares product, onebin, Android version,
and every stock MD5 with bundled or generated `car.json`; it is read-only.
`run` scans the full catalogue in a separate root `app_process` and reflects over the
mapped classes and members. `verify` repeats the reflection check for a matching map.
Both write only to package-allocated private scratch, then call `hmg_seogaemo_map_publish`.
`export` writes `maps/<make>/<model>/<region>/<firmware>/map.json` and `car.json`
to USB when mounted, otherwise the adb export directory. The existing `export usb|adb`
also includes the active PR-ready map folder.

The package API owns `hmg_seogaemo_map_work`, `hmg_seogaemo_map_publish`, `hmg_seogaemo_map_work_cleanup`, and
`hmg_seogaemo_map_export`. Publish validates fixed scratch filenames, product and hash IDs,
copies the verified map to an immutable directory under
`/data/nemesis/backup/hmg-seogaemo/car-maps/<product>/<stock-hash-id>/<map-id>/`,
records pointer and content changes in the ledger, then atomically replaces the
`car-maps/active` pointer file. The pointer is the only path consumers select;
there is no write to a stock file. A failed publish leaves the previous pointer.
The boot ID is included in the map ID and verification seal, so a new boot needs
reflection verification again. Module `hookpoints.required` IDs must all survive
resolution and reflection before publish. `map.json` exposes only verified points.
Generated maps and the ledger are package-local state retained for export until
package removal; module revert never removes them. The app invokes map commands
through `RootClient.map` on a worker thread, with fixed arguments and bounded output.
