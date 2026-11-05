# Round 2 sources, part 1: code repositories

Collected 2026-11-05. Depth-1 clones in `round2/clones/`. Line refs are to the pinned commit.

## S1. luka-dev/mib2q-carplay-rgi

- URL: https://github.com/luka-dev/mib2q-carplay-rgi
- Title (README H1): "MHI2Q CarPlay cluster integration"
- Pinned commit: `df73e061a29c4d996550716987f42a9243daa09a`, committed 2026-10-29
- Local path: `round2/clones/mib2q-carplay-rgi/`
- Coverage: iAP2 RGI TLV parsing (`hook/routeguidance/rgd_tlv.c`, `rgd_hook.c`), Java presentation (`java_patch/com/luka/carplay/rgd/`), BAP and HUD mapping, lane guidance, renderer. Docs in `docs/cluster-and-rgi/`. Target is MHI2Q Audi/VW HUD, not Hyundai/Kia.
- Key facts:
  - Full 54-type table with BAP codes: `docs/cluster-and-rgi/MANEUVER_MAPPING.md` lines 117-157.
  - Angle sentinels and presence rules: `MANEUVER_MAPPING.md` 58-81; `rgd_tlv.c:617-619`.
  - Junction-type gate: `ManeuverMapper.java:239-260` (NO_INFO when junctionType not 0/1).
  - Route-zero 800 ms grace, reroute has no deadline: `rgd_hook.c:33`, `:1122-1124`.
  - 0x5200 retry 2 s x5 then 30 s: `rgd_hook.c:40-42`, `:940-941`.
  - Units ignored in Java; distance fields assumed metres: `RouteGuidance.java:1043-1052`, `rgd_hook.c:727-729`.
  - Lane angle to BAP code table: `BAPBridge.java:1721-1760`.
  - Lanes precede the 0x5201 that activates them: `LANE_GUIDANCE.md` (decompiled iOS 27 CarKit).
- Confidence: H for TLV IDs and sentinels (decompile of accessoryd/Maps/CarPlay, cross-checked with code). M for metres assumption. M for junction-gate bug (code read, not executed).

## S2. kamgurgul/mib2q-carplay

- URL: https://github.com/kamgurgul/mib2q-carplay
- Title (README H1): "mib2q-carplay"
- Pinned commit: `5c460600c9205cdafa2c4fecd7e2210b8606aedb`, committed 2026-11-04 (last commit message: "Release 1.4: HW301 v1176/v1177 images and build markers")
- Local path: `round2/clones/mib2q-carplay/`
- Note: the brief said `docs/cluster-and-rgi/*`; this repo's doc tree is `docs/rgd/`. The `docs/cluster-and-rgi/` tree is in S1.
- Coverage: same iAP2 RGI stack family as S1 (forked or re-derived; `rgd_hook.c` differs from S1 but the debounce and retry constants match). Docs: `docs/rgd/rgd-tlv.md`, `maneuver-mapping.md`, `lane-guidance.md`, `rgd-activation.md`, `bap-fctids.md`, `bargraph-sync.md`. Also an AA (Android Auto) side in `java_patch/com/luka/carplay/aa/`.
- Key facts:
  - Type to BAP table 0-53: `docs/rgd/maneuver-mapping.md` lines 40-95.
  - Junction gate removed; cites GitHub issue #9 (Sygic omits JunctionType; arrow lost): `ManeuverMapper.java:169-174`, `maneuver-mapping.md` line 128.
  - Signed U-turn angle normalised by driving side; drivingSide fallback: `maneuver-mapping.md` 'Signed exit angle' section.
  - Activation rules: visible_in_app is a visibility flag, not route activity: `docs/rgd/rgd-activation.md`, `RouteGuidance.java:470-500`.
  - Presentation retry 500 ms: `rgd-activation.md` state diagram.
  - Lane pipeline and 0xFF unknowns: `docs/rgd/lane-guidance.md` 56-68.
- Confidence: H for doc claims that match code. M for the issue-9 narrative (cited, not re-tested).

## S3. f-io/LIVI

