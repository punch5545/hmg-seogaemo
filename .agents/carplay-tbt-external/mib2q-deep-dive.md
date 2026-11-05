# luka-dev RGI, kamgurgul mib2q-carplay, f-io LIVI: how 0x5200-0x5204 become display state

Pinned revisions (clones were depth 1 under `round2/clones/`; not in git):
- luka-dev/mib2q-carplay-rgi @ `df73e061a29c4d996550716987f42a9243daa09a` (2026-10-29), docs in `docs/cluster-and-rgi/`.
- kamgurgul/mib2q-carplay @ `5c460600c9205cdafa2c4fecd7e2210b8606aedb` (2026-11-04), docs in `docs/rgd/` (not `docs/cluster-and-rgi/`); hook and Java paths as luka-dev.
- f-io/LIVI @ `e4ae798e09d6c51140c91816034294687238016e` (2026-11-04): schema and event code only, no icon/cluster logic.

Scope: these drive the MHI2 Audi/VW HUD (BAP FctID 23/24, FD62 raster) with a procedural renderer, not a Hyundai/Kia cluster. Only the iAP2 input side and state logic transfer; icon tables show semantics, not Hyundai icon IDs. Wire formats: [iap2-protocol.md](iap2-protocol.md).

## 1. CPManeuverType -> icon (luka-dev)
Source: `MANEUVER_MAPPING.md` lines 121-145 (BAP RHT/LHT codes, HUD selector); code `java_patch/com/luka/carplay/rgd/ManeuverMapper.java` lines 161-380.

| MT | CarPlay type | BAP main/dir (RHT) | Renderer |
|---:|---|---|---|
| 0, 5 | NO_TURN, FOLLOW_ROAD | FOLLOW_STREET | APPROACH |
| 1 | LEFT_TURN | TURN 40 (L) | TURN, exact angle if left |
| 2 | RIGHT_TURN | TURN C0 (R) | TURN, exact angle if right |
| 3 | STRAIGHT_AHEAD | TURN 00 | TURN 0 deg |
| 4, 18, 26 | U_TURN, START_WITH_U_TURN, U_TURN_WHEN_POSSIBLE | UTURN 40/C0 by sign, else by side | UTURN |
| 6 | ENTER_ROUNDABOUT | TURN L/R by driving side | full ring |
| 7 | EXIT_ROUNDABOUT | EXIT_ROUNDABOUT_TRS (junction 1 only) | full ring |
| 8 | OFF_RAMP | TURN SLIGHT L/R by angle sign, else side | slight TURN |
| 9 | ON_RAMP | TURN SLIGHT L/R | slight TURN, no forward road |
| 10, 12, 27 | ARRIVE_* | ARRIVED | ARRIVED centre |
| 11, 16, 51 | START_ROUTE, EXIT_FERRY, CHANGE_HIGHWAY | TURN, bucketed from angle | TURN |
| 13, 49 | KEEP_LEFT, SLIGHT_LEFT | TURN SLIGHT_LEFT | TURN |
| 14, 50 | KEEP_RIGHT, SLIGHT_RIGHT | TURN SLIGHT_RIGHT | TURN |
| 15, 17 | ENTER_FERRY, CHANGE_FERRY | FOLLOW_STREET | APPROACH |
| 19 | U_TURN_AT_ROUNDABOUT | ROUNDABOUT_TRS + UTURN | ring, fixed 180 |
| 20, 21 | LEFT/RIGHT_TURN_AT_END | TURN by end-of-road angle | TURN |
| 22 / 23 | HIGHWAY_OFF_RAMP_LEFT / RIGHT | TURN SLIGHT_LEFT / RIGHT (type wins over angle) | slight TURN |
| 24, 25 | ARRIVE_DESTINATION_L/R | ARRIVED L/R | ARRIVED L/R |
| 28-46 | ROUNDABOUT_EXIT_1..19 | ROUNDABOUT_TRS + 16-step dir from angle | full ring, raw angle |
| 47, 48 | SHARP_LEFT/RIGHT | TURN SHARP | TURN |
| 52, 53 | CHANGE_HIGHWAY_LEFT/RIGHT | CHANGE_LANE L/R | LANE_CHANGE |
| 255 / other | NOT_SET / out of range | NO_SYMBOL or NO_INFO | nothing |

kamgurgul (`docs/rgd/maneuver-mapping.md`, 5c46060) differs only slightly: 7 EXIT_ROUNDABOUT by side only (no junction check); main difference is the junction gate (section 6).

## 2. Angle sentinels and direction rules
Same convention in both repos (`rgd_tlv.c`, `ManeuverMapper.java`):

