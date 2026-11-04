Status: proposal (2026-11-04), not implemented. Owner chose runtime-installable modules (option B).
The registry platform is deferred (owner, 2026-11-04); modularization comes first.

# Installable module design

## Goal

Non-developers install, remove, and use modules from the head unit without a laptop.
Developers, including people working with an AI assistant, can write, test, and ship
a module without rebuilding the app. Exact stock restore stays the top priority: an
installed module gets no write path that the runtime cannot record and undo.

## Current constraint

Modules are compiled into the APK. `tools/gen-modules.sh` generates the registry from
`modules/*/module.properties`, each module's UI is a Java class implementing
`module/Module.java`, and strings are Android resources. Adding or removing a module
therefore needs a new APK build, a new release, and an app reinstall.

## Package format (owner decision 2026-11-04)

No JSON. The head unit has only toolbox and mksh, so every file is either key=value or a
shell script the root side reads directly; nothing is compiled on the host.

One file per module: `<id>-<version>.hsm`, a zip archive.

```
module.properties  what the module is and what it needs (key=value)
apply.sh           what it does: runtime verbs plus if/elif/else/fi (restricted, see below)
check.sh           optional pre-enable checks: block or warn, same restricted syntax
status.sh          optional extra status checks, prints key=value and info.* lines
ui.txt             optional screen, one block per line
strings/ko.txt     display strings per locale, key=value; en.txt is required
files/             optional payload copied by put_file, hook code for hook_apps
README.md          what the module does, in plain language
```

### module.properties

Plain names replace the internal `owns_*` keys.

```properties
id=wireless-carplay-and-aa
version=1.0.0
api=1
cars=NEEV.KOR.*.260902
restart=head_unit
rerun_on=boot,screen_on
uses=wifi.radio:ap,system.app_process:only
conflicts=hmgapi.wireless.carplay.aa,leafia.wireless.carplay.aa,nemesis.wireless.projection
stock_file.1=/system/bin/app_process:33f798115689d1859e8d2159dc88e7dc
hookpoints=hookpoints.txt
setting.1=audio_fix:toggle:on
```

| Key | Meaning | Replaces |
| --- | --- | --- |
| `cars` | product globs the module may run on | product checks in scripts |
| `restart` | `none`, `adbd`, or `head_unit` | `restart` |
| `rerun_on` | `boot`, `screen_on`: re-run `apply.sh` in reassert mode (see Lifecycle) | `watch` |
| `depends` | our modules that must be on first; enabled in the same confirmed plan | `depends` |
| `uses` | shared resources and how they are used; see Conflicts | `excludes` (mostly) |
| `not_with` | rare: a specific module id that cannot be on together | `excludes` |
| `conflicts` | external tools that already own the feature; refuse to apply | `conflicts` |
| `stock_file.N` | path and stock MD5 required before apply | `known_stock.N` |
| `hookpoints` | file listing required stable hookpoint IDs | `hookpoints.required` |
| `setting.N` | `key:type:default`; types `toggle`, `choice(a|b)`, `number(min..max)` | `Module.settings()` |

Ownership (`owns_props`, `owns_files`, `owns_lines.N`, `services`, `service_calls`) is
not written by hand. Because `apply.sh` is restricted, the touched list is the set of
every write verb in every branch, collected statically at install time (and by
`hsm pack`). On the car the runtime rejects any write outside that list.

### Conflicts (owner decision 2026-11-04)

Modules declare what they use, not which modules they dislike. Three layers:

1. Automatic: two modules whose touched lists overlap (same property, whole file, or
   stock line) cannot be on together. Nothing to write.
2. `uses=<resource>:<mode>,...`: the same resource used in different modes conflicts
   (`wifi:ap` vs `wifi:sta`); the same mode may coexist; `:only` makes the resource
   exclusive. Names are hierarchical and come from the catalogue in
   `module-resources.md`; `x.<author>.<name>` is free for custom use.
   `wireless-carplay-and-aa` declares `uses=wifi.radio:ap`, `wifi-client` declares
   `uses=wifi.radio:sta`, and neither names the other.
3. `check.sh` (optional): runs before enable with read-only checks only, plus
   `uses_on <resource>:<mode>`; `block @string` refuses with a reason.

```sh
if uses_on wifi:sta; then
    block @need_wifi_free
fi
```

`not_with` remains for the rare case that must name a module; `conflicts` stays for
external tools. When enabling is refused, the app shows the reason in plain language and
offers to turn off the blocking module through the normal confirm flow.

### apply.sh