- URL: https://github.com/f-io/LIVI
- Title (README H1): "LIVI – Linux In-Vehicle Infotainment" (open-source CarPlay and Android Auto head unit in Rust with a Slint UI)
- Pinned commit: `e4ae798e09d6c51140c91816034294687238016e`, committed 2026-11-04
- Local path: `round2/clones/LIVI/`
- Licence per file header: GPL-3.0-or-later.
- Coverage: Rust. `native/crates/iap2-csm/src/messages/route_guidance.rs` (schema for 0x5200-0x5204). `native/crates/livi-runtime/src/events.rs` (event to JSON). `native/crates/livi-runtime/src/bringup.rs` (0x5200 at start). `native/crates/livi-aa-stack/src/channels/nav_maneuver.rs` (Android Auto turn events to iAP2 ManeuverType numbers). No cluster icon code, no lane arrow geometry, no retry logic found.
- Key facts:
  - Schema TLV IDs match S1 for 0x5201 (0-21), 0x5202 (0-13), 0x5204 (0-3): `route_guidance.rs`.
  - State 0 resets the whole navigation model immediately, no debounce: `events.rs:115-125`.
  - 0x5204 keyed by lane index, not maneuver index: `events.rs:209-217`.
  - Passes distance and units raw (`distanceToDestinationUnits`, `remainDistanceUnits`) to its UI: `events.rs:153-162`.
  - 0x5200 sent once at bring-up: `bringup.rs:121-127`.
- Confidence: H for what the code does. Not run. It is a passive JSON bridge, so it does not establish any cluster behaviour.

## Not re-read in this pass (round 1 covers them)

- `iap2/sources.md` items and `hkmc/` items were not re-read for this round, per brief.
- luka-dev `docs/cluster-and-rgi/THIRD_PARTY_NAV_APPS.md` (which apps fill which CPManeuver fields) was listed but not read. It is the most likely source for a fix to the junction-gate question and should be the next read.
- luka-dev `DISTANCE_BARGRAPH.md` was only grepped, not read end to end.
# Round 2 sources (part 2)

Format: URL | title | date | coverage | key facts | confidence.
"Fetched" = page read. "Snippet" = search result text only. "403" = blocked; secondary summary used.

## Korean community

1. https://damoang.net/car/99226 | "bmw id8 ios26 카플레이 hud?" | ~2025-10 (per snippet) | BMW iD8, iPhone iOS 26, Naver Map / KakaoNavi / Android Auto | 403 on fetch; snippet only. Author: CarPlay HUD shows road direction + lane highlight, not next-turn and distance; Android Auto shows turn-by-turn. Reply: start route in Apple Maps first. | Low-medium. Single user, unverified. Paywalled: no. Blocked: yes.

2. https://www.clien.net/service/board/cm_car/13815499 | "안드로이드 오토 or 카플레이를 쓰고 싶어요!" (Clien Q/A) | 2019-08-12 (fetched) | Kia Ray Turbo, CarPlay/Android Auto question. Search had labeled it as TMAP cluster content; it is not. | Fetched: no cluster/HUD content. Replies: Hyundai/Kia factory nav is Genie/Mappy, CarPlay "likely hard" (speculation). | Low. Off-topic for cluster.

3. https://www.clien.net/service/board/park/19000682 | Clien iOS 26 CarPlay board (per search) | ~2025 | TMAP CarPlay route detail limits | Not fetched. Snippet suggests no cluster content. | Unverified.

4. https://apps.apple.com/kr/app/tmap-%ED%8B%B0%EB%A7%B5/id431589174 | TMAP App Store listing (KR) | Release notes 10.16.0, date not shown | CarPlay rest-stop info, destination continuation, voice-search and volume issues | Snippet only. Nothing on cluster/HUD. | Medium for release note text; low for cluster relevance. Not paywalled.

## Western forums and vendor notes (for field inference only)

6. https://www.macrumors.com/2024/02/20/ios-17-4-beta-carplay-instrument-cluster/ | "iOS 17.4 Beta Adds CarPlay Option to Show Upcoming Maneuvers in Instrument Cluster" | 2024-02-20 (fetched) | Apple Maps only | Cluster shows upcoming maneuvers, optional. Named: BMW, Volvo, Polestar. No lane/junction detail. Third-party apps not mentioned. | High for what it says; article itself says "it appears" in places.

7. https://developer.apple.com/forums/thread/740100 | "HUD Turn-by-turn function by third-party apps" | Oct 2023 to Aug 2024 (fetched) | Apple Maps second video stream vs third-party apps | No Apple staff replies. Accepted answer (Apr 2024): CarPlay docs updated March 2024 to plan HUD services for third parties; no link. No field list. | Medium. Secondary reading of docs.

8. https://abrp.featurebase.app/p/turn-by-turn-directions-not-shown-on-head-up-display | "Turn-by-turn directions not shown on HUD when ABRP runs via CarPlay - BYD Atto 3 Evo" | Aug (year omitted, car is 2026 model; likely 2026) (fetched) | HUD via CarPlay, Apple Maps and Google Maps as controls | Apple Maps and Google Maps work on HUD; ABRP does not. Dev suspects missing maneuver/distance metadata; ABRP says works on VW ID.7. Kia EV9 GT-Line owner (Aug 28): works on iOS 26.5. Status: awaiting clarification. | Medium. Third-party app, not Korean.

