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

Stock picks pictures from map data, not by lane count: at 양재IC (four through lanes and one
exit lane) it drew the three-lane 10193/10194. PE14 pictures are therefore used as templates.
CpJunctions selects from two inputs only, the maneuver type and the phone's decoded lanes at
the maneuver; instruction text and road names are never used (owner decision). iAP2 has no
overpass, underpass or tunnel maneuver, and no PE14 picture draws elevation.

- Types: highway exits, highway changes and keeps (8, 13, 14, 22, 23, 51, 52, 53); keeps were
  added after the 2026-11-07 drives (owner decision). Straight-on, ordinary turns and
  roundabouts get no picture. A keep in town with an exit-like lane set without a U-turn lane
  also gets one (accepted).
- Lanes must show one plain exit: one or two contiguous edge lanes turning to the exit side,
  at least one through lane, every recommended lane among the exit lanes, through lanes off
  route with no turn arrows, no U-turn (±150° or more) lane anywhere. Explicit types fix the
  side (13 left, 14 right); 8 and 51 take it from the lanes and give nothing when both edges
  qualify.
- A keep away from an exit (13 with exit lanes peeling off right, 14 with exit lanes peeling
  off left; route on the through lanes only, exit lanes off route) shows the same template
  with its straight highlight.
- Templates: right, one exit lane: 10193/10194 (stock pair); 10168/10170 (second stock pair)
  when the next maneuver is also a right exit (8, 23, 53); 10046/10047 on a two-lane road.
  Right, two exit lanes, four or more lanes: 10320/10322 (wide branch). Left, one exit lane,
  three or more lanes: 10016/10018 (no stock sample). Straight past a right exit: 10193/10195,
  10046/10048 on two lanes, 10320/10323 for two exit lanes; past a left exit: 10016/10017,
  10001/10002 on two lanes. Everything else gets no picture.

The CarPlay app seen on the car sends no junction or exit angles and quantises lane arrows
to 0, ±90 and ±180, so angles cannot pick Y, pocket, loop or multi-road pictures. The full
picture table stays in the payload as the reference the tests check templates against.
C remains 65535. Whether stock shows a picture at JC splits, and which picture it uses for
two-lane exits, still needs a stock capture on the car.

CpTurns sends the selection inside the same raw-metre approach window as stock turns. Only
the current component's displayed lane index supplies lane evidence; late matching packets
recompute the selection. Unchanged pictures are not resent, also when the phone re-indexes
the same maneuver on the way (seen at every junction on the 2026-11-07 drives); a different
picture for a new maneuver or component clears the old one first. At 0 m the phone may link
the next maneuver's lanes while the list still names the current one, so the shown picture
is kept until the list moves on. Window exit, missing
current maneuver, loading/rerouting, arrival, route end, source change, stop and disconnect
all clear with three 65535 values. Junction clearing and trap-bar restoration precede the
final idle compass at stop/disconnect. Each CarPlay guidance session first sends one clear,
so a picture stock navigation left behind cannot persist. Stock navigation PE14 pictures are
suppressed during phone guidance, alongside its ordinary TBT writes, but its own clears
(A 65535, or A 0 with B 65535) pass; camera/trap calls continue normally. The PE14 point is
optional for the module: without it the junction view is off and CarPlay runs.

## Tools (local)

`tmp/lane-probe/pe14.sh` (single call); `tmp/pe14-explorer/`: `cam.command` (Terminal keeps the
iPhone Continuity Camera open), `sweep2.py` (send and photograph per code), `make_sheets.py`,
`group_sheets.py`, `watch.py`, `explore.py`.
