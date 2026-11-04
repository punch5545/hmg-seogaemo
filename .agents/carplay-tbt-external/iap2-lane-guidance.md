# Lane guidance (0x5204) + accessory identification requirements
Sources: luka-dev/mib2q-carplay-rgi `rgd_tlv.h` (@df73e061) + `docs/cluster-and-rgi/LANE_GUIDANCE.md`; LIVI `messages/route_guidance.rs`+`identification.rs` (@e4ae798e); kamgurgul `docs/rgd/rgd-tlv.md`. Native-parser names from MHI3 libesoiap2 `processLaneGuidanceInformation/parseLaneInformation`. H unless marked.

## Message IDs
0x5200 StartRouteGuidanceUpdates (acc->phone) · 0x5201 RouteGuidanceUpdate · 0x5202 RouteGuidanceManeuverUpdate · 0x5203 StopRouteGuidanceUpdates (acc->phone) · 0x5204 RouteGuidanceLaneGuidanceInformation (phone->acc). Frame: link `FF 5A`, session msg `40 40 <len BE16> <msgid BE16>`, params `<len BE16 incl 4-byte header> <id BE16> <value>`; all big-endian.

## 0x5200 Start (accessory sends) — params (id: type)
0 ComponentID (u16, must match ident) · 1 SourceName (empty flag = "send it") · 2 SourceSupportsRouteGuidance (flag) · 3 SupportsExitInfo (flag) · 4 DestinationTimeZoneOffsetMinutes (flag; LIVI only, newer iOS). 0x5203 Stop: param 0 ComponentID. (HSAE receiver quirk: stop id LE.)
Hook must keep stock link seq/ack intact; Luka injected 0x5200 via stock SendIAP2 and retries (2s x5, then 30s) until first 0x5201 — a 0x5200 can be ignored/stall if sent at the wrong time.

## 0x5201 RouteGuidanceUpdate params
0 ComponentID u16 · 1 State u8 · 2 ManeuverState u8 · 3 CurrentRoadName str · 4 DestinationName str · 5 ETA u64 (absolute) · 6 TimeRemaining u64 (s) · 7 DistanceRemaining u32 (m) · 8 DistanceRemainingDisplay str · 9 ...DisplayUnits u8 · 10 DistToNextManeuver u32 (m) · 11 ...DisplayString · 12 ...DisplayUnits u8 · 13 ManeuverCurrentList (array of u16 indexes) · 14 ManeuverCount u16 · 15 BeingShownInApp bool · 16 LaneGuidanceCurrentIndex u16 · 17 LaneGuidanceTotalCount u16 · 18 LaneGuidanceShowing bool · 19 SourceName str · 20 SourceSupportsRouteGuidance bool · 21 DestinationTimeZoneOffsetMinutes i16 · 22 StopType · 23 ChargingStationInfoList · 24/25/26 Arrival/Departure/FinalWaypoint BatteryLevel (decimal ids = Luka hex 0x15..0x1A; Luka and LIVI use the same wire ids, 0x00 = ComponentID).

## 0x5202 params
0 ComponentID · 1 Index u16 · 2 Description(InstructionText) str · 3 ManeuverType u8 · 4 AfterManeuverRoadName str · 5 DistanceBetweenManeuver u32 m · 6 ...DisplayString · 7 ...DisplayUnits u8 · 8 DrivingSide u8 · 9 JunctionType u8 · 10 JunctionElementAngle (repeated i16) · 11 JunctionElementExitAngle i16 · 12 LinkedLaneGuidanceInfo u16 · 13 ExitInfo str. At route start iOS bursts the whole maneuver list (~20 msgs/200 ms observed on CPC200) with Index; 0x5201 ManeuverCurrentList selects which are current.

## 0x5204 LaneGuidanceInformation
| param | name | type |
|--|--|--|
|0x0000|ComponentID|u16|
|0x0001|LaneGuidanceIndex|u16 (matches 0x5201 p16 / 0x5202 p12)|
|0x0002|LaneInformations|**repeated nested group** (one per lane)|
|0x0003|LaneGuidanceDescription|str, e.g. "Use the 2 left lanes"|

Nested LaneInformation (inside each 0x0002):
| sub-id | name | type |
|--|--|--|
|0x0000|LaneIndex / position|u16|
|0x0001|LaneStatus|u8: 0 NotGood, 1 Good, 2 Preferred(best)|
|0x0002|LaneAngles|repeated i16 degrees (0 = straight, neg left, pos right), up to ~16; one lane may allow several directions|
|0x0003|LaneAngleHighlight|i16 degrees; the angle iOS highlights for this lane. 1000 is a valid "none/unknown" value. Apple is inconsistent about whether highlight is also in LaneAngles|

Behaviour (Luka decompile of CarKitNavigation `CRAccNavController sendLaneGuidances`, iOS 27): iOS sends a window of `MaxLaneGuidanceStorageCapacity` lane events starting at current lane index, only events not yet sent, re-sent when currentLaneGuidanceIndex moves; the 0x5204s for a window arrive **before** the 0x5201 that makes one current. Lane data is independent of the maneuver and can appear/disappear inside one maneuver. No lane widths, no vehicle lane position, no connectivity.
Show-gate used by Luka: `laneGuidanceShowing==1 && !(maneuverCount==0 && routeState<=0)`.

## Accessory identification (IdentificationInformation group 0x001E RouteGuidanceDisplayComponent)
Inner params: 0 Identifier u16 · 1 Name str · 2 MaxCurrentRoadNameLength u16 · 3 MaxDestinationNameLength · 4 MaxAfterManeuverRoadNameLength · 5 MaxManeuverDescriptionLength · 6 MaxGuidanceManeuverStorageCapacity · 7 MaxLaneGuidanceDescriptionLength · 8 MaxLaneGuidanceStorageCapacity. (9 TLVs, ids 0-8.)
- **Critical (Luka):** an earlier build put a spurious capability field at id 7 which shifted the lane ids to 8/9: "iOS accepted the identify but silently refused to send 0x5204 LaneGuidance". Lane capacity fields at wrong IDs => no lane messages, no error. So declare lane description length (id 7) and lane storage capacity (id 8) at exactly those ids and non-zero.
- Values used: Luka advertises 0x0100 (256) road/destination lengths; lane storage capacity 6; 8 lanes x 16 angles are parsed. HSAE receiver config: ID 9, 30-char limits. harman-f example component id 1237.
- Also the ident must list 0x5200/0x5203 in MessagesSentByAccessory and 0x5201/0x5202/0x5204 in MessagesReceivedFromDevice (xcertplay catalog directions confirm). Component ids must be unique across ident groups (harman: Location 1234, VehicleInfo 1235, VehicleStatus 1236, RGI 1237).
- `SupportsExitInfo` is requested via 0x5200 p3 (not in ident) to get 0x5202 p13 ExitInfo. `RouteGuidanceDisplayComponent` is the only ident group needed; a (undocumented-here) newer iOS accepts 0x5200 p4 timezone flag.
- NOT FOUND: any public listing of an ident field named `SupportsLaneGuidance`; capability is implied by non-zero lane capacity ids 7/8.
- RoadObjectDetection (0x0D00-0x0D02) is a separate sensor-input channel, not route guidance (Luka docs).
