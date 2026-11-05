# CarPlay TBT to cluster/HUD: other OEM and aftermarket implementations (round 2)

Collected 2026-11-05, time-boxed. Round 1 (`../iap2/`, `../hkmc/`) is not repeated. Clones are shallow, under `clones/`.
Confidence: H = read in source or primary doc; M = repo README or secondary article; L = forum or search snippet only.

## 1. Pinned clones (read in place)

| Clone | Commit | Date | Relevant files |
|---|---|---|---|
| `clones/carlink` (enigy/carlink, Kotlin Android, GM focus) | ba21dc7 | 2026-11-04 | `app/src/main/kotlin/com/carlink/navigation/ManeuverMapper.kt`, `LaneGuidance.kt`, `LaneIconComposer.kt`, `OcbmNavigationFeed.kt`, `DistanceFormatter.kt`, `Iap2RouteParser.kt`, `Iap2StateParser.kt` |
| `clones/WaylandAce-mib2q-carplay-rgi` (MHI2Q/MIB2 route guidance fork) | 1832d6e | 2026-05-20 | `README.md`, `docs/`, `c_hook/`, `java_patch/` (not deep-read) |
| `clones/mhi2-carplay-cluster` (maxim-smirnov, MHI2Q cluster map + HUD) | 58fb958 | 2026-10-17 | `README.md` only (maneuver renderer + BAP bridge; `tools/audit_maneuver_parity.py` exists, not read) |
| `clones/mhi2-au37x-carplay` (chefranov, Audi VC map + MMI) | cf95ec5 | 2026-11-02 | `README.md` (no route-guidance detail extracted) |
| `clones/MHI2-Carplay-Maps` (tomasz-twardzicki, MU1367/MU1427 native guidance) | 5093715 | 2026-11-04 | `README.md` (native maneuver arrows, distance to turn, route totals on AID; HUD projection) |
| `clones/touareg-cn-carplay-mods` (a78c7, VW Touareg China MH2p/Cinemo) | 7ccb704 | 2026-11-01 | release notes v19.6 fetched via web; repo has large binary zips under `releases/` (not extracted, not read) |

Note: `clones/mib2q-carplay-rgi` in this round is a leftover empty dir; round 1 luka-dev clone at df73e06 is the reference for maneuver enums.

## 2. Icon mapping: CPManeuverType -> icon (carlink, GM/AAOS path)

carlink does not draw cluster icons from iAP2 directly. It maps CPManeuverType (0..53) to Android Car App Library `Maneuver.TYPE_*`, which the cluster then renders. Table (H, read from `ManeuverMapper.mapManeuverType`):

- 0 -> STRAIGHT; 1 -> TURN_NORMAL_LEFT; 2 -> TURN_NORMAL_RIGHT; 3 -> STRAIGHT; 4 uTurn -> U_TURN_LEFT (RIGHT if left-drive)
- 5 followRoad -> STRAIGHT; 6 enterRoundabout -> ROUNDABOUT_ENTER_CCW (CW if left-drive); 7 exitRoundabout -> ROUNDABOUT_EXIT_CCW/CW
- 8 rampOff -> OFF_RAMP_NORMAL_RIGHT/LEFT; 9 rampOn -> ON_RAMP_NORMAL_RIGHT/LEFT; 10 endOfNavigation -> DESTINATION; 11 proceedToRoute -> DEPART
- 12 arrived -> DESTINATION; 13 keepLeft -> KEEP_LEFT; 14 keepRight -> KEEP_RIGHT
- 15/16/17 enter/exit/changeFerry -> FERRY_BOAT
- 18 uTurnToRoute -> U_TURN (side-dependent); 19 roundaboutUTurn -> ROUNDABOUT_ENTER_CW/CCW
- 20 endOfRoadLeft -> TURN_NORMAL_LEFT; 21 endOfRoadRight -> TURN_NORMAL_RIGHT
- 22 rampOffLeft -> OFF_RAMP_NORMAL_LEFT; 23 rampOffRight -> OFF_RAMP_NORMAL_RIGHT
- 24 arrivedLeft -> DESTINATION_LEFT; 25 arrivedRight -> DESTINATION_RIGHT
- 26 uTurnWhenPossible -> U_TURN (side-dependent); 27 endOfDirections -> DESTINATION
- 28..46 roundaboutExit1..19 -> ROUNDABOUT_ENTER_AND_EXIT_CW/CCW; exit number = type - 27
- 47 sharpLeft -> TURN_SHARP_LEFT; 48 sharpRight -> TURN_SHARP_RIGHT; 49 slightLeft -> TURN_SLIGHT_LEFT; 50 slightRight -> TURN_SLIGHT_RIGHT
- 51 changeHighway -> KEEP_RIGHT; 52 changeHighwayLeft -> KEEP_LEFT; 53 changeHighwayRight -> KEEP_RIGHT
- else -> TYPE_UNKNOWN with a logged warning