Only runtime verbs may write. Each verb baselines, writes, verifies, and appends to the
ledger. There is no revert script: turning a module off replays its ledger backwards,
so every branch that ran is restored exactly.

```sh
set_prop persist.ium.support carplay
set_prop persist.sys.iap2.wifi t
put_file /system/lib/libleafiaaac.so files/libleafiaaac.so

if file_missing /system/etc/permissions/com.hkmc.software.wifihotspot.xml; then
    put_file /system/etc/permissions/com.hkmc.software.wifihotspot.xml files/feature/wifihotspot.xml
fi

if setting_is audio_fix on; then
    put_files /system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes/ files/recipes/
fi

replace_line /system/onebin/neev_kr/featurexml.cfg "<stock line>" "<new line>"
hook_apps files/hook.jar com.daudio.app.dipo com.tcc.iap2server
app_data_dir wireless-runtime com.daudio.app.dipo com.tcc.iap2server
```

Write verbs: `set_prop`, `put_file`, `put_files`, `replace_line`, `restart_service`,
`call_service`, `hook_apps`, `app_data_dir`.
Read-only checks for branching: `setting_is`, `prop_is`, `file_exists`, `file_missing`,
`car_is`, `module_on`, `hookpoints_ok`, plus ordinary mksh tests on their output.
Settings reach the script only through `setting_is` and `setting_value`, validated
against `module.properties`; free-form values never reach a command line.
Also available: `log <text>` (developer log, not the ledger) and, in `check.sh`,
`block @string` (refuse enable) and `warn @string` (shown in the confirm dialog).

Restricted interpreter (owner decision 2026-11-04). `apply.sh`, `check.sh` and
`status.sh` look like shell but are never run by a shell. A root-side mksh
interpreter reads them line by line and accepts only runtime verbs, read-only checks,
`if/elif/else/fi`, `not`, comments and blank lines; anything else is refused at install
with a line number. Host lint on a PC cannot protect the car, so this is the only
enforcement point for third-party modules. A module that needs real shell declares
`raw_shell=1`; it installs only in developer mode, behind a high-risk confirm, and
loses the exact-restore guarantee for anything written outside the verbs.

### ui.txt

One block per line; the app draws them at NEMESIS scale with existing settings
components. Without `ui.txt` the screen shows status, the on/off switch, and the summary.

```
status
toggle   setting=audio_fix  label=@audio_fix
info     @how_it_works
warning  @needs_restart
button   name=reconnect  label=@reconnect  confirm=@reconnect_confirm
enable   module=wifi-client  label=@use_wifi_client
```

Two more blocks: `value label=@adb_addr key=adb_endpoint` shows `info.adb_endpoint`
from the last status (replaces `infoRows`), and `open screen=wifi_settings` opens a
stock screen from a fixed core list (replaces `OPEN_STOCK_WIFI_SETTINGS`).

Every state-changing block goes through `ui.ConfirmDialog`. Modules ship no Java code
in API 1, so the app process stays free of third-party code. `button` runs
`apply.sh` where `action_is <name>` is true; `enable` uses the normal enable plan for
another module (replaces the Java `ENABLE_MODULE` action). Home tiles
(`Module.homeTiles`) are dropped: no module uses them and the Home redesign does not
need module-supplied tiles.

## Lifecycle rules

- Re-run: `rerun_on` events re-run `apply.sh` while the module is on. A re-run never
  captures a new baseline; it only reasserts values whose baseline already exists.
  `set_prop --reassert` marks a property that may be overwritten again when the stock
  firmware changes it (replaces `reassert_props`).
- Restart-only restore: a module whose writes are only `call_service` cannot be undone
  byte for byte. The runtime classifies it automatically (replaces `restore=reboot`),
  and the disable confirm says a head-unit restart restores stock behaviour.
- Setting change while on: re-run `apply.sh` with the new values, then revert ledger
  entries the new run no longer touches. A changed branch therefore leaves nothing of
  the old branch behind.
- `api=`: the runtime refuses an unknown API version. Later versions only add verbs
  and blocks; existing meanings never change.
- IDs: built-in module IDs are reserved; installing a module whose `id` is already
  installed from another source is refused.
- Whole restore and HMG-Seogaemo uninstall include installed modules in the same
  dependency graph and revert them in reverse order.

## Core hook host

