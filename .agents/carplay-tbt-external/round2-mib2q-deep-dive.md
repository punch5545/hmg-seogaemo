# CarPlay route guidance deep dive: luka-dev RGI, kamgurgul mib2q-carplay, f-io LIVI

Round 2. Scope: how three open CarPlay-to-cluster projects turn iAP2 0x5200-0x5204 into display state.
Clones (depth 1) are in `round2/clones/`. Round 1 facts in `iap2/` and `hkmc/` are not repeated.

Pinned revisions:
- luka-dev/mib2q-carplay-rgi @ `df73e061a29c4d996550716987f42a9243daa09a` (2026-10-29). Doc tree is `docs/cluster-and-rgi/`.
- kamgurgul/mib2q-carplay @ `5c460600c9205cdafa2c4fecd7e2210b8606aedb` (2026-11-04). Doc tree is `docs/rgd/`, not `docs/cluster-and-rgi/` as the brief said. Hook and Java code are in the same paths.
- f-io/LIVI @ `e4ae798e09d6c51140c91816034294687238016e` (2026-11-04). Schema and event code only, no icon or cluster logic.

Important scope note: these projects drive the MHI2 Audi/VW HUD (BAP FctID 23/24, FD62 raster) and a procedural renderer. They do not drive a Hyundai/Kia cluster. Only the iAP2 input side and the state logic transfer directly. Icon tables are a reference for semantics, not for Hyundai icon IDs.

## 1. CPManeuverType to icon (luka-dev, full table)

Source: `mib2q-carplay-rgi/docs/cluster-and-rgi/MANEUVER_MAPPING.md` lines 121-145 (BAP RHT/LHT codes, HUD selector s-number). Code is `java_patch/com/luka/carplay/rgd/ManeuverMapper.java` (lines 161-380).

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
| 20 | LEFT_TURN_AT_END | TURN by end-of-road angle | TURN |
| 21 | RIGHT_TURN_AT_END | TURN by end-of-road angle | TURN |
| 22 | HIGHWAY_OFF_RAMP_LEFT | TURN SLIGHT_LEFT (type wins over angle) | slight TURN |
| 23 | HIGHWAY_OFF_RAMP_RIGHT | TURN SLIGHT_RIGHT | slight TURN |
| 24, 25 | ARRIVE_DESTINATION_L/R | ARRIVED L/R | ARRIVED L/R |
| 28-46 | ROUNDABOUT_EXIT_1..19 | ROUNDABOUT_TRS + 16-step dir from angle | full ring, raw angle |
| 47, 48 | SHARP_LEFT/RIGHT | TURN SHARP | TURN |
| 52, 53 | CHANGE_HIGHWAY_LEFT/RIGHT | CHANGE_LANE L/R | LANE_CHANGE |
| 255 / other | NOT_SET / out of range | NO_SYMBOL or NO_INFO | nothing |

Kamgurgul (`docs/rgd/maneuver-mapping.md`, 5c46060) differs in a few rows: 8 and 9 same idea (SLIGHT), 22/23 same, 7 EXIT_ROUNDABOUT by side only (no junction check), 20/21 same. The main difference is the junction gate, see section 6.

## 2. Angle sentinels and direction rules

Both repos use the same convention (`rgd_tlv.c` and `ManeuverMapper.java`):

| Value | Meaning |
|---|---|
| 0 | real straight |
| +1000 | absent exit angle for an indexed maneuver (set by C parser, `rgd_tlv.c:617-619`) |
| -1000 | defensive only, never a direction |
| -1 with presence | real -1 deg |
| -1 without presence | unfilled Java slot default |

Apple never synthesises 1000. Absent TLV means no angle (luka `MANEUVER_MAPPING.md` lines 85-89, decompiled iOS 26.6 and 27.0). For accNav 4/18/26, the sign is normalised by driving side in iOS (RHT negative = left U-turn). Generic bucket (types 11/16/51): |a| 0-22 straight, 23-67 slight, 68-112 normal, 113-180 sharp.

## 3. Lanes: iAP2 0x5204 to cluster arrows

