# Which apps and head units carry CarPlay route guidance to a cluster/HUD

Confidence: H = decompile/source/primary doc; M = repo README, maintainer reply, secondary article; L = forum/snippet. Collected 2026-11-05. Bibliography: [sources.md](sources.md). Wire fields: [iap2-protocol.md](iap2-protocol.md).

## 1. Mechanism and app matrix
Mechanism (Luka `THIRD_PARTY_NAV_APPS.md`, iOS 26.6 CarPlay.framework decompile, H): the *app never writes iAP2*. A CarPlay app calls `CPMapTemplate startNavigationSessionForTrip:` and fills `CPNavigationSession` maneuvers; CarPlay.framework serializes `CPManeuver` (13 params: componentID, index, instructionVariants, maneuverType, roadFollowingManeuverVariants, initialDistance(+display), trafficSide, junctionType, junctionElementAngles, junctionExitAngle, linkedLaneGuidanceIndex, highwayExitLabel) and `CPRouteGuidance` (22 params) into 0x5201/0x5202. Unset fields are absent. Images are never sent (cluster draws its own icon).
**Gate:** 0x5201 p0x14 SourceSupportsRouteGuidance = whether the app's CPMapTemplateDelegate implements `mapTemplateShouldProvideNavigationMetadata:` returning YES. Lanes need the app to create `CPLaneGuidance` (iOS 17.4+) -> 0x5204.

| App | Gate (0x14) | maneuverType | angles/junction | lanes (0x5204) | Source / confidence |
|--|--|--|--|--|--|
| Apple Maps | 1 | yes (full 0..53) | yes: signed exit angle (role-2 element), junction elements, driving side; U-turn sign normalised by side | yes (0x5204 events + linked index) | Luka/kamgurgul Maps 26.6 decompile, H |
| Google Maps | 1 (reported "full RGI") | set | not analysed | not analysed | Luka compat table, M |
| Waze 5.23.0.1 / 5.24.0.1 | **0** (no delegate selector) | never set | never | never builds CPLaneGuidance (lane strip is bitmap `junctionImage`); roundabout exit number is a bitmap, not `roundaboutExitN` | Luka IPA static analysis, H for those builds; no cluster/HUD data at all |
| AMap (Autonavi) 16.25.0 / iOS 26.6 | 1 (3 impls) | **never set** (typeless) | - | - | static; but owner confirmed (2026-09-25) recent AMap + iOS in-app CarPlay guidance setting works on cluster/HUD, M |
| NAVER Map | unknown | unknown | unknown | unknown | one forum report: HUD shows only road/lane direction, not turn+distance (damoang, BMW iD8, iOS 26, ~2025-10; single user, unverified), L |
| T map | unknown | unknown | unknown | unknown | same thread: HUD shows nothing; App Store 10.16.0 notes (CarPlay rest-stop info, destination continuation) say nothing on cluster/HUD, L |
| Kakao Navi | unknown | unknown | unknown | unknown | CarPlay since 2018-09-17 (night mode via light sensor, background phone/music; no HUD/cluster mention); reported no HUD on iOS 26 BMW, L |

Reported workaround (Korean users): start Apple Maps guidance first, then run T map/NAVER in front; HUD keeps Apple Maps data. Plausible if the metadata gate/session is per navigation session; unverified (L).

Korean-app gaps: no field-level evidence for TMAP/NAVER/Kakao. TMAP: Clien `cm_car/13815499` (fetched) is an off-topic 2019 Kia Ray CarPlay Q&A; replies speculate factory nav is "Genie/Mappy" based so CarPlay "likely hard". KakaoNavi on Honda Accord via Android Auto (~2021 snippet): left/right and exit name missing on some cars, "Guidance Unavailable" on failure (not CarPlay, not Hyundai).

