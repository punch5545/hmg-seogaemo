# Kia K8 Hybrid (GL3 HEV) KOR 260902

The head unit reported product `GL3HEV.KOR.0000.V039.001.260902`, onebin
`gl3hev_kr` and Android 4.4.2 (Telechips tcc803x). Its owner mapped the car
with HMG-Seogaemo 0.1.53 and sent the export of 2026-11-10 through
[issue #13](https://github.com/seogaemo/hmg-seogaemo/issues/13). The export
supplies all 18 firmware hashes; 17 of them occur in no other bundle.

The unit resolved 348 catalogue entries and its verifier reflected all 348
(`state=on source=generated mapped=348 verified=348 failed=0`). The bundle
keeps exactly those entries. All 348 names equal the Ioniq 5 KOR 260902
bundle, and the same 12 points were resolved by reference names.

Two optional points are not mapped, and no module requires them:

| Hookpoint | Reason |
| --- | --- |
| `themesettings.split_item.type` | no method matched on the unit, as on the Ioniq 5 KOR |
| `dipo.carplay.telephony` | added to the catalogue after this export was made |

The [evidence record](evidence.json) holds the owner's on-car reflection result
for the 348 points. Physical feature testing and revert checks are pending; the
owner separately reported a USB Wi-Fi adapter problem on this car
([issue #11](https://github.com/seogaemo/hmg-seogaemo/issues/11)).