- Each 0x5204 carries per-lane `status` (0 not good, 1 good, 2 preferred), `angles[]`, a `highlight` angle, and an index. Native cache holds 32 events keyed by lane index (`rgd_hook.c` ~L368-410).
- Lane events are independent of the maneuver. The active lane is the one whose index equals 0x5201 `lane_guidance_index`. Java does not pick a fallback (`LANE_GUIDANCE.md`).
- Angle to lane arrow: `BAPBridge.java:1721-1760`. Nearest of 9 native keys (-180, -135, -90, -45, 0, 45, 90, 135, 180) to codes 0x72, 0x60, 0x40, 0x20, 0x00, 0xE0, 0xC0, 0xA0, 0x92. Values ±1000 or outside ±180 become 0xFF before quantisation.
- Missing angles for one lane never borrow another lane's. Only a valid primary with status 2 is highlighted (renderer side).
- Shown gate: `laneGuidanceVisible = laneGuidanceShowing == 1 && !(maneuverCount == 0 && routeState <= 0)`. No distance or maneuver-type gate. Lanes can show well before the junction.
- Lane instruction text (0x5204 / 0x03, "Use the 2 left lanes") is parsed but not sent to the cluster (`IAP2_ROUTE_GUIDANCE_TLVS.md`).

## 4. Distance, units, update cadence

- Distance remaining (0x5201 TLV 7 `dest_m`) and distance to maneuver (0x5201 TLV 10 `dist_maneuver_m`) are published as raw uint32 under keys named `_m` (`rgd_hook.c:727-729`). Java copies them into `distDestM` and `distManeuverM` (`RouteGuidance.java:1043-1052`). Units are not read. Meters are assumed. Confidence M.
- Units TLVs (0x5201 TLV 9 and 12, 0x5202 TLV 7) are stored and published (`dist_dest_units`, `dist_maneuver_units`, `m%u_distance_units`) but Java ignores them. The enum from MHI3 type info is 0=KM, 1=MILES, 2=M, 3=YARDS, 4=FT (`rgd_tlv.h:194-200`). Apple's own display string (TLV 8, 11) is also ignored, and the project formats for the cluster locale instead.
- Bargraph denominator is 0x5202 TLV 0x05 (`distance_between`), not remaining distance. Policy: prepare thresholds, 15 percent cap, 20 percent blink at 600 ms (`DISTANCE_BARGRAPH.md`, `RGI_AND_BAP_BRIDGE.md`).
- Cadence: no time-based push. Updates are event driven. The C hook has a 1 Hz bus tick (`rgd_periodic_tick`, `rgd_hook.c:1341`) used only to flush the route-zero debounce. Java presentation retry is 500 ms (`PRESENTATION_RETRY_MS`, `RouteGuidance.java:62`). Kamgurgul `rgd-activation.md` confirms the same 500 ms.
- Ordering per update: lanes (0x5204) before the 0x5201 that makes one current; `delegateUpdatedRouteGuidance` runs maneuvers, lanes, route update (`LANE_GUIDANCE.md`, decompiled iOS 27). Confidence H for the order, from decompile.

## 5. Behaviour when fields are omitted

| Missing field | Behaviour (luka-dev) | Source |
|---|---|---|
| Any TLV | parser keeps presence bits; Java applies only present keys as deltas, so old values persist | `IAP2_ROUTE_GUIDANCE_TLVS.md`, `rgd_hook.c` |
| Distance remaining / to maneuver | stays -1 unless present | `RouteGuidance.java:143-146` |
| Exit angle (0x5202 TLV 0x0B) | +1000 for indexed maneuver (unknown) | `rgd_tlv.c:617-619` |
| Driving side | fallback to traffic side for U-turn family | `MANEUVER_MAPPING.md:74-79` |
| Lane angle | 1000, maps to 0xFF, lane shown without direction | `BAPBridge.java:1721+` |
| Junction type (0x5202 TLV 0x09) | stays -1; non-roundabout ordinary types then hit NO_INFO (see section 6) | `ManeuverMapper.java:239-260` |
| Source name, DisplayString, EV fields | not used | `IAP2_ROUTE_GUIDANCE_TLVS.md` |

Unknown 0x52xx messages are logged at WARN, not dropped silently (`hook_framework.c:348-368`).

## 6. Junction gate: known bug in one repo, still present in the other

- luka-dev `ManeuverMapper.java:239-260`: if junctionType is not 0 and not 1, most maneuver types return NO_INFO. The absent value -1 is in that set. Result: a LEFT_TURN with no JunctionType TLV produces no arrow. The doc (`MANEUVER_MAPPING.md`, "Junction-type gate") says this is MHI3 parity.
- kamgurgul `ManeuverMapper.java:169-174` removed the gate and cites issue #9 (https://github.com/kamgurgul/mib2q-carplay/issues/9). Third-party apps such as Sygic omit JunctionType, and every ordinary turn lost its arrow. junctionType is kept only for side streets.

Implication for us: if we copy the luka-dev mapper, carry the issue-9 fix. Confidence M that this reproduces in luka-dev as-is (from code reading only, not run).

## 7. Rerouting and route state machine

Both repos share the same C hook logic (`rgd_hook.c`, constants identical).

States: 0 NO_ROUTE_SET, 1 ROUTE_SET, 5 REROUTING.

