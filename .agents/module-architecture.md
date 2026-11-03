# Built-in module architecture

Design for HMG-Seogaemo on Gen5W (Android 4.4.2/API 19, 1920×720 hdpi, English default and Korean), installed by NEMESIS 1.16.4+. This is a design, not implemented behavior. The target setup is **NEMESIS + HMG-Seogaemo only**: HMG-API is not installed. The built-in modules are `wifi-client`, `wireless-adb`, and future `wireless-carplay-and-aa`, compiled into one APK and NEMESIS package; there is no third-party loader. All feature behavior is independently reimplemented without copying AGPL/GPL code.

## Module contract

Each `Module` has a stable ID; English/Korean name and description resource IDs; Settings category and optional Home action; `requiresRoot`; a small typed settings schema (boolean or bounded choice/value with defaults); `checkCompatibility`, `checkConflicts`, `apply`, `revert`, and `status`; and a restart requirement (`NONE`, `ADBD`, or `HEAD_UNIT`). `status` reports desired, observed, pending restart, error code, and last operation time. The registry lists only built-in modules and is generated at build time from `modules/*/module.properties` (see layout below). `owns_props` and `owns_files` name state a module may change; `conflicts` lists external owners to detect and refuse, while `depends` and `excludes` relate built-in IDs.

`wireless-adb` does not depend on `wifi-client` (optional; see `module-runtime-contract.md`, "Current module decisions"). `wifi-client` excludes `wireless-carplay-and-aa`; exclusion is symmetric even if declared on one side. Enabling a module auto-enables its full dependency chain after UI confirmation. Disabling a module auto-disables all dependents. Enabling either excluded module prompts with the exact enabled modules that will be turned off, including dependents, then disables them before enabling the requested module. The root side independently re-checks dependencies and exclusions and refuses inconsistent states. Boot reconciliation applies in dependency order and reverts in reverse dependency order. The current scaffold only exposes generated relations; UI and root operations remain future work.

Compatibility is checked both before enabling and at root execution: Android/API, `ro.board.platform`, `ro.build.product` (model, region, firmware), actual Wi-Fi chip, required stock files/init behavior, and `nemesis version >=1.16.4`. Unknown or untested variants fail closed with a reason. NEMESIS descriptor `os`/`models` narrows installation; it does not replace module checks. Descriptor `depends=!id` is reserved for a package-wide conflict; module-only conflicts, including `adb.tcp`, are checked at runtime so the rest of Seogaemo remains installable. HMG-API, Leafia, and NEMESIS package ownership checks are only safety checks to detect leftovers and refuse mutation; none is a runtime dependency (`tmp/hmg_nemesis/04-Packages.md:27-80`).

## State, privilege, and recovery

