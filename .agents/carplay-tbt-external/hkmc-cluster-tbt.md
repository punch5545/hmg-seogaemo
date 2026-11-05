# Cluster and HUD turn-by-turn on Hyundai/Kia/Genesis Gen5W: what HMG-API sends

Compiled 2026-11-05 from public sources ([sources.md](sources.md) section A-C). Mostly HMG-API's source (AGPL-3.0, `tmp/HMG-API` at dd10045), **read only**: these are facts about what it sends, not copied code. H = as sent by HMG-API; M = what the cluster draws / absence in sources. "Inferred" = unverified on a car. Firmware-confirmed (stock-side) codes supersede the send-side tables here: [../cluster-tbt-icons.md](../cluster-tbt-icons.md), [../cluster-lane-frame.md](../cluster-lane-frame.md).

## 1. Interface: Binder/Java, not raw CAN
- The head-unit app calls `com.hkmc.cluster.NavigationCluster` (framework point `framework.navigation_cluster`, constructor takes a Context). H (`Cluster.java`, `hook-points.md`).
- HMG-API's M-CAN map (`docs/micom-map.md`) has battery/seat frames but no TBT or lane frame. No public source documents TBT CAN IDs (M).
- `IClusterService` / `INavigationClusterListener` are marked "not on Hyundai Ioniq 5 USA 260903" in HMG-API's catalogue: on that build TBT goes via `NavigationCluster`, and the battery-info service is absent. H.

## 2. Methods and arguments (HMG-API signatures, `docs/hook-points.md`; resolved by reflection) H
| Java method | Args | Purpose |
|---|---|---|
| `updateTBTInfo(int, int, float, float, int, int, int, int, int)` | type (icon), sideStreet (65535 = none), direction (deg, icon arrow), distance (in `unit`), distanceErr, combiSideStreet, unit, barGraph, barGraphErr | Current turn |
| `updateTBTSubInfo(float, int, int, int, int, int, int, float, int)` and `_b` (10 args) | distance remaining, distErr, unit, hour, minute, timeType, timeFormat, direction, directionErr, estPoint | Remaining trip and ETA |
| `updateTBTSubInfo2C(int x9)` | unit, tenths, whole, then zeros | HUD distance; whole up to 4095, tenths only km/mi |
| `updateTPTurnListNew(byte[])` | frame starting 0xF2 | HUD turn list (section 4) |
| `updateTPTurnList(TurnList[])` | up to 3 turns: type, direction, traffic, no, name, exitNumber, distance, unit | Upcoming turns |
| `updateLaneInfo(byte[])` / `updateTPLaneInfo(LaneInformation)` | 81-byte frame starting 0xF1 | Lanes |
| `updateTPCrossroadName(CrossroadName)`, `updateTPFlexibleDestinationName(DestinationName)` | name strings | Road/destination text |
| `updateRemainDis(int, int, int, int)` | in catalogue, unused by HMG-API TBT | likely remaining-distance variant (unverified) |

- Clearing: turn `updateTBTInfo(0, 0, 0, 0, -1, 0, 0, 0, -1)`; sub-info distance 0 and err -1; HUD a 1-byte 0xF2 frame; lanes an all-zero 0xF1 frame; destination an empty name.
- Unknown distance: `barGraph = 254`, `barGraphErr = -2`.
- Unknown icon with a known turn: icon below 64 substitutes type 2 (meaning undocumented, placeholder).

## 3. Icon codes (HMG-API Android Auto mapper: `AaTurns.setTurnType`, `setRampType`, `setRoundaboutType`)
H for the mapping as sent; M for what the cluster draws (from HMG-API testing on a US EV6 and Ioniq 5; no public icon atlas). "dir" = arrow angle sent with the code.

| Situation | Right (type / dir) | Left (type / dir) | Unspecified side |
|---|---|---|---|
| Straight, merge, name change | 65 / 0 | 65 / 0 | 65 / 0 |
| Slight turn | 66 / 30 | 71 / 330 | 64 / 0 |
| Turn | 67 / 90 | 70 / 270 | 64 / 0 |
| Sharp turn | 68 / 135 | 69 / 225 | 64 / 0 |
| U-turn | 73 / 270 | 72 / 270 | 72 (unspecified treated as left) |
| On-ramp | 173 / 30 | 174 / 330 | 172 / 0 |
| Off-ramp | 81 / 30 | 82 / 330 | 86 / 0 |
| Fork | 173 / 0 | 174 / 0 | no change |
| Ferry boat | 176 / 0 | | |
| Roundabout exit | 32 | 33 | 64 / 0 |
| Roundabout enter (angle-based, RHT) | 96 (180), 97 (135-180, dir 45), 99 (90-135, dir 90), 100 (45-90, dir 135), 102 (0-45 or 315-360, dir 180), 103 (270-315, dir 225), 105 (225-270, dir 270), 106 (180-225, dir 315) | | |