### Implications for our unit
- Never assume ManeuverType/angles present: an app can be gate=1 yet typeless (old AMap). Handle NotSet(255)/missing types; fall back to distance + road text.
- Absent exit angle (0x0B) is normal; absent lane messages are normal for any app but Apple Maps (maybe Google).
- Dongle relays (CPC200) strip junction/angles/lanes: through such a bridge you see only type + side + roundabout exit.
- Test with Apple Maps first (richest) to separate wire/parse problems from app omission.
- Our own observation records are the best source for NAVER/T map/Kakao. Next steps: owner test of each app with Apple Maps as control, cluster on, record which fields reach the cluster (Naver Cafe 아이오닉5/EV6/제네시스 posts; Apple March 2024 CarPlay instrument-cluster docs on the developer portal for field names, not read).

## 2. Cross-brand observations (field inference only)
- Apple Maps iOS 17.4 beta (MacRumors 2024-02-20): option to show upcoming maneuvers in the instrument cluster (supplementary Maps view in driver display/HUD); early adopters BMW, Volvo, Polestar; no lane/junction detail; third-party apps not named.
- Apple dev forum thread 740100 (Oct 2023, answer Apr 2024): Apple Maps alone uses "Instrument Cluster Video Stream 2" in the simulator; March 2024 CarPlay docs plan HUD-area services for third parties. No Apple staff reply, no API name, no CPManeuver reference (M existence, L content).
- BYD Atto 3 Evo (ABRP thread): Apple Maps and Google Maps show HUD turn-by-turn via CarPlay, ABRP does not (developer suspects missing maneuver/distance metadata; works on VW ID.7). Kia EV9 GT-Line owner reports CarPlay HUD works on iOS 26.5. Unverified.
- Waze 5.4.0 (snippet): cluster TBT via CarPlay on BMW, Polestar, Ford reportedly; BMW HUD still lacks Waze. Hyundai/Kia not named.
- Mach-E forum (snippet): Waze/Apple Maps cluster directions reported absent; Google Maps once worked then stopped; commenter suspects CarPlay lacks the cluster metadata; Android Auto reportedly works. Other non-Hyundai snippets: a third-party app shows a small cluster map with no directions while Waze shows directions.
- Hyundai/Kia/Genesis: no owner report of factory CarPlay TBT on the cluster. Factory navigation does drive cluster turn info (Santa Fe, 2025 Tucson guide; secondhand). Apple (2026-04-18) lists Hyundai, Kia, Genesis as "working toward" CarPlay Ultra, no model/trim/date; Gurman (secondary): one major Hyundai/Kia model H2 2026 (unverified). Genesis owner threads on Apple Maps in cluster (403; snippet: cluster matches center-screen map). Wireless CarPlay on 10.25/12.3-inch standard Gen5W navigation reported as not in the EU wireless update (Whichcar snippet; unverified). Variant coding / model-year behaviour: nothing found.

## 3. Other OEM / aftermarket implementations
Pinned clones (shallow, `clones/`, not in git):

| Clone | Commit | Date | Relevant |
|---|---|---|---|
| enigy/carlink (Kotlin Android, GM focus) | ba21dc7 | 2026-11-04 | `app/src/main/kotlin/com/carlink/navigation/ManeuverMapper.kt`, `LaneGuidance.kt`, `LaneIconComposer.kt`, `OcbmNavigationFeed.kt`, `DistanceFormatter.kt`, `Iap2RouteParser.kt`, `Iap2StateParser.kt` |
| WaylandAce/mib2q-carplay-rgi | 1832d6e | 2026-05-20 | README, `docs/`, `c_hook/`, `java_patch/` (not deep-read; luka lineage) |
| maxim-smirnov/mhi2-carplay-cluster | 58fb958 | 2026-10-17 | README only (maneuver renderer + BAP bridge; `tools/audit_maneuver_parity.py` unread) |
| chefranov/mhi2-au37x-carplay | cf95ec5 | 2026-11-02 | README (no route-guidance detail) |
| tomasz-twardzicki/MHI2-Carplay-Maps | 5093715 | 2026-11-04 | README (MU1367/MU1427 native arrows, distance to turn, route totals on AID; HUD projection) |
| a78c7/touareg-cn-carplay-mods | 7ccb704 | 2026-11-01 | release v19.6 via web; large binary zips under `releases/` unread |