The root side owns `/data/nemesis/backup/hmg-seogaemo/` (mode 0700): atomic, versioned desired-state/settings records, per-module baseline snapshots, operation journal, and bounded logs. APK preferences keep only UI choices such as language; they are never the authority for enabled state. Before the first mutation of an enable session, snapshot each owned persistent property's **live value and `/data/property/<key>` existence/content**, including the difference between absent and empty; snapshot any owned file's bytes, existence, mode, and ownership. Wireless ADB changes no stock file. Never overwrite the baseline on reapply or update. On successful revert, restore the saved values and backing-file absence/content; clear the baseline only after verification. If a property was originally absent, clear its live value and remove its persistent backing file; a reboot may be needed for exact boot-state restoration (compare HMG-API's unset path at `tmp/HMG-API/core/runtime/java/hmgapi/engine/Engine.java:2007-2013`).

Operations take one root-side lock and journal intent before mutation. Apply changes only differing values, verifies readback, and commits desired state after success; repeated apply/revert is harmless. Failure rolls back to the baseline when safe, otherwise retains the snapshot and reports an error. If an external owner or unexpected drift appears, stop rather than overwrite its changes. At boot, a NEMESIS root `kind=sh,mode=once` service waits until properties are available, checks compatibility/ownership, and reconciles enabled modules. The APK has no `BootReceiver`. On package update, the old `preuninstall` recognizes a valid staged Seogaemo update and preserves state/baselines; the new `postinstall` migrates them and reconciles. On real NEMESIS uninstall, `preuninstall` reverts every enabled module and verifies restoration **before** permitting package removal, then removes package-owned state and the APK. Failed restoration leaves backups and aborts normal uninstall. NEMESIS runs the old hook on update and stops services before uninstall (`tmp/hmg_nemesis/Sources/native/pkg.c:630-680,851-880`); backup cleanup is the package's responsibility (`tmp/hmg_nemesis/04-Packages.md:184-186,241-249`). Direct `pm uninstall` of only the APK bypasses that hook: the root boot service must detect the missing APK and revert on the next boot; the UI should direct users to NEMESIS package uninstall.

The UI is an ordinary, unprivileged APK. It calls a fixed root entry point through NEMESIS: `hmg-seogaemo-root <id> apply|revert|status` (later settings: `set <id> <known-key> <validated-value>`). The root script accepts only registered IDs, verbs, and typed values; uses fixed paths/commands, no caller-supplied shell fragments or arbitrary file paths; limits output; and sets `umask 077`. The app constructs a constant command for each action and parses bounded machine-readable status. NEMESIS's root socket is accessible to any UID, so this limits **Seogaemo's own interface**, not NEMESIS's global root capability (`tmp/hmg_nemesis/Sources/native/su.c:64-103,229-258`).

## Source and package layout

**Decided: one module = one directory under `modules/`, and the module list is generated at build time from day one.**

```text
Makefile                            # top level: APK + NEMESIS zips into out/
tools/gen-modules.sh                # POSIX sh + awk: modules/*/module.properties -> generated files
app/                                # module-independent shell
  AndroidManifest.xml
  res/values*/strings.xml           # shared UI strings (EN default, KO)
  src/com/seogaemo/hmg/
    ui/                             # Home, Settings, sidebar, shared row views
    module/                         # Module, ModuleStatus, ModuleRegistry, RootClient
modules/
  wifi-client/                      # dir name == module id
  wireless-adb/
  wireless-carplay-and-aa/          # future behavior; stub only
    module.properties               # id, class, version, requires_root, restart,
                                    # owns_props, owns_files, depends, excludes, conflicts
    src/com/seogaemo/hmg/modules/wirelessadb/WirelessAdbModule.java
    res/values/strings.xml          # wireless_adb_* strings (EN)
    res/values-ko/strings.xml       # (KO)
    root/wireless-adb.sh            # apply | revert | status, run as root
    test/                           # off-car script tests
nemesis/                            # module-independent package parts
  package/NEMESIS.data              # descriptor 1.1 template
  package/bin/hmg-seogaemo-root         # validated dispatcher, only ids in modules.list
  package/bin/open.sh               # Manager Launch / APK recovery
  package/scripts/postinstall.sh, preuninstall.sh
  package/svc/reconcile.sh          # one-shot boot reconciliation
  usb/NEMESIS.data                  # USB install wrapper
out/                                # build output (gitignored)
```

Naming: module ID and directory use kebab-case (`[a-z0-9]+(-[a-z0-9]+)*`); Java package drops the hyphens (`com.seogaemo.hmg.modules.wirelessadb`); string resource names use the snake_case ID as prefix (`wireless_adb_*`); the root script is `<id>.sh`.

Build-time module list (`tools/gen-modules.sh`, run by `make` before aapt/javac):

1. Scan `modules/*/module.properties` in sorted order. Fail the build if `id` ≠ directory name, an ID is duplicated or malformed, `class` is missing from that module's `src/`, `root/<id>.sh` is missing while `requires_root=true`, or a resource name lacks the module prefix.
2. Parse `depends=` and `excludes=` as comma-separated built-in IDs (empty allowed). Reject unknown IDs, self-reference, dependency cycles, and any dependency closure containing an excluded pair. Symmetrize `excludes` from either declaration. Emit a topological order, choosing the lowest ID whenever multiple modules are ready.
3. Generate `out/gen/com/seogaemo/hmg/module/GeneratedModules.java`: static Java 1.7 arrays that instantiate each class and expose ordered IDs, dependencies, and symmetric exclusions. `ModuleRegistry` provides read-only relation copies; no runtime switch logic is implemented yet.
4. Generate `out/package/modules.list` in dependency order and `out/package/modules.graph` with tab-separated `id`, `depends=<csv>`, `excludes=<csv>` fields. Copy scripts to `out/package/modules/<id>.sh`, and render each `module.properties` with the project `VERSION`. The packaged `hmg-seogaemo-root` accepts only listed IDs and fixed verbs; module behavior and reconciliation remain stubs.
5. Compile with `javac` over `app/src`, `modules/*/src` and `out/gen`; link resources with `aapt package -S app/res -S modules/<id>/res … --auto-add-overlay` and a single `R` package `com.seogaemo.hmg`.

Adding a module = adding a directory; removing it = deleting the directory. No hand-edited list exists anywhere.

`MainActivity` currently holds both screens and in-memory mock toggles; its Wireless pane is not live (`app/src/com/seogaemo/hmg/MainActivity.java:40-49,257-285,374-390`). Implementation later splits views without changing API 19/framework-View constraints. `make package` now assembles the NEMESIS descriptor 1.1 with the root shell app entry, a one-shot service, the API 19 v1-signed APK, and the USB wrapper. Both archive names start with `hmg-seogaemo-<version>`; the USB wrapper points to `packages/hmg-seogaemo.zip`. Release builds require the stable keystore; debug builds use a throwaway key. See `.agents/research-nemesis-packaging.md` for the current package behavior and remaining car validation.

## Wi-Fi client module (`wifi-client`)

Off by default. Gate apply on `ro.build.product` matching `NEEV.KOR.*.260902` (real value `NEEV.KOR.0000.V039.001.260902`), detected physical Wi-Fi chip, validated stock `featurexml.cfg` pattern, NEMESIS 1.16.4+, and no HMG-API/Leafia/NEMESIS wireless package leftovers claiming the files. `owns_files` is `/system/etc/permissions/android.hardware.wifi.xml` and `/system/onebin/neev_kr/featurexml.cfg`; `owns_props` is empty. First version adds the PackageManager Wi-Fi feature XML and handles only its active removal line in `featurexml.cfg`. Capture each file's existence, exact bytes, mode, and owner before mutation; preserve the baseline through reapply/update; revert exact bytes or absence, then verify before clearing it. Apply/status must distinguish unsupported, conflict, pending head-unit reboot, and observed stock Settings Wi-Fi menu. Network selection uses that stock menu; Seogaemo has no picker. `restart=HEAD_UNIT`. It excludes `wireless-carplay-and-aa`, which also disables its dependents when `wifi-client` is enabled. `wireless-adb` offers it as an optional "Connect to another Wi-Fi" action. See the on-car checklist in `.agents/research-wifi-client.md` for apply, reboot, menu/network, conflict, and exact-restore checks.

`persist.wifi.state=3` will be tested later.

## Future wireless projection module (`wireless-carplay-and-aa`)

Because HMG-API is absent, the car-as-AP wireless CarPlay/Android Auto behavior must come from our own future module. This is a large separate research item: wireless feature files, projection/bridge properties, and possibly hooks must be mapped and tested before defining owned state or behavior. Its current directory is a stub, excluded by `wifi-client`.

## UI and Wireless ADB (`wireless-adb`)

The existing Wireless category hosts the module's **off-by-default** toggle, fixed `5555` port and connection rows based on the observed interface address, observed state, and a stock-switch warning. The Home Connectivity tile may deep-link to that pane; it is not a second switch. Replace the current mock CarPlay/Android Auto toggles only when real modules exist. Keep 70 dp sidebar and at least 75 dp setting rows, 60 dp actions, large text, clear press states, and EN/KO strings (`.agents/ui-guidelines.md`; `app/res/values/strings.xml:25-49`). Show `Off`, `On`, `Applying`, `Conflict`, `Failed`, or `Restart required`, plus per-module error/log detail in Logs. A restart banner names the reason and offers a deliberate head-unit restart action; a successful ADB daemon restart alone does not claim the head unit rebooted.

Wireless ADB owns only `persist.service.adb.tcp.enable` and `persist.adb.tcp.port`. Enabling: verify compatibility and no other owner; capture baseline; set the stock switch to `0`; set TCP port to `5555`; restart `adbd` only if the port changed; verify both property values. Test ADB reachability separately on the car. The stock switch can assign `eth0` `192.168.0.123`, overlapping the hotspot subnet; if that address is already present, report **head-unit restart required** to clear it (`tmp/HMG-API/core/runtime/java/hmgapi/engine/Engine.java:1900-1917`). Use an observed reachable address for the connection command; `192.168.0.1:5555` applies only when the car hotspot is running, and `wifi-client` does not promise that AP. Disabling restores both exact baselines and restarts `adbd` if the effective port changes. A saved stock switch value of `1` can reintroduce the projection conflict after revert; warn before disabling/uninstalling. USB ADB and `persist.sys.usb.config` are outside this first module (`tmp/HMG-API/mods/adb/patches.json:3-7`, `settings.json:4-9`).

Block enable if NEMESIS `adb.tcp` is installed: it sets the opposite switch value and edits `/system/build.prop` (`tmp/hmg_nemesis/Sources/packages/adb-tcp/NEMESIS.data:1-12`, `scripts/common.sh:288-300`). Also detect leftover HMG-API `adb` ownership and Leafia's `wireless.carplay.aa` ADB marker; if ownership is ambiguous, show a conflict and make no change. Leafia's marker records a conditional property fix and its uninstall may restore that fix (`tmp/HMG-Leafia-Plus/packages/wireless-carplay-aa/scripts/common.sh:313-335`). Stock adbd has no key authentication; anyone able to reach its TCP port may connect. The owner accepts this risk; no key-authorizing daemon is planned. Keep the module off by default. Reachability and firewall behavior remain car-specific.

Car test: record model/firmware/NEMESIS version and original property/file states; verify default off; enable with the stock switch initially on and off; check `eth0`, hotspot DHCP, wireless CarPlay and Android Auto, `adb connect`, and status after reboot. Repeat apply, disable, reinstall/update, and uninstall; compare exact original state and verify no stale TCP listener. Test with each external owner installed, with a failed `adbd` restart, and with an interrupted apply. Do not infer success from property readback alone.

## Remaining scope question

USB ADB switching, if desired later, needs a separate design because it can disrupt wired phone projection and USB drives.
