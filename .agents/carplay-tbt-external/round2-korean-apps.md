# Round 2: Korean nav apps and CarPlay route guidance on cluster/HUD

Scope: TMAP, Naver Map, KakaoNavi. Research date 2026-11-05. Time-boxed.
Sources and per-source confidence: `sources-part2.md` in this folder.

## Bottom line

- **No source in this round gives the CarPlay route-guidance fields** (exit angle, lane guidance, maneuver type, junction type) for TMAP, Naver Map, or KakaoNavi. Field-level evidence exists only for Apple Maps (developer forum and simulator observations, unconfirmed by Apple).
- **Naver Map:** one secondhand user report (damoang, BMW iD8, iOS 26, 2025-10) says CarPlay HUD shows only the road-direction arrow and lane highlight, not the next-turn arrow or distance. Android Auto on the same car showed full turn-by-turn. Workaround reported: start a route in Apple Maps first, then the other app. Single user, unverified.
- **KakaoNavi:** the only fetched article (2018-09-17) covers CarPlay support in general and says nothing about HUD, cluster, direction, or lane output. A 2021 Honda Android Auto test (snippet only) reported missing left/right and exit-name display on some cars and "Guidance Unavailable" on recognition failure. Not CarPlay, not Hyundai.
- **TMAP:** App Store release notes (10.16.0) mention CarPlay rest-stop info and destination continuation. Nothing about cluster or HUD. Voice and volume issues are listed. Snippet only.
- **Hyundai/Kia/Genesis:** no owner report found of CarPlay turn-by-turn on the factory cluster. Factory navigation does drive cluster turn info (Santa Fe, 2025 Tucson guide, both secondhand). Apple says Hyundai, Kia, and Genesis are "working toward" CarPlay Ultra, with no model, trim, or date named. Reported first Hyundai/Kia CarPlay Ultra model: H2 2026 (Gurman via secondary press). Unverified.
- **Variant coding / model-year behavior:** nothing found. Wireless CarPlay availability on Gen5W standard nav (10.25 and 12.3 inch) is reported as not in the EU wireless update (Whichcar, snippet). Treat as unverified.

## App notes

### TMAP (티맵)
- Evidence: App Store listing (kr), snippet only. Release notes for CarPlay 10.16.0 (rest-stop info, destination continuation). Voice-search and volume issues listed as known.
- Clien board (cm_car/13815499) that the search returned: fetched. It is actually a 2019 Kia Ray question about CarPlay, not a TMAP cluster post. No cluster or HUD content.
- Hyundai/Kia rumor (Clien 2019 replies): factory nav is "Genie/Mappy" based, so CarPlay is "likely hard". Personal speculation, not verified.
- Fields sent to cluster: **unknown**.

### Naver Map (네이버지도)
- Evidence: damoang post on BMW iD8 with iOS 26 (snippet from search; direct fetch returned 403). User says Naver Map and KakaoNavi "support CarPlay HUD" per their settings, but HUD showed direction-to-road and lane highlight only.
- Search for Naver's own CarPlay HUD notes: nothing found.
- Fields sent to cluster: **unknown**. Reported: lane highlight appears; next-turn arrow and distance do not (one user, one car).

### KakaoNavi (카카오내비)
- Evidence: 2018-09-17 article (fetched) on CarPlay support: night mode via car light sensor, background phone and music. No HUD or cluster mention.
- Damoang and search snippets: user reports that KakaoNavi HUD over CarPlay did not show on an iOS 26 BMW. Workaround same as Naver (Apple Maps first).
- Fields sent to cluster: **unknown**.

## Cross-app observations (non-Korean, for field inference only)

- Apple Maps: iOS 17.4 "instrument cluster experience" shows upcoming maneuvers (MacRumors, fetched). Named first adopters: BMW, Volvo, Polestar. Lane guidance and junction types are not described.
- Apple dev forum (thread 740100, fetched): Apple Maps uses a second instrument-cluster video stream; third parties do not get it. Apr 2024 reply says March 2024 CarPlay docs plan HUD services for third parties. No field list, no Apple staff reply on the page.
- BYD Atto 3 Evo (ABRP thread, fetched): Apple Maps and Google Maps show HUD turn-by-turn via CarPlay on that car; ABRP does not. Developer speculates ABRP lacks maneuver and distance metadata. Kia EV9 GT-Line owner reports CarPlay HUD works on iOS 26.5. Unverified.
- Waze 5.4.0 (snippet): cluster turn-by-turn via CarPlay on BMW, Polestar, Ford reportedly; BMW HUD still does not show Waze. Hyundai/Kia not named.
- Mach-E forum (snippet): Waze and Apple Maps cluster directions reported absent; Google Maps once worked then stopped. Commenter suggests CarPlay may not carry the extra cluster metadata. Android Auto reportedly shows directions in cluster.

## Gaps (not covered within time box)

- Naver Cafe (아이오닉5, EV6, 제네시스 동호회): search returned no indexed cafe posts.
- Drive2, 4pda, XDA: no hits.
- Reddit (r/CarPlay, r/Ioniq5, r/KiaEV6, r/Hyundai): no Reddit thread surfaced by search; no Hyundai/Kia-specific thread found.
- Damoang: the key thread returned 403; content taken from search snippet only.
- Clien: only one board fetched, and it was off-topic. The iOS 26 CarPlay board (park/19000682) was not fetched.
- Hyundai/Kia official update notices (update.kia.com 752, update.hyundai.com RU/KO notice) appeared in search but were not read.

## Next steps

1. Log in-car or read Naver Cafe directly for Ioniq 5 / EV6 CarPlay HUD posts with iOS version and app version.
2. Test on car (owner): each app (TMAP, Naver, Kakao) with Apple Maps as control, cluster on, record which maneuver and distance fields reach the cluster. This is the only route to field-level facts.
3. Obtain the Apple CarPlay "instrument cluster" documentation (developer portal, March 2024 update) directly; it is the source for field names. Not read here.
