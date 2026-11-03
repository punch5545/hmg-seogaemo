# HMG-API upstream tracking

HMG-Seogaemo's core mapper and hookpoint catalogue are independently implemented. Their behavior follows [VysseCodes/HMG-API](https://github.com/VysseCodes/HMG-API) at commit `4fab437`. Stable hookpoint IDs intentionally match HMG-API so the existing wireless projection hooks consume the published map. The owner supplied the NEEV KOR names and stock MD5s in the bundled map.

| Our component | HMG-API path at `4fab437` |
| --- | --- |
| `core/map/src/.../HmgSeogaemoDexIndex.java` | `core/runtime/java/hmgapi/engine/Dex.java` |
| `core/map/src/.../HmgSeogaemoJsonIO.java` | `core/runtime/java/hmgapi/engine/Json.java` |
| `core/map/src/.../HmgSeogaemoMapFiles.java`, `HmgSeogaemoMapTool.java` | `core/runtime/java/hmgapi/engine/CarMap.java`, `MapProbe.java` |
| `core/map/src/.../HmgSeogaemoMapScanner.java` | `core/runtime/java/hmgapi/engine/MapProbe.java` |
| `core/map/src/.../HmgSeogaemoMapVerifier.java` | `core/runtime/java/hmgapi/engine/MapCheck.java` |
| `tools/map-car.py` | `tools/map-car.py` |
| `tools/verify-map`, `tools/validate-maps.py` | `tools/test-hooks.py` |
| `tools/hmg-seogaemo-list-stock-files.py` | `core/hookpoints/*.json` |
| `tools/test-map-scanner.py`, `tools/test-map-car.py` | `tools/probe-test.py`, `tools/test-hooks.py` |
| `core/hookpoints/btsettings.json` | `core/hookpoints/btsettings.json` |
| `core/hookpoints/buttonsettings.json` | `core/hookpoints/buttonsettings.json` |
| `core/hookpoints/dipo.json` | `core/hookpoints/dipo.json` |
| `core/hookpoints/framework.json` | `core/hookpoints/framework.json` |
| `core/hookpoints/gpm.json` | `core/hookpoints/gpm.json` |
| `core/hookpoints/homesetting.json` | `core/hookpoints/homesetting.json` |
| `core/hookpoints/iap2.json` | `core/hookpoints/iap2.json` |
| `core/hookpoints/launcher.json` | `core/hookpoints/launcher.json` |
| `core/hookpoints/panel.json` | `core/hookpoints/panel.json` |
| `core/hookpoints/settings.json` | `core/hookpoints/settings.json` |
| `core/hookpoints/themesettings.json` | `core/hookpoints/themesettings.json` |

Run `sh tools/upstream-diff.sh [path/to/HMG-API]` after updating a local clone. Review the reported commits and changed paths, then translate relevant behavior into our schema and implementation. Keep point IDs stable. Re-run `make test`, `tools/verify-map`, and on-car mapping before accepting changed shape rules. Upstream changes are never copied automatically.