| Value | Meaning |
|---|---|
| 0 | real straight |
| +1000 | absent exit angle for an indexed maneuver (C parser, `rgd_tlv.c:617-619`) |
| -1000 | defensive only, never a direction |
| -1 with presence | real -1 deg |
| -1 without presence | unfilled Java slot default |

Apple never synthesises 1000; absent TLV = no angle (luka `MANEUVER_MAPPING.md` lines 85-89, decompiled iOS 26.6 / 27.0). accNav 4/18/26 sign is normalised by driving side in iOS (RHT negative = left U-turn). Generic bucket (types 11/16/51): |a| 0-22 straight, 23-67 slight, 68-112 normal, 113-180 sharp.

## 3. Lanes: 0x5204 -> cluster arrows
- Per-lane `status` (0 not good, 1 good, 2 preferred), `angles[]`, `highlight` angle, index. Native cache: 32 events keyed by lane index (`rgd_hook.c` ~L368-410).
- Lane events are independent of the maneuver; the active lane is the one whose index equals 0x5201 `lane_guidance_index`; Java picks no fallback (`LANE_GUIDANCE.md`).
- Angle -> arrow (`BAPBridge.java:1721-1760`): nearest of 9 native keys (-180, -135, -90, -45, 0, 45, 90, 135, 180) -> codes 0x72, 0x60, 0x40, 0x20, 0x00, 0xE0, 0xC0, 0xA0, 0x92. ±1000 or outside ±180 -> 0xFF before quantisation.
- Missing angles for one lane never borrow another lane's. Only a valid primary with status 2 is highlighted (renderer side).
- Shown gate: `laneGuidanceVisible = laneGuidanceShowing == 1 && !(maneuverCount == 0 && routeState <= 0)`; no distance/type gate, lanes can show well before the junction.
- Lane instruction text (0x5204 / 0x03) is parsed but not sent to the cluster (`IAP2_ROUTE_GUIDANCE_TLVS.md`).
- Order per update: lanes (0x5204) before the 0x5201 that makes one current; `delegateUpdatedRouteGuidance` runs maneuvers, lanes, route update (`LANE_GUIDANCE.md`, decompiled iOS 27). H.

## 4. Distance, units, cadence
- 0x5201 TLV 7 `dest_m` and TLV 10 `dist_maneuver_m` are published as raw uint32 under `_m` keys (`rgd_hook.c:727-729`); Java copies to `distDestM`/`distManeuverM` (`RouteGuidance.java:1043-1052`). Units not read; metres assumed (M).
- Unit TLVs (0x5201 TLV 9/12, 0x5202 TLV 7) stored and published (`dist_dest_units`, `dist_maneuver_units`, `m%u_distance_units`) but ignored by Java; enum 0=KM, 1=MILES, 2=M, 3=YARDS, 4=FT (`rgd_tlv.h:194-200`). Apple display strings (TLV 8, 11) also ignored; project formats for the cluster locale.
- Bargraph denominator is 0x5202 TLV 0x05 (`distance_between`), not remaining distance. Policy: prepare thresholds, 15 percent cap, 20 percent blink at 600 ms (`DISTANCE_BARGRAPH.md`, `RGI_AND_BAP_BRIDGE.md`).
- No time-based push; event driven. C hook has a 1 Hz bus tick (`rgd_periodic_tick`, `rgd_hook.c:1341`) used only to flush the route-zero debounce. Java presentation retry 500 ms (`PRESENTATION_RETRY_MS`, `RouteGuidance.java:62`; kamgurgul `rgd-activation.md` agrees).

## 5. Omitted fields
| Missing field | Behaviour (luka-dev) | Source |
|---|---|---|
| Any TLV | presence bits kept; Java applies only present keys as deltas, old values persist | `IAP2_ROUTE_GUIDANCE_TLVS.md`, `rgd_hook.c` |
| Distance remaining / to maneuver | stays -1 unless present | `RouteGuidance.java:143-146` |
| Exit angle (0x5202 TLV 0x0B) | +1000 for indexed maneuver (unknown) | `rgd_tlv.c:617-619` |
| Driving side | fallback to traffic side for U-turn family | `MANEUVER_MAPPING.md:74-79` |
| Lane angle | 1000 -> 0xFF, lane shown without direction | `BAPBridge.java:1721+` |
| Junction type (0x5202 TLV 0x09) | stays -1; ordinary types then hit NO_INFO (section 6) | `ManeuverMapper.java:239-260` |
| Source name, DisplayString, EV fields | not used | `IAP2_ROUTE_GUIDANCE_TLVS.md` |

Unknown 0x52xx messages are logged at WARN, not dropped silently (`hook_framework.c:348-368`).

