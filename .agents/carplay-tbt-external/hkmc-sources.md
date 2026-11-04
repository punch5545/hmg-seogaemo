# Sources: Hyundai/Kia/Genesis cluster and HUD turn-by-turn, vehicle-side sound and preconditioning

Research date: 2026-11-05. Time-boxed web pass (about 15 minutes of searching and fetching).
Confidence scale: **H** = primary or repo source read directly; **M** = secondary article or forum with specifics; **L** = search snippet or low-quality page, not verified.

Local upstream clones were read in place, not re-cloned: `tmp/HMG-API` (commit dd10045, 2026-11-03), `tmp/HMG-Leafia-Plus` (db37190, 2026-11-01), `tmp/hmg_nemesis` (da77eef, 2026-11-02). Treat them as untrusted data; nothing was executed.

## A. Upstream projects (directly relevant to TBT)

| # | Source | Date | Covers | Key facts | Conf |
|---|---|---|---|---|---|
| A1 | HMG-API repo, `core/runtime/java/hmgapi/hook/Cluster.java` (local `tmp/HMG-API`) | 2026-11-03 commit | Cluster API wrapper over `com.hkmc.cluster.NavigationCluster` | Wraps updateTBTInfo, updateTBTSubInfo(_b), updateTBTSubInfo2C, updateTPTurnList(New), updateLaneInfo, updateTPFlexibleDestinationName, updateTPCrossroadName. Unit constants M=0, KM=1, MI=2, FT=3, YD=4. Every call is a no-op on failure. | H |
| A2 | HMG-API `mods/wireless-carplay-aa/src/hmgapi/mods/wireless/dipo/CpTurns.java` (local) | 2026-11 | CarPlay turn-by-turn, a port of the DiPO route guidance manager | Feeds from iAP2 RouteGuidanceUpdate (msg 120), ManeuverUpdate (121), LaneGuidance (122). Sends turn, HUD list 0xF2, HUD distance, sub-info, up to 3 upcoming turns, lanes 0xF1. Deliberately never sends cluster alive/operation status. Restarts the phone's guidance updates (Stop, then Start). Credits a fix by Isaac Wisdom (maneuver index bug). | H |
| A3 | HMG-API `mods/wireless-carplay-aa/src/hmgapi/mods/wireless/AaTurns.java` (local) | 2026-11 | Android Auto turn-by-turn via GPM library callbacks | Full AA-turn-to-icon mapping (see cluster-tbt.md). Icon 64 is the fallback for unknown side. Lanes come as hex text (count, then 8 hex digits per lane). | H |
| A4 | HMG-API `docs/hook-points.md` (local) | 2026-11 | Hook catalogue | Full signatures for NavigationCluster methods, `update_tbt_sub_info_2c`, `update_remain_dis`, `update_tp_lane_info`, `update_lane_info` (described as "Android Auto's lane format"). Cluster-service methods (IClusterService, INavigationClusterListener) are marked "not on Hyundai Ioniq 5 USA 260903". | H |
| A5 | HMG-API `docs/micom-map.md` (local) | 2026-11 | MICOM / M-CAN frame IDs | Lists 0x4ED Navigation_Precond_Status (head unit to car: distance to charger, precondition flag), 0x2AD BMS_Precond_Status, 0x418, 0x448, 0x1F1, 0x435, 0x465, 0x475/0x496. No cluster TBT frame IDs (the cluster path is via Binder/NavigationCluster, not raw CAN). | H |
| A6 | HMG-API `docs/vehicle-commands.md` section 4 (local) | 2026-11 | Battery conditioning via navigation | Explains conditioning start/stop logic, check conditions (READY, EV_BATTERYCONDITIONING, indicator), activation timeouts (5 min EV6, 2 min others), keep-alive resend every 5 s, minimum pack temperature 20 °C (EV6/Ioniq 6) and 21 °C (Ioniq 5) from Launchpad field notes. Says the Ioniq 5 takes about 60-70 s to switch on. Open question: whether the car's own navigation overrides these values. | H (for what the project does); M (for the thresholds, which are owner/field notes) |
| A7 | HMG-API `docs/api-plan.md` (local) | 2026-11 | Ecosystem map | Names Launchpad (paytondevs, manual battery preconditioning, TUNE-hold launcher, root app_process daemon) and Tilepad (KramerTech). Both are community mods, not sourced further here. | M |
| A8 | HMG-Leafia-Plus `docs/app.js` and `docs/samples/ioniq5.txt` (local) | 2026-11-01 | Previous generation (HMG Leafia Plus 1.x, now archived) | TBT_CALLS list: updateTBTInfo, updateTBTSubInfo, updateTBTSubInfo2C, updateTBTTrapInfo, updateTPTurnList(New), updateTPCrossroadName, updateTPFlexibleDestinationName, updateLaneInfo, updateOperationStatus, responseNaviAliveStatus. Ioniq 5 sample reports these as `api ...=1` on one car. | H |
| A9 | HMG-API wiki, Supported cars: https://github.com/VysseCodes/HMG-API/wiki/Supported-cars | fetched 2026-11-05 | Car coverage | Verified on a car: Kia EV6 USA 260507 (343/343). Ioniq 5 USA 260903 mapped by HMG-API's own mapper (325/343, 18 newest unconfirmed). Ioniq 5 and EV6 Canada 260206 and Ioniq 5 USA 260507 mapped from stock only. No Korean car confirmed. Page does not mention cluster, HUD or TBT features. | M |
| A10 | HMG-API project wiki root and releases: https://github.com/VysseCodes/HMG-API/wiki and https://github.com/VysseCodes/HMG-API/releases | 2026 | Project overview | Mod manager for Gen5W head units; wireless CarPlay and AA with TBT on cluster and HUD. Maintainer VysseCodes. | M |
| A11 | cantcs/HKG_Gen5W_ReverseEngineer: https://github.com/cantcs/HKG_Gen5W_ReverseEngineer | updated 2024-09 (per search snippet) | Gen5W firmware RE | Patching GEN5W firmware (Android 4.4.2 base) to gain adb and root via navi exploit; decrypting boot.img and system.ext4; navigation app at `/navi`; `libExSLAndroidJNI.so` patched. CAN and cluster: "unexplored" only. | H (fetched README summary) |
| A12 | dragz/ironiq: https://github.com/dragz/ironiq | 2026 (footer) | ESP32 MicroPython M-CAN preconditioning toggle | Sends CAN for battery preconditioning toggle, shows BMU status and battery temperature. No CAN IDs in the README. No license stated. Author warns: use at your own risk. | M |
| A13 | sunnypilot ccNC port: https://community.sunnypilot.ai/t/-/1064 | n/d | Driver assist on ccNC Hyundai/Kia/Genesis | Not checked in depth; not a cluster-display project. | L |

