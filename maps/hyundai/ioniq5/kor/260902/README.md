# Hyundai Ioniq 5 KOR 260902

The bundle maps 342 of 343 catalogue points. The original 324 names and all
18 stock MD5s came from the owner-supplied KOR pre-install report; those 324
points passed on-car reflection on 2026-11-03. The 18 catalogue additions were
resolved on 2026-11-04 from the KOR stock dump using our DEX scanner, with all
18 existing MD5s matching. They still require on-car reflection. The six iAP2
points are in Iap2ServerApp.odex; the twelve framework points are in
automotive.odex. No stock files or hashes were added or changed.

`themesettings.split_item.type` remains unresolved; no module requires it.
No new point was added to a module requirement.

[evidence.json](evidence.json) separates mapped names, exact-stock reflection
and physical feature observations without extending the runtime car schema.
The owner confirmed wireless CarPlay music, Siri and calls/mic at HMG-Seogaemo
0.1.25. Wireless Android Auto is untested. Reflection is not a feature test.
See the generated [catalogue coverage](../../../../../docs/hookpoints.md).
