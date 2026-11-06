Status: current — car-verified 2026-11-06/07 on NEEV KOR (stock drive, CarPlay guidance, parked camera sweep).

# HUD hologram turn

In AR display mode the windshield HUD draws the next turn as an animated 3D hologram. Separate
from the cluster AR view ([cluster-ar-view.md](cluster-ar-view.md)) and the HUD junction view
([hud-junction-view.md](hud-junction-view.md)).

## Trigger

- The hologram comes from the same stock-format `updateTBTInfo` that drives the cluster AR view;
  there is no separate hologram call.
- HUD display mode must be AR (user setting; `USMService HUD_ARView` 31 = AR, 1 = normal).
- The gear must be out of P (stationary in D is enough).
- In AR mode the HUD does not draw junction pictures (PE14).

## Shapes per type

- Only turn, exit and fork types draw a hologram (TURN 13, 17/18, 19/20, 25, 26), all as the
  same chevron: only the side of the direction matters (45/90/135 right, 225/270/315 left,
  180 symmetric, 0 nothing). A straight-ahead maneuver therefore draws nothing on the HUD.
- U-turn and roundabout types draw no hologram; ARRIVED 3 shows a flag; 9 / 10 show the route
  search text.

Dataset: `datasets/hud-hologram-tbt/` (local, gitignored): photos per `DisplayType` × direction,
per-row shapes in `classify.csv` (second-pass review: `classify-reviewed.csv`, `review.md`),
overview in `summary.md`.

CarPlay sends stock types through CpGuidance inside the approach window documented in
[cluster-ar-view.md](cluster-ar-view.md). Outside it, or with ambiguous geometry, the flat
projection icon remains. The F2 HUD turn list and its periodic refresh retain the existing
projection format; hologram output comes from the main TBT call. The module leaves the
owner's HUD display mode unchanged.
