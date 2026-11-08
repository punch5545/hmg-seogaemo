# UI redesign (current)

Owner-approved direction from the 2026-11-05 mockup rounds. Mockups live in `tmp/ui-mock/`
(not in git): `v9-screens.png` is the screen list, `v19.png` the final card and detail
behaviour, `v20-module-ui.png` the module block mapping. Where they differ, this file wins.

Goal: non-developers use the car comfortably; developers add and remove modules freely.
CarPlay is one feature among equals, never the headline.

## Rules

- Tokens: only `ui-guidelines.md` / `MainActivity` colours (`#111111`, `#171717`,
  `#202020`, `#343434`, `#f5f5f5`, `#9a9a9a`, `#0070f3`, `#ee0000`, warning `#f5a623`).
  6dp buttons, 8dp cards, 60dp header and action buttons. No gradients, glow, pill
  switches, filled icon tiles, chips or badges (owner rejected them as off-tone).
- No fixed screen size. Card columns follow the available width; cards always fill
  the page height. Pages scroll sideways with page dots.
- User-facing words are short product labels; never ledger, hookpoint (the Add screen may show
  the `.hsm` file name, owner choice 2026-11-06). The owner chose 모듈/Modules as the user-facing name (2026-11-05).
  Proper nouns stay as written (`Wi-Fi`, `CarPlay`, `Android Auto`, `ADB`, `USB`, `NEMESIS`).
- Every state change goes through `ui.ConfirmDialog` (Cancel left, default).

## Navigation

Header: logo, title `HMG Seogaemo` (owner choice, 2026-11-05), tabs **모듈 · 차량 · 설정** (EN Modules · Car · Settings).

| # | Screen | Reached from |
|---|---|---|
| 1 | Features | first tab |
| 2 | Feature detail | tap a card body |
| 3 | Add | `+ 추가` card at the end of the last Features page |
| 4 | Confirm | any state change; shows what changes, in order, as icons |
| 5 | Progress | after confirm; step checks and a bar |
| 6 | Car | second tab: car, firmware, NEMESIS, compatibility count, `다시 검사` |
| 7 | Settings | third tab: sidebar 소프트웨어 업데이트 · 언어 · 지원 · 개발자 옵션 · 초기화 |
| 8 | Reset | 순정으로 복원, HMG-Seogaemo 제거 (error-colour buttons) |

A restart bar (`재시작 후 적용됩니다` · 나중에 · 지금 재시작) appears above the cards only
while a restart is pending. Developer options reveal a diagnostics row (ledger, log,
raw output) on each detail screen.

## Features card

- Full-height card: line icon (top), name, optional one-line note, bottom status row
  (`⏻ 켜짐` blue, `⏻ 꺼짐` grey, `적용 중` warning with spinner only while an operation runs,
  `⏻ 재시작 필요` warning without spinner for `pending-restart`, `사용 불가` grey).
- On: blue 1dp border and blue icon. Off: grey icon and name.
- Not available: dimmed, note line in error colour gives the reason (for example
  `Wi-Fi 연결 사용 중`).
- Tap the status row: confirm popup (`Wi-Fi 연결을 끌까요?`, one result line,
  취소 / 끄기 or 켜기). Tap anywhere else: detail.
- Order: on and applying, then off, then not available; name order inside each group.
  Re-sort only when the screen is entered, never right after a tap.
- Before planning a change the app refreshes the status of every related module, so a
  stale or unknown state (backlog: wireless-ADB "확인 불가") never blocks a switch.

## Feature detail

- Left panel looks like the card: icon, name, description; on = blue border and icon.
  One full-width action button: `끄기` (neutral) when on, `켜기` (primary blue) when off.
  No separate status text next to the button.
- Right: `옵션` list drawn from the module's `ui.txt` blocks with the app's own rows
  (toggle row, value row with `›`, read-only value, warning line, action row with
  `실행`, open-screen row, enable-other-feature row). `제거` (error colour) closes the
  list for directly added features only.

## Module-provided UI

Modules never draw views (API 1). `icon=` picks from the fixed set in the contract;
`status.sh` `info note ...` fills the card note; `ui.txt` blocks fill the options list.
The `status` block is ignored. See `module-runtime-contract.md`.

## Not decided yet

- Add screen details for a store/registry (deferred with the platform).

## Implementation (2026-11-05)

Components in `app/src/com/seogaemo/hmg/ui/`: `Theme` (tokens, text/shape/button helpers,
Korean object particle), `LineIcon` (stroke icons for the contract set plus `plus`, `check`,
`restart`, `download`, `globe`, `help`, `code`, `undo`), `FeatureCard`, `FeaturesView` (width-derived
columns, `Pager`, dots, `RestartBar`, add card), `Feature` (built-in `Module` or `InstalledModule`),
`PlanSummaryView` (confirm body), `StepProgressDialog`, `OptionsList` (rows for `ui.txt` blocks and
built-in `SettingsPage` items), and `ConfirmDialog.showView` for caller-built bodies. `Module.icon()`
names the card icon of a built-in module. `HomeView` is gone; `HomeTile` stays in the contract unused.

`MainActivity` screens: `showFeatures` (sort on entry only; `loadFeatures(false)` updates cards in
place), `showDetail`, `showAdd`, `showCar`, `showSettings` (categories update, language, support,
developer, reset = `showStock`). Card state mapping is `entry(Feature)`: `on`, `off`, `pending-restart`
= 재시작 필요 (no spinner) with the restart bar, `off` while an excluding module is on = 사용 불가 with `<name> 사용 중`,
root-reported conflict/incompatible/not-implemented = 사용 불가 with the reason, root unreachable
(emulator) = neutral 확인 불가. After an operation the plan's modules whose status is unreadable are
shown as 적용 중 and re-read every 2 s (six attempts). `ModuleManager.plan` now skips unrelated
modules whose status is unreadable and re-reads related ones up to four times.

Confirm result lines live in each module's resources as `<snake_id>_on_result` / `_off_result`.
`tools/test-confirm-routing.py` follows the moved entry points (`showSupport` export,
`restartNow` restart bar).

The `button` block runs `<id> action <name>` after a confirm and is enabled only while the module is on.
Known gaps: installed-module setting rows show the last value the app set (cached) or the declared default,
because `status` does not report settings; built-in `INFO` items (ports, addresses, daemon state)
are not 옵션 rows: they appear only in the developer-only 진단 정보 dialog as `label: value` lines
above the raw status output (ledger and logs still go through Support → USB export). The app cannot read
USB mounts, so opening Add first runs `module usb-import` (root copies the USB `HSM/` folder packages into `files/hsm-usb/`,
no confirm because it changes no stock state) and lists that directory with the name, icon, summary and
version read from each package, plus removed bundled modules (`module list-removed`, installed again with
`module add-bundled` after the usual confirm); install and remove show `StepProgressDialog` and a result dialog.
Screenshots use the android-21 arm64 AVD `hmg_1920x720`, the only image on the build machine.
