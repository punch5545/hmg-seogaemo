Status: partly superseded — the former parallel-work ownership table is historical; runtime APIs, Home tile wiring, and module decisions reflect current code.

# Module runtime contract (parallel-work interface)

## Map verification input identity (2026-11-04)

The verified seal retains its product, stock-file hash ID and boot ID as the
first three lines, then appends an MD5 identity of the selected map/car bytes and export layout,
catalogue JSON files and packaged required-point manifests. A changed map,
catalogue or requirement invalidates cached status even within the same boot;
legacy three-line seals require verification once. Bundled verification binds
the original bundle inputs even when optional failures are filtered from the
published map; generated verification binds its published map/car. The immutable
map ID also includes the complete published car metadata and layout to avoid collisions.
The stock hash ID is unchanged. Zygote still accepts the first two seal lines
and checks exact identity and stock MD5s independently, allowing a previously
verified projection map to load before boot reconciliation completes. Optional
new points do not expand any module's required set.

## Deferred package APK installation (2026-11-04)

USB postinstall retains its APK and fixed installer under private
`app-install/payload/` and atomically publishes `app-install/pending`. Only that
explicit request permits automatic installation. Reconcile invokes the pending
worker before OTA delegation and missing-APK cleanup; the worker waits for
Android and PackageManager readiness. The synchronous USB hook uses the same
worker and spends one attempt, leaving service launches the remaining budget.
A single worker waits with capped readiness backoff and attempts installation
at most four times per kernel boot (5/10/20-second delays), retaining the request
after failure for a later boot. Attempt reservations survive worker restarts.
The worker holds the shared operation lock during installation and respects
stock pause and uninstall pending. Once it observes the app installed, it records
`seen`; a later absence cancels the request and lets removal cleanup proceed.
Successful installation retires the request before deleting retained payload.
Preuninstall cancels the request after guarded stock restore. No request means
APK absence always follows removal cleanup, including legacy installations.
An APK update failure while the app is still registered does not suppress
desired-on reapply, including repair in modules already reporting on.

## Watch event acknowledgments (2026-11-04)

Boot watch receipts are published only after successful dispatch. Each boot/event
has at most six attempts with delays 5, 10, 20, 40, 60 seconds (uptime clock),
and reservations survive watcher restart. A new observed screen off/on edge
starts a fresh screen-event budget; failures remain queued while the screen
stays on. Disable, stock pause and the dispatcher intent lock still suppress
calls. Every Binder attempt uses `sg_service_call` and its ledger; the consent
module treats only `sg_service_call_ok_this_boot` as boot completion.

## Boot lifecycle readiness (2026-11-04)

Reconcile is a NEMESIS `background` shell service: start asynchronously on each
service-manager startup and package install/update, without restarting after exit.
Normal startup waits for `sys.boot_completed=1` and `pm path android` before
interpreting an absent app as removal. Readiness retries back off from 2 seconds
to a 30-second cap, with no permanent boot timeout. OTA health remains finite
(six readiness waits); map-dependent applies retain their detached retry worker.
Desired-on modules are still reapplied on the install/update path.

Implemented interface between the shared module runtime, modules, and updater.
The ownership table records the original parallel-work split; it no longer assigns active worktrees.

## Applied projection payload upgrades (2026-11-04)

`sg_zygote_install` compares the selected wrapper, loader and JAR on every
apply, including desired-on reconciliation after OTA and NEMESIS USB postinstall.
An applied install may advance only from its recorded applied bytes/metadata;
unknown drift conflicts before any payload write. The stock executable backup
and every original file baseline remain immutable through all generations.
A durable `zygote-upgrade` journal publishes intended bytes/metadata before
mutation. Guards also recognize those intended states after interruption, so
retry converges and stock restore can undo a partial upgrade. A different
selected generation during a pending upgrade is refused; restore or resume the
same selected payload first. The journal remains until all new applied markers
and ledger entries are committed.

Upgrade captures then invalidates tracked Dalvik caches, writes loader and JAR,
and atomically replaces the wrapper last. It records restart-required before
the first mutation; the old running zygote still holds its previous inodes.
Cache deletions remain journaled until their applied-absence markers are
committed, including paths no longer visible in cache globs after interruption.
No zygote service restart is performed. A same-payload reapply makes no payload
or cache writes. Projection reapply reasserts all three owned properties using
`sg_prop_set`; status checks all three.

