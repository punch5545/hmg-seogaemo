# Which nav apps send which iAP2 route-guidance fields
Mechanism (Luka `THIRD_PARTY_NAV_APPS.md`, iOS 26.6 CarPlay.framework decompile, H): the *app never writes iAP2*. A CarPlay app calls `CPMapTemplate startNavigationSessionForTrip:` + fills `CPNavigationSession` maneuvers; CarPlay.framework serializes `CPManeuver` (13 params: componentID, index, instructionVariants, maneuverType, roadFollowingManeuverVariants, initialDistance(+display), trafficSide, junctionType, junctionElementAngles, junctionExitAngle, linkedLaneGuidanceIndex, highwayExitLabel) and `CPRouteGuidance` (22 params) into 0x5201/0x5202. Anything not set by the app is simply absent. Images are never sent (cluster draws its own icon). **Gate:** 0x5201 p0x14 SourceSupportsRouteGuidance = whether the app's CPMapTemplateDelegate implements `mapTemplateShouldProvideNavigationMetadata:` returning YES.
Lane guidance needs the app to create `CPLaneGuidance` (iOS 17.4+ API) -> 0x5204.

| App | Gate (0x14) | maneuverType | angles/junction | lanes (0x5204) | Source / confidence |
|--|--|--|--|--|--|
|Apple Maps|1|yes (full 0..53)|yes: signed exit angle (role-2 element), junction elements, driving side; U-turn sign normalised by side|yes (as 0x5204 events + linked index)|Luka/kamgurgul Maps 26.6 decompile — H|
|Google Maps|1 (reported "full RGI")|set|not analysed|not analysed|Luka compat table, "existing integration and app analysis" — M|
|Waze 5.23.0.1 / 5.24.0.1|**0** (no delegate selector)|never set|never|never builds CPLaneGuidance (draws lane strip as bitmap `junctionImage`)|Luka IPA static analysis — H for those builds; no cluster/HUD data at all. Roundabout exit number is rendered to a bitmap, not `roundaboutExitN`|
|AMap (Autonavi) 16.25.0 / iOS 26.6|1 (3 impls)|**never set** (typeless maneuvers)|—|—|static; BUT owner confirmed (2026-09-25) recent AMap + iOS with in-app CarPlay guidance setting works on the cluster/HUD — M|
|NAVER Map|unknown|unknown|unknown|unknown|Only forum report: HUD shows just road/lane direction, not turn+distance (damoang snippet, BMW iOS 26) — L|
|T map|unknown|unknown|unknown|unknown|Forum: HUD shows nothing (same thread) — L|
|Kakao Navi|unknown|unknown|unknown|unknown|CarPlay supported since 2018; cluster/HUD extent unknown — L|
|Workaround reported by Korean users|Start Apple Maps guidance first, then run T map/NAVER in front: HUD keeps Apple Maps' data. Plausible since the metadata gate/session is owned per navigation session; unverified here. L|

## Practical implications for our unit
- Don't assume ManeuverType/angles present: an app can be gate=1 yet typeless (old AMap). Handle NotSet(255)/missing types and fall back to distance+road text.
- Absence of 0x0B (exit angle) is normal; absence of lane messages is normal for any app but Apple Maps (and maybe Google).
- Dongle-style relays (CPC200) strip junction/angles/lanes — if a head unit is fed through such a bridge you will see type+side+roundabout exit only.
- Test with Apple Maps first (richest) to separate wire/parse problems from app omission.
