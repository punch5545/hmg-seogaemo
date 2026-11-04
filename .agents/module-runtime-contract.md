Status: partly superseded — the former parallel-work ownership table is historical; runtime APIs, Home tile wiring, and module decisions reflect current code.

# Module runtime contract (parallel-work interface)

Implemented interface between the shared module runtime, modules, and updater.
The ownership table records the original parallel-work split; it no longer assigns active worktrees.

## Shared text-file line ownership (2026-11-03)

`owns_files=` grants exclusive whole-file ownership. No other module may claim
that path through either `owns_files` or `owns_lines`, even with exclusions.
Put, delete, bind mounts and zygote installation require `owns_files`.

Declare one `owns_lines.N=/absolute/path:<exact stock line>` per entry, with a
unique positive decimal N (no leading zero). Split at the first colon only:
commas, colons, equals signs, backslashes, leading/trailing spaces and an empty
stock line are literal, without escaping or trimming. Paths use letters, digits
and `/._+-`, with no `..` or `//`. Lines contain no control characters (including
NUL, CR, LF and tab). Duplicate keys and duplicate `(path, stock line)` claims
fail generation, including within one module. `restore=reboot` cannot own lines.
The generator emits `modules.lines`, a graph companion in generated module order:
tab-separated module ID, entry number, and literal `path:stock`. Packaged manifests
retain these declarations.

`sg_file_replace_line <path> <stock line> <new line>` requires that exact claim
or exclusive whole-file ownership. New text has the same control-character
restrictions. For line ownership, old always means the stock line on repeat
apply. Each claimed stock line must occur exactly once in the baseline. Match
patches against stock input, never another patch's output; compose in generated
module order. Preserve each input line's newline, including an unterminated last
line. Reapplying the same patch is harmless. Exclusive whole-file callers retain
their existing baseline/restore behavior.

Shared state is `shared-lines/<MD5-of-path>/` under the runtime data root: immutable
stock `bytes`, `meta` (mode, numeric UID/GID), `existence=present`, `path`, `build`;
immutable `versions/` snapshots with composed bytes and per-module patches; an
atomic `active` pointer; and a `pending` journal naming a fully prepared next
snapshot. A missing file has no matching line and is refused. Module-local
`line.<MD5-of-path>/ready` markers expose partial applies to lifecycle recovery.
Existing whole-file baselines must be reverted before using line ownership; never
adopt their applied bytes as stock. This expands the pre-deployment interface;
it does not convert active whole-file baselines.

Before apply/revert, product, current bytes and metadata must match the recorded
build and last composed state (stock when no snapshot is active). External edits
and firmware changes return conflict (3) and retain backups. Runtime also rejects
a claim held by a different enabled module or an active patch for the same stock
line, independently of generator validation.

Prepare next bytes, patches and audit events before atomic target replacement.
Interrupted preparation/write leaves stock or the previous composed file intact.
Recovery accepts only previous or fully prepared next bytes with stock metadata:
previous cancels the uncommitted transaction; next completes ledger/pointer
publication. Unknown bytes fail closed. Ledger publication is at least once across
interruption (audit rows can repeat). There is no force/adopt-external-content path.

Revert preflights every module target before its first restore. Recompose without
that module's patches, retaining other modules' patches. After the last patch,
verify stock bytes, mode and owner before clearing shared state. Line-only partial
applies are reverted at boot and preflighted on uninstall. `sg_applied <path>`
checks this module has active patches and the entire composed file matches.
Ledger `line_patch`/`line_revert` rows reference exact old/new line text files;
`line_compose` references before/after file bytes with toolbox MD5 hashes, product
and stock metadata. Ordinary ledger export includes these records.

Wi-Fi client and wireless projection claim different `featurexml.cfg` lines. Their
mutual exclusion remains because STA and AP share a radio; file safety is
independent of that exclusion.

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

## Packaged executable invocation (2026-11-03)

NEMESIS extraction on the owner’s Ioniq 5 KOR ignores ZIP executable modes:
only scripts/ and svc/ are 0755; other files are 0644. Invoke packaged shell
entry points using `/system/bin/sh <fixed-path>`, including Java root commands
and module probes. Fixed IDs/verbs and validation remain unchanged. Scripts
need readability, not executable mode. Postinstall creates the daemon client
with mode 0755. The package API commits the zygote wrapper as 0755 and preserves
the stock executable’s baseline mode. These rules also cover ordinary files
created by OTA extraction; no archive mode metadata is required.

## OTA entry dispatch (2026-11-04)

The installed dispatcher validates exact argument counts, fixed core verb pairs,
module ID syntax and watch events before selecting an OTA payload. If current is
a symlink to a readable dispatcher, exec that dispatcher with the same argv;
compare canonical directories to prevent self/alias loops. Both copies validate.
Broken or missing selection falls back to the installed package. Service and
preuninstall/launch entry points use the same canonical guard; USB postinstall is excluded. Reconcile retains its
pre-delegation interrupted-update rollback. The authenticated updater itself
remains the fixed NEMESIS-tree implementation/trust anchor; selected dispatchers
route update verbs to that fixed core, not downloaded updater copies.
`bin/install-apk.sh` deliberately installs its own payload: update/rollback callers
choose the exact APK, so this helper must not redirect to current. Existing old
installed wrappers need one NEMESIS package refresh to gain this delegation;
a new payload alone cannot change code in an already installed old wrapper.