OTA rollback (explicit, automatic and interrupted-update recovery) refuses
before selecting another package/APK while projection restore material remains.
Disable projection or restore stock first, then retry rollback. Failed update
health with active projection keeps the selected package and reports blocked
rollback, rather than mixing old package code with new system payload bytes.
This conservative policy also covers interrupted first apply and upgrade.
Uninstall always restores ORIGINAL stock, never the previous applied generation.

The guarded fixed updater advertises `HMG_SEOGAEMO_PROJECTION_ROLLBACK_GUARD=1`
to its health reconcile. With `HMG_SEOGAEMO_UPDATE_HEALTH=1` and no capability,
`sg_zygote_install` keeps the applied generation and returns successful
`info.hook_upgrade=deferred until update is finalized`; caches remain intact.
After finalization, ordinary reapply upgrades. The new APK checks fixed module
status `info.restore_material=absent` before dispatching rollback; missing or
present material refuses with localized guidance after the normal confirmation.
This protects app rollback even with an older fixed updater. USB replaces that
updater and uses ordinary postinstall reconcile, so it does not defer. Direct
root rollback through a pre-upgrade fixed updater cannot acquire this new guard;
disable projection first or reinstall via USB before using that legacy command.

## Payload metadata and directory transactions (2026-11-04)

Whole-file applied markers include `applied-meta` (four octal mode digits,
numeric UID and GID). Guards compare bytes and metadata and reject symlinks
at the target or any ancestor. Legacy markers without metadata are backfilled
only after their bytes verify; unknown historical metadata cannot be recovered.
`sg_commit_file` accepts numeric owners (and normalizes `root:root` to `0:0`)
and verifies mode/UID/GID before rename.
Same-byte external chmod/chown is a restore conflict. Property backing baselines
also retain metadata; legacy backing baselines retain their existing behavior.
Bind sources must be regular, non-symlink, public 0644 root-owned files; the
runtime refuses unsuitable sources rather than changing packaged inodes.

`sg_payload_dirs <owned whole-file path>` reconciles its ancestors through the
runtime. Existing stock ancestors outside `/system/etc/hmg-seogaemo` are checked
for symlinks but never adjusted. Created ancestors and project namespace dirs
are journaled before mutation, then set to 0755 root:root and verified. Module
namespace operations are restricted to `<namespace>/<module-id>/...`; the shared
namespace root has central baseline/applied metadata and per-module references.
The global operation lock serializes references. Last reference out restores or
removes the shared parent. Directory changes append ledger records. `ls -ldn`
provides fixed mode/UID/GID fields on KitKat; the parser handles toolbox's absent
link-count field and host listings without stat or text-processing tools.
Payload mode lint rejects literal private `/system` file modes, including simple
path-variable assignments, unless the call has an inline
`# payload-mode: allow -- <specific reason>` justification.

Directories use immutable existence/original metadata, a ready marker and a
pending intended metadata record before mutation. Recovery recognizes only the
original or intended/applied metadata; unknown metadata conflicts. Revert checks
all directory metadata before file restoration, then restores directories deepest
first. Created dirs are removed only if empty and still match; nonempty dirs
conflict and retain journals/backups. A retried restore accepts already-restored
original metadata or absent created dirs. Before restoring existing metadata,
the runtime durably publishes `restore-pending` (original metadata) and
`restore-intermediate` (current mode with original UID/GID). Guards accept those
known states after interruption; unknown metadata still conflicts. Reapply clears
restore intent only after its applied metadata is verified.
Legacy existing dirs (including 0700
ancestors) are conservatively pre-existing with current metadata as baseline;
file-put provenance does not prove directory absence. Uninstall restores that
metadata and never deletes these legacy directories. No guessed absent baseline.

CarPlay apply reconciles recipes and ancestors before its already-applied return.
When assets are initially unreadable, repairs record the current boot before
their first mutation and remain pending-restart through that boot, because DiPO
reads recipes when registering native patches. Marker publication failure stops
repair before mutation; an interrupted repair retains the restart intent even
if all assets are already readable on retry. Read-only status
checks world-read file bits and world-search ancestor bits using permission
digits directly (leading-zero arithmetic differs between POSIX shells and
Android mksh). It reports failed with an assets-unreadable detail and does not
claim native patch success merely from
zygote loader presence. Boot reconcile already invokes desired apply, so it also
migrates legacy ancestors after OTA. NativePatch keeps one attempt per process:
a failed native write may be partial, so retrying it is unsafe; restart is required.

