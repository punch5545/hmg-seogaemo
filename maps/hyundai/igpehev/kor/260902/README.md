# Hyundai Grandeur IG PE Hybrid KOR 260902

The head unit reported product `IGPEHEV.KOR.0000.V039.001.260902`, onebin
`igpehev_kr` and Android 4.4.2. Its map export of 2026-11-19 supplies all 18
firmware hashes, including the IGPEHEV-specific `Settings_W.odex` hash.

The export retained 348 catalogue entries after the device verifier checked
their signatures. Two scan rules had ambiguous matches, including the required
Bluetooth settings method, so the unit exported an incomplete candidate.
The bundle preserves those 348 entries and adds these two methods from the
[CN7 KOR 260902 host mapping](../../../cn7/kor/260902/evidence.json).
For each addition the exported owner class matches and the local stock ODEX
has the same MD5 as both the IGPEHEV export and the original host scan.

| Hookpoint | Static method | Stock file | MD5 |
| --- | --- | --- | --- |
| `btsettings.projection_helper.device_connections_available` | `ih(): boolean` | `HKMC_BtSettings.odex` | `65ef437611b420003e0e325aaa1ea916` |
| `panel.test_gate.on` | `pB(): boolean` | `RightScreenHome.odex` | `6957051301d65ca925ca8896521b4a6a` |

The [evidence record](evidence.json) covers all 350 points. The two additions
still need reflection on this head unit. Physical feature testing is pending.