- route_state 0 is not forwarded at once. It becomes pending with an 800 ms grace (`ROUTE_ZERO_NORMAL_GRACE_MS`, `rgd_hook.c:33`). It cancels on active evidence (state > 0, 0x5202, 0x5204, non-empty maneuver data), or on hard clear (disconnect, source_supports_rg=0).
- If the previous state was REROUTING (or the emitted state was 5), the zero is treated as reroute and has no deadline (`rgd_hook.c:1122-1124`). Rationale in the header comment: poor connectivity can keep Maps in REROUTING for minutes.
- On a real 1 to 0 transition, the slot cache is flushed and `rgd_maneuver_map_reset()` sets `route_generation` to monotonic milliseconds, so reused slot versions are distinguishable.
- Java activation (`RouteGuidance.java:470-500`): visible_in_app is a visibility flag, not route activity. visible_in_app=1 activates. visible_in_app=0 only deactivates when routeState < 1 and maneuverCount is 0 and maneuverOrder is empty. Route_state 0 is authoritative. Kamgurgul `rgd-activation.md` says the same.
- Maneuver slots accepted at REROUTING regardless of index (`rgd_can_process_maneuver_index`, `rgd_hook.c:280-293`).

LIVI contrast: `livi-runtime/src/events.rs:116-125`. On state 0 it resets the whole navigation model at once, with no debounce, no grace, and no reroute special case. Good for a passive display, but it would flicker on reroute if it drove a cluster.

## 8. Timing of the start message (0x5200) and keep-alive

- Luka-dev sends 0x5200 itself (`rgd_hook.c`, retry logic at ~L929-945). Fast retry every 2 s for the first 5 attempts, then every 30 s, until the first real RGI message arrives. Retry stops on that message.
- It injects through the stock `ICinemoIAP_SendIAP2` on a single worker thread, not by replacing a LocationInfo carrier. Replacing carriers caused iOS to answer STOP_LOCATION (`IAP2_ROUTE_GUIDANCE_TLVS.md`, commit 687596f failed experiment).
- Identify start (0x1D00) clears retry state so a reconnect does not inherit a latched request.
- No separate periodic keep-alive for RGI beyond this retry. Stopping is 0x5203 (HU to phone), which the code does not appear to send in normal flow. Not verified.
- LIVI sends 0x5200 at bring-up (`livi-runtime/src/bringup.rs:121-127`). A grep of `livi-runtime` and `livi-cp` found no retry logic for route guidance. `livi-runtime/src/ident.rs:51` lists 0x5200 and 0x5203 among advertised messages. Its StartRouteGuidance requests `source_name`, `supports_route_guidance`, `supports_exit_info`, `destination_time_zone_offset`.

## 9. Message schema cross-check (LIVI and luka-dev)

LIVI `native/crates/iap2-csm/src/messages/route_guidance.rs` (GPL-3.0, schema only) agrees with luka-dev on TLV IDs for 0x5201 (0-21), 0x5202 (0-13), 0x5204 (0-3). One difference: LIVI names TLV 0x5201/19 `source_name` and 0x5201/21 `destination_time_zone_offset` as i16. Luka-dev names 0x15 "DestinationTimeZoneOffsetMinutes" and treats 0x17-0x1A as recognised-and-skipped, with a documented disagreement on 0x17 (`IAP2_ROUTE_GUIDANCE_TLVS.md`, marked `[open]`).

## 10. LIVI nav_maneuver.rs (Android Auto side, not iAP2)

`native/crates/livi-aa-stack/src/channels/nav_maneuver.rs` maps Android Auto turn events to the same iAP2 ManeuverType numbers. Example: AA `OffRamp` + left gives RampOffLeft (22), `Turn` + right gives RightTurn (2), `Merge` gives RampOn (9). It does not read iAP2. Useful only as a second vocabulary for the same numbers. It also shows AA has no "roundabout exit N" codes: AA roundabout events collapse to 6/7.

## Confidence summary

- H: TLV IDs, angle sentinels, lane order (decompile + on-car notes), C debounce constants (same in both repos).
- M: meters assumption for distance; junction gate reproduces in luka-dev (code read, not run); LIVI has no retry (only the visible bring-up).
- L/open: whether 0x5203 is ever sent; lane instruction text usefulness; cluster icon IDs (not in these repos, and these repos target the HUD).

## Differences between the two repos (quick)

- `rgd_hook.c` differs (luka-dev is newer), but the debounce constants and retry constants match.
- `ManeuverMapper.java` differs by about 60 lines, mainly the junction gate and comments.
- Doc paths differ: luka-dev has `docs/cluster-and-rgi/`, kamgurgul has `docs/rgd/`.