Caveats (H):
- Inline comments in the Kotlin source are off by one or mislabeled (e.g. `1 // noTurn`, `2 // right`). Trust the code values and cross-check against round 1 `maneuver-types.md`.
- Round 1's table jumps from 14 to 27 and has no names for 15..26. carlink's names for 15..26 (ferry, endOfRoad, rampOffLeft/Right, arrivedLeft/Right, uTurnWhenPossible) are a useful second source for those values, but they are not independently verified here.
- Mapping collapses several iAP2 values (e.g. 3 straight and 5 followRoad both to STRAIGHT; 51 and 53 both to KEEP_RIGHT). Any cluster with finer icons loses detail.
- Roundabout exit angle is converted to a swept angle: CCW (right drive) = (180 - iap2) mod 360; CW (left drive) = (180 + iap2) mod 360; 0 -> 360. Marked by the author "derived, validate on-vehicle" (M).
- Drive side: NaviTurnSide/drivingSide 1 = left-hand drive flips U-turn and roundabout rotation.

## 3. Lane rendering

carlink (H, `LaneGuidance.kt`, `LaneIconComposer.kt`):
- Input is iAP2 0x5204 LaneInformation via the OCBM box JSON `kind=laneGuidance`: per lane `status` (0 do-not-take, 1 usable, 2 preferred), `laneAngle`, optional `laneAngles` list, optional `laneAngleHighlight`.
- Lane direction angles use Apple's convention: 0 straight, +90 right, -90 left, +-180 back.
- `LaneHint` draws only the recommended direction on a lit lane; other directions are drawn grey only on non-recommended lanes, because an extra grey arrow under the lit one was hard to read (owner note, 2026-10-31).
- Two icon modes: ARROW (turn arrow shrunk to top, lane row below) and LANES (whole square is lanes, the lanes to take lit on a teal band). Stroke and head widths scale with lane count, with min/max clamps.

