Status: current — car-verified 2026-11-06/07 on NEEV KOR (stock drive, parked camera sweeps).

# HUD junction view (PE14)

In normal display mode the windshield HUD draws a junction picture (road layout with the route
highlighted) near highway exits and splits. Separate from the cluster AR view
([cluster-ar-view.md](cluster-ar-view.md)) and the HUD hologram ([hud-hologram.md](hud-hologram.md)).
Firmware path and packing: [navi-ar-guidance.md](navi-ar-guidance.md).

## Call

`NavigationCluster.updateCluDataNew_14(A, B, C, L, K)` (Binder 71, micom function 0x1c).

| Value | Meaning |
|---|---|
| A | junction road picture, codes 10001-11011 (292 pictures) |
| B | route highlight on the picture; required |
| C | optional second highlight, drawn together with B; 65535 = none |
| L, K | `pattern_guide_lane` / `pattern_guide_color`; no HUD effect, send 0 |

- Codes come in groups: a picture code followed by its 2-4 highlight codes. A highlight shows
  straight-on, the exit or ramp, the left or right branch of a Y split, or a lane variant.
- Left-exit pictures are at the start of the range (10001-10043); most of the rest are right-side
  exits and ramps, with Y splits, Y splits with a pocket, loops and forks in between.
- Independent of the turn guidance: one call shows the picture and it stays until
  `(65535, 65535, 65535, 0, 0)` clears it (stock alternates that with `(0, 65535, 65535, 0, 0)`).
- Drawn in P and in D, in HUD normal mode only.
- Stock navigation sends a pair near highway exits (seen 10193/10194, 10168/10170) and clears it
  after the exit; ordinary road turns get no junction view.

## Code tables

Dataset: `datasets/hud-junction-pe14/` (local, gitignored), described in its `README.md`.
`sweep/groups/table.csv` lists each picture (layout, lanes, side, straight / exit / left / right
highlight codes), `sweep/groups/b_table.csv` each highlight code, `sweep/groups/summary.md` the
families and offsets. The compact generated CpJunctionTable in the wireless payload retains
all 292 pictures and 721 highlight entries. Regenerate it with
`tools/generate-hmg-seogaemo-junctions.py --table PATH/table.csv --b-table PATH/b_table.csv`;
only the generated source and generator are committed.

## CarPlay selection and lifecycle

CpJunctions chooses a picture for exits, ramps, keeps, forks and highway changes. A straight
route shows the through highlight past an exit only with a highway road name or instruction; ordinary urban turns do not get a
junction view. Explicit maneuver types and exit angles determine route side first, followed
by weighted recommended-lane highlights/arrows and instruction text. Driving handedness is
never used as exit side. All lane geometry, including non-recommended lanes, and other
junction angles help identify exit, Y, pocket, loop, fork and multi-road layouts. Actual
decoded lane count takes precedence over the route-update count. Road names and the next
maneuver refine otherwise equal candidates. The observed stock pairs 10193/10194 and
10168/10170 remain available as weak tie preferences.

Selection uses the classified family, side, route and lane count. The CSVs do not define
physical lane masks for `lane_variant` highlights, so those entries are retained but never
selected. C remains 65535. This is a best-fit diagram from the phone's available geometry;
CarPlay supplies no stock map picture identifier or complete lane connectivity.

CpTurns sends the selection inside the same raw-metre approach window as stock turns. Only
the current component's displayed lane index supplies lane evidence; late matching packets
recompute the selection. Unchanged pictures are not resent. A changed maneuver or component
clears the old picture even if the replacement selects the same codes. Window exit, missing
current maneuver, loading/rerouting, arrival, route end, source change, stop and disconnect
all clear with three 65535 values. Junction clearing and trap-bar restoration precede the
final idle compass at stop/disconnect. Stock navigation PE14 writes are suppressed during
phone guidance, alongside its ordinary TBT writes; camera/trap calls continue normally.

## Tools (local)

`tmp/lane-probe/pe14.sh` (single call); `tmp/pe14-explorer/`: `cam.command` (Terminal keeps the
iPhone Continuity Camera open), `sweep2.py` (send and photograph per code), `make_sheets.py`,
`group_sheets.py`, `watch.py`, `explore.py`.