- **Icon 64** is HMG-API's fallback "unknown / straight, no side", sent when side is unknown (H code path, M drawing). Fits our observation that 64 shows a loading state. Per the firmware audit the Gen5W converter falls back to 8, not 64 ([../cluster-tbt-icons.md](../cluster-tbt-icons.md)). Controlled send on a car needs an owner decision.
- Depart/destination events are not in the icon table. For the destination-type event (AA number 32, handled as a name event) HMG-API sends the destination name and distance -1 (exact event-to-case mapping only partly read).
- Codes 74-80, 84-95, 101, 104, 107-171, 175 appear in no public source reviewed (M, absence).

## 4. HUD turn list frame (0xF2) — `AaTurns.sendHudList`
H as sent; cluster reading M (HMG-API reports it works on EV6 HUD, per Leafia's "no HUD? HUD frames go unused").
- byte 0 `0xF2`; byte 1: 2 if there is a next icon else 1; byte 2 this turn's icon code; byte 3 next turn's code (0 if none); byte 4 unused; bytes 5+ road name UTF-16LE, max 78 bytes (frame max 83).
- Send only when type, next type or road name changed.

## 5. Lane frame (0xF1, 81 bytes) — `AaTurns.sendLanes`, `CpTurns.lanes`
H as sent; rendering M. Stock-side layout is confirmed in [../cluster-lane-frame.md](../cluster-lane-frame.md).
- byte 0 `0xF1`; byte 1 lane count n (max 8); bytes 3..10 one status byte per lane (offset 3+k): 1 normal, 2 highlighted (CarPlay path); in AA path the lane's type value from the library's hex string; bytes 19..42 per lane three arrow bytes at 19+3k, 20+3k, 21+3k; others zero.
- AA input (`AaTurns`): hex string, first 2 digits lane count, then 8 hex digits per lane (type + three arrow bytes); sent only on change.
- CarPlay input (`CpTurns.lanes`): iAP2 LaneGuidance (message 122), item 13/2 holds lanes; each lane has status and up to 3 angles.

## 6. Units
- `Cluster` constants: 0 metres, 1 km, 2 miles, 3 feet, 4 yards (H; firmware-confirmed).
- AA library unit codes: 1 metres, 2 km, 3 km one decimal, 4 miles, 5 miles one decimal, 6 feet, 7 yards. Unknown unit -> 15 in code (meaning undocumented).
- Converts to the car's distance-unit setting (`Car.distanceUnit`: km or miles) via metres when the phone's unit differs.
- Float distance is in `unit`, not metres; HUD distance uses whole + tenths. `distance = e3 / 1000.0` for km/miles, raw metres for metres (matches the AA callback's "display distance x1000" flag).

## 7. Keep-alive, handshake, what not to send
- Leafia's TBT_CALLS (old generation) include `updateOperationStatus` and `responseNaviAliveStatus` and treat them as required for "ok" (`docs/app.js`, from the car's API report). H.
- HMG-API deliberately **does not** send cluster alive/operation status; comments: GPM navigation's own manager "also claims the cluster (alive + status), which these cars never give back" (project experience, not measured). Whether the cluster needs a periodic keep-alive for TBT to stay visible is **open**; no public source answers.
- CarPlay path: stops and restarts phone route-guidance updates (iAP2 `stop_route_guidance`, then start) when a full resend is needed; on source change (Apple Maps -> Waze) clears what the old app left and restarts guidance.
- Turn frames are sent from one handler thread in both paths; cluster calls are wrapped so a failure never throws into the caller.

## 8. CAN IDs
- HMG-API `docs/micom-map.md`: **0x4ED Navigation_Precond_Status** (HU to car: distance to charger, precondition flag); other IDs are seats, park brake, speed, steering wheel.
- No public source lists CAN IDs for TBT, distance or lane frames on Gen5W, Ioniq 5, EV6, EV9 or GV60. The public opendbc Hyundai/Kia DBC URL tried returned 404 (not retried). Treat raw CAN injection for TBT as unknown and high risk. The CNR report (B1) describes CAN injection from the IVI but could not be read (403).

## 9. Gaps
1. Cluster icon atlas for 64-176 (only send side is public); owner-approved capture of the cluster per code would settle it.
2. Whether TBT needs keep-alive / operation status (HMG-API says no; unmeasured).
3. `unit = 15` case and `updateRemainDis` argument meaning.
4. Whether `updateTBTInfo` distance is in the unit or metres on every firmware (treat as unit-based).
5. Genesis forum thread (C1) suggests TBT is set in the navigation/head-unit variant, not the cluster; unverified, relevant if the feature is off on a non-US unit.

## 10. Source key
Local, read only: `tmp/HMG-API` (`core/runtime/java/hmgapi/hook/Cluster.java`, `docs/hook-points.md`, `docs/micom-map.md`, `docs/vehicle-commands.md`, `mods/wireless-carplay-aa/src/hmgapi/mods/wireless/AaTurns.java`, `.../dipo/CpTurns.java`), `tmp/HMG-Leafia-Plus` (`docs/app.js`, `docs/samples/ioniq5.txt`). Public: [sources.md](sources.md) A1-A11, C1, C3.
