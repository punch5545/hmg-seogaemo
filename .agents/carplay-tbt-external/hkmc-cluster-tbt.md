# Cluster and HUD turn-by-turn on Hyundai/Kia/Genesis Gen5W (external evidence)

Compiled 2026-11-05 from public sources in `sources.md`. Most detail comes from HMG-API's source (AGPL-3.0, tmp clone at commit dd10045). Those sources were **read only**: the facts below describe what that project sends and why, and they are not copied code. Confidence is given per section. Treat anything marked "inferred" as unverified against a car.

## 1. The interface: Binder/Java, not raw CAN

- The head unit's app calls `com.hkmc.cluster.NavigationCluster` (framework point `framework.navigation_cluster`, constructor takes a Context). Its methods are the TBT API. **H** (HMG-API `Cluster.java`, `hook-points.md`).
- The cluster itself is reached inside the head unit, not through a raw CAN ID that a third party can write. HMG-API's hook map (`docs/micom-map.md`) lists the M-CAN frames it knows for the battery and seats, but no TBT or lane frame. **H** for HMG-API's map; the absence of TBT CAN IDs in public sources is **M** (no public source documents them, see section 7).
- The cluster-service Binder (`IClusterService`, `INavigationClusterListener`) is marked "not on Hyundai Ioniq 5 USA 260903" in HMG-API's catalogue. So on that build the TBT path is the `NavigationCluster` class, while the battery-info service is absent. **H** (catalogue).

## 2. Methods and argument layout (as HMG-API calls them)

Method names are the Java names; HMG-API resolves them by reflection. Signatures from `docs/hook-points.md`. **H**

| Java method | Args (as HMG-API passes them) | Purpose |
|---|---|---|
| `updateTBTInfo(int, int, float, float, int, int, int, int, int)` | type (icon), sideStreet (65535 = none), direction (degrees, icon arrow), distance (in `unit`), distanceErr, combiSideStreet, unit, barGraph, barGraphErr | Current turn |
| `updateTBTSubInfo(float, int, int, int, int, int, int, float, int)` and `_b` (10 args) | distance remaining, distErr, unit, hour, minute, timeType, timeFormat, direction, directionErr, estPoint | Remaining trip and ETA |
| `updateTBTSubInfo2C(int x9)` | unit, tenths, whole, then zeros | HUD distance; whole number up to 4095, tenths only for km and mi |
| `updateTPTurnListNew(byte[])` | frame starting 0xF2 | HUD turn list (see section 4) |
| `updateTPTurnList(TurnList[])` | up to 3 turns: type, direction, traffic, no, name, exitNumber, distance, unit | Upcoming turns |
| `updateLaneInfo(byte[])` / `updateTPLaneInfo(LaneInformation)` | 81-byte frame starting 0xF1 | Lanes |
| `updateTPCrossroadName(CrossroadName)`, `updateTPFlexibleDestinationName(DestinationName)` | name strings | Road and destination text |
| `updateRemainDis(int, int, int, int)` | present in catalogue, not used by HMG-API's TBT code | Likely the remaining-distance variant (not verified) |

Notes:
- Clearing: HMG-API clears a turn with `updateTBTInfo(0, 0, 0, 0, -1, 0, 0, 0, -1)`, clears sub-info with distance 0 and err -1, blanks the HUD with a 1-byte 0xF2 frame and the lanes with an all-zero 0xF1 frame, and clears the destination with an empty name. **H**
- Unknown distance: HMG-API sends `barGraph = 254`, `barGraphErr = -2`. **H**
- Unknown icon with a known turn: when the icon is below 64 it substitutes type 2. **H** (meaning of 2 is not documented; treat as placeholder).

## 3. Icon (type) codes

