Status: current — external research on CarPlay route guidance and Hyundai/Kia cluster/HUD TBT, collected 2026-11-05 (rounds 1 and 2).

# External research: CarPlay route guidance -> cluster/HUD

Supports the turn-by-turn items in [backlog.md](backlog.md). Facts are third-party unless they say "our car"; verify on the car before relying on them. Do not copy third-party code into the repo (AGENTS.md licensing); cite path + commit. Firmware-side (stock) tables are in [cluster-tbt-icons.md](cluster-tbt-icons.md), [cluster-lane-frame.md](cluster-lane-frame.md), [cluster-lane-arrows.md](cluster-lane-arrows.md); our fixes in [carplay-tbt-learnings.md](carplay-tbt-learnings.md).

| File (`carplay-tbt-external/`) | Content |
| --- | --- |
| [iap2-protocol.md](carplay-tbt-external/iap2-protocol.md) | 0x5200-0x5204 parameter ids/types, 0x5204 lane layout, identification requirements, ManeuverType 0..53 (+255), DrivingSide, JunctionType, angle semantics, units, states |
| [mib2q-deep-dive.md](carplay-tbt-external/mib2q-deep-dive.md) | luka-dev / kamgurgul / LIVI: icon mapping, angle sentinels, lanes -> arrows, cadence, omitted fields, junction-gate bug, reroute debounce, 0x5200 retry |
| [apps-and-oems.md](carplay-tbt-external/apps-and-oems.md) | What Apple Maps / Google / Waze / AMap / NAVER / T map / Kakao send; carlink, MHI2/MIB2 forks, Touareg CN, BYD, other OEMs |
| [hkmc-cluster-tbt.md](carplay-tbt-external/hkmc-cluster-tbt.md) | HMG-API's `NavigationCluster` calls: signatures, send-side icon codes, 0xF2 HUD list, 0xF1 lane frame, units, keep-alive, CAN |
| [hkmc-extras.md](carplay-tbt-external/hkmc-extras.md) | Virtual driving sound and battery preconditioning notes |
| [sources.md](carplay-tbt-external/sources.md) | Annotated bibliography (all rounds), confidence per source, not-found list |

## Cross-cutting takeaways
- **Best public sources:** luka-dev/mib2q-carplay-rgi (@df73e061), kamgurgul/mib2q-carplay (@5c460600) (RE'd from iOS accessoryd/Maps, on-car logs on VW MIB2), f-io/LIVI (independent matching schema).
- **Omitted fields are normal** (Sygic omits JunctionType/ExitAngle; our car: NAVER never sends the exit angle). Type-based arrows are required and no junction gate may be reintroduced (kamgurgul issue #9).
- **Deltas:** messages carry only changed TLVs; keep previous values per key. Missing exit angle = unknown, lane angle 1000 = lane without direction.
- **Lane ident ids** 7/8 must be exact; our car already receives 0x5204, so the stock identification is fine; writing our own decoder is feasible (backlog item).
- **Korean apps:** no field-level evidence for TMAP/NAVER/Kakao; our own observation records are the best source.
- **Hyundai/Kia:** no public TBT CAN IDs; keep-alive need unresolved; no owner report of factory CarPlay TBT on the cluster.
