Source: car-mapping.md. Historical: upstream HMG-API mapping research and the superseded pre-core CarPlay map gate; not current state.

## Upstream HMG-API mapping research

**Report.** The 2026-11-02 pre-install report has `map.status=none`, `hooks_on=false`, but its `map_car` probe found 324 points,
12 `by_reference`, missing only `themesettings.split_item.type`
(`tmp/car-reports/car-report-neev-kor-260902-20261102-2115.json:109-133`); `Report.java:63-91` runs `MapProbe` on `/system`
plus HMG-API stock backups. It shows discovery, not that hooks were enabled.

**Algorithm.** `core/hookpoints/*.json` defines stable IDs, app package, stock files, and per-point `kind`, owning class (`of`),
`find` shape (package/class name, superclass, constructor signatures, field/method names and types, static flags,
parameter/return types, code string constants); named regex patterns capture obfuscated names from smali call patterns;
`@point` references depend on earlier resolution (`core/hookpoints/README.md:1-36`, `dipo.json:1-21`). Host `tools/map-car.py`
uses baksmali incl. regex/`only_caller`; zero or multiple candidates leave a point missing or fail `--strict` (`:1-22,205-262`).
On the unit Java `MapProbe` parses dex in `.odex`/`.jar` directly (not reflection, no baksmali), scans boot-classpath odex
and each catalogue app/library, requires one class/member per shape (`Dex.java:16-23,45-68`; `MapProbe.java:12-27,59-74,
97-159,186-287`); it cannot evaluate smali-only regex/`code` rules. `by_reference` tries names from other cars' maps and
keeps a candidate only if present with the remaining shape (`MapProbe.java:19-22,76-95,237-313`). `engine map-car --write`
creates a `probed` map needing `verify-map` (`MapProbe.java:328-390`; `CarMap.java:18-32,127-139`); `verify-map` uses
`PathClassLoader`/reflection without launching apps, writing `map-check.json` (`MapCheck.java:13-19,35-94,101-119`).
`probe-test.py` (`:1-7,37-75`) and `test-hooks.py` (`:1-18`) compare on-car/host mapper output and per-point stock trees.

**Name comparison.** All `map_car.points` entries vs every non-`car.json` file in `tmp/HMG-API/maps/kia/ev6/usa/260507/`:
324/324 identical, 0 different, 0 absent; the US map has the extra `themesettings.split_item.type`. So identical names
contributed (incl. the 12 fallback points) while 312 resolved from shapes. Only 1/18 stock-file MD5s matches the US map; all
four files HMG-Seogaemo's CarPlay module needs differ (`report:136-156`; US `car.json:12-31`). Equal names do not prove
binary compatibility.

**Time.** Upstream UI says "about a minute" and allows 600 s (`ReportTab.java:60-75`); no measured KOR duration. Host mapping
uses hundreds of MB of temp smali (`map-car.py:331-332`); unsuitable for the head unit. Telechips timing/memory must be
measured on the car.

**Pre-core CarPlay map gate (superseded).** The CarPlay module shipped a fixed 324-point `payload/data/car-map.json`; root precheck required
the exact product, `neev_kr`, Android 4.4.2 and four odex MD5s (`root/wireless-carplay-and-aa.sh:7-44,64-67`); zygote rechecked
them (`payload/src/hmgapi/hook/HmgApi.java.src:21-41`, `Points.java.src:31-55`). A change to another mapped library could evade
that four-file gate. Copying HMG-API's Java mapper into the shared runtime would have extended AGPL obligations there (`modules/wireless-carplay-and-aa/payload/NOTICE.md:3-15`), hence the independent core mapper.
