# iAP2 route guidance wire format (0x5200-0x5204)

Sources: luka-dev `rgd_tlv.h` (@df73e061) + `docs/cluster-and-rgi/LANE_GUIDANCE.md`; LIVI `messages/route_guidance.rs` + `identification.rs` (@e4ae798e); kamgurgul `docs/rgd/rgd-tlv.md`, `docs/re/ios/maps-maneuvers.md`; see [sources.md](sources.md). Native-parser names from MHI3 libesoiap2 `processLaneGuidanceInformation/parseLaneInformation`. H unless marked.

## Messages

| ID | Name | Direction |
|--|--|--|
| 0x5200 | StartRouteGuidanceUpdates | acc -> phone |
| 0x5201 | RouteGuidanceUpdate | phone -> acc |
| 0x5202 | RouteGuidanceManeuverUpdate | phone -> acc |
| 0x5203 | StopRouteGuidanceUpdates | acc -> phone |
| 0x5204 | RouteGuidanceLaneGuidanceInformation | phone -> acc |

Frame: link `FF 5A`, session msg `40 40 <len BE16> <msgid BE16>`, params `<len BE16 incl. 4-byte header> <id BE16> <value>`; all big-endian.

### 0x5200 Start (params id: type)
0 ComponentID (u16, must match ident) · 1 SourceName (empty flag = "send it") · 2 SourceSupportsRouteGuidance (flag) · 3 SupportsExitInfo (flag; needed for 0x5202 p13) · 4 DestinationTimeZoneOffsetMinutes (flag; LIVI only, newer iOS). 0x5203 Stop: param 0 ComponentID (HSAE receiver quirk: stop id little-endian).
Hook must keep stock link seq/ack intact; a 0x5200 sent at the wrong time can be ignored or stall. Retry/injection behaviour: [mib2q-deep-dive.md](mib2q-deep-dive.md) section 8.

### 0x5201 RouteGuidanceUpdate
0 ComponentID u16 · 1 State u8 · 2 ManeuverState u8 · 3 CurrentRoadName str · 4 DestinationName str · 5 ETA u64 (absolute) · 6 TimeRemaining u64 (s) · 7 DistanceRemaining u32 (m) · 8 DistanceRemainingDisplay str · 9 ...DisplayUnits u8 · 10 DistToNextManeuver u32 (m) · 11 ...DisplayString · 12 ...DisplayUnits u8 · 13 ManeuverCurrentList (array of u16 indexes; element 0 = maneuver being approached) · 14 ManeuverCount u16 · 15 BeingShownInApp bool · 16 LaneGuidanceCurrentIndex u16 · 17 LaneGuidanceTotalCount u16 · 18 LaneGuidanceShowing bool · 19 SourceName str · 20 SourceSupportsRouteGuidance bool · 21 DestinationTimeZoneOffsetMinutes i16 · 22 StopType · 23 ChargingStationInfoList · 24/25/26 Arrival/Departure/FinalWaypoint BatteryLevel.
Decimal ids = Luka hex 0x15..0x1A for 21..26; Luka and LIVI use the same wire ids; 0x00 = ComponentID. Param 0x14 (20) SourceSupportsRouteGuidance is the app gate, see [apps-and-oems.md](apps-and-oems.md).

### 0x5202 RouteGuidanceManeuverUpdate
0 ComponentID · 1 Index u16 · 2 Description(InstructionText) str · 3 ManeuverType u8 · 4 AfterManeuverRoadName str · 5 DistanceBetweenManeuver u32 m · 6 ...DisplayString · 7 ...DisplayUnits u8 · 8 DrivingSide u8 · 9 JunctionType u8 · 10 JunctionElementAngle (repeated i16) · 11 JunctionElementExitAngle i16 · 12 LinkedLaneGuidanceInfo u16 (index of the 0x5204 event) · 13 ExitInfo str (highway exit label; only if accessory set SupportsExitInfo).
At route start iOS bursts the whole maneuver list (~20 msgs / 200 ms on CPC200) keyed by Index; 0x5201 ManeuverCurrentList selects the current ones.