Export publication makes only its dedicated container and descendants public:
0755 directories and 0644 regular files. Symlinks are refused; USB mount roots and
private runtime state are never chmod targets.

## Wireless ADB and diagnostic export (2026-11-04)

Wireless ADB status exposes info.effective_port (the nonempty transient
service.adb.tcp.port overrides persist.adb.tcp.port) and info.adbd_state.
Applied properties with another effective port report conflict; non-running
adbd reports failed. Apply restarts the allowed adbd service when its port
changes, it is not running, or readable /proc/net/tcp{,6} tables lack a
local :15B3 LISTEN (0A) entry. info.adb_listener is listening/absent/unknown.
Absent listeners report failed. After restarting, apply waits up to four
seconds for the listener. Unreadable tables remain unknown. Both observed
properties remain read-only; LISTEN does not establish remote reachability.

Ordinary USB/adb exports add diagnostics through lib/diagnostics.sh. Collection
is read-only except for writing its dedicated export destination. Each artifact
is capped at 64 KiB, with a 32-log (2 MiB) budget. Status probes have separate
64 KiB/10 second limits; owned-path metadata is capped at 8 KiB per module,
and properties at 64 KiB. A manifest reports omissions, truncations and probe exits.
Owned files and payload directories contribute ls -ldn metadata only, never
contents. Only fixed log/result paths are copied; Wi-Fi configuration and
secret-named properties are excluded/redacted. Optional runtime diagnostics
use the fixed HMG_SEOGAEMO_DIAGNOSTIC_RUNTIME_DIRS list relative to SG_DATA_ROOT
(initially runtime); only .log, .txt, result and module-output files qualify.
Export publication retains 0755 directories and 0644 files.

## Wireless projection runtime results (2026-11-04)

The hook publishes diagnostics independently of loader mapping. Zygote (UID 0)
uses `/data/nemesis/backup/hmg-seogaemo/runtime/wireless-carplay-and-aa/`.
Each hooked app uses `Context.getDir("hmg-seogaemo-wireless-runtime", 0)`, normally
`/data/data/<package>/app_hmg-seogaemo-wireless-runtime/`, owned by that app's UID
(DiPO commonly UID 1000). No app writes into root's private state and no shared
world-writable directory is needed. These are generated diagnostics, never stock
baselines or an authorization channel. Root prepares the private runtime parent
through the package API before enabling the loader. Root reads only the fixed projection app
packages: `com.daudio.app.dipo`, `com.tcc.iap2server`,
`com.google.android.projection.sink`, `com.mnsoft.navi`, `com.android.btsettings`.

Each process writes `p-<pid>-<start_ticks>.txt` atomically via `.txt.tmp`.
UTF-8 `key=value` lines, first `=` is the separator; keys `[a-zA-Z0-9_.-]+`,
no escaping, duplicate keys/control characters/unterminated records rejected.
Values are sanitized to one line. Format 1 metadata: `format=1`, `boot`, `pid`,
`start_ticks` (field 22 of `/proc/<pid>/stat`), `uid`, `package`, `process`,
`payload_jar_sha256`, `payload_loader_sha256`. Payload hashes are captured in
zygote before forks, identifying the loaded generation even after an OTA replaces
disk files. Zygote writes `gate.state=initializing|ready|off` and `gate.reason`
for its identity/map/seal/stock-hash/boot-hook decision. App records inherit
`gate.state`/`gate.reason` from the zygote;
`part.<name>.state=initializing|loaded|failed|off`, `.reason` and `.missing`
describe Java installation. Parts are `dipo`, `iap2`, `gpm_usb`, `gpm_tbt`,
`navigation`, `btsettings`; `.missing` lists absent stable IDs, comma separated.
`native.<library-basename>.state=registered|applied|failed`, `.reason` and
`.errno` describe recipe registration and the single native apply attempt.
Registration is waiting for a library, never proof of application. Errno 0
means no syscall error captured; failures include numeric errno and common
symbolic names (e.g. `13:EACCES`). Errors may follow partial hook/native writes;
restart, never retry native writes in the same process. `event.*` holds bounded
late failures (e.g. iAP2 bind/cluster/callback errors).
For stock GPM turn-by-turn (`stepofspec >= 999`),
`native.libautoreceiver_infobank.so.applicability=stock` and the part reason
explicitly identify why no AA recipe is registered.