9. https://www.macheforum.com/site/goto/post?id=133742 | "Instrument cluster navigation directions from CarPlay?" (Mach-E forum) | Date not seen | Mustang Mach-E: Waze, Apple Maps, Google Maps | Snippet only. Mixed results: some report no cluster directions via CarPlay; Android Auto works. Commenter: CarPlay may not supply cluster metadata. | Low-medium. Unverified; forum.

10. https://forums.macrumors.com/threads/apple-says-these-vehicle-brands-plan-to-offer-all-new-carplay-ultra.2457135/page-4 and https://macrumors.com/2026/04/18/apple-says-carplay-ultra-is-coming-to-these-vehicle-brands/ | Apple says CarPlay Ultra brands (Hyundai, Kia, Genesis among them) | 2026-04-18 (snippet) | Program status | Snippet only. Apple: Hyundai, Kia, Genesis "working toward" CarPlay Ultra; no model, trim, or date. Gurman (secondary): one major Hyundai/Kia model in H2 2026. | Medium for Apple's statement (via snippet); low for the H2 2026 report.

11. https://9to5mac.com/2026/05/04/carplay-ultra-automakers/ | CarPlay Ultra automakers | 2026-05-04 (snippet) | Program status | Snippet only. Aston Martin is the only brand live (US/Canada, May). | Medium, secondary.

12. https://genesisowners.com/genesis-forum/goto/post?id=609570 and .../609656 | Genesis owner threads on Apple Maps in cluster | Date not seen | Genesis (Hyundai group) | 403 on fetch. Snippet: owner says Apple Maps cluster matches center-screen map; another notes center screen shows route overview when cluster has directions. | Low. Blocked. Unverified.

13. https://genesisowners.com/genesis-forum/threads/wireless-carplay-coming-to-all-hyundai-models.47873/ | "Wireless CarPlay coming to all Hyundai models" | ~2023 (snippet) | Wireless CarPlay rollout | Snippet only. Forum claim, contradicted by official-sounding reporting below. | Low.

14. https://www.whichcar.com.au/?p=521905 | Whichcar: Hyundai wireless CarPlay | Date not seen (snippet) | EU/AU wireless rollout | Snippet only. EU update covers newer CCNC; current Hyundai/Kia/Genesis with 10.25 or 12.3 inch "standard-class Gen 5W navigation" not due for the wireless update. Kia AU says OTA on EV9. | Medium-low. Secondary, region-specific.

15. https://cartechstudio.com/blogs/apple-carplay/hyundai-tucson-apple-carplay | 2025 Tucson CarPlay guide (blog) | ~2025 (snippet) | Tucson ccNC, wireless CarPlay by nav package | Snippet only. Digital cluster shows turn-by-turn behind wheel (factory nav implied); HUD arrows; not clear if CarPlay. Wireless on 2022-2024 Tucson depends on nav. | Low. Blog, unverified.

16. https://www.macheforum.com/... and https://mracommunity.nodebb.com/post/68925 | Third-party app small map in cluster, no directions; Waze showed directions | Date not seen | Non-Hyundai | Snippet only. | Low. Unverified.

17. https://www.sateconomy.co.kr/news/view/179588829537572 | "카카오내비, 애플 카플레이 지원" (Kakao Navi CarPlay) | 2018-09-17 (fetched) | KakaoNavi CarPlay general | Night mode via car light sensor; background phone/music. No HUD, cluster, direction, or lane mention. | High for content; no field data.

18. https://v.daum.net/v/cRU2g8F5er?f=p | "[시승기] HUD에 카카오내비 경로 띄우는 혼다 어코드 하이브리드" | ~2021 (snippet) | KakaoNavi via Android Auto on Honda HUD | Snippet only. Some cars: left/right and exit-name not shown at intersections; "Guidance Unavailable" on failure. Honda, Android Auto. | Low-medium for field behavior analogy; not CarPlay.

19. https://apps.apple.com/kr/app/카카오내비-주차-발렛-전기차충전-세차-보험-중고차/id417698849 | KakaoNavi App Store listing | Undated | Feature listing | Snippet only. | Low relevance.

20. https://update.kia.com/US/KO/updateNoticeView/752 and https://update.hyundai.com/RU/KO/updateNoticeView/GkRBp4 | Kia/Hyundai navigation update notices (KO) | Undated | Factory nav updates | Not read; appeared in search. Possible lead for CarPlay/cluster notes. | Unverified.

