Status: current — external research on CarPlay route guidance and Hyundai/Kia cluster/HUD TBT, collected 2026-11-05 (rounds 1 and 2).

# External research: CarPlay route guidance → cluster/HUD

Web research to support the turn-by-turn items in [backlog.md](backlog.md). Details,
per-source confidence and citations are in [carplay-tbt-external/](carplay-tbt-external/).
Facts here are third-party unless they say "our car"; verify on the car before relying on them.
Do not copy third-party code into the repo (see AGENTS.md licensing); cite path + commit.

| File | Content |
| --- | --- |
| [iap2-sources.md](carplay-tbt-external/iap2-sources.md) | Annotated bibliography for the iAP2 side |
| [iap2-lane-guidance.md](carplay-tbt-external/iap2-lane-guidance.md) | 0x5200–0x5204 parameter IDs/types, 0x5204 lane layout, identification requirements |
| [iap2-maneuver-types.md](carplay-tbt-external/iap2-maneuver-types.md) | ManeuverType 0..53 (+255), DrivingSide, JunctionType, angle semantics |
| [iap2-apps.md](carplay-tbt-external/iap2-apps.md) | What Apple Maps / Waze / AMap / Korean apps send |
| [hkmc-sources.md](carplay-tbt-external/hkmc-sources.md) | Annotated bibliography for the Hyundai/Kia side |
| [hkmc-cluster-tbt.md](carplay-tbt-external/hkmc-cluster-tbt.md) | Cluster icon codes, 0xF2 HUD list, 0xF1 lane frame, distance/unit, keep-alive notes |
| [hkmc-extras.md](carplay-tbt-external/hkmc-extras.md) | Virtual driving sound and battery preconditioning notes |
| [round2-mib2q-deep-dive.md](carplay-tbt-external/round2-mib2q-deep-dive.md) | luka-dev / kamgurgul / LIVI: icon mapping, angle sentinels, lanes → cluster arrows, cadence, omitted fields, 0x5200 retry |
| [round2-other-oems.md](carplay-tbt-external/round2-other-oems.md) | carlink (GM/AAOS), MHI2/MIB2 variants, Touareg CN: icon tables, lane rendering, units |
| [round2-korean-apps.md](carplay-tbt-external/round2-korean-apps.md) | TMAP / NAVER Map / KakaoNavi community evidence (thin) |
| [round2-sources.md](carplay-tbt-external/round2-sources.md) | Round 2 bibliography |

## Key findings (round 1)

- **Best public sources:** `luka-dev/mib2q-carplay-rgi` (@df73e061) and `kamgurgul/mib2q-carplay`
  (@5c460600) reverse-engineered 0x5200–0x5204 from iOS accessoryd/Maps with on-car logs (VW MIB2);
  `f-io/LIVI` `route_guidance.rs` gives a matching independent schema.
- **ManeuverType** is 0..53 (255 = not set) and matches Apple's public `CPManeuverType` names.
  DrivingSide 0 = right-hand traffic, 1 = left-hand; JunctionType 0 = intersection, 1 = roundabout.
  ExitAngle is a signed i16 in degrees and is **omitted** when there is no exit element (our car:
  NAVER Map never sends it, so type-based arrows are required).
- **0x5204 lanes:** ComponentID, LaneGuidanceIndex, repeated nested LaneInformation groups
  (index, status 0 not good / 1 good / 2 preferred, repeated angles, highlight angle; 1000 = none),
  description. Lane messages arrive **before** the 0x5201 that makes them current; matching is by
  LaneGuidanceIndex (0x5201 p16, 0x5202 p12). This is enough to write our own decoder (backlog item).
- **Identification:** lane capacity fields must be at exactly ids 7/8 of RouteGuidanceDisplayComponent
  (0x001E); wrong ids make iOS silently stop sending 0x5204. Our car already receives 0x5204, so the
  stock identification is fine.
- **Apps:** CarPlay.framework builds the messages from what the app sets. Apple Maps sends everything;
  Waze declares no route guidance; data for NAVER/T map/Kakao is anecdotal (low confidence).
- **Hyundai side:** HMG-API's `Cluster.java`/`AaTurns.java`/`CpTurns.java` (local `tmp/HMG-API`) hold
  most of the icon table: 65 straight; 66/67/68 right slight/turn/sharp; 71/70/69 left; 72/73 U-turn;
  81/82/86 off-ramp; 172–174 on-ramp/fork; 176 ferry; 32/33 and 96–106 roundabout. Icon 64 is the
  "unknown" fallback (our car: rendered as "loading"). No public TBT CAN IDs; keep-alive need unresolved
  (Leafia lists alive/status calls, HMG-API never sends them).
- **Extras:** manual preconditioning has no stock menu on MY2023 Ioniq 5 (triggered by nav route to a
  DC charger); HMG-API has a start sequence with temperature gates. Simulated drive sounds exist on
  N models; no source for the standard Ioniq 5.

## Key findings (round 2)

- **Omitted fields are normal.** Third-party apps (e.g. Sygic) omit JunctionType and ExitAngle.
  kamgurgul removed luka-dev's junction gate for exactly this (issue #9): a left turn without
  JunctionType must still get its arrow. Same lesson as our NAVER fix; do not reintroduce a gate.
- **Deltas:** messages carry only changed TLVs; keep previous values per key (luka-dev applies
  present keys as deltas). Missing exit angle → treat as unknown (they use 1000), lane angle 1000 →
  lane without direction.
- **0x5200 start retry:** luka-dev re-sends StartRouteGuidanceUpdates every 2 s ×5, then every 30 s,
  until the first 0x5201, injected through the stock send path on one worker thread; iAP2 identify
  start (0x1D00) resets it. No other keep-alive on the iAP2 side.
- **Korean apps:** still no field-level evidence for TMAP/NAVER/Kakao; one user report says NAVER
  feeds the HUD only lane/road-direction on a BMW. Our own observation records are the best source.
- **Hyundai/Kia:** no owner report of factory CarPlay TBT on the cluster; Apple lists HMG as working
  toward CarPlay Ultra (unverified timing).

