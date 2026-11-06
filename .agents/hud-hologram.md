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

- Only turn, exit and fork types draw a hologram: TURN 13 (shape follows the direction),
  EXIT_RIGHT 17 / EXIT_LEFT 18, combined exits 19/20, FORK_2 25, FORK_3 26 (the cluster AR
  view draws FORK_2 only at 90 and FORK_3 not at all, so CarPlay never sends 26).
- U-turn and roundabout types draw no hologram; ARRIVED 3 shows a flag; 9 / 10 show the route
  search text.

Dataset: `datasets/hud-hologram-tbt/` (local, gitignored): photos per `DisplayType` × direction,
per-row shapes in `classify.csv`, overview in `summary.md`.

CarPlay sends stock types through CpGuidance inside the approach window documented in
[cluster-ar-view.md](cluster-ar-view.md). Outside it, or with ambiguous geometry, the flat
projection icon remains. The F2 HUD turn list and its periodic refresh retain the existing
projection format; hologram output comes from the main TBT call. The module leaves the
owner's HUD display mode unchanged.