USB postinstall never delegates. After successful APK install (or Android down), retire current/current-path, previous rollback pointers and update journal; retain version directories, baselines and ledger. Failed APK installation preserves selection.

Dispatcher refusals emit state/restart/detail. Status adds saved info.desired=on|off, with live fallback for missing legacy intent. Plans revert exclusions if observed active OR saved on. Successful pending-restart reverts require info.desired=off and permit the next step. App execution retains step outcomes and strongest restart even on later failure, showing one final result. OTA resume survives restart and waits for target readiness and network.

## Root side

### Head-unit restart

Fixed command `hmg-seogaemo-root device reboot` accepts exactly two arguments,
no paths or free-form input. The dispatcher runs toolbox `sync` then the KitKat `reboot` binary,
stopping if sync fails. `RootClient.rebootDevice()` exposes only that literal
command. Settings → General offers Restart head unit (KO 헤드 유닛 재시작),
with the shared destructive/error ConfirmDialog and Cancel focused on the left.
Only confirmation starts the background root call; failure is localized in UI.
Pinned AOSP `android-4.4.2_r1` implements reboot in `system/core/reboot/reboot.c`
(a standalone binary, not a toolbox applet); its no-argument form requests the
normal init reboot and waits for it to occur. The shell profile permits that form.

### Stock restore and package removal

Fixed commands `hmg-seogaemo-root stock restore|status` and
`hmg-seogaemo-root package uninstall` accept exactly two arguments.
`core/stock.sh` owns one shared restore transaction, also used by the real
NEMESIS `preuninstall` hook (the staged-update exception still preserves state).
It holds the operation lock across preflight, reverse generated dependency order
reverts, verification, desired-state commits, and result publication. It visits
all modules, including interrupted applies with snapshots but desired off.

Before restore, a persistent `stock/paused` marker blocks apply and watcher
dispatch, including reconcile after a reboot. All snapshots are preflighted
before any module is reverted, using the existing runtime guards (including
zygote counters and derived dex-cache tracking). A conflict or failure stops
the operation without force; the failed and unvisited modules keep desired
state and backups. Earlier successful modules stay off. The pause survives
failure; a subsequent explicitly confirmed successful module apply clears it.
An operation copy under `stock/recovery.<time>.<pid>/<module>/` retains recovery
material if service cleanup fails after the runtime consumed a verified baseline.
Successful restore writes desired off for every module. Module reverts use
`sg_revert_all` and their service cleanup; bind mounts, wrapper guards and
tracked dex-cache entries follow that API's exact-restore rules. Maps, ledger,
and OTA recovery payloads remain available for diagnostics and recovery.

The bounded status receipt has `state=off|pending-restart|conflict|failed`,
`restart=none|head_unit`, `detail`, and `info.module_<snake_id>` for every
module (`reverted`, `conflict`, `failed`, or `not_attempted`). Pending restart
is a successful guarded restore with no remaining baseline and desired off;
it never claims transient framework behavior is restored before a kernel boot.
`stock status` is read-only, returns the last receipt, and clears the displayed
restart requirement after a different boot ID. No receipt reports no operation.
Start, module outcomes, and completion/failure are recorded in the change ledger;
ordinary USB/adb export continues to work. An interrupted receipt fails closed.

`package uninstall` first runs that restore; any failure prevents NEMESIS dispatch.
On success it queues a fixed runner copied into the root-owned
`/data/nemesis/backup/hmg-seogaemo/uninstall/` directory, outside the package/APK.
The background runner ignores HUP, disconnects all caller stdio, changes to `/`,
and invokes only `nemesis uninstall hmg-seogaemo` (never `--force`). The root
caller publishes the runner PID as ready only after its result and lock release;
without that handoff the runner expires after 30 seconds without removing anything.
The runner does not use a fixed delay to decide whether removal is safe. The root
caller is a NEMESIS daemon child, independent of the app UID and package services.
A persistent pending marker blocks new applies until removal finishes/fails;
failed dispatch retains its log/result for diagnosis and retry. Queued means
accepted, not removed. NEMESIS stops services, calls the same restore hook again,
then the hook removes/verifies the APK before permitting package removal.
The copied runner records the NEMESIS exit result after the package disappears.

Settings → General exposes both actions through `ui.ConfirmDialog`, with
progress and per-module results in EN/KO. Uninstall is destructive. Cancel
before confirmation changes nothing; once confirmed work is not cancellable.
Restore failure leaves the app/package installed, backups retained and watch
calls paused. A head-unit restart may still be required after successful removal.

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