Snapshots are capped at 64 KiB, 256 fields, 8192 characters/value. The reader
rejects oversized/malformed records. The process also mirrors Java tag messages
directly into `p-<pid>-<start_ticks>.log`, rotating to `.log.1` at 64 KiB
(each message capped at 2048 characters), preserving them before logcat wraps.
This keeper requires no logcat reader thread or service in zygote. Native startup
messages preceding Java initialization remain logcat-only; native recipe failures
are also recorded through Java. Retain at most eight process stems per directory,
including stale boots and interrupted temporary files; oldest artifacts are pruned
on publication (best effort across simultaneous processes). Files are 0600,
directories 0700; root can include them in a future export. Diagnostic
I/O/permission/storage errors are caught and never stop or enable hooks. Missing
diagnostics therefore mean unknown readiness, never success. Cleanup/export wiring
belongs to the root consumer; app-private files may survive module revert.
At status/export time root gathers only validated current app-private files from
those fixed packages into `$SG_DATA_ROOT/runtime/wireless-carplay-and-aa/apps/<package>/`.
Use bounded reads/copies (64 KiB/file), reject symlinks/nonregular files/incorrect
UIDs, and never trust paths from the record. The collector includes `.txt` and
`.log`; include `.log.1` for the older half of the ring. Across all packages
select at most 32 current files for export. Stale boots/PIDs must not replace
current failures.

Status must check expected boot, live PID/start ticks, expected UID/package/process,
loaded payload hashes versus installed payload, `gate.state=ready`, and all relevant
parts/native results. Absent processes/results are pending/unknown; a gate/part/recipe
failure must not be reported as on. `part.*=loaded` means initialization returned,
not a successful wireless session. In particular Core/Audio/Utils in DiPO and AA
in GPM must be applied (or explicitly deemed inapplicable by the consumer) before
claiming those native capabilities. Reject symlinks, nonregular files and unexpected
ownership; never source/eval the record as shell. These app-owned reports are
diagnostic evidence, not a defense against a compromised OEM app. Seal validation
still checks only `product\nhash-id\n`; additional seal lines remain compatible.

The consumer uses `sg_projection_runtime prepare|status|applying|clear` in the
package API, restricted to the projection owner. `prepare` creates the runtime
parent and module directory as root:root 0700 before loader installation. The
API invokes the packaged API 19 Java reader (SHA-256 through MessageDigest,
KitKat libcore lstat for UID/type); it never executes record contents. Expected
app UID comes from the fixed package's data directory, and proc UID, cmdline,
record process and start ticks must agree. Only the package main process or its
colon subprocesses qualify. Every current record is considered, so a ready
record cannot hide another current failure. Zygote plus all five packages and
their relevant parts/native recipes are required for `on`. Missing evidence is
`unknown`; an apply receipt uses `pending-restart` for missing evidence so the
dispatcher can retain enabled intent. Old loaded hashes mean `pending-restart`.

Collection replaces only generated app copies, never baselines, with at most
32 files of 64 KiB across the fixed packages; malformed, stale, aliased or
incorrectly owned inputs are omitted. Current old-generation records remain
exportable as restart evidence. `clear` removes the generated root module tree
after successful revert (including an already reverted module), and removes an
empty runtime parent. App-private originals may remain. Export probes status
first, then includes the fixed app-copy depth and `.log.1`.
The reader runs with `ANDROID_DATA` fixed to the module's private `vm/` tree,
including its 0700 root-owned `dalvik-cache/`, so executing the reader never
leaves a global Dalvik cache entry. Successful clear removes this generated
cache along with the diagnostic copies.
The fixed reader artifact is checked beneath the canonical selected package
directory, allowing OTA's `update/current` package pointer.

