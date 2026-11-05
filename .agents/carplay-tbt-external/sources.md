# Sources: CarPlay route guidance, Hyundai/Kia cluster/HUD TBT, vehicle sound/preconditioning

Collected 2026-11-05 (rounds 1 and 2, time-boxed web passes). Confidence: H = decompiled Apple binary, on-car capture, repo/primary doc read directly; M = receiver-firmware RE, README, maintainer reply, secondary article, single source; L = forum/anecdote/search snippet only. "Fetched" = page read; "snippet" = search text only; "403" = blocked.
Local upstream clones (read in place, untrusted, nothing executed): `tmp/HMG-API` (dd10045, 2026-11-03), `tmp/HMG-Leafia-Plus` (db37190, 2026-11-01), `tmp/hmg_nemesis` (da77eef, 2026-11-02). Raw copies of key round-1 files in `clones/raw/`; round-2 depth-1 clones under `round2/clones/` (not in git). Pinned commits keep line refs valid.

## I. iAP2 wire format (RE'd from Apple binaries)
1. luka-dev/mib2q-carplay-rgi @ df73e061: `docs/cluster-and-rgi/IAP2_ROUTE_GUIDANCE_TLVS.md`, `hook/routeguidance/rgd_tlv.h`, `rgd_tlv.c` (MHI2Q Audi/VW hook, 2026). Ground truth: accessoryd 23G71 (iOS 26.6) `+[ACCNavigation* keyForType:]` decompile, MHI3 libesoiap2 / dio_manager type-info strings, on-car logs. All TLV ids 0x5200-0x5204, ManeuverType 0..53 (+255), units, driving side, junction types, lane sub-TLVs, angle sentinels, 0x5200 injection pitfalls, ident group 0x001E. H. Also `LANE_GUIDANCE.md`, `MANEUVER_MAPPING.md` (angles, U-turn sign rules), `THIRD_PARTY_NAV_APPS.md` + archive `docs/archive/internal-reference-2026-10-29/cluster-and-rgi/THIRD_PARTY_NAV_APPS.md` (which apps populate which CPManeuver fields; static IPA analysis, not car-tested except AMap owner note).
2. kamgurgul/mib2q-carplay @ 5c460600: `docs/rgd/rgd-tlv.md`, `docs/re/ios/maps-maneuvers.md` (Apple Maps accNav enum, signed ExitAngle, iOS 26.6 anchors), `dongle/hw301/FINDINGS.md` (dongle drops 0x5201/2/4 unless patched). H. Sibling/fork lineage of luka-dev.
3. f-io/LIVI @ e4ae798e: `native/crates/iap2-csm/src/messages/route_guidance.rs`, `identification.rs`: typed schema (matches Luka exactly; adds 0x5200 p4 and 0x5201 p21 as i16). M-H.
4. catplay-labs/catplay `core/catplay_csm/src/msg/iap2.rs`, fifthBro/mh2p-cluster, yuedizhibo/MHI2Q-CarPlay-AltScreen, Lanye-z/MHI2Q-CarPlay-MMI-Mirror: forks of #1 (same rgd_tlv.h); not independently verified.
5. harman-f/mhi2_altscreen_carplay `modules/navigation/source/src/common/mibr_rgi_wire.h`: independent small implementation; ident group 30 (=0x1E), component id 1237; builds 0x5200 (component id + empty flags for SourceName/SourceSupportsRouteGuidance/SupportsExitInfo) and 0x5203. M.
6. shilapi/xcertplay, shihabal3amri/DiPlay (`iap2/catalog/Iap2Endpoint.kt`, `Iap2IdentificationClient.kt`): Kotlin message catalog with directions (0x5200/0x5203 acc->phone; 0x5201/2/4 phone->acc). Thin. M.
7. 0312birdzhang/harbour-sailplay `NAVIGATION.md` (Oct 2026): sender side (Sailfish phone emulating iPhone to HSAE receiver); receiver `libiAP2.so` RE: 0x5200 p0 = BE16 component id, p1-3 empty flags; DrivingSide is 0x5202 p**8**; ipodconfig.xml `RouteGuidanceDisplayComponent` ID 9, 30-char limits; stop (0x5203) component id little-endian on this HSAE build. M.
8. lvalen91/CPC200-CCPA_resources @ a5831314 `documentation/02_Protocol_Reference/usb_protocol.md` (also abuharsky/carplay copy): CPC200/Carlinkit dongle RE (Feb-Mar 2026). Converts iAP2 route guidance to `NaviJSON` (MediaData 0x2A subtype 200): NaviStatus, NaviRoadName, NaviOrderType, NaviManeuverType (= CPManeuverType 0-53), NaviTurnAngle (an enum, NOT degrees), NaviTurnSide (0 RHD, 1 LHD), NaviRoundaboutExit (1-19), NaviRemainDistance. **Strips** JunctionType, JunctionElementAngle/ExitAngle, AfterManeuverRoadName and lanes. Live capture: 12 roundabouts all junction 0 / angle 0; iPhone sent 21 params per 0x5201; 0x5202 burst ~20 msgs/200 ms at route start; 0x5201 ~1 Hz. H for dongle behaviour.
9. https://developer.apple.com/documentation/carplay/cpmaneuvertype (iOS 17.4+): public case names match iAP2 order; raw values via CPManeuver.h (Xamarin diff https://github.com/xamarin/xamarin-macios/wiki/CarPlay-iOS-xcode13.0-beta2, old 0..~26). H names.
10. https://developer.apple.com/documentation/carplay/cpmaneuver, `cprouteinformation`: CPManeuver properties; cluster support iOS 16.4, HUD metadata iOS 17.4 (secondary summary).
11. https://www.oligo.security/blog/pwn-my-ride-exploring-the-carplay-attack-surface: iAP2 over Bluetooth framing for wireless CarPlay; not nav-specific. M.
12. https://damoang.net/car/99226 (BMW iD8, iOS 26 thread, ~2025-10; 403, snippet): NAVER Map shows only lane/road-direction on HUD; T map nothing; Android Auto full TBT; workaround Apple Maps first (also in K1). L.
13. https://www.clien.net/service/board/cm_car/18180597 (2023-07): app preference chatter, no HUD info; not useful.
14. https://www.sateconomy.co.kr/news/view/179588829537572 (Kakao Navi CarPlay 2018-09-17; fetched; night mode via light sensor, background phone/music, no HUD/cluster). https://speakerdeck.com/kakao/ifkakao24-53 (if(kakao)24 session mentions HUD/cluster as dev challenge; extent unknown). L.
- Searched, nothing: no public Wireshark dissector, no MFi spec text (NDA), no Chinese/Russian head-unit RE with 0x52xx tables; GitHub code search for `LaneGuidanceInformation`, `kIAP2RouteGuidance` empty; Luka/kamgurgul/LIVI is the whole public corpus found. Not searched: 4pda, XDA, Naver cafes, Baidu, Reddit.