### 0x5204 LaneGuidanceInformation
| param | name | type |
|--|--|--|
| 0x0000 | ComponentID | u16 |
| 0x0001 | LaneGuidanceIndex | u16 (matches 0x5201 p16 / 0x5202 p12) |
| 0x0002 | LaneInformations | **repeated nested group**, one per lane |
| 0x0003 | LaneGuidanceDescription | str, e.g. "Use the 2 left lanes" |

Nested LaneInformation (inside each 0x0002):
| sub-id | name | type |
|--|--|--|
| 0x0000 | LaneIndex / position | u16 |
| 0x0001 | LaneStatus | u8: 0 NotGood, 1 Good, 2 Preferred (best) |
| 0x0002 | LaneAngles | repeated i16 degrees (0 straight, neg left, pos right), up to ~16; one lane may allow several directions |
| 0x0003 | LaneAngleHighlight | i16; angle iOS highlights for the lane. 1000 = valid "none/unknown". Apple is inconsistent about whether the highlight is also in LaneAngles |

Behaviour (Luka decompile of CarKitNavigation `CRAccNavController sendLaneGuidances`, iOS 27):
- iOS sends a window of `MaxLaneGuidanceStorageCapacity` lane events from the current lane index, only events not yet sent; re-sent when currentLaneGuidanceIndex moves.
- The 0x5204s for a window arrive **before** the 0x5201 that makes one current.
- Lane data is independent of the maneuver and can appear/disappear within one maneuver.
- No lane widths, vehicle lane position or connectivity.
- Luka show-gate: `laneGuidanceShowing==1 && !(maneuverCount==0 && routeState<=0)`.

