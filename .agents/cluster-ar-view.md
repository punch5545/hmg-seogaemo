Status: current — car-verified 2026-11-06 on NEEV KOR (stock drive, CarPlay guidance, parked probes).

# Cluster AR turn view

The instrument cluster switches from the flat turn icon to its AR turn view when it receives a
turn in the stock `Navigation.DisplayType` format. Separate from the HUD hologram
([hud-hologram.md](hud-hologram.md)) and the HUD junction view
([hud-junction-view.md](hud-junction-view.md)). Firmware reference: [navi-ar-guidance.md](navi-ar-guidance.md).

## Call

`NavigationCluster.updateTBTInfo(type, sideStreet, direction, distance, distanceErr, combiSideStreet, unit, bar, barErr)`

| Argument | Value for the AR view |
|---|---|
| type | stock `Navigation.DisplayType`: TURN 13, U_TURN 31, FORK_2 25, EXIT_RIGHT 17 / EXIT_LEFT 18, EXIT_ROUNDABOUT_TRS_RIGHT 32 / _LEFT 33, OVERPASS_* … |
| sideStreet | 0 |
| direction | degrees in 45° steps (0 straight, 90 right, 270 left) |
| distance, unit | distance text shown in the view (unit 0 = m, 1 = km) |
| bar, barErr | ignored by the view; send as stock does |

- Projection icon codes (what the CarPlay module sends today) keep the flat icon.
- Distance does not gate the view: 600 m and 0 m both draw it. Stock navigation opens its AR
  window about 150-600 m before a maneuver and leaves it after the maneuver.
- Works with stock guidance and with CarPlay guidance active.
- Types 9 CALC_ROUTE / 10 RECALC_ROUTE show "경로 탐색 중입니다" / "경로 재탐색 중입니다".

## Vertical bar

The bar beside the AR view is `updateTBTTrapInfo` (17 ints, 12th = `barGraphLevel`) /
`updateTBTTrapInfoNew`, 0..100 (100 full). The same call carries speed-camera, limit and toll
fields, so a sender owns those fields while it drives the bar.

## Reading the state

`ClusterService` does not log `updateTBTInfo`; `dumpsys com.hkmc.micom.cluster` ("TBT Info",
"TBT Trap Info") shows the current values. Probe scripts: `tmp/lane-probe/tbt.sh`, `trap.sh`.
