# iAP2 RouteGuidanceManeuverUpdate (0x5202) ManeuverType (param 0x03, u8)
Source for every value: luka-dev/mib2q-carplay-rgi `hook/routeguidance/rgd_tlv.h:135-191` (@df73e061), verified there against MHI3 dio_manager `EManeuverType::toString` (0..53) and Apple Maps accNav enum decompile (kamgurgul `docs/re/ios/maps-maneuvers.md`); names equal Apple's public `CPManeuverType`. Valid 0..53; >=54 invalid; 255 = NotSet (Apple internal -1). Confidence H for 0..53, except ordering of 18/19/26/27 cross-checked only via Luka+kamgurgul (both agree) — same lineage.

| # | Name | # | Name |
|--|--|--|--|
|0|NoTurn|27|ArriveEndOfDirections|
|1|LeftTurn|28|RoundaboutExit1|
|2|RightTurn|29|RoundaboutExit2|
|3|StraightAhead|30..46|RoundaboutExit3..19 (n = value-27)|
|4|UTurn|47|SharpLeftTurn|
|5|FollowRoad|48|SharpRightTurn|
|6|EnterRoundabout|49|SlightLeftTurn|
|7|ExitRoundabout|50|SlightRightTurn|
|8|OffRamp|51|ChangeHighway|
|9|OnRamp|52|ChangeHighwayLeft|
|10|ArriveEndOfNavigation|53|ChangeHighwayRight|
|11|StartRoute|255|NotSet|
|12|ArriveAtDestination| | |
|13|KeepLeft| | |
|14|KeepRight| | |
|15|EnterFerry| | |
|16|ExitFerry| | |
|17|ChangeFerry| | |
|18|StartRouteWithUTurn| | |
|19|UTurnAtRoundabout| | |
|20|LeftTurnAtEnd| | |
|21|RightTurnAtEnd| | |
|22|HighwayOffRampLeft| | |
|23|HighwayOffRampRight| | |
|24|ArriveAtDestinationLeft| | |
|25|ArriveAtDestinationRight| | |
|26|UTurnWhenPossible| | |

Cross-check: Xamarin CPManeuver.h diff (iOS 13 era) shows NoTurn=0, LeftTurn=1, RightTurn=2, StraightAhead=3, UTurn=4, ArriveAtDestinationLeft=24 — consistent. CPC200 live capture: Right turn -> NaviManeuverType 2; roundabout exit1/2 -> 28/29 (consistent).

## Related enums (0x5202 / 0x5201)
- DrivingSide (0x5202 param 0x08, u8): 0 = right-hand traffic, 1 = left-hand traffic (Apple `CPManeuver _descriptionForTrafficSide:` @CarPlay 0x23d91c7e8: 0->"right", 1->"left"). H. CPC200 saw an undocumented `2` on its dongle path (dongle-side artifact). Optional TLV.
- JunctionType (param 0x09, u8): 0 = intersection, 1 = roundabout. Luka: iOS emits only 0 and 1 (decompiled `convertJunctionTypeForFunc` in MHI3). H/M.
- JunctionElementAngle (param 0x0A, repeated signed i16, degrees): angles of the *other* roads at the junction (role != 1,2 elements; role-1 skipped, role-2 = exit). Apple Maps pushes non-entry, non-exit elements here. H (kamgurgul Maps decompile).
- JunctionElementExitAngle (param 0x0B, signed i16, degrees, `NSUnitAngle.degrees`): angle of the exit road relative to entry; sign preserved by iOS (neg = left, pos = right in the signed convention used by Luka's mapper; 0 = straight). Optional: TLV is **absent** when Apple has no role-2 element — Apple never emits 1000. For GEO U-turn (accNav 4): Apple flips sign by driving side so ordinary U-turn is negative (left) for RHT, positive (right) for LHT. Types 18/26/86/88 forward their raw sign. iOS 27 accessoryd: `CFNumberGetValue(SInt16)` -> u16 TLV, no clamp. H.
  - Luka's *local* sentinels (not Apple): +1000/-1000 = "absent" in their pipeline; same 1000 value also appears in lane angles (see lane-guidance.md). Don't confuse with wire.
- DistanceUnits (0x5201 params 0x09/0x0C, 0x5202 param 0x07, u8): 0 km, 1 miles, 2 m, 3 yards, 4 ft (MHI3 type-info). Distances in params 0x07/0x0A/0x05 are raw meters (u32); display strings/units are separate params. H/M.
- RouteGuidanceState (0x5201 p0x01): 0 NoRouteSet, 1 RouteSet, 2 Arrived, 3 Loading, 4 Locating, 5 Rerouting, 6 ProceedToRoute. ManeuverState (p0x02) enum not recorded in sources.
- 0x5202 param 0x0D ExitInfo: string (highway exit label / number), only sent if accessory set SupportsExitInfo in 0x5200. 0x0C LinkedLaneGuidance: u16 index of the 0x5204 lane event for this maneuver.
- Apple GEO->accNav collapse: GEO 25->18, 35->26, 86/88->4 (Maps 26.6 table @0x101388934).