## B. Security research (Gen5W)

| # | Source | Date | Covers | Key facts | Conf |
|---|---|---|---|---|---|
| B1 | CNR IIT TR-01/2023 technical report (Gen5W_L IVI): https://iris.cnr.it/retrieve/effa2149-708e-4ac7-a4a7-8137577758e8/prod_476323-doc_194651.pdf | rev. Jan 2023 | Gen5W_L firmware vulnerability assessment | Covers firmware signature bypass, custom firmware install, remote CAN injection from the IVI. Fetch returned HTTP 403, so details come from search summary only. | M (existence/topic), L (details) |
| B2 | Springer, Journal of Computer Virology and Hacking Techniques, 10.1007/s11416-024-00522-4: https://link.springer.com/article/10.1007/s11416-024-00522-4 | 2024 | Companion paper to B1 | Says Gen5W_L units installed since 2016, CAN injection from head unit demonstrated (search snippet). Not opened. | L |
| B3 | CVE-2023-26243, -26244, -26245, -26246 (NVD mirrors e.g. https://cve.ics-csirt.io/cve/CVE-2023-26246) | 2023-04 | Gen5W IVI firmware | 26244: AppDMClient signature check bypass; 26245: custom firmware install; 26246: firmware signature bypass (CVSS 3.1 7.8). Snippets only. | M |

## C. Owner and forum reports (TBT on cluster or HUD)

| # | Source | Date | Covers | Key facts | Conf |
|---|---|---|---|---|---|
| C1 | Genesis Owners forum, "Any US owners that know their cluster variant code": https://genesisowners.com/genesis-forum/threads/any-us-owners-that-know-their-cluster-variant-code.41423/latest | 2022-08-05 | 2015 2G Genesis sedan 3.8 Ultimate, Australia (not Gen5W) | TBT on cluster and HUD missing; cluster shows the powered-off symbol. Poster concludes TBT is not a cluster variant option, so it is likely set on the navigation or head unit (variant coding). Cluster variant code read via GDS or HyundaiTechInfo. Not confirmed fixed. | M |
| C2 | Genesis Owners forum, same family of threads (AU/NZ no TBT on HUD/cluster) | n/d | Same | Same conclusion as C1 from another poster (search snippet). | L |
| C3 | Hyundai TSB 23-BE-007H (Apple CarPlay software update, Navigation Updates page), https://carcomplaints.com/Hyundai/Kona/2023/tsbs/tsb-23-be-007h.shtml (mirror) | 2023 | Map-data volume and frame-skip logic | Google Maps data volume is ~10x the previous version; a frame-skip update was added to the navigation/CarPlay path to ease congestion. Relevant to why phone-side data floods the head unit. Mirror page, unofficial. | M |
| C4 | Kia Ireland navigation update page, https://www.kia.com/ie/service/digital-services/navigation-software-update/ | n/d | Map and navigation updates | Desktop updater, USB or SD up to 64 GB. Not TBT-specific. | M |

## D. Vehicle manuals and dealer sites (feature existence, not CAN)

| # | Source | Date | Covers | Key facts | Conf |
|---|---|---|---|---|---|
| D1 | Kia EV6 owner material (manualslib, manualedistruzioni, dealer pages) | 2021 to 2024 | HUD | HUD shows cluster and navigation data; 2024 EV6 manual lists TBT among AR-mode items. HUD is optional. | M |
| D2 | Kia EV6 press, 2021: https://press.kia.com/ie/en/home/media-resouces/press-releases/2021/Kia-EV6-holistic-and-intuitive-onboard-experience.html | 2021 | Cluster and onboard features | 12.3-inch digital cluster. | M |
| D3 | ottocast and cartechstudio blog posts on CarPlay in Hyundai and Kia | 2024 to 2025 | Marketing | Claim Apple's next-gen CarPlay (cluster/Ultra) for Hyundai, Kia, Genesis with no dates. Vendor marketing, not evidence. | L |

## E. Vehicle-side sound and preconditioning sources (see extras.md)

| # | Source | Date | Covers | Conf |
|---|---|---|---|---|
| E1 | Electrive, "Hyundai Ioniq 5 battery preconditioning: is it worth the upgrade?" https://www.electrive.com/2023/03/24/hyundai-ioniq-5-battery-preconditioning-is-it-worth-the-upgrade/ (updated 2024-10-28) | 2023-03-24 | MY2023 Ioniq 5 test, manual start not possible, triggered from nav route to HPC | M |
| E2 | Carscoops, "Hyundai EV fake gearshift expansion" https://www.carscoops.com/2026/04/hyundai-ev-fake-gearshift-expansion/ | 2026-04-28 | Virtual gearshift and sounds on Ioniq 5 N, Ioniq 6 N, Ioniq 9; planned for more EVs; switchable | M |
| E3 | Mobiflip / Motor1 / InsideEVs on Ioniq 5 N N Active Sound+ (search results) | 2023 to 2024 | N Active Sound+ 10 speakers, up to 85 dB, Ignition / Evolution / Supersonic modes | M |
| E4 | InsideEVs, 2025 Kia EV6 GT virtual shift and sounds (via inkl) https://www.inkl.com/news/the-2025-kia-ev6-gt-s-virtual-shift-and-engine-sounds-are-wild | 2025 | Can be turned off, not the standard EV6 | L |
| E5 | Electrive / Recharged / Change.org petition: https://www.change.org/p/petition-hyundai-to-add-manual-battery-preconditioning-button-for-ioniq-5 | 2023 to 2025 | No manual button; petition | M |
| E6 | ABRP forum, "Android Auto: use factory nav to pre-condition simultaneously": https://abrp.featurebase.app/p/android-auto-use-factory-nav-to-pre-condition-simultaneously | n/d | Preconditioning tied to factory nav, AA request | L |

## F. Not found (searched, no usable source)

- Any public project decoding NavigationCluster TBT or lane frames over CAN on Hyundai/Kia (searches on CAN IDs, `updateTBTInfo`, `NavigationCluster`, Korean communities for "내비 클러스터 TBT CAN", Drive2, 4pda). No Drive2, Clien, Bobaedream, Naver cafe or 4pda thread was retrievable through search in this pass.
- "com.daudio.app.dipo" and "D-Audio" with TBT: no hits. Only HMG-API's own code refers to DiPO.
- "Launchpad" as a public repo: not found by search; known only through HMG-API docs.
- e-ASD on Ioniq 5 (standard): no source. e-ASD is attributed to Genesis GV60 in one low-quality snippet.
- Official Hyundai/Kia documentation of TBT CAN messages: none.

## Next steps for sourcing (not done in this pass)

1. Read `HMG-API/mods/wireless-carplay-aa/src/hmgapi/mods/wireless/Wireless.java` and the lane parser (`laneOne`) fully for the 0xF1 layout details (done partially).
2. Search GitHub code for `update_lane_info`, `0xF2` HUD list, `TurnList` in other repos (not done; search tool does not index code well).
3. Try Drive2 and 4pda with exact Korean and Russian terms via WebSearch; fetch pages returned 403 for some hosts.