DiPlay (M, GitHub issue #927, fetched 2026-11-05 via web):
- Lane data arrives only if the app declares lane capacity in iAP2 identification. DiPlay 0.2.21 did not declare it, so the iPhone sent zero 0x5204 records. PR #1028 adds the declaration.
- The reported BYD DiLink cluster/HUD nav data has distance and turn code, and reportedly no lane field. HUD has one icon slot, which cannot hold a lane band. Unconfirmed for that car.

## 4. Update cadence and state model

- carlink (H): 0x5201 routeGuidance state ~1/s (distance to maneuver, remaining distance, time, currentManeuverIndex). 0x5202 maneuver records are sent one per maneuver of the route, keyed by index; the live maneuver is whichever index the last 0x5201 named. A delayed-end grace timer exists (`END_GRACE_MS`) but its value was not read.
- carlink (H, `Iap2StateParser.kt`): 0x5201 field 0x0d `RouteGuidanceManeuverCurrentList` carries current and next index as a len-4 list; element 0 is the maneuver being approached. Earlier versions inferred the index from sparse bursts and needed five iterations (comments in code).
- DiPlay (M): 0x5201 and 0x5202 are the navigation info messages the head unit receives; the maintainer says the CarPlay data reached the cluster once the cluster nav option was enabled.
- Round 1 cites ~1 Hz for 0x5201 and a ~200 ms 0x5202 burst at route start (dongle capture, `../iap2/sources.md` item 8). Consistent with carlink.

## 5. Unit handling

carlink `DistanceFormatter.kt` (H):
- Explicit display unit; it does not auto-convert between feet, miles, meters, km.
- Imperial (US, UK, MM, LR locales) < 305 m shown in feet, else miles. Metric elsewhere: < 1000 m meters, else km. Unknown geography defaults to metric.
- Uses Car App Library `UNIT_MILES_P1` / `UNIT_KILOMETERS_P1` constants.

## 6. Per-OEM / per-platform findings

- **Audi/VW MHI2Q/MIB2 (aftermarket, several forks):** route guidance from 0x5200..0x5204 hooked into the phone-side iAP2 stack and forwarded to the cluster/HUD state file (luka-dev lineage, round 1). Maneuver arrows, distance to turn, remaining route and ETA are shown on the cluster in MU1427/MU1367 builds (tomasz-twardzicki README, M; a78c7 touareg notes, M).
- **VW Touareg China (MH2p, a78c7 v19.6, 2026-11-01, M, web-fetched):** wired CarPlay uses a different iAP2 stack (Cinemo, over USB). The earlier nav hook only covered the wireless stack, so wired CarPlay showed no HUD arrows until the hook was also installed on the Cinemo side. The hook adds a route-guidance component to identification and, if the phone rejects the modified identification, disables itself until reboot (`wired_off` file kill-switch). **Relevant to us: wired and wireless CarPlay may use different iAP2 sessions, so an identification change may need to cover both.**
- **BMW (Apple-side, M/L):** iOS 17.4 (Feb 2024, MacRumors, fetched) adds an Apple Maps option to show upcoming maneuvers in the instrument cluster on supported vehicles with a supplementary Maps view in the driver display or HUD. BMW, Volvo, Polestar named as early adopters. Third-party apps are not named. Forum reports say Waze/Google Maps cluster or HUD guidance is inconsistent (L).
- **Apple developer forum "HUD Turn-by-turn function by third-party apps" (thread 740100, Oct 2023, fetched):** an OP notes that Apple Maps alone used "Instrument Cluster Video Stream 2" in the simulator. A later self-answer (Apr 2024) says Apple's March 2024 CarPlay docs reveal plans to offer HUD-area services to third-party developers. No Apple engineer answer, no API name, no CPManeuver reference (M for the existence of the doc change, L for its content).
- **VW MIB3 Digital Cockpit (L):** owner reports conflict. A '23 GTI shows next turn with Apple Maps; a 2022 Taos shows next turn only; a Tiguan review says native nav gave lane guidance in the cluster but CarPlay directions did not. Digital Cockpit requires SEL/R-Line trim per the review (L).
- **Mercedes NTG (L):** no Mercedes documentation found. A Reddit report says a 2024 GLE shows CarPlay arrows over wireless CarPlay in the cluster. Other forum posts say cluster arrows come from the car's own nav. Treat as unverified.
- **Stellantis/Uconnect (L):** Dodge Uconnect page describes cluster guidance for built-in TomTom nav only. No evidence found that Uconnect forwards CarPlay maneuver data to the cluster. Forum users disagree on whether Android Auto or CarPlay directions reach the cluster.
- **BYD DiLink (DiPlay issues #728, #927, fetched):** Han 2023 Champion (DiLink 4.0) and Song PLUS 2023 Champion (DiLink 3.0), wireless CarPlay via the car's hotspot. Cluster nav turn arrows and distance needed an explicit "HUD和仪表盘导航" setting on the head unit (Settings > Navigation). Cluster map needed network ADB. **Key point: even when the phone sends guidance, the head unit may gate cluster display behind a user setting. Our implementation should check for that gate before debugging the message layer.** (M, maintainer reply.)
- **Chinese/Amap (高德) cluster support (M/L):** Amap announced CarPlay cluster navigation in Aug 2024 (iOS 17.4+, iPhone 12+). Some older cluster builds show only simplified guidance, not a full map (Sina Auto and toutiao snippets, L; promotional tone).
- **Carlinkit / CPC200 dongle path (round 1, H):** the dongle converts guidance to NaviJSON and strips junction and lane data. carlink's own docs say it replaces that stock NaviJSON path with a full iAP2 parser. Not repeated here.

## 7. Open questions for our implementation

1. Head-unit gate: confirm whether Hyundai/Kia clusters have an equivalent of the BYD "HUD和仪表盘导航" setting before treating missing icons as a protocol fault.
2. Lane capability: declare lane capacity in identification (DiPlay PR #1028 shows the missing declaration broke 0x5204 entirely).
3. Wired vs wireless: check whether the Gen5W/Hyundai path uses the same iAP2 session for USB CarPlay (touareg v19.6 finding).
4. Icon coarsening: carlink collapses several types; decide whether our cluster icon set needs the full 0..53 range.
5. Mapping labels: use the code values, not inline comments, in any table we port.
