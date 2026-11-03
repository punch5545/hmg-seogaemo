# Display size support in upstream (NEMESIS / HMG-API / HMG-Leafia-Plus)

Analysis of the upstream clones in `tmp/` (2026-11-03). Paths are relative to `tmp/`.
Reference car for all three projects: 2023 Kia EV6 US, firmware 260507, wide screen.

## Summary

All three projects assume the **wide screen (1920x720, density 1.5)**. None handles
12.3", 8", 1280px-class or dual-screen layouts explicitly. Variant detection guards
hooks/patches against firmware, not layout against the panel.

## 1. Sizes the code knows about

- HMG-API app: designed for 1920x720 @ density 1.5; every size is scaled by a fixed
  `K = 0.66f`, no screen-size check (`HMG-API/core/app/java/hmgapi/app/Ui.java:187`).
- HMG-API docs: Android Auto stock DPI is 240 on wide screens (`HMG-API/docs/mod-reference.md:203`).
- NEMESIS Manager: uses `DisplayMetrics` for dp conversion, declares no panel
  resolution (`hmg_nemesis/Sources/manager/src/com/nemesis/manager/Ui.java:94`).
- Leafia: no projection pixel size or DPI in its feature edits
  (`HMG-Leafia-Plus/packages/wireless-carplay-aa/comp/1_projection/scripts/common.sh:518`).

## 2. Fixed vs adaptive UI

- NEMESIS Manager
  - Adaptive: home splits width 1/3 list + 2/3 pager, pager width follows size changes
    (`hmg_nemesis/Sources/manager/res/layout/activity_main.xml:16`,
    `hmg_nemesis/Sources/manager/src/com/nemesis/manager/IconPager.java:53`).
  - Fixed: icon grid 4x2, tiles 204x166dp copied from stock Setup's hdpi geometry
    (`hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:35`, `:201`, `:244`).
  - Popup card: 46% of screen width, max 640dp (`.../DialogActivity.java:95`).
- HMG-API
  - App: fixed side columns 460/360, QR square 380, all through `Ui.dp()` with `K`
    (`HMG-API/core/app/java/hmgapi/app/ModsTab.java:60`, `SettingsTab.java:39`, `ReportTab.java:49`).
  - Side panel: fills the stock panel slot, but the header uses literal px text size
    and padding (`HMG-API/core/runtime/java/hmgapi/hook/Panel.java:33`, `:217`, `:222`).
  - Launcher items and toasts reuse stock launcher placement and the car's toast
    broadcast (`hook/HmgApi.java:23`, `hook/Notices.java:6`).
- Cluster / HUD: turn-by-turn goes through the car's `NavigationCluster` service, not
  pixel rendering, so it is size-independent (`HMG-API/core/runtime/java/hmgapi/hook/Cluster.java:9`, `:33`;
  `HMG-Leafia-Plus/packages/wireless-carplay-aa/scripts/common.sh:96`).

## 3. Variant detection

- NEMESIS: `ro.build.product`, model folder from `ro.mobis.targetdir`
  (`hmg_nemesis/Sources/native/pkg.c:310`); wireless package edits every
  `/system/onebin/*/featurexml.cfg` (`hmg_nemesis/Sources/packages/wireless-projection/scripts/common.sh:320`).
- Leafia: patch set by `ro.build.product` prefix + stock hashes
  (`HMG-Leafia-Plus/packages/wireless-carplay-aa/scripts/cars.sh:8`).
- HMG-API: model/region/firmware from `ro.build.product`, hook maps keyed by
  car/firmware with stock-file checks (`HMG-API/core/runtime/java/hmgapi/engine/SystemCheck.java:76`,
  `engine/CarMap.java:70`). **Display size is not a compatibility key** (`SystemCheck.java:15`).

## 4. CarPlay / Android Auto resolution

- Leafia and HMG-API's wireless mod only flip feature flags / feature files; no
  width, height or DPI change (`HMG-Leafia-Plus/README.md:31`;
  `HMG-API/mods/wireless-carplay-aa/mod.json:53`, `patches.json:11`).
- HMG-API `projection.aa_density` (60-480 DPI) rewrites both density args for every
  stock video setting; width/height stay stock (`HMG-API/docs/mod-reference.md:192`,
  `HMG-API/core/runtime/java/hmgapi/hook/ProjectionHooks.java:74`). Car test saw
  200 DPI at every offered resolution (`HMG-API/docs/car-test.md:306`).
- No CarPlay video-resolution override exists.

## 5. Gaps and risks (inference, not verified on a car)

- Smaller / different panels: NEMESIS fixed tiles may clip; HMG-API fixed columns,
  top actions, QR square and px-sized panel header may not fit.
- `aa_density` is one value per unit; a value tuned for the wide screen can make AA
  controls too small or large on other panels.
- Car test plan asks to record side-panel size/appearance but has no acceptance
  criteria for other sizes (`HMG-API/docs/car-test.md:189`).
- No multi-size plan in upstream docs, wiki, changelogs or git history.

## Where to start for multi-size support

1. HMG-API `Ui.K`: derive from real `DisplayMetrics` instead of a constant.
2. Add display size/density to Car Report and car maps.
3. Per-panel default for `aa_density`.

Prerequisite: real panel resolution/density from the target cars.