### carlink icon mapping (H, `ManeuverMapper.mapManeuverType`; CPManeuverType -> Car App Library `Maneuver.TYPE_*`)
0 -> STRAIGHT; 1 TURN_NORMAL_LEFT; 2 TURN_NORMAL_RIGHT; 3 STRAIGHT; 4 U_TURN_LEFT (RIGHT if left-drive); 5 STRAIGHT; 6 ROUNDABOUT_ENTER_CCW (CW if left-drive); 7 ROUNDABOUT_EXIT_CCW/CW; 8 OFF_RAMP_NORMAL_RIGHT/LEFT; 9 ON_RAMP_NORMAL_RIGHT/LEFT; 10 DESTINATION; 11 DEPART; 12 DESTINATION; 13 KEEP_LEFT; 14 KEEP_RIGHT; 15/16/17 FERRY_BOAT; 18 U_TURN (side-dependent); 19 ROUNDABOUT_ENTER_CW/CCW; 20 TURN_NORMAL_LEFT; 21 TURN_NORMAL_RIGHT; 22 OFF_RAMP_NORMAL_LEFT; 23 OFF_RAMP_NORMAL_RIGHT; 24 DESTINATION_LEFT; 25 DESTINATION_RIGHT; 26 U_TURN (side-dependent); 27 DESTINATION; 28..46 ROUNDABOUT_ENTER_AND_EXIT_CW/CCW (exit number = type - 27); 47 TURN_SHARP_LEFT; 48 TURN_SHARP_RIGHT; 49 TURN_SLIGHT_LEFT; 50 TURN_SLIGHT_RIGHT; 51 KEEP_RIGHT; 52 KEEP_LEFT; 53 KEEP_RIGHT; else TYPE_UNKNOWN with a warning.
- Collapses several types (3 and 5 -> STRAIGHT; 51 and 53 -> KEEP_RIGHT); a finer cluster loses detail. Use code values, not inline comments (mislabeled, e.g. `1 // noTurn`).
- Roundabout exit angle -> swept angle: CCW (right drive) = (180 - iap2) mod 360; CW (left drive) = (180 + iap2) mod 360; 0 -> 360. Author marks "derived, validate on-vehicle" (M). drivingSide 1 flips U-turn and roundabout rotation.