Confidence **H** for the mapping as HMG-API sends it, **M** for what the cluster draws (the codes come from HMG-API's testing on a US EV6 and Ioniq 5 mapping; no public icon atlas was found).

Codes from HMG-API's Android Auto mapper (`AaTurns.setTurnType`, `setRampType`, `setRoundaboutType`). "dir" is the arrow angle sent with the code.

| Situation | Right (type / dir) | Left (type / dir) | Unspecified side |
|---|---|---|---|
| Straight, merge, name change (no side) | 65 / 0 | 65 / 0 | 65 / 0 |
| Slight turn | 66 / 30 | 71 / 330 | 64 / 0 |
| Turn | 67 / 90 | 70 / 270 | 64 / 0 |
| Sharp turn | 68 / 135 | 69 / 225 | 64 / 0 |
| U-turn | 73 / 270 | 72 / 270 | 72 (unspecified side is treated as left) |
| On-ramp | 173 / 30 | 174 / 330 | 172 / 0 |
| Off-ramp | 81 / 30 | 82 / 330 | 86 / 0 |
| Fork | 173 / 0 | 174 / 0 | (no change) |
| Ferry boat | 176 / 0 | | |
| Roundabout exit | 32 | 33 | 64 / 0 |
| Roundabout enter (angle-based, for right-hand traffic) | 96 (180), 97 (135-180, dir 45), 99 (90-135, dir 90), 100 (45-90, dir 135), 102 (0-45 or 315-360, dir 180), 103 (270-315, dir 225), 105 (225-270, dir 270), 106 (180-225, dir 315) | | |

Other points:
- **Icon 64** is the fallback "unknown / straight, no side" in HMG-API. It is what gets sent when the side is unknown. **H** for the code path, **M** for the cluster drawing. This fits the user's observation that 64 shows a loading state: it is probably a "no valid turn yet" icon rather than a real arrow. Test the hypothesis with a controlled send on a car only after an owner decision.
- Codes 81 to 176 include the ramp and ferry families; 2 is only a placeholder substitution (section 2).
- Depart and destination events are not in HMG-API's icon table. For the destination-type event (AA number 32, which HMG-API handles as a name event) it sends the destination name and sets distance to -1, so the turn is shown without a distance. **H** (exact event-to-case mapping only partly read)
- The user's partial code list (64 to 176) matches the ranges above. Codes 74 to 80, 84 to 95, 101, 104, 107 to 171, and 175 do not appear in any public source reviewed here. **M** (absence in sources, not proof).

## 4. HUD turn list frame (0xF2)

From `AaTurns.sendHudList` (HMG-API). **H** for what HMG-API sends; the cluster's reading of it is **M** (HMG-API reports it works on the EV6 HUD, per Leafia's "no HUD? HUD frames go unused" note).

- byte 0: 0xF2
- byte 1: 2 when there is a next icon, else 1
- byte 2: this turn's type (icon code)
- byte 3: the next turn's type (0 if none)
- byte 4: unused
- bytes 5 onward: road name in UTF-16LE, at most 78 bytes (total frame at most 83 bytes)
- Send only when type, next type or road name changed.

## 5. Lane frame (0xF1, 81 bytes)

From `AaTurns.sendLanes` and `CpTurns.lanes` (HMG-API). **H** for layout as sent; the cluster's rendering is **M**.

- byte 0: 0xF1
- byte 1: lane count n, at most 8
- bytes 3 to 10: one status byte per lane (k at offset 3+k). Value 1 normal, 2 highlighted (CarPlay path). In the Android Auto path this byte is the lane's type value from the library's hex string.
- bytes 19 to 42: per lane three arrow bytes at 19+3k, 20+3k, 21+3k
- other bytes zero

Input forms:
- Android Auto (`AaTurns`): a hex string. First 2 hex digits are lane count, then 8 hex digits per lane: type, then three arrow bytes. Sent only when the string changes. **H**
- CarPlay (`CpTurns.lanes`): iAP2 LaneGuidance (message 122), item 13/2 holds lanes; each lane has status and up to 3 angle values. **H**

## 6. Units

