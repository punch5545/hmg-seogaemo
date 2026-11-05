Status: current — index of the agent notes, reviewed against local main on 2026-11-03.

# Agent notes

Read `module-architecture.md`, `module-runtime-contract.md`, `ui-guidelines.md`, and `ota-implementation.md` first. Then read the module implementation notes relevant to your change. The research notes retain upstream evidence and explicitly mark older HMG-Seogaemo proposals as historical.

| Document | Status | Purpose |
| --- | --- | --- |
| [backlog.md](backlog.md) | Current | Open on-car checks and planned work; delete items when done. |
| [boot-lifecycle.md](boot-lifecycle.md) | Current | NEMESIS startup semantics, readiness retries, and QuickBoot evidence limits. |
| [module-architecture.md](module-architecture.md) | Partly superseded | Built-in modules, package, state, UI, and recovery architecture; Home wiring is current. |
| [module-runtime-contract.md](module-runtime-contract.md) | Partly superseded | Implemented root/Java contract and test rules; original parallel work ownership table is historical. |
| [module-runtime-implementation.md](module-runtime-implementation.md) | Current | Runtime and host-test implementation findings. |
| [stock-restore.md](stock-restore.md) | Current | Whole-package stock restore, detached uninstall, recovery and on-car checks. |
| [android44-shell-audit.md](android44-shell-audit.md) | Current | Pinned KitKat toolbox/mksh evidence, compatibility fixes, lint boundaries and waivers. |
| [ui-guidelines.md](ui-guidelines.md) | Partly superseded | Display tokens and touch measurements; emulator capture commands are historical. |
| [display-size-support.md](display-size-support.md) | Partly superseded | Upstream display research and the current single-panel decision. |
| [ota-implementation.md](ota-implementation.md) | Current | Signed GitHub Releases OTA, trust roots, transaction, and validation gaps. |
| [wifi-client.md](wifi-client.md) | Current | Implemented Wi-Fi client file edits, status, tests, and on-car checklist. |
| [wireless-adb.md](wireless-adb.md) | Current | Implemented ADB property behavior, network observations, and on-car checklist. |
| [wireless-carplay-and-aa.md](wireless-carplay-and-aa.md) | Current | Implemented AGPL projection port, owned changes, recovery, and on-car checklist. |
| [user-button-shortcut.md](user-button-shortcut.md) | Current | Independent steering/head-unit shortcuts, owner-approved reassertion on boot/wake/property drift, original restore and KOR evidence. |
| [skip-consent-screen.md](skip-consent-screen.md) | Current | Factory82 provenance, owner-tested NEEV KOR behavior, implemented watcher and reboot restore, on-car checklist. |
| [neev-kor-api-inventory.md](neev-kor-api-inventory.md) | Current | Offline NEEV KOR vehicle/vendor API inventory, exact signatures, risk classes, candidate hooks and deferred on-car checks. |
| [research-car-mapping.md](research-car-mapping.md) | Implemented plus historical research | KOR evidence, core mapper design, and remaining on-car validation. |
| [catalogue-v101.md](catalogue-v101.md) | Current | KOR evidence for 18 v1.0.1 additions, honest coverage, and verification-cache migration. |
| [cluster-lane-frame.md](cluster-lane-frame.md) | Current | 81-byte stTPLaneInfo cluster lane frame, firmware-confirmed trailer bitmasks (hi-pass/bus/toll/overpass). |
| [navi-lib-index.md](navi-lib-index.md) | Current | Mechanical index of the stock navigation engine libExSLNavi.so (binary not committed). |
| [carplay-tbt-external-research.md](carplay-tbt-external-research.md) | Current | Third-party research on iAP2 route guidance and Hyundai cluster/HUD TBT (with `carplay-tbt-external/`). |
| [carplay-tbt-learnings.md](carplay-tbt-learnings.md) | Current | CarPlay turn-by-turn fixes ported from HMG-API, open problems, Wi-Fi client/AP findings. |
| [research-nemesis-packaging.md](research-nemesis-packaging.md) | Partly superseded | NEMESIS source research and current package layout; older proposal marked historical. |
| [research-ota-update.md](research-ota-update.md) | Partly superseded | Upstream OTA research and historical design choices, with current release notes. |
| [research-wifi-client.md](research-wifi-client.md) | Partly superseded | Upstream STA/AP research and updated HMG-Seogaemo module test plan. |
| [research-wifi-enable.md](research-wifi-enable.md) | Historical research | Superseded core Wi-Fi proposal; use `wifi-client.md` for current behavior. |
| [research-adb-wireless-module.md](research-adb-wireless-module.md) | Partly superseded | Upstream ADB research; design inferences predate `wireless-adb.md`. |
| [car-session-followups.md](car-session-followups.md) | Current | Real-car USB reinstall and status/export corrections. |
| [module-plugin-design.md](module-plugin-design.md) | Proposal | Runtime-installable module packages, tiers, trust, install/remove, and AI-friendly tooling. |
| [module-resources.md](module-resources.md) | Proposal | Resource names and modes modules use to declare conflicts. |
| [README.md](README.md) | Current | This index and reading order. |
| [ci-upstream-tracking.md](ci-upstream-tracking.md) | Current | CI timing, bounded shell-suite parallelism, workflow pins and upstream review evidence. |

The target is Hyundai Ioniq 5 KOR, `ro.build.product=NEEV.KOR.0000.V039.001.260902` (package glob `NEEV.KOR.*.260902`), onebin `neev_kr`, Android 4.4.2. Installation is NEMESIS plus HMG-Seogaemo only. Off-car tests do not establish physical-car behavior.

Home now collects `Module.homeTiles()` from enabled modules and keeps Settings first. The current three modules provide no Home tiles, so Settings is the only card.

Core mapping uses `core/hookpoints/`, `core/map/`, and `maps/`. The operational workflow is `docs/mapping-new-firmware.md`; `UPSTREAM.md` pins HMG-API reference paths and `tools/upstream-diff.sh` reports later changes. Settings → Car mapping is the core status, mapping, and PR export page.