Mutation API (each call: validate target against the module's `owns_props`/`owns_files`/`owns_lines.N`,
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

`/system` returns to read-only through `sg_remount_ro`, which treats a failed `remount ro` as a warning in
the module log, never as a failed change. On the Ioniq 5 (2026-11-04) replacing the running `app_process`
or removing a loaded JAR leaves the old file open, so `remount ro` fails with EBUSY until the next restart.
Failing the change there left the wrapper written but not ledgered, and the rollback failed the same way,
which blocked `revert`, `stock restore` and the next `apply` (`orphaned app_process backup`).

Directories created under `/system` for a payload file (`sg_mkdir_target`) are mode 0755. The module scripts run
with umask 077, which left `/system/etc/hmg-seogaemo/<module>/recipes` at 0700 on the Ioniq 5 (2026-11-04): the hook
runs as the system user inside DiPO, got EACCES on every recipe, and the native audio patch (AAC/Opus over Wi-Fi) was
never applied, so the phone did not offer CarPlay as an audio output. State directories keep the private mode.

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
`adb shell /data/local/tmp/nemesis-d root -c '/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export adb'`.

Status block on stdout (one `key=value` per line, ASCII, detail ≤ 200 chars):

```
state=off|on|pending-restart|conflict|incompatible|failed|not-implemented
restart=none|adbd|head_unit
detail=<short reason>
```

Optional extra lines `info.<key>=<value>` (`[a-z0-9_]` keys, ASCII values ≤ 200 chars) carry
read-only facts the module wants shown in its Settings pane (e.g. `info.hotspot_ssid`,
`info.hotspot_ip`, `info.adb_endpoint`). `status` must be cheap and side-effect free. A pending disable reports
`info.desired=off`; the UI uses this for its off toggle and disable restart text.
A successful revert may return `pending-restart`, `restart=head_unit`, and
`info.desired=off` for either exact file restore (PackageManager still caches a
removed feature) or `restore=reboot`. The dispatcher commits desired off in
both cases. Pending enable retains the on toggle and distinct enable text.

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
`adb shell /data/local/tmp/nemesis-d root -c '/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export adb'`.

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
reflection verification again.
The zygote hook gate (`HmgApi.zygoteInit`) checks only the first two lines of the `verified` seal
(product, stock hash ID), not the boot ID: the zygote starts about two seconds after boot while this
boot's verification finishes one to three minutes later, so a boot-bound gate left the Java hooks off on
every cold boot (the native library still loaded, so `status` read "hooks loaded"). Every stock file is
still compared with the MD5s in `car.json` before the map is used. Module `hookpoints.required` IDs must all survive
resolution and reflection before publish. `map.json` exposes only verified points.
Generated maps and the ledger are package-local state retained for export until
package removal; module revert never removes them. The app invokes map commands
through `RootClient.map` on a worker thread, with fixed arguments and bounded output.

## Reasserted user-button properties (2026-11-04)

Owner-approved `reassert_props=<csv>` explicitly permits reasserting and restoring
listed properties despite external value drift. Each entry must be a valid,
uniquely listed member of `owns_props`; `restore=reboot` is forbidden. Other
properties/files retain guarded compare-before-restore semantics. Product/build
guards remain mandatory, including for drifted values. Current users are only
`user-button-shortcut` / `persist.sys.key.swrccustom` and
`user-button-head-unit` / `persist.sys.key.custom`. Their confirmation warns that
HMG-Seogaemo wins over stock settings while enabled; turning the module off
restores the original baseline, overriding any intervening choice.

Exact-restore modules may declare `watch=boot,screen_on` when they declare
`reassert_props`. Initial confirmed apply captures the original baseline once;
subsequent `sg_prop_set` calls preserve it and record reassertions in the ledger.
Watch must require desired on, an existing baseline, and matching product; it must
never capture a new baseline. Read-only status reports pending reassertion while
desired on and drifted, using `state=pending-restart`, `restart=head_unit`, and
`info.desired=on`. This is a queued repair, not a request to restart the head unit.

On each active watcher poll (normally 5 seconds), enabled modules with
`watch=screen_on` and `reassert_props` are checked against their saved applied
property values. A ready baseline and applied marker are required. A mismatch
reuses fixed `watch screen_on` dispatch under the existing root lock and desired/
paused-state guards, even when the boot ID and dumpsys power state did not change.
No new event or root argument is exposed. Matching properties cause no call or
write. Disabling stops dispatch. Failed reassertions retry on subsequent polls;
physical timing after QUICKBOOT_POWERON still needs on-car verification. The
existing 300-second idle sleep can delay noticing the first newly enabled watch
module; once active the watcher uses its ordinary 5-second interval.

Restore preflight and property restore accept external drift only for explicitly
owned reassert_props. They still fail on product mismatch and retain backups on
failure. Restore ledger before bytes for these properties describe the observed
live drifted value, while the immutable baseline remains the first-apply value.


### Empty restore and diagnostics (2026-11-04)

A module with no restore material is already stock: sg_revert_all succeeds without
requiring a build marker. Restore material includes property/file/line ready
markers, bind records, changes, dex.cache, directory references (dir.* and
dir-order). Any such material retains the firmware, drift and metadata guards.
A stale saved desired=on is cleared only after a successful revert receipt.
Preflight conflicts expose the stage and target through detail output, and
whole-package restore includes that diagnostic in its final receipt.