- `Cluster` unit constants: 0 = metres, 1 = km, 2 = miles, 3 = feet, 4 = yards. **H**
- AA library unit codes: 1 metres, 2 km, 3 km with one decimal, 4 miles, 5 miles with one decimal, 6 feet, 7 yards. **H**
- HMG-API converts to the owner's setting from the car's distance-unit setting (`Car.distanceUnit`: km, or miles). Where the phone's unit differs it converts to metres then to the car's unit. **H**
- An unknown unit maps to 15 in the code. Meaning not documented. **H** (code), unknown meaning.
- The float distance is in `unit`, not in metres; HUD distance uses whole and tenths split. **H**
- Interesting detail for us: HMG-API computes `distance = e3 / 1000.0` for unit km or miles, and for metres uses the raw metre count. Matches the AA callback's "display distance x1000" flag described in HMG-API's comments. **H**

## 7. Keep-alive, handshake, and what not to send

- Leafia's TBT_CALLS list (old generation) includes `updateOperationStatus` and `responseNaviAliveStatus`, and the Leafia UI treats them as required for the feature to be "ok". **H** (Leafia `docs/app.js`, which reads them from the car's API report).
- HMG-API deliberately **does not** send the cluster alive or operation status. Its comments say the GPM navigation's own manager "also claims the cluster (alive + status), which these cars never give back". **H** for the statement; the reason is project experience, not measured. The gap "does the cluster need a periodic keep-alive for TBT to stay visible" is **open**. No public source answers it.
- CarPlay path: HMG-API stops and restarts the phone's route guidance updates (iAP2 `stop_route_guidance`, then start) when the component state needs a full resend. **H**
- On source change (for example Apple Maps to Waze) HMG-API clears what the old app left and restarts guidance, so the phone resends everything. **H**
- Turn frames are sent from one handler thread in both paths. **H**
- The cluster calls are wrapped so a failed call never throws into the caller. **H**

## 8. CAN IDs

- HMG-API's M-CAN map (`docs/micom-map.md`) for relevant navigation-adjacent frames: **0x4ED Navigation_Precond_Status** (head unit to car: distance to charger, precondition flag). Other IDs there are for seats, park brake, speed, and steering wheel. **H**
- No public source lists CAN IDs for NavigationCluster TBT, distance, or lane frames on Gen5W, Ioniq 5, EV6, EV9, or GV60. Searches for NAVI/CLU messages in the public opendbc generic Hyundai/Kia DBC could not be fetched (HTTP 404 on the URL tried, not retried). **M** (absence).
- The cluster path in HMG-API goes through Java; treat any raw CAN injection for TBT as unknown and high risk. The CNR report (B1) describes CAN injection from the IVI, but its details could not be read (HTTP 403).

## 9. Gaps and what would settle them

1. The cluster's icon atlas for codes 64 to 176 (only HMG-API's send side is public). A capture of the cluster screen for each code on a car (owner-approved) would settle it.
2. Whether the cluster needs keep-alive or operation status for TBT to stay on. HMG-API's experience says no; not measured.
3. The `unit = 15` case and `updateRemainDis` argument meaning.
4. Whether `distance` for `updateTBTInfo` is in the unit or in metres on every firmware (HMG-API uses the unit flag; treat as unit-based).
5. Genesis and Hyundai variant coding: a Genesis forum thread (C1) suggests TBT is set in the navigation or head unit variant, not the cluster. Unverified; relevant if the feature is off on a non-US unit.

## 10. Source key

Upstream (local clone, read only): `tmp/HMG-API` (`core/runtime/java/hmgapi/hook/Cluster.java`, `docs/hook-points.md`, `docs/micom-map.md`, `docs/vehicle-commands.md`, `mods/wireless-carplay-aa/src/hmgapi/mods/wireless/AaTurns.java`, `.../dipo/CpTurns.java`), `tmp/HMG-Leafia-Plus` (`docs/app.js`, `docs/samples/ioniq5.txt`). Public: see `sources.md` items A1 to A11, C1, C3.