### Lane rendering
- carlink (H, `LaneGuidance.kt`, `LaneIconComposer.kt`): input is 0x5204 via OCBM box JSON `kind=laneGuidance`: per lane `status` (0 do-not-take, 1 usable, 2 preferred), `laneAngle`, optional `laneAngles`, optional `laneAngleHighlight`; Apple convention 0 straight, +90 right, -90 left, ±180 back. `LaneHint` draws only the recommended direction on a lit lane; other directions grey only on non-recommended lanes (extra grey arrow under the lit one was hard to read, owner note 2026-10-31). Icon modes: ARROW (turn arrow shrunk to top, lane row below) and LANES (whole square lanes, lit lanes on a teal band); stroke/head widths scale with lane count, min/max clamps.
- DiPlay (M, issue #927, fetched 2026-11-05): lane data arrives only if the app declares lane capacity in identification (0.2.21 did not -> zero 0x5204; PR #1028 adds it). Reported BYD DiLink cluster/HUD data has distance and turn code, reportedly no lane field; HUD has one icon slot. Unconfirmed.

### Cadence and state model
- carlink (H): 0x5201 ~1/s (distance to maneuver, remaining distance, time, currentManeuverIndex); 0x5202 one per route maneuver keyed by index, live one = index named by last 0x5201. Delayed-end grace timer `END_GRACE_MS` exists (value unread). `Iap2StateParser.kt`: 0x5201 field 0x0d ManeuverCurrentList carries current and next index as len-4 list, element 0 = approached maneuver; earlier versions inferred the index from sparse bursts and needed five iterations.
- DiPlay (M): 0x5201/0x5202 are the navigation info the head unit receives; maintainer says CarPlay data reached the cluster once the cluster nav option was enabled.
- Consistent with the ~1 Hz 0x5201 and ~200 ms 0x5202 burst from the CPC200 capture.

### Units (carlink `DistanceFormatter.kt`, H)
Explicit display unit; no auto-conversion. Imperial (US, UK, MM, LR locales): < 305 m shown in feet, else miles. Metric elsewhere: < 1000 m metres, else km. Unknown geography -> metric. Uses `UNIT_MILES_P1` / `UNIT_KILOMETERS_P1`.

### Per-OEM findings
- **Audi/VW MHI2Q/MIB2 (aftermarket forks):** 0x5200..0x5204 hooked in the phone-side iAP2 stack and forwarded to the cluster/HUD state file (luka lineage). Arrows, distance to turn, remaining route and ETA show on cluster in MU1427/MU1367 builds (tomasz-twardzicki README, M; a78c7 notes, M).
- **VW Touareg China (MH2p, a78c7 v19.6, 2026-11-01, M):** wired CarPlay uses a different iAP2 stack (Cinemo over USB); the nav hook covered only wireless until v19.6, so wired showed no HUD arrows. Hook (`cl_probe.so`) adds a route-guidance component to identification and self-disables until reboot if the phone rejects it (`wired_off` file kill-switch); keyframe requests every 2 s while waiting, 30 s otherwise; cluster log per-boot folders; `wide=2400`, `view=90/85` options off by default. Not verified on-car by us. **Relevant: wired and wireless CarPlay may use different iAP2 sessions, so an identification change may need to cover both.**
- **VW MIB3 Digital Cockpit (L):** conflicting reports. '23 GTI shows next turn with Apple Maps; 2022 Taos next turn only; Tiguan review (MacRumors 2021): native nav lane guidance in cluster, CarPlay Apple Maps guidance on SEL/R-Line but no CarPlay lane guidance.
- **Mercedes NTG (L):** no docs; Reddit says 2024 GLE shows CarPlay arrows over wireless; other posts say cluster arrows come from the car's nav. Unverified.
- **Stellantis/Uconnect (L):** Dodge Uconnect page describes cluster guidance for built-in TomTom nav only; users disagree whether AA/CarPlay directions reach the cluster.
- **BYD DiLink (DiPlay #728, #927, M):** Han 2023 Champion (DiLink 4.0), Song PLUS 2023 Champion (DiLink 3.0), wireless CarPlay via car hotspot. Cluster arrows/distance needed head-unit setting "HUD和仪表盘导航" (Settings > Navigation); cluster map needed network ADB. **Even when the phone sends guidance, the head unit may gate cluster display behind a user setting; check for that gate before debugging the message layer.**
- **Amap (高德) cluster (L):** CarPlay cluster navigation announced Aug 2024 (iOS 17.4+, iPhone 12+); some older cluster builds show only simplified guidance (promotional snippets).
- **Carlinkit / CPC200:** dongle converts guidance to NaviJSON and strips junction and lane data (see [sources.md](sources.md) item I8); carlink's docs replace that path with a full iAP2 parser.

### Open questions for our implementation
1. Does a Hyundai/Kia cluster have an equivalent of BYD's "HUD和仪表盘导航" gate? Check before treating missing icons as a protocol fault.
2. Declare lane capacity in identification (DiPlay PR #1028).
3. Does Gen5W use the same iAP2 session for USB (wired) CarPlay (Touareg v19.6)?
4. Decide whether our cluster icon set needs the full 0..53 range (carlink coarsens).
5. Use code values, not inline comments, in any table we port.