## Accessory identification (group 0x001E RouteGuidanceDisplayComponent)
Inner params (9 TLVs, ids 0-8): 0 Identifier u16 · 1 Name str · 2 MaxCurrentRoadNameLength u16 · 3 MaxDestinationNameLength · 4 MaxAfterManeuverRoadNameLength · 5 MaxManeuverDescriptionLength · 6 MaxGuidanceManeuverStorageCapacity · 7 MaxLaneGuidanceDescriptionLength · 8 MaxLaneGuidanceStorageCapacity.
- **Critical (Luka):** an earlier build put a spurious capability field at id 7, shifting lane ids to 8/9; "iOS accepted the identify but silently refused to send 0x5204". Declare lane description length (id 7) and lane storage capacity (id 8) at exactly those ids, non-zero. Same failure in DiPlay 0.2.21 (no declaration, zero 0x5204; fixed by PR #1028).
- Values: Luka advertises 0x0100 (256) road/destination lengths, lane storage capacity 6; parses 8 lanes x 16 angles. HSAE receiver config: ID 9, 30-char limits. harman-f example component id 1237.
- Ident must list 0x5200/0x5203 in MessagesSentByAccessory and 0x5201/0x5202/0x5204 in MessagesReceivedFromDevice (xcertplay catalog confirms directions). Component ids unique across ident groups (harman: Location 1234, VehicleInfo 1235, VehicleStatus 1236, RGI 1237).
- `RouteGuidanceDisplayComponent` is the only ident group needed. No public ident field `SupportsLaneGuidance` found; capability is implied by non-zero ids 7/8. Our car already receives 0x5204, so the stock identification is fine.
- RoadObjectDetection (0x0D00-0x0D02) is a separate sensor channel, not route guidance.

## ManeuverType (0x5202 p3, u8)
Source: luka-dev `hook/routeguidance/rgd_tlv.h:135-191` (@df73e061), verified against MHI3 dio_manager `EManeuverType::toString` (0..53) and Apple Maps accNav enum decompile (kamgurgul `docs/re/ios/maps-maneuvers.md`); names equal Apple's public `CPManeuverType`. Valid 0..53; >=54 invalid; 255 = NotSet (Apple internal -1). H for 0..53; ordering of 18/19/26/27 cross-checked only via Luka + kamgurgul (same lineage). carlink's names for 15..26 are a second source (not independently verified).

| # | Name | # | Name | # | Name |
|--|--|--|--|--|--|
| 0 | NoTurn | 10 | ArriveEndOfNavigation | 20 | LeftTurnAtEnd |
| 1 | LeftTurn | 11 | StartRoute | 21 | RightTurnAtEnd |
| 2 | RightTurn | 12 | ArriveAtDestination | 22 | HighwayOffRampLeft |
| 3 | StraightAhead | 13 | KeepLeft | 23 | HighwayOffRampRight |
| 4 | UTurn | 14 | KeepRight | 24 | ArriveAtDestinationLeft |
| 5 | FollowRoad | 15 | EnterFerry | 25 | ArriveAtDestinationRight |
| 6 | EnterRoundabout | 16 | ExitFerry | 26 | UTurnWhenPossible |
| 7 | ExitRoundabout | 17 | ChangeFerry | 27 | ArriveEndOfDirections |
| 8 | OffRamp | 18 | StartRouteWithUTurn | 28 | RoundaboutExit1 |
| 9 | OnRamp | 19 | UTurnAtRoundabout | 29 | RoundaboutExit2 |

30..46 = RoundaboutExit3..19 (n = value-27) · 47 SharpLeftTurn · 48 SharpRightTurn · 49 SlightLeftTurn · 50 SlightRightTurn · 51 ChangeHighway · 52 ChangeHighwayLeft · 53 ChangeHighwayRight · 255 NotSet.

Cross-checks: Xamarin CPManeuver.h diff (iOS 13 era, https://github.com/xamarin/xamarin-macios/wiki/CarPlay-iOS-xcode13.0-beta2) NoTurn=0, LeftTurn=1, RightTurn=2, StraightAhead=3, UTurn=4, ArriveAtDestinationLeft=24. CPC200 live capture: right turn -> NaviManeuverType 2; roundabout exit1/2 -> 28/29. Inline comments in carlink's Kotlin mapper are off by one; use code values.

## Related enums
- **DrivingSide** (0x5202 p8, u8): 0 right-hand traffic, 1 left-hand (Apple `CPManeuver _descriptionForTrafficSide:` @CarPlay 0x23d91c7e8). H. CPC200 saw an undocumented `2` (dongle artifact). Optional TLV.
- **JunctionType** (p9, u8): 0 intersection, 1 roundabout; iOS emits only 0/1 (MHI3 `convertJunctionTypeForFunc`). H/M.
- **JunctionElementAngle** (p10, repeated i16 deg): angles of the *other* roads (role != 1,2; role-1 skipped, role-2 = exit). H (kamgurgul Maps decompile).
- **JunctionElementExitAngle** (p11, i16 deg, `NSUnitAngle.degrees`): exit road relative to entry, sign preserved (neg left, pos right in Luka's mapper; 0 straight). **Absent** when Apple has no role-2 element; Apple never emits 1000. GEO U-turn (accNav 4): Apple flips sign by driving side (ordinary U-turn negative/left for RHT, positive/right for LHT); types 18/26/86/88 forward raw sign. iOS 27 accessoryd: `CFNumberGetValue(SInt16)` -> u16 TLV, no clamp. H.
  - Luka's *local* sentinels (+1000/-1000 = absent; same 1000 in lane angles) are not wire values.
- **DistanceUnits** (0x5201 p9/p12, 0x5202 p7, u8): 0 km, 1 miles, 2 m, 3 yards, 4 ft (MHI3 type-info). Distances in p7/p10 (0x5201) and p5 (0x5202) are raw metres (u32); display strings/units are separate params. H/M. (Different enum from the cluster unit codes, see [../cluster-tbt-icons.md](../cluster-tbt-icons.md).)
- **RouteGuidanceState** (0x5201 p1): 0 NoRouteSet, 1 RouteSet, 2 Arrived, 3 Loading, 4 Locating, 5 Rerouting, 6 ProceedToRoute. ManeuverState (p2) enum not recorded.
- Apple GEO->accNav collapse: GEO 25->18, 35->26, 86/88->4 (Maps 26.6 table @0x101388934).