## A. Upstream projects (Hyundai/Kia side)
| # | Source | Covers / key facts | Conf |
|---|---|---|---|
| A1 | `tmp/HMG-API` `core/runtime/java/hmgapi/hook/Cluster.java` | Wrapper over `com.hkmc.cluster.NavigationCluster`: updateTBTInfo, updateTBTSubInfo(_b), updateTBTSubInfo2C, updateTPTurnList(New), updateLaneInfo, updateTPFlexibleDestinationName, updateTPCrossroadName. Units M=0, KM=1, MI=2, FT=3, YD=4. Every call no-op on failure. | H |
| A2 | `.../mods/wireless-carplay-aa/.../dipo/CpTurns.java` | CarPlay TBT, port of the DiPO route guidance manager. Feeds from iAP2 RouteGuidanceUpdate (msg 120), ManeuverUpdate (121), LaneGuidance (122). Sends turn, HUD list 0xF2, HUD distance, sub-info, up to 3 upcoming turns, lanes 0xF1. Never sends cluster alive/operation status. Restarts phone guidance (Stop, Start). Credits Isaac Wisdom for the maneuver index fix. | H |
| A3 | `.../wireless/AaTurns.java` | Android Auto TBT via GPM library callbacks; full AA-turn-to-icon mapping ([hkmc-cluster-tbt.md](hkmc-cluster-tbt.md)); icon 64 fallback; lanes as hex text. | H |
| A4 | `docs/hook-points.md` | Signatures of NavigationCluster methods, `update_tbt_sub_info_2c`, `update_remain_dis`, `update_tp_lane_info`, `update_lane_info` ("Android Auto's lane format"). IClusterService / INavigationClusterListener "not on Hyundai Ioniq 5 USA 260903". | H |
| A5 | `docs/micom-map.md` | M-CAN: 0x4ED Navigation_Precond_Status, 0x2AD BMS_Precond_Status, 0x418, 0x448, 0x1F1, 0x435, 0x465, 0x475/0x496. No cluster TBT frame IDs. | H |
| A6 | `docs/vehicle-commands.md` section 4 | Battery conditioning via navigation: logic, checks (READY, EV_BATTERYCONDITIONING, indicator), timeouts (5 min EV6, 2 min others), 5 s keep-alive, min pack temp 20 C EV6/Ioniq 6, 21 C Ioniq 5 (Launchpad notes), Ioniq 5 ~60-70 s. Open: does car nav override. | H project / M thresholds |
| A7 | `docs/api-plan.md` | Names Launchpad (paytondevs, manual preconditioning, TUNE-hold launcher, root app_process daemon) and Tilepad (KramerTech); not sourced further. | M |
| A8 | `tmp/HMG-Leafia-Plus` `docs/app.js`, `docs/samples/ioniq5.txt` (2026-11-01) | Previous generation (Leafia Plus 1.x, archived). TBT_CALLS: updateTBTInfo, updateTBTSubInfo, updateTBTSubInfo2C, updateTBTTrapInfo, updateTPTurnList(New), updateTPCrossroadName, updateTPFlexibleDestinationName, updateLaneInfo, updateOperationStatus, responseNaviAliveStatus. Ioniq 5 sample reports `api ...=1`. | H |
| A9 | https://github.com/VysseCodes/HMG-API/wiki/Supported-cars (fetched 2026-11-05) | Verified: Kia EV6 USA 260507 (343/343). Ioniq 5 USA 260903 mapped by HMG-API's mapper (325/343, 18 newest unconfirmed). Ioniq 5/EV6 Canada 260206 and Ioniq 5 USA 260507 mapped from stock only. No Korean car confirmed. No cluster/HUD/TBT mention. | M |
| A10 | https://github.com/VysseCodes/HMG-API/wiki and /releases | Mod manager for Gen5W; wireless CarPlay/AA with TBT on cluster and HUD. Maintainer VysseCodes. | M |
| A11 | https://github.com/cantcs/HKG_Gen5W_ReverseEngineer (updated 2024-09) | Gen5W firmware RE (Android 4.4.2): adb/root via navi exploit, decrypting boot.img and system.ext4, navigation app at `/navi`, `libExSLAndroidJNI.so` patched. CAN/cluster "unexplored". | H |
| A12 | https://github.com/dragz/ironiq | ESP32 MicroPython M-CAN preconditioning toggle, BMU status, battery temperature; no CAN IDs in README; no license; use at own risk. | M |
| A13 | https://community.sunnypilot.ai/t/-/1064 | sunnypilot ccNC port (driver assist); not a cluster-display project; not checked in depth. | L |