21. https://www.manualslib.com/manual/3313904/Kia-Ev6-2024.html?page=210 | Kia EV6 2024 manual, "Features of your vehicle" | 2024 | Owner manual navigation/cluster | Not read; search result only. Lead for factory cluster TBT and CarPlay text. | Unverified.

22. https://developer.apple.com/forums/thread/744996 | "Instrument cluster don't appear in a car" | Undated | Developer | Not read. | Unverified.

## Not reached in this round

- Naver Cafe (아이오닉5 / EV6 / 제네시스 동호회): no indexed hits.
- Drive2, 4pda, XDA, Reddit (r/CarPlay, r/Ioniq5, r/KiaEV6, r/Hyundai): no hits in search.
- Paywalled: none found; 403 blocks noted above.
# Sources part 3 (round 2): other OEM and aftermarket CarPlay TBT implementations

Collected 2026-11-05. Confidence: H = read in source or primary doc; M = repo README, maintainer reply or secondary article read via fetch; L = search snippet or forum only. Round 1 sources are in `../iap2/sources.md` and `../hkmc/sources.md`, not repeated.

## Cloned repos (shallow, read in place under `clones/`)

| # | Source | Date | Coverage | Key facts | Conf |
|---|---|---|---|---|---|
| R1 | https://github.com/enigy/carlink, commit ba21dc7 (`clones/carlink`) | 2026-11-04 | Android CarPlay/AA receiver with cluster+HUD route guidance (GM focus) | `ManeuverMapper.kt` maps CPManeuverType 0..53 to Car App Library TYPE_*; `LaneGuidance.kt` and `LaneIconComposer.kt` render lanes (status 0/1/2, Apple angle convention); `OcbmNavigationFeed.kt`: 0x5201 ~1/s, 0x5202 per maneuver keyed by index; `DistanceFormatter.kt` imperial/metric rules. Inline comments in the mapper are mislabeled; code values are the reference. | H |
| R2 | https://github.com/WaylandAce/mib2q-carplay-rgi, commit 1832d6e (`clones/WaylandAce-mib2q-carplay-rgi`) | 2026-05-20 | Audi MHI2Q route guidance fork | README claims full HUD route guidance (icons, lanes, distance bar, ETA). Source not deep-read; treat as lineage of round 1 luka-dev. | M |
| R3 | https://github.com/maxim-smirnov/mhi2-carplay-cluster, commit 58fb958 (`clones/mhi2-carplay-cluster`) | 2026-10-17 | Audi MHI2Q cluster map + HUD maneuver overlay (MOST video renderer) | README: maneuver icons and lanes on HUD (FctID 23, 24); BAPBridge state machine; separate maneuver renderer process. Has `tools/audit_maneuver_parity.py` (not read). | M |
| R4 | https://github.com/tomasz-twardzicki/MHI2-Carplay-Maps, commit 5093715 (`clones/MHI2-Carplay-Maps`) | 2026-11-04 | VW MHI2 MU1367/MU1427 native guidance on AID | README: native maneuver arrows, distance to next turn, remaining distance and travel time on the cluster; guidance projected on HUD; guidance-reader fix in v1.4.3-e. | M |
| R5 | https://github.com/chefranov/mhi2-au37x-carplay, commit cf95ec5 (`clones/mhi2-au37x-carplay`) | 2026-11-02 | Audi VC map + MMI D-pad | Scope in README: CarPlay map and album art on VC; route guidance detail not extracted. | L |
| R6 | https://github.com/a78c7/touareg-cn-carplay-mods, commit 7ccb704 (`clones/touareg-cn-carplay-mods`) | 2026-11-01 | VW Touareg China (MH2p) CarPlay + HUD arrows + cluster map | Release v19.6 (fetched): wired CarPlay uses a different iAP2 stack (Cinemo over USB); nav hook only covered wireless until v19.6; `cl_probe.so` adds route-guidance component to identification and self-disables on rejection; keyframe requests every 2 s while waiting, 30 s otherwise. Binary zips under `releases/` not extracted. | M |

## Web sources (fetched or searched)