The reader is a root Dalvik process that can hang, so the API never runs it
inline. It requires a non-empty `BOOTCLASSPATH` (the inherited value, else the
`export BOOTCLASSPATH` line of `/init.environ.rc`; the value must start with
`/system/` and use only path-list characters) and otherwise reports
`state=unknown` without starting the VM. It starts the reader in the background
with stdout and stderr in private files directly under the data root, waits with
`sg_bounded_wait <pid> <seconds>` (1-second polls, 15 seconds by default,
`SG_READER_TIMEOUT` for tests, capped at 120), and on expiry kills the reader and
its children (`sg_kill_tree`, toolbox `ps` PID/PPID fields) and returns 124.
Output must be exactly one `state=`, `restart=` and `detail=` line with a known
state/restart pair and a clean exit (1 only with `state=failed`); anything else,
or a timeout, is `state=unknown` (`pending-restart`/`head_unit` for `applying`).
`status` therefore never keeps `operation.lock` longer than the limit. `clear`
that cannot run the reader (timeout, no boot class path, exit 126 or above)
removes the root-only generated tree directly; a reader refusal (exit 1) still
fails. `sg_bounded_wait` and `sg_kill_tree` are generic and are also used for the
mapper and the update verifier. Callers under `set -e` must write
`sg_bounded_wait ... && code=0 || code=$?`.

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
File and directory metadata guard failures emit `info.restore_conflict` with
`file preflight: <path>` or `dir preflight: <path>` during restore preflight.
Direct restore and apply/delete guards identify their own stage in the same
field. These diagnostics keep the conflict return code (3) and retain all
baselines; they never authorize a mutation or weaken an existing guard.


### Status punctuation (2026-11-04)

sg_clean preserves ASCII semicolons in human-readable status detail. Module root
status strings are ASCII; auditing found no non-ASCII punctuation in those sources.


Confirmed app plans execute their reviewed steps without replanning. An OTA
switch resume stores its originating kernel boot ID; a collected head-unit
restart requires a different boot before update continuation, plus target
readiness and usable network. Map refresh completion retries the continuation
so an already-connected launch does not lose its only connectivity event.

## Boot map verification and deferred application (2026-11-04)

Reconcile checks a packaged nonempty `hookpoints.required/<id>` before applying
desired-on modules. It requires successful map status `state=on`, otherwise
attempts fixed `map verify` once per pass. Unverified modules and their
transitive dependents remain desired on and are deferred; independent modules
continue normally. Pause, APK presence, desired state and selected payload are
checked again after verification before application. Reverse restore discovery
includes every restore-material class, including directory-only partial applies.

The NEMESIS once service and OTA health return after the initial bounded pass.
Map deferral is not a package health failure; actual module failures still fail.
Deferred work continues in one private, deduplicated detached worker, retrying
only deferred IDs every 30 seconds. It ignores HUP, disconnects caller stdio with
internal exec redirection, changes to `/`, and clears the OTA health flag. Its
root-owned `reconcile-retry` marker holds the actual child PID, launcher token and
canonical payload identity. Stale or interrupted claims are reclaimed after at
most five one-second publication polls. A changed payload can replace an old
claim; the old worker checks exact ownership before applying and cannot remove
the replacement marker. Workers terminate on stock pause, missing APK, payload
selection change, or no remaining desired work. No public root verb is added;
watcher continues to invoke only declared watch events, never apply/revert.

Mapper `status` is bounded to 15 seconds and `verify` to 30 seconds, in addition
to the existing operation-lock wait of at most nine seconds. Timeout or signal
cancellation terminates and reaps the actual app_process child and cleans up
owned lock and scratch state. Confirmed full mapping scans retain their existing
behavior. Later successful app verification is observed by the deferred worker.

## Automatic intent recheck under lock (2026-11-04)

After acquiring operation.lock, reconcile calls re-read current desired state and
APK presence before journaling or executing module code. Automatic apply requires
desired on and an installed APK. Automatic reverse revert requires desired not on
or a missing APK. If the condition no longer holds, return success with
state=not-implemented, restart=none, info.skipped=true, current info.desired and
detail=Automatic module intent changed. This receipt describes skipped work, not
observed module state. It does not rewrite desired state, invalidate stock
receipts or invoke the module. Explicit user calls retain their confirmed intent.

## Recovery when the selected OTA payload is damaged (2026-11-04)

The installed dispatcher routes update stage/apply/rollback/status and package
stock restore/status/uninstall through the stable installation before OTA
selection. Device reboot and ledger export also remain available there. Stock
transactions may use the selected runtime only after checking required nonempty
runtime files, generated module manifests and shell syntax with sh -n; otherwise
they retain the installed guarded restore implementation. The NEMESIS uninstall
hook always enters the installed stock controller rather than a selected hook.

Ordinary dispatch delegates only after the same completeness and syntax check.
An invalid selected payload falls back to the installed runtime for module
status/precheck and map status. Other module/map mutations return a structured
failed/none receipt directing the owner to rollback or USB reinstall, without
executing either payload's mutation. A failed delegated mutation is never
replayed. These checks detect missing/empty files and shell parse damage; they do
not replace signed OTA verification or prove runtime behavior of valid syntax.