## 6. Junction gate bug
- luka-dev `ManeuverMapper.java:239-260`: if junctionType is not 0/1 (absent = -1), most types return NO_INFO, so a LEFT_TURN without JunctionType gets no arrow. Doc (`MANEUVER_MAPPING.md`, "Junction-type gate") calls it MHI3 parity.
- kamgurgul `ManeuverMapper.java:169-174` removed the gate, citing issue #9 (https://github.com/kamgurgul/mib2q-carplay/issues/9): Sygic omits JunctionType and every ordinary turn lost its arrow. junctionType is kept only for side streets.
- Implication: if we copy the luka-dev mapper, carry the issue-9 fix and never reintroduce a gate (same lesson as our NAVER fix: NAVER never sends the exit angle). Reproduction in luka-dev is from code reading only (M).

## 7. Rerouting / route state machine
Shared C hook (`rgd_hook.c`, identical constants). States 0 NO_ROUTE_SET, 1 ROUTE_SET, 5 REROUTING.
- route_state 0 is not forwarded at once: pending with 800 ms grace (`ROUTE_ZERO_NORMAL_GRACE_MS`, `rgd_hook.c:33`); cancels on active evidence (state > 0, 0x5202, 0x5204, non-empty maneuver data) or hard clear (disconnect, source_supports_rg=0).
- If previous state was REROUTING (or emitted state 5) the zero is treated as reroute with no deadline (`rgd_hook.c:1122-1124`): poor connectivity can keep Maps in REROUTING for minutes.
- On a real 1->0 transition the slot cache is flushed and `rgd_maneuver_map_reset()` sets `route_generation` to monotonic ms.
- Java activation (`RouteGuidance.java:470-500`): visible_in_app is a visibility flag, not route activity; =1 activates; =0 deactivates only when routeState < 1 and maneuverCount 0 and maneuverOrder empty. route_state 0 is authoritative (kamgurgul `rgd-activation.md` same).
- Maneuver slots accepted at REROUTING regardless of index (`rgd_can_process_maneuver_index`, `rgd_hook.c:280-293`).
- LIVI (`livi-runtime/src/events.rs:115-125`): state 0 resets the whole model at once, no debounce/grace/reroute case; fine for a passive display, would flicker on reroute driving a cluster. 0x5204 keyed by lane index (`events.rs:209-217`); passes distance and units raw (`events.rs:153-162`).

## 8. Start message (0x5200) and keep-alive
- luka-dev sends 0x5200 itself (`rgd_hook.c` ~L929-945; constants `:40-42`, `:940-941`): retry every 2 s for the first 5, then every 30 s, until the first real RGI message.
- Injected through the stock `ICinemoIAP_SendIAP2` on one worker thread, not by replacing a LocationInfo carrier (that made iOS answer STOP_LOCATION; commit 687596f failed experiment).
- Identify start (0x1D00) clears retry state so a reconnect does not inherit a latched request.
- No other periodic keep-alive on the iAP2 side. Whether 0x5203 is ever sent in normal flow: not verified.
- LIVI sends 0x5200 once at bring-up (`livi-runtime/src/bringup.rs:121-127`); no retry found in `livi-runtime`/`livi-cp`. `ident.rs:51` lists 0x5200 and 0x5203; its Start requests `source_name`, `supports_route_guidance`, `supports_exit_info`, `destination_time_zone_offset`.

## 9. Schema cross-check
LIVI `route_guidance.rs` (GPL-3.0, schema only) agrees with luka-dev on TLV ids for 0x5201 (0-21), 0x5202 (0-13), 0x5204 (0-3). Differences: LIVI names 0x5201/19 `source_name` and 0x5201/21 `destination_time_zone_offset` i16; Luka names 0x15 "DestinationTimeZoneOffsetMinutes" and treats 0x17-0x1A as recognised-and-skipped, with an `[open]` disagreement on 0x17 (`IAP2_ROUTE_GUIDANCE_TLVS.md`).

## 10. LIVI `nav_maneuver.rs` (Android Auto side)
`native/crates/livi-aa-stack/src/channels/nav_maneuver.rs` maps AA turn events to iAP2 ManeuverType numbers (AA `OffRamp`+left -> 22, `Turn`+right -> 2, `Merge` -> 9). Does not read iAP2. AA has no "roundabout exit N": roundabout events collapse to 6/7.

## Confidence and differences
- H: TLV ids, angle sentinels, lane order (decompile + on-car notes), C debounce constants (same in both repos).
- M: metres assumption; junction gate reproducing in luka-dev; LIVI having no retry.
- L/open: whether 0x5203 is ever sent; usefulness of lane instruction text; cluster icon ids (not in these repos).
- luka-dev `rgd_hook.c` is newer than kamgurgul's (debounce/retry constants match); `ManeuverMapper.java` differs by ~60 lines (junction gate, comments).
- Not read end to end: luka `DISTANCE_BARGRAPH.md` (grepped only).