| # | Source | Date | Coverage | Key facts | Conf |
|---|---|---|---|---|---|
| W1 | https://github.com/shihabal3amri/DiPlay/issues/927 (fetched) | 2026-10-18 to 10-24 | BYD DiLink cluster lane arrows | Maintainer: iPhone sends lane data via iAP2 0x5204; DiPlay 0.2.21 did not declare lane capacity so no records. PR #1028 adds declaration. Reported cluster/HUD data has distance and turn code, no lane field; HUD has one icon slot. Car: BYD Song PLUS 2023 Champion, DiLink 3.0. | M |
| W2 | https://github.com/shihabal3amri/DiPlay/issues/728 (fetched) | 2026-10-13 | BYD Han cluster nav not showing | Maintainer: cluster arrows and distance need head-unit setting "HUD和仪表盘导航" (Settings > Navigation); CarPlay nav data already arrives (cites 0x5201 and 0x5202). Cluster map needs network ADB. Head unit DiLink 4.0. | M |
| W3 | https://www.macrumors.com/2024/02/20/ios-17-4-beta-carplay-instrument-cluster/ (fetched) | 2024-02-20 | Apple Maps maneuvers on instrument cluster | iOS 17.4 beta option to show upcoming maneuvers in cluster on supported vehicles (supplementary Maps view in driver display or HUD). BMW, Volvo, Polestar named. Third-party apps not named. | M |
| W4 | https://developer.apple.com/forums/thread/740100 (fetched) | 2023-10 (answer 2024-04) | Third-party HUD turn-by-turn | Only Apple Maps used "Instrument Cluster Video Stream 2" in simulator. OP's self-answer: March 2024 CarPlay docs reveal plans for HUD-area services for third-party developers. No Apple engineer reply, no API name. | M (existence), L (content) |
| W5 | https://github.com/tomasz-twardzicki/MHI2-Carplay-Maps/pull/2 (search snippet only) | n/d | Mu1427 route guidance PR | Title only seen in search; not fetched. | L |
| W6 | https://github.com/a78c7/touareg-cn-carplay-mods/releases/tag/v19.6 (fetched) | 2026-11-01 | As R6 | Same facts as R6; Cinemo wired stack; `wired_off` kill switch file; cluster log per-boot folders; `wide=2400` and `view=90/85` options off by default. Hook not verified on-car by us. | M |
| W7 | https://auto.sina.cn/2026-07-26/detail-inikcync0893495.d.html (snippet) | 2026-07-26 | ID.6 X CarPlay review | Search snippet only; not fetched. Not used for facts. | L |
| W8 | https://www.toutiao.com/topic/7485645804607146019/ (snippet, Amap cluster CarPlay) | 2024-08 (per snippet) | Amap CarPlay cluster support | Amap announced CarPlay cluster navigation; claims first domestic map to support it; iPhone 12+, iOS 17.4+. Promotional snippet. | L |
| W9 | https://abrp.featurebase.app/p/turn-by-turn-directions-not-shown-on-head-up-display (snippet) | n/d | BYD Atto 3 Evo, ABRP via CarPlay | Feature request: no HUD turn arrows for ABRP over CarPlay while Apple Maps and Google Maps do show them on same car. Suggests app-level metadata gap. | L |
| W10 | Mercedes forum and Reddit threads (search results only; no single canonical URL) | n/d | Mercedes NTG cluster | No Mercedes doc found. Reddit anecdote (2024 GLE wireless CarPlay shows arrows) only. | L |
| W11 | https://mracommunity.nodebb.com/topic/9926/full-myroute-app-carplay-map-integration-in-central-instrument-cluster (snippet) | n/d | VW MIB3 cluster map by third-party app | Community thread; not read. | L |
| W12 | https://www.macheforum.com/site/threads/instrument-cluster-navigation-directions-from-carplay.1953/ (snippet) | n/d | Mixed brands | Forum: CarPlay directions do not appear in cluster for many setups; Android Auto or built-in nav do. Anecdotal. | L |
| W13 | https://www.macrumors.com/review/2021-volkswagen-tiguan-carplay/ (snippet) | 2021 | VW Tiguan Digital Cockpit | Review: CarPlay Apple Maps guidance in cluster on SEL/R-Line trim; native nav lane guidance in cluster; CarPlay lane guidance not shown. | L |
| W14 | https://developers.google.com/maps/documentation/navigation/ios-sdk/carplay (snippet) | n/d | Google Maps SDK CarPlay | Only the CarPlay docs URL seen; not read. | L |
| W15 | https://linux.do/t/topic/2886983 (snippet) | n/d | Scam warning | Paid install services for free open-source CarPlay mods. Not technical. | L |

## Not found

- No BMW, Mercedes or Stellantis primary documentation on CarPlay to cluster route guidance was found.
- No CSDN or Baidu article on iAP2 RouteGuidance was found. Chinese keyword search returned mainly product pages and reviews. The Chinese-language projects that do exist are GitHub repos (R6, W2, W8).
- The carlink README and NOTICE were read only for context. The repo has no Carlinkit-specific NaviManeuverType table beyond the mapper.
