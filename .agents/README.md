Status: current — index of the agent notes, reviewed against local main on 2026-11-03.

# Agent notes

Read `module-architecture.md`, `module-runtime-contract.md`, `ui-guidelines.md`, and `ota-implementation.md` first. Then read the module implementation notes relevant to your change. The research notes retain upstream evidence and explicitly mark older Seogaemo proposals as historical.

| Document | Status | Purpose |
| --- | --- | --- |
| [module-architecture.md](module-architecture.md) | Partly superseded | Built-in modules, package, state, UI, and recovery architecture; Home wiring is current. |
| [module-runtime-contract.md](module-runtime-contract.md) | Partly superseded | Implemented root/Java contract and test rules; original parallel work ownership table is historical. |
| [module-runtime-implementation.md](module-runtime-implementation.md) | Current | Runtime and host-test implementation findings. |
| [ui-guidelines.md](ui-guidelines.md) | Partly superseded | Display tokens and touch measurements; emulator capture commands are historical. |
| [display-size-support.md](display-size-support.md) | Partly superseded | Upstream display research and the current single-panel decision. |
| [ota-implementation.md](ota-implementation.md) | Current | Signed GitHub Releases OTA, trust roots, transaction, and validation gaps. |
| [wifi-client.md](wifi-client.md) | Current | Implemented Wi-Fi client file edits, status, tests, and on-car checklist. |
| [wireless-adb.md](wireless-adb.md) | Current | Implemented ADB property behavior, network observations, and on-car checklist. |
| [wireless-carplay-and-aa.md](wireless-carplay-and-aa.md) | Current | Implemented AGPL projection port, owned changes, recovery, and on-car checklist. |
| [research-nemesis-packaging.md](research-nemesis-packaging.md) | Partly superseded | NEMESIS source research and current package layout; older proposal marked historical. |
| [research-ota-update.md](research-ota-update.md) | Partly superseded | Upstream OTA research and historical design choices, with current release notes. |
| [research-wifi-client.md](research-wifi-client.md) | Partly superseded | Upstream STA/AP research and updated Seogaemo module test plan. |
| [research-wifi-enable.md](research-wifi-enable.md) | Historical research | Superseded core Wi-Fi proposal; use `wifi-client.md` for current behavior. |
| [research-adb-wireless-module.md](research-adb-wireless-module.md) | Partly superseded | Upstream ADB research; design inferences predate `wireless-adb.md`. |
| [README.md](README.md) | Current | This index and reading order. |

The target is Hyundai Ioniq 5 KOR, `ro.build.product=NEEV.KOR.0000.V039.001.260902` (package glob `NEEV.KOR.*.260902`), onebin `neev_kr`, Android 4.4.2. Installation is NEMESIS plus HMG-Seogaemo only. Off-car tests do not establish physical-car behavior.

Home now collects `Module.homeTiles()` from enabled modules and keeps Settings first. The current three modules provide no Home tiles, so Settings is the only card.