## B. Security research (Gen5W)
| # | Source | Key facts | Conf |
|---|---|---|---|
| B1 | CNR IIT TR-01/2023 (Gen5W_L IVI), https://iris.cnr.it/retrieve/effa2149-708e-4ac7-a4a7-8137577758e8/prod_476323-doc_194651.pdf (rev. Jan 2023) | Firmware signature bypass, custom firmware install, remote CAN injection from the IVI. Fetch 403; summary only. | M topic, L details |
| B2 | Springer, J. Comput. Virol. Hack. Tech., 10.1007/s11416-024-00522-4 (2024) | Companion to B1; Gen5W_L units since 2016; CAN injection from HU demonstrated (snippet). Not opened. | L |
| B3 | CVE-2023-26243/-26244/-26245/-26246 (e.g. https://cve.ics-csirt.io/cve/CVE-2023-26246), 2023-04 | 26244 AppDMClient signature check bypass; 26245 custom firmware install; 26246 firmware signature bypass (CVSS 3.1 7.8). Snippets. | M |

## C. Owner/forum reports on cluster or HUD TBT
| # | Source | Key facts | Conf |
|---|---|---|---|
| C1 | Genesis Owners forum "Any US owners that know their cluster variant code" https://genesisowners.com/genesis-forum/threads/any-us-owners-that-know-their-cluster-variant-code.41423/latest (2022-08-05) | 2015 2G Genesis sedan 3.8 Ultimate, Australia (not Gen5W): TBT on cluster/HUD missing, cluster shows powered-off symbol; poster concludes TBT is a navigation/head-unit variant-coding option, not cluster. Cluster variant read via GDS/HyundaiTechInfo. Not confirmed fixed. | M |
| C2 | Same family of Genesis threads (AU/NZ no TBT on HUD/cluster) | Same conclusion (snippet). | L |
| C3 | Hyundai TSB 23-BE-007H (Apple CarPlay software update), mirror https://carcomplaints.com/Hyundai/Kona/2023/tsbs/tsb-23-be-007h.shtml (2023) | Google Maps data volume ~10x previous; frame-skip update added to navigation/CarPlay path. Unofficial mirror. | M |
| C4 | Kia Ireland navigation update page https://www.kia.com/ie/service/digital-services/navigation-software-update/ | Desktop updater, USB/SD up to 64 GB; not TBT-specific. | M |

## D. Manuals/dealer sites (feature existence)
- D1 Kia EV6 owner material (manualslib, manualedistruzioni, dealers, 2021-2024): HUD shows cluster and navigation data; 2024 EV6 manual lists TBT among AR-mode items; HUD optional. M.
- D2 Kia EV6 press 2021 https://press.kia.com/ie/en/home/media-resouces/press-releases/2021/Kia-EV6-holistic-and-intuitive-onboard-experience.html: 12.3-inch digital cluster. M.
- D3 ottocast, cartechstudio blogs (2024-2025): claim next-gen CarPlay for Hyundai/Kia/Genesis, no dates; vendor marketing. L.

## E. Vehicle-side sound and preconditioning ([hkmc-extras.md](hkmc-extras.md))
- E1 Electrive https://www.electrive.com/2023/03/24/hyundai-ioniq-5-battery-preconditioning-is-it-worth-the-upgrade/ (2023-03-24, upd. 2024-10-28): MY2023 test, no manual start, triggered from nav route to HPC. M.
- E2 Carscoops https://www.carscoops.com/2026/04/hyundai-ev-fake-gearshift-expansion/ (2026-04-28): virtual gearshift/sounds on Ioniq 5 N, Ioniq 6 N, Ioniq 9; planned for more EVs; switchable. M.
- E3 Mobiflip / Motor1 / InsideEVs on Ioniq 5 N N Active Sound+ (2023-2024): 10 speakers, up to 85 dB, Ignition/Evolution/Supersonic. M.
- E4 InsideEVs 2025 Kia EV6 GT via inkl https://www.inkl.com/news/the-2025-kia-ev6-gt-s-virtual-shift-and-engine-sounds-are-wild: can be turned off; GT only. L.
- E5 Electrive/Recharged/Change.org https://www.change.org/p/petition-hyundai-to-add-manual-battery-preconditioning-button-for-ioniq-5 (2023-2025): no manual button; petition. M.
- E6 ABRP forum https://abrp.featurebase.app/p/android-auto-use-factory-nav-to-pre-condition-simultaneously: preconditioning tied to factory nav. L.

## F. Not found (searched)
- Any public project decoding NavigationCluster TBT/lane frames over CAN on Hyundai/Kia (CAN IDs, `updateTBTInfo`, `NavigationCluster`, Korean "내비 클러스터 TBT CAN", Drive2, 4pda). No Drive2, Clien, Bobaedream, Naver cafe or 4pda thread retrievable.
- "com.daudio.app.dipo" / "D-Audio" with TBT (only HMG-API refers to DiPO); "Launchpad" as public repo; standard-Ioniq-5 e-ASD; official Hyundai/Kia TBT CAN documentation.
- No BMW, Mercedes or Stellantis primary docs on CarPlay-to-cluster guidance; no CSDN/Baidu article on iAP2 RouteGuidance (Chinese-language projects are GitHub repos R6, W2, W8). carlink README/NOTICE read for context only; no Carlinkit NaviManeuverType table beyond the mapper.
- Next sourcing steps (not done): read HMG-API `mods/wireless-carplay-aa/.../Wireless.java` and `laneOne` fully for 0xF1 layout; GitHub code search for `update_lane_info`, `0xF2` HUD list, `TurnList`; Drive2/4pda with exact Korean/Russian terms.

## K. Round 2: Korean community and Western forums
1. https://damoang.net/car/99226 "bmw id8 ios26 카플레이 hud?" (~2025-10): see I12. L-M.
2. https://www.clien.net/service/board/cm_car/13815499 (Clien Q/A, 2019-08-12, fetched): Kia Ray Turbo CarPlay/AA question; search had mislabeled it as TMAP cluster content; replies: factory nav is Genie/Mappy, CarPlay "likely hard" (speculation). L, off-topic.
3. https://www.clien.net/service/board/park/19000682 (iOS 26 CarPlay board, ~2025): TMAP CarPlay route detail limits; not fetched. Unverified.
4. https://apps.apple.com/kr/app/tmap-%ED%8B%B0%EB%A7%B5/id431589174 (TMAP KR): release notes 10.16.0 (CarPlay rest-stop info, destination continuation; voice-search/volume issues); snippet; nothing on cluster/HUD.
6. https://www.macrumors.com/2024/02/20/ios-17-4-beta-carplay-instrument-cluster/ (fetched): Apple Maps only; BMW, Volvo, Polestar; no lane/junction detail; article says "it appears" in places. High for what it says.
7. https://developer.apple.com/forums/thread/740100 (Oct 2023 - Aug 2024, fetched): no Apple staff replies; accepted answer (Apr 2024) says CarPlay docs updated March 2024 plan HUD services for third parties; no link, no field list. Medium.
8. https://abrp.featurebase.app/p/turn-by-turn-directions-not-shown-on-head-up-display (BYD Atto 3 Evo, Aug, likely 2026, fetched): Apple/Google Maps work on HUD, ABRP does not (works on VW ID.7); Kia EV9 GT-Line owner (Aug 28) works on iOS 26.5; awaiting clarification. Medium.
9. https://www.macheforum.com/site/goto/post?id=133742 and .../threads/instrument-cluster-navigation-directions-from-carplay.1953/ (Mach-E; snippets): mixed results; Android Auto works; CarPlay may lack cluster metadata. Low-medium. Also https://mracommunity.nodebb.com/post/68925 (third-party app small cluster map, no directions; Waze showed directions; non-Hyundai).
10. https://forums.macrumors.com/threads/apple-says-these-vehicle-brands-plan-to-offer-all-new-carplay-ultra.2457135/page-4 and https://macrumors.com/2026/04/18/apple-says-carplay-ultra-is-coming-to-these-vehicle-brands/ (2026-04-18 snippet): Hyundai, Kia, Genesis "working toward" CarPlay Ultra, no model/trim/date; Gurman (secondary): one major Hyundai/Kia model H2 2026. Medium / low.
11. https://9to5mac.com/2026/05/04/carplay-ultra-automakers/ (2026-05-04 snippet): Aston Martin only brand live (US/Canada, May). Medium, secondary.
12. https://genesisowners.com/genesis-forum/goto/post?id=609570 and .../609656 (403; snippet): Apple Maps in Genesis cluster matches center-screen map; center screen shows route overview when cluster has directions. Low.
13. https://genesisowners.com/genesis-forum/threads/wireless-carplay-coming-to-all-hyundai-models.47873/ (~2023 snippet): forum claim contradicted by K14. Low.
14. https://www.whichcar.com.au/?p=521905 (snippet): EU wireless update covers newer CCNC; 10.25/12.3-inch "standard-class Gen 5W navigation" not due for it; Kia AU says OTA on EV9. Medium-low, region-specific.
15. https://cartechstudio.com/blogs/apple-carplay/hyundai-tucson-apple-carplay (2025 Tucson ccNC; snippet): digital cluster shows TBT (factory nav implied), HUD arrows, unclear if CarPlay; wireless on 2022-2024 Tucson depends on nav. Low.
17. https://www.sateconomy.co.kr/news/view/179588829537572: see I14. High content, no field data.
18. https://v.daum.net/v/cRU2g8F5er?f=p "[시승기] HUD에 카카오내비 경로 띄우는 혼다 어코드 하이브리드" (~2021 snippet): KakaoNavi via Android Auto on Honda; some cars lack left/right and exit-name; "Guidance Unavailable". Low-medium; not CarPlay.
19. KakaoNavi App Store listing id417698849: low relevance.
20. https://update.kia.com/US/KO/updateNoticeView/752 and https://update.hyundai.com/RU/KO/updateNoticeView/GkRBp4 (navigation update notices): not read; possible lead for CarPlay/cluster notes.
21. https://www.manualslib.com/manual/3313904/Kia-Ev6-2024.html?page=210: not read; lead for factory cluster TBT/CarPlay text.
22. https://developer.apple.com/forums/thread/744996 "Instrument cluster don't appear in a car": not read.
- Not reached: Naver Cafe (아이오닉5/EV6/제네시스), Drive2, 4pda, XDA, Reddit (r/CarPlay, r/Ioniq5, r/KiaEV6, r/Hyundai). Paywalled: none; 403 blocks noted above.

## S. Round 2: code repositories (luka / kamgurgul / LIVI; analysis in [mib2q-deep-dive.md](mib2q-deep-dive.md))
- S1 luka-dev/mib2q-carplay-rgi, https://github.com/luka-dev/mib2q-carplay-rgi, README "MHI2Q CarPlay cluster integration", df73e061a29c4d996550716987f42a9243daa09a (2026-10-29), target MHI2Q Audi/VW HUD (not Hyundai/Kia). Key refs: 54-type table with BAP codes `MANEUVER_MAPPING.md` 117-157; angle sentinels `MANEUVER_MAPPING.md` 58-81, `rgd_tlv.c:617-619`; junction gate `ManeuverMapper.java:239-260`; route-zero grace `rgd_hook.c:33`, `:1122-1124`; 0x5200 retry `rgd_hook.c:40-42`, `:940-941`; units ignored `RouteGuidance.java:1043-1052`, `rgd_hook.c:727-729`; lane angle table `BAPBridge.java:1721-1760`; lanes precede 0x5201 `LANE_GUIDANCE.md` (iOS 27 CarKit). H for TLV ids/sentinels; M for metres assumption and junction-gate bug (code read, not executed).
- S2 kamgurgul/mib2q-carplay, "mib2q-carplay", 5c460600c9205cdafa2c4fecd7e2210b8606aedb (2026-11-04, "Release 1.4: HW301 v1176/v1177 images and build markers"). `rgd_hook.c` differs from S1; debounce/retry constants match. Docs `docs/rgd/`: `rgd-tlv.md`, `maneuver-mapping.md` (table 0-53 lines 40-95; issue-9 note line 128; 'Signed exit angle' section), `lane-guidance.md` (56-68 lane pipeline, 0xFF unknowns), `rgd-activation.md` (visible_in_app, 500 ms retry), `bap-fctids.md`, `bargraph-sync.md`; AA side in `java_patch/com/luka/carplay/aa/`. Junction gate removed: `ManeuverMapper.java:169-174`. H for doc claims matching code; M for issue-9 narrative.
- S3 f-io/LIVI, "LIVI – Linux In-Vehicle Infotainment" (Rust + Slint), e4ae798e09d6c51140c91816034294687238016e (2026-11-04), GPL-3.0-or-later. `route_guidance.rs`, `livi-runtime/src/events.rs`, `bringup.rs`, `livi-aa-stack/src/channels/nav_maneuver.rs`. No cluster icon code, lane geometry or retry logic. Passive JSON bridge; establishes no cluster behaviour. H for what code does; not run.

## R. Round 2: other OEM / aftermarket clones ([apps-and-oems.md](apps-and-oems.md) has commits and file lists)
- R1 enigy/carlink (ba21dc7, 2026-11-04), H.
- R2 WaylandAce/mib2q-carplay-rgi (1832d6e, 2026-05-20): README claims full HUD guidance (icons, lanes, distance bar, ETA); not deep-read. M.
- R3 maxim-smirnov/mhi2-carplay-cluster (58fb958, 2026-10-17): HUD icons/lanes (FctID 23, 24), BAPBridge state machine, separate maneuver renderer process. M.
- R4 tomasz-twardzicki/MHI2-Carplay-Maps (5093715, 2026-11-04): native arrows, distance, remaining distance/time on cluster; HUD projection; guidance-reader fix v1.4.3-e. M. PR #2 (Mu1427 route guidance) title only (snippet).
- R5 chefranov/mhi2-au37x-carplay (cf95ec5, 2026-11-02): Audi VC map + MMI D-pad. L.
- R6 a78c7/touareg-cn-carplay-mods (7ccb704, 2026-11-01); release https://github.com/a78c7/touareg-cn-carplay-mods/releases/tag/v19.6 (fetched). M.

## W. Round 2: web
- W1 https://github.com/shihabal3amri/DiPlay/issues/927 (2026-10-18..24, fetched): BYD Song PLUS 2023 Champion, DiLink 3.0; lane capacity not declared in 0.2.21; PR #1028. M.
- W2 https://github.com/shihabal3amri/DiPlay/issues/728 (2026-10-13, fetched): Han cluster nav needs "HUD和仪表盘导航" setting; DiLink 4.0; cluster map needs network ADB. M.
- W3 MacRumors 2024-02-20 = K6; W4 Apple forum 740100 = K7; W9 ABRP = K8.
- W7 https://auto.sina.cn/2026-07-26/detail-inikcync0893495.d.html (ID.6 X CarPlay review; snippet, unused); W8 https://www.toutiao.com/topic/7485645804607146019/ (Amap CarPlay cluster 2024-08; iPhone 12+, iOS 17.4+; promotional). L.
- W10 Mercedes forum/Reddit (no canonical URL; 2024 GLE anecdote). L.
- W11 https://mracommunity.nodebb.com/topic/9926/full-myroute-app-carplay-map-integration-in-central-instrument-cluster (VW MIB3 cluster map by third-party app; not read). L.
- W13 https://www.macrumors.com/review/2021-volkswagen-tiguan-carplay/ (snippet): Apple Maps guidance in cluster on SEL/R-Line; native nav lane guidance in cluster; CarPlay lane guidance not shown. L.
- W14 https://developers.google.com/maps/documentation/navigation/ios-sdk/carplay (URL only, not read). L.
- W15 https://linux.do/t/topic/2886983 (scam warning: paid installs of free open-source CarPlay mods; not technical). L.
- Not re-read in round 2: luka `THIRD_PARTY_NAV_APPS.md` was listed but not read in that pass (round 1 read it); `DISTANCE_BARGRAPH.md` only grepped.