The `app_process` wrapper, native loader, boot guard, stock-MD5 check, Dalvik cache
tracking, and wrapper restore move from `wireless-carplay-and-aa` into the core. Modules
ship only hook code (a jar per target process) and call `hook_apps`; hook code resolves
stable hookpoint IDs through the core map. Several modules can then hook apps without
each replacing `app_process`. The wrapper is derived from HMG-API (AGPL-3.0): keep its
headers and provenance in `UPSTREAM.md` when it moves.

Hook code contract (to define in P3): one entry class per jar named in `hook_apps`,
called once per target process with the process name; a core API to resolve stable
hookpoint IDs (a missing ID disables that part, never crashes the app); a hook API
version; and the bounded per-process result record already used by
`wireless-carplay-and-aa` (`p-<pid>-<start_ticks>.txt`), generalised so `status` can
report every hooking module.

Expected shape of `wireless-carplay-and-aa` after conversion: three `set_prop`,
`put_file`/`put_files` for the AAC library and recipe files, `if file_missing` +
`put_file` for the three feature XMLs, three `replace_line` in `featurexml.cfg`,
`hook_apps` for its five target apps, `app_data_dir` for runtime and dex dirs, and a
`status.sh` for projection runtime readiness.

## Install and remove

Sources:
- USB: `hmg-seogaemo/modules/*.hsm` on the stick root; the app lists them.
- adb: `hsm push` copies to the staging directory and calls the fixed install verb.
- Store: a registry index fetched like OTA updates (deferred).

Install flow:
1. The app copies the archive to `/data/nemesis/backup/hmg-seogaemo/staging/<sha256>.hsm`.
2. `hmg-seogaemo-root module install <sha256>` (fixed verb, hex argument only) checks
   `module.properties`, product globs, ownership collisions with installed modules, and
   hookpoints, then unpacks to `/data/nemesis/backup/hmg-seogaemo/installed/<id>/`
   (outside the package directory, so package and OTA updates keep it).
3. The app shows a plain-language review before the confirm: what it changes, whether
   a restart is needed, whether it hooks apps, and its source.
4. Installed is not enabled. Enabling uses the existing plan and confirm flow.

Remove flow: revert through the ledger first, verify stock, then delete the module
directory. If the revert reports a conflict, keep the module and its backups and stop.

Update flow: install the new version beside the old one, revert the old one if its
touched list changed, then switch. An unchanged touched list keeps the baseline.

## Trust (owner decisions 2026-11-04)

Module code lives in each author's own GitHub repository (package files plus GitHub
Release assets). We keep only a registry of which repositories are listed. NEMESIS root
is reachable by any UID, so every check runs on the root side.

| Route | Who | Shown as |
| --- | --- | --- |
| Store (registered repositories) | everyone, mainly non-developers | 등록된 모듈 · 작성자 |
| Direct install (repository URL, USB or adb file) | developer mode on | 직접 추가함 · 검증 안 됨 |

Registry: a separate web platform, not a pull-request flow. Authors submit a
registration request; the platform shows which modules are registered; the owner can
also register any repository directly.

Direct install is expected to be the main path for module developers. They may share
files with others; the receiver must enable developer mode and accept an
unverified-module confirm. Every route uses the same ownership, ledger, exact restore,
and boot guard. Modules that call `hook_apps` get a high-risk install warning whichever
route they came from. Developer mode is a persistent setting behind a confirm.

## Developer and AI tooling

`hsm` host CLI (Python, alongside `tools/`):
- `hsm new <id>` writes a working module, an `AGENTS.md` for AI assistants, and a test.
- `hsm validate` checks properties, verbs, product globs, shell lint, strings, and
  touched-list collisions, with machine-readable errors
  (`HSM-E012 apply.sh:4: set_prop target persist.sys.x is owned by wifi-client`).
- `hsm test` runs apply, status, revert, and an exact-restore check against fakebin and
  the stock file snapshot from `maps/`, once per branch setting combination.
- `hsm pack`, `hsm push --adb <ip>`, `hsm logs <id>`.

In-app developer page (developer mode only), per module: raw status, last root call
stdout and exit code, ledger entries, single apply or revert, reload from USB.

## Implementation plan

Each phase lands as its own commits, each passing `make test`. Built-in modules keep
working through every phase.

### P0 Contract (docs only)

Define in `module-runtime-contract.md`: package layout, `module.properties` keys,
`apply.sh` verbs and checks, `status.sh` output, `ui.txt` blocks, strings format, the
installed-module directory, and the `module install|remove|list` verbs. Map every
current `Module.java` method to a property, a UI block, or a verb.

### P1 Root runtime

- Load modules from `/data/nemesis/apps/hmg-seogaemo/modules/<id>/` in addition to
  package-baked ones; same ownership, ledger, watcher, and reconcile paths.
