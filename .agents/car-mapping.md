Status: current — core mapping implemented off-car (2026-11-03); v1.0.1 catalogue additions and verification migration (2026-11-04). On-car timing/reflection for the additions unverified.

# Car mapping: NEEV.KOR.260902

Upstream citations are under `tmp/HMG-API`; the private Car Report is never committed. Operator workflow:
`docs/mapping-new-firmware.md`; provenance and stable IDs: `UPSTREAM.md`; AGENTS.md has the mapping summary.

## Implemented core mapping

- Owner chose GNU AGPL-3.0 for the repo and an independent core mapper. `core/hookpoints/` re-authors the full HMG-API
  catalogue in our schema with stable IDs. `maps/hyundai/ioniq5/kor/260902/` has the bundled names and all 18 stock MD5s.
  `modules/wireless-carplay-and-aa/hookpoints.required` declares 120 direct and supporting points, none unresolved.
- `core/map/`: standalone Java 7/API 19 DEX index, shape resolver and reflection verifier run in a root `app_process`. The
  full catalogue is scanned only on confirmed `map run`; `map status` matches product, onebin, Android release and stock
  MD5s. A known map is verified once per boot from an app worker (`map verify` via adb). Every resolved point is reflected
  against its stock classloader; failed optional points are removed from the published map, failed required points block
  publication. The package API publishes immutable files and an atomic active pointer under `car-maps/`, with ledger and PR
  export. The wireless module consumes that pointer (its old `/system` map copy and four-hash gate are gone). Settings → Car
  mapping shows status, source, counts, IDs and confirmed map/export actions.
- `tools/map-car.py`: independent baksmali host mapper. `tools/verify-map` validates schema and compares host/on-car maps. CI
  validates every bundled map, catalogue coverage, module requirements, forbidden fields. `UPSTREAM.md` and
  `tools/upstream-diff.sh` track HMG-API commit `4fab437` without copying source.
- Design decisions kept: explicit "Map this car" after a cheap status check (not automatic on unknown firmware); a map
  generated on the car activates after on-car verification plus explicit module enable; mapper runs on a background worker
  outside zygote and the UI process; every required point needs exactly one candidate and passing reflection, else the whole
  projection hook set stays off with point and reason reported; native-recipe checks stay independent.
- Host synthetic DEX/smali and fake-root tests do not prove mapping time, Dalvik reflection, memory, verified points, zygote
  loading or projection on the Ioniq 5. Open: time/memory budget from a parked-car measurement; retention of generated maps
  and redacted report fields.

## v1.0.1 additions (2026-11-04)

- The 18 IDs listed in UPSTREAM.md were translated from HMG-API v1.0.1 definitions into our schema (no upstream mapper code).
  All 18 resolve uniquely in the owner's read-only KOR stock dump (2026-11-04) with our DEX index/scanner; no US/EV6 names
  or hashes used. No new stock files needed: the 18 existing `car.json` MD5s match; `car.json` is byte-identical.
- 6 iAP2 points in `/system/app/Iap2ServerApp.odex` (`a672fd0e0070414f2eb70a2807117a6f`); 12 framework points in
  `/system/framework/automotive.odex` (`c76b8209ef171739b177eb14e8df61dd`). Per-ID signatures in the bundled `evidence.json`.
  Keep the stock `iAP2UsedVehicelInformation` spelling. No module requires a new ID.
- Bundle maps 342/343; `themesettings.split_item.type` stays missing. Only the original 324 points have owner-reported
  on-car reflection (324/324, 2026-11-03); the 18 need it. Wireless CarPlay works without audio at 0.1.21; the audio fix awaits
  physical verification. The sidecar and generated `docs/hookpoints.md` distinguish these claims; the host validator checks
  consistency and optional exact-stock MD5s, not that a physical observation happened.

## Verification seal migration

A same-boot update could reuse a seal after the bundled map or catalogue changed (reproduced with the real Java MapTool). The seal now carries a map/car/catalogue/required-manifest digest; old three-line seals and changed inputs report off until reverified, including same-boot OTA reconciliation. Normative format, zygote gate and map-ID rules: [module-runtime-contract.md](module-runtime-contract.md), "Core car mapping". The migration still needs on-car validation.

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

**Superseded pre-core state.** The CarPlay module shipped a fixed 324-point `payload/data/car-map.json`; root precheck required
the exact product, `neev_kr`, Android 4.4.2 and four odex MD5s (`root/wireless-carplay-and-aa.sh:7-44,64-67`); zygote rechecked
them (`payload/src/hmgapi/hook/HmgApi.java.src:21-41`, `Points.java.src:31-55`). A change to another mapped library could evade
that four-file gate. Copying HMG-API's Java mapper into the shared runtime would have extended AGPL obligations there (`modules/wireless-carplay-and-aa/payload/NOTICE.md:3-15`), hence the independent core mapper.
