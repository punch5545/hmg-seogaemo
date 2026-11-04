Status: current — 2026-11-04 catalogue additions and verification migration.

# v1.0.1 hookpoint evidence

The 18 stable IDs listed in UPSTREAM.md were translated from HMG-API v1.0.1
definitions into our schema. No upstream mapper or tool implementation was
copied. All 18 resolve uniquely in the owner's read-only KOR stock dump from
2026-11-04, using our DEX index/scanner. No US or EV6 names or hashes were used.
No additional stock files are required; all 18 existing car.json MD5s match
the dump and car.json is byte-for-byte unchanged.

The six iAP2 points resolve in /system/app/Iap2ServerApp.odex
(a672fd0e0070414f2eb70a2807117a6f). The twelve framework points resolve in
/system/framework/automotive.odex (c76b8209ef171739b177eb14e8df61dd).
Per-ID class/member signatures and stock paths are in the bundled evidence.json.
Keep the stock iAP2UsedVehicelInformation spelling. No module requires a new ID.

The bundle now maps 342/343 IDs; themesettings.split_item.type remains missing.
Only the original 324-point snapshot has owner-reported on-car reflection
evidence (324/324, 2026-11-03). The new 18 require on-car reflection. Wireless
CarPlay works without audio at 0.1.21; the audio fix awaits physical verification.
The sidecar and generated docs/hookpoints.md distinguish these claims. The
host validator checks consistency and optional exact-stock MD5s, not whether a
physical observation happened. No private dump files are committed.

## Verification migration

Previously a same-boot update could reuse a seal after changing the bundled map
or catalogue. A read-only audit reproduced this with the real Java MapTool.
The seal now appends a deterministic map/car/catalogue/required-manifest digest.
Old three-line seals and changed inputs report off until reverified, including
same-boot OTA reconciliation. Product/stock hash lines remain unchanged, so
the projection loader can accept a previously verified map before boot-time
reconciliation; tying that gate to the boot-bound full seal would break cold boot.

Map IDs include complete published car metadata. Optional reflection failures
prune by_reference too. Generated verification preserves export layout, binds
the filtered published result, and selects the active generated map before
historical directories. Bundled seals bind original inputs so optional filtering
does not immediately invalidate status. Real-JVM fixtures cover these paths,
static/reference signatures, required failure, stock mismatch and boot changes.

The verification migration and newly added points still need on-car validation;
host fixtures establish behavior under simulated reflection and stock inputs.