- Verb layer in `lib/common.sh` over the existing `sg_*` functions; ledger-replay revert.
- `module install <sha256>`, `module remove <id>` (revert first, fail closed on
  conflict), `module list`.
- Host tests with fakebin for install, remove, collision, branch restore, conflict, and
  reboot reconcile.

### P2 App runtime

- `ModuleRegistry` reads `module list` output instead of the generated class list.
- One generic `PackageModule` implements `Module` from `module.properties`, `ui.txt`,
  and `strings/<locale>.txt`.

### P3 Core hook host

Move the `app_process` wrapper, loader, boot guard, and Dalvik cache tracking into the
core behind `hook_apps`, with host tests and on-car verification before any module uses it.

What is single-module today (survey 2026-11-04):
- `payload/native/zygote.c`: `HOOKDIR` is the CarPlay module state directory; the boot
  guard files (`enable`, `enabled`, `starts`, `disable`) live there.
- `payload/native/loader.c`: one fixed `JAR` (`/system/framework/hmg-seogaemo-wireless-hook.jar`).
- `payload/src/hmgapi/hook/HmgApi.java.src`: one `ENTRY = new Wireless()`, gate on the
  CarPlay `enabled` file, runtime record under `runtime/wireless-carplay-and-aa`.
- `lib/common.sh` `sg_zygote_install|upgrade|foreign|guard_sweep`: fixed target paths, and
  `/system/bin/app_process` is owned by the CarPlay module in the ledger.

Target shape:
- The core owns the wrapper, loader, engine jar and guard state under
  `/data/nemesis/backup/hmg-seogaemo/core/hook/` (not a module). The engine reads
  `core/hook/mods/<id>/{entry,processes,hookpoints}` written by the runtime and loads each
  mod jar with its own class loader into the listed processes only; a mod whose hookpoints
  are not verified, or that throws, is turned off alone.
- `hook_apps JAR PKG...` records a mod entry (whole-file owned by that module) and asks the
  core to install the wrapper when the first mod appears and restore stock when the last one
  goes. The core keeps its own ledger owner (`core-hook`) so restores stay exact.
- Per-mod runtime records `runtime/<id>/p-<pid>-<start_ticks>.txt` for `status`.

Migration for cars that already run CarPlay (the risky part): with CarPlay on, a package
update must move ownership of `app_process`, the stock backup and the guard files from the
module to `core-hook` without restoring stock in between. Plan: an upgrade journal like
`zygote-upgrade`, applied by reconcile at boot, refusing (conflict, nothing changed) on any
drift. Until it has been run on the car with adb recovery ready, CarPlay keeps the current
path and `hook_apps` stays refused at install.

Implemented 2026-11-05 as a separate path (CarPlay unchanged and mutually exclusive); see the
contract section "Core hook host" and `core/hook/`. Host tests cover install, shared wrapper,
release after the last module and stock restore. The CarPlay migration is still not done.

On-car checklist before relying on `hook_apps`: fresh enable and restore with only the core
host; CarPlay migration on a car with CarPlay on; boot guard after 3 forced zygote crashes;
OTA package update with the hook host on; stock restore and uninstall exact.

### P4 Convert built-ins, lowest risk first

1. `skip-consent-screen`
2. `user-button-head-unit`, `user-button-shortcut`
3. `wireless-adb` (its "connect another Wi-Fi" action becomes an `enable` block)
4. `wifi-client`
5. `wireless-carplay-and-aa` (native build stays in this repository)

Each conversion deletes that module's Java class and keeps its `test/run.sh` green.
After the last one, remove `gen-modules.sh` and the compile-time registry.

### P5 Developer tooling

Done 2026-11-04: `tools/hsm new|validate|pack|push`. `validate` installs into a throwaway
copy of the real root runtime (same interpreter and HSM-E errors) against this repository's
built-ins and prints the generated ownership; `tools/test-hsm-cli.py` covers it. Not yet:
`hsm test` (apply/revert on host is only safe for property-only modules, because file verbs
write absolute `/system` paths), `hsm logs`.

`tools/hsm` commands, one example module per complexity level, and the `AGENTS.md`
template so an AI assistant can write a valid module from a description.

### P6 Install UX

Developer mode, install from USB, adb, or repository URL, the review screen, and
remove. The registry platform and store come after this.

## Open decisions

- Registry platform hosting, index format, and how the app verifies the index.
- Whether the registry pins a release digest or follows each repository's latest release.
