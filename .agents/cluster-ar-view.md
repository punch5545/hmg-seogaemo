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

- Projection icon codes keep the flat icon. CarPlay uses them outside its approach window or
  when the maneuver geometry cannot be mapped reliably.
- Distance does not gate the view: 600 m and 0 m both draw it. Stock navigation opens its AR
  window about 150-600 m before a maneuver and leaves it after the maneuver.
- Works with stock guidance and with CarPlay guidance active.
- Types 9 CALC_ROUTE / 10 RECALC_ROUTE show "경로 탐색 중입니다" / "경로 재탐색 중입니다".

## Vertical bar

The bar beside the AR view is `updateTBTTrapInfo` (17 ints, 12th = `barGraphLevel`) /
`updateTBTTrapInfoNew`, 0..100 (100 full). The same call carries speed-camera, limit and toll
fields. CarPlay captures the latest real stock call in the navigation process and replaces
only the bar during an approach lease. Both the 17-int and object-record paths preserve the
camera, speed-limit and toll values. While the captured record reports an active camera,
trap, warning zone or toll gate (any of those fields other than 0 / -1) the stock bar passes
through unchanged. With no captured stock record it sends no trap record. The trap points
are optional for the module: without them only the bar is off.
Leaving the window restores the latest stock bar; a 3500 ms lease also restores it if DiPO
stops renewing. Trap calls are never included in the phone-guidance suppression list.

## CarPlay policy

Stock-format output is enabled with normal TBT, independent of the developer logging toggle.
The window is 300 m for ordinary turns and 600 m for ramps, exits, keeps and highway changes,
inclusive, using the raw distance in metres to the current maneuver. These fixed thresholds
approximate the observed stock urban onset of 150–375 m and highway onset of 580–600 m;
CarPlay does not expose the stock road-dependent guide-window policy. A changed current
maneuver needs a fresh distance. Unknown distance cannot open the window.

The independently implemented CpGuidance policy uses maneuver type, explicit exit angle,
other junction angles and driving handedness. Turns round to 45 degrees (the branch angle
equals the direction; 180 draws no line and is never sent). Exits send EXIT_RIGHT 17 at 90 or
EXIT_LEFT 18 at 270; 19/20 are not used (20 draws a left-side exit on this cluster). Keeps and highway changes send EXIT_LEFT 18 at 270 to the left
and FORK_2 25 at 90 to the right (FORK_2 draws only at 90, FORK_3 26 draws nothing); a measured
straight is no keep. Straight past one side road sends SERVICE_ROAD 23 at 270 (branch right) or 24 at 90 (branch
left), which draw the main road straight on, only with a highway road name or instruction;
otherwise TURN 13 at 0. U-turns use the Korean
left loop by default. Roundabouts send 32 (counter-clockwise travel, right-hand traffic) or 33
(clockwise, left-hand traffic) by driving side, with the exit clock position measured clockwise
from 12 o'clock as direction; positions require an explicit exit angle; ordinal exit numbers
do not provide an angular position. Ambiguous or contradictory geometry retains the projection
icon. When the next maneuver is not known yet the projection icon is resent; the idle compass
is only for stop and disconnect.
The bar decreases linearly from 100 at entry to 0 at the maneuver. Route states Loading and
Rerouting send stock types 9 and 10. The HUD F2 list retains its projection icon format.

The classified car sweep is in local, gitignored dataset `datasets/cluster-ar-tbt/`: `summary.md`, per-row `classify.csv`, second-pass review in `classify-reviewed.csv` and `review.md`.

## Reading the state

`ClusterService` does not log `updateTBTInfo`; `dumpsys com.hkmc.micom.cluster` ("TBT Info",
"TBT Trap Info") shows the current values. Probe scripts: `tmp/lane-probe/tbt.sh`, `trap.sh`.
