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
families and offsets. Choose A and B for a maneuver and side from these tables.

## Tools (local)

`tmp/lane-probe/pe14.sh` (single call); `tmp/pe14-explorer/`: `cam.command` (Terminal keeps the
iPhone Continuity Camera open), `sweep2.py` (send and photograph per code), `make_sheets.py`,
`group_sheets.py`, `watch.py`, `explore.py`.
