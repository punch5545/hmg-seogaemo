Status: current — implemented AGPL port; physical-car boot and projection remain unverified.

# Wireless CarPlay and Android Auto: NEEV.KOR.260902 port

Review date: 2026-11-03. Target: Hyundai Ioniq 5 KOR, `ro.build.product=NEEV.KOR.0000.V039.001.260902`, onebin `neev_kr` (`ro.mobis.onebindir`), Android 4.4.2. Owner confirms HMG-API 1.0.0-beta.5 with its mapping and wireless mod worked on this car (hotspot and wireless CarPlay). The pre-install Car Report at `tmp/car-reports/car-report-neev-kor-260902-20261102-2115.json` has 324 mapped points, 12 by reference, one unrelated ThemeSettings point missing. Its `map.status=none` and `wifi_chip=false` are pre-install observations, not a negation of the owner's working test.

## Dependencies and port choice

| Original HMG-API part | Port |
| --- | --- |
| `feature_flags` generates CarPlay/AA wireless permission XMLs and comments their removal lines; `patches.json` comments the hotspot removal line. | `sg_file_put` only for absent XMLs and `sg_file_replace_line` for exact active lines. Missing removal lines are no-ops, as in the upstream engine; duplicate lines are refused. An existing XML must declare the expected feature. We also add hotspot XML if absent on this KOR build. |
| `persist.ium.support=carplay`; bridge `persist.sys.iap2.wifi=t`, `persist.iap2.iden.wireless=t`. | Same values via `sg_prop_set`; ledger restores live values and `/data/property` existence/content. |
| `Wireless`, `AaTurns`, `dipo/{Dipo,Bridge,CpTurns}` Java hooks DiPO, iAP2, GPM, navigation, USB startService, BtSettings. | Copied with fixed upstream default settings. Korean BtSettings menu hook is a no-op; upstream changes only US/CAN. |
| Zygote wrapper, Dalvik JNI loader, hook JAR, car hook map. | Wrapper, loader and hook JAR remain under this module; the core now selects and publishes the verified map. `sg_zygote_install` runs through the shared package API. |
| Native `MemRecipe` on `libautoreceiver_infobank.so`, `libCarPlayCore.so`, `libCarPlayAudio.so`, `libCarPlayUtils.so`. | Same 5 recipes applied **in memory** after exact byte-region verification. Stock libraries stay untouched. No `lpatch` binary or rebuilt OEM APK/odex. KOR CarPlayCore hash `ef49c09b7ab5eb72298f9973443b66b3` is listed as Wi-Fi native; Utils hash `e85385d70194efe82cc8a6d0f66f5115` as converter native. Other builds may reject recipes. |
| `libleafiaaac.so` for AAC/Opus. | Rebuilt from upstream `leafiaaac.c` with local clang/lld and installed through the package API. No manufacturer binary is bundled. |
| Generic settings UI, HMG-API `hmgapid`, mod engine, APK patch transaction, network keeper. | Dropped. The upstream mod declares no network rule; this car's pre-install firewall report is permissive. No Wi-Fi client property or feature is requested. |

The `hmgapi.*` Java namespaces remain internally to preserve the mod's app-part class loading; they resolve only to bundled classes, never to installed HMG-API. `modules/wireless-carplay-and-aa/build/build.sh` calls the packaged `payload/build.sh`, which builds ARM objects with clang/lld and API 19 DEX with `javac`/`d8`. The NEMESIS ZIP includes the latter script and all corresponding hook source. The shared Makefile now calls it before packaging and invokes `test/run.sh` in `make test`. The NEMESIS ZIP includes the rebuilt payload bytes; the hook JAR uses a fixed ZIP timestamp so repeat builds are byte-identical. Payload Java source is stored as `.java.src` so the shared APK compile does not accidentally include vendor stubs or hook classes; the module build copies it to temporary `.java` files before compilation. `payload/build.sh`, `payload/native/build.sh`, and `payload/native/syslib-stubs.sh` are host-only build scripts; no payload shell script runs on the car. The on-car root script uses toolbox `md5` and mksh `read` for hash and process parsing.

## Exact car map and firmware guards

The map is now `maps/hyundai/ioniq5/kor/260902/{map.json,car.json}` in the core package. It contains 324 resolved point names and all 18 stock-file MD5s reported for this car. The module declares 120 point IDs, including owner classes and supporting fields, in `hookpoints.required`. Root precheck requires `map status` to report a verified core map. The zygote loader reads the package API's active pointer under `/data/nemesis/backup/hmg-seogaemo/car-maps/`, checks the exact product, onebin, Android version, boot verification seal, and all map stock MD5s, then loads the published verified entries. The private payload map and `/system` map install were removed. The report has no whole-file hash for `app_process`, `featurexml.cfg`, or native libraries. Native recipes still verify their own byte regions. HMG-API's Dalvik offsets were proven on US EV6; on-car KOR validation remains necessary.

Declared hook points and their exact resolved members on this car:

| Point | Resolved member |
| --- | --- |
| `framework.context_impl.start_service` | `android.app.ContextImpl.startService` |
| `btsettings.projection_helper.open_wireless_page` | `com.android.settings2.bluetooth.iP.aQ` |
| `framework.navigation_cluster.update_tbt_info` | `com.hkmc.cluster.NavigationCluster.updateTBTInfo` |
| `framework.navigation_cluster.update_tbt_sub_info` | `com.hkmc.cluster.NavigationCluster.updateTBTSubInfo` |
| `framework.navigation_cluster.update_tbt_sub_info_b` | `com.hkmc.cluster.NavigationCluster.updateTBTSubInfo` |
| `framework.navigation_cluster.update_tbt_sub_info2` | `com.hkmc.cluster.NavigationCluster.updateTBTSubInfo2` |
| `framework.navigation_cluster.update_tbt_sub_info2_b` | `com.hkmc.cluster.NavigationCluster.updateTBTSubInfo2` |
| `framework.navigation_cluster.update_tbt_sub_info_2c` | `com.hkmc.cluster.NavigationCluster.updateTBTSubInfo2C` |
| `framework.navigation_cluster.update_remain_dis` | `com.hkmc.cluster.NavigationCluster.updateRemainDis` |
| `framework.navigation_cluster.update_tp_crossroad_name` | `com.hkmc.cluster.NavigationCluster.updateTPCrossroadName` |
| `framework.navigation_cluster.update_tp_destination_name` | `com.hkmc.cluster.NavigationCluster.updateTPFlexibleDestinationName` |
| `framework.navigation_cluster.update_tp_turn_list` | `com.hkmc.cluster.NavigationCluster.updateTPTurnList` |
| `framework.navigation_cluster.update_tp_turn_list_new` | `com.hkmc.cluster.NavigationCluster.updateTPTurnListNew` |
| `framework.navigation_cluster.update_tp_lane_info` | `com.hkmc.cluster.NavigationCluster.updateTPLaneInfo` |
| `iap2.init_info.check_supports_features` | `com.tcc.iap2server.Iap2InitInformationMsg.checkSupportsFeatures` |
| `iap2.service.on_create` | `com.tcc.iap2server.Iap2Service.onCreate` |
| `iap2.service_receiver.on_receive` | `com.tcc.iap2server.Iap2Service$1.onReceive` |
| `gpm.navigation_status.init` | `com.google.android.projection.protocol.NavigationStatus.init` |
| `gpm.navigation_status.next_turn` | `com.google.android.projection.protocol.NavigationStatus.navigationNextTurnCallback` |
| `gpm.navigation_status.next_turn_distance` | `com.google.android.projection.protocol.NavigationStatus.navigationNextTurnDistanceCallback` |
| `gpm.navigation_status.status` | `com.google.android.projection.protocol.NavigationStatus.navigationStatusCallback` |
| `gpm.receiver.send_navigation_focus` | `com.google.android.projection.protocol.GalReceiver.sendNavigationFocusState` |
| `gpm.receiver.destroy` | `com.google.android.projection.protocol.GalReceiver.destroy` |
| `dipo.iap2_proxy.stop_route_guidance` | `com.tcc.iap2.IIap2Service$Stub$Proxy.Iap2SvcStopRouteGuidanceUpdate` |
| `dipo.btcm.register_listener` | `com.broadcom.bt.hfdevice.hfbtcm.HfDeviceBTCMManager.registerHfDeviceStatusListener` |
| `dipo.carplay.session_created` | `com.daudio.app.dipo.telechips.CarPlayService.onSessionCreated` |
| `dipo.carplay.session_started` | `com.daudio.app.dipo.telechips.CarPlayService.onSessionStarted` |
| `dipo.carplay.session_finalized` | `com.daudio.app.dipo.telechips.CarPlayService.onSessionFinalized` |
| `dipo.carplay.iap_message` | `com.daudio.app.dipo.telechips.CarPlayService.oniAPSendMessage` |
| `dipo.utils.is_support_wireless_carplay` | `com.daudio.app.dipo.i.e.isSupportWirelessCarPlay` |
| `dipo.iap2_listener.connected` | `com.daudio.app.dipo.e.g.connected` |
| `dipo.iap2_listener.disconnected` | `com.daudio.app.dipo.e.g.disconnected` |
| `dipo.iap2_listener.notify` | `com.daudio.app.dipo.e.g.notify` |
| `dipo.session_listener.session_details` | `com.daudio.app.dipo.service.q.a` |
| `dipo.iap2.bt_address` | `com.daudio.app.dipo.e.b.K` |
| `dipo.bt.set_carplay_bt_address` | `com.daudio.app.dipo.f.c.o` |

`Car`, `Cluster`, and `NativePatch` consume supporting framework points from the same 324-point map. A library recipe mismatch leaves that stock library unchanged and logs the failure; it does not prove that the corresponding feature works on KOR.

## Complete change/restore inventory

The three exact feature removal lines use `owns_lines.1` through `.3`;
`featurexml.cfg` is not whole-file owned. Runtime composition retains any other
module's distinct line patches on revert. The `wifi-client` exclusion remains
for the shared STA/AP radio; shared-file safety is enforced independently.

The package API must baseline each owned path before writing it, record applied bytes and metadata, bind baselines to `ro.build.product`, and verify exact restoration. `sg_revert_all` must preflight **all** targets for drift before the first restore write, refuse if any changed externally, retain baselines/ledger, and abort uninstall. Reapply/update must never replace the first baseline.

| On-car path or property | Apply and restore |
| --- | --- |
| `/system/onebin/neev_kr/featurexml.cfg` | Comment each exact CarPlay wireless, AA wireless, and hotspot removal line if present. Restore original bytes/mode/owner. |
| `/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml` | Add if absent; restore exact bytes or absence. |
| `/system/etc/permissions/com.hkmc.software.connectivity.androidauto_wireless.xml` | Same. |
| `/system/etc/permissions/com.hkmc.software.wifihotspot.xml` | Same. |
| `/system/lib/libleafiaaac.so` | Put rebuilt converter; restore bytes or absence. |
| `/system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes/aa-tbt-48ee793a.recipe`, `aa-tbt-a0bea1c4.recipe`, `carplay-core-wifi.recipe`, `carplay-audio-formats.recipe`, `carplay-utils-converter.recipe`, `iap2-lane-msgid.recipe` | Put six recipes; restore each exact original or absence and remove newly created empty directories. |
| `/system/bin/app_process` | `sg_zygote_install` installs wrapper; restore exact stock executable bytes/mode/owner. |
| `/system/bin/app_process.hmg-seogaemo-stock` | `sg_zygote_install` copies the current original here before swap; restore prior bytes or absence. |
| `/system/lib/libhmg_seogaemo_wireless_hook.so`, `/system/framework/hmg-seogaemo-wireless-hook.jar` | `sg_zygote_install` installs runtime; restore exact bytes or absence. |
| `/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/enable`, `enabled`, `starts`, `disable` | `sg_zygote_install` owns guard state. The wrapper increments `starts`, resets it after 120 seconds alive and obeys `disable`; the API must track/export/restore all generated state. |
| `persist.ium.support`, `persist.sys.iap2.wifi`, `persist.iap2.iden.wireless` plus `/data/property/` backing files | Set `carplay`, `t`, `t`; restore live and backing state, including absent vs empty. |
| Potential `/data/dalvik-cache/system@framework@hmg-seogaemo-wireless-hook.jar@classes.dex` and `/data/data/com.daudio.app.dipo/app_hmg-seogaemo-wireless-dex/` | Android may generate optimized DEX. Package API must track and remove only this module's generated entries and restore any pre-existing bytes. Dalvik can regenerate cleanly after stock restoration. |

Not part of the firmware transaction but removed at revert through the package API
(`owns_generated_dirs`, advisory, never a conflict): the hook's own
`app_hmg-seogaemo-wireless-runtime` diagnostic directory and its
`app_hmg-seogaemo-wireless-dex` Dalvik output directory in each of `com.daudio.app.dipo`,
`com.tcc.iap2server`, `com.google.android.projection.sink`, `com.mnsoft.navi` and
`com.android.btsettings`. Running apps rewrite them until the next restart, so the revert
records `restart-cleanup/wireless-carplay-and-aa` and boot reconcile finishes the removal. If the app_process swap has no
ownership record (stock backup or hook `.so`/`.jar` present, or an `app_process`
other than the declared stock MD5 `33f798115689d1859e8d2159dc88e7dc` seen on this car),
status, revert, whole-package restore and uninstall report one conflict naming all of
them and change nothing (`sg_zygote_foreign`). The wrapper's `starts`/`enable`/`enabled`/`disable`
files alone never conflict: restore sweeps them, and while the head unit has not restarted
since the revert the module reports pending-restart (`sg_zygote_guard_sweep`). Projection status uses a root Dalvik reader bounded to 15 seconds;
a timeout, a missing boot class path or malformed output reports `unknown`.

No stock app APK/odex, manufacturer native library file, `build.prop`, Wi-Fi client state, ADB property, firewall rule, or service is changed. Android user data created during phone sessions is outside this firmware transaction.

## Shared API integration

- `sg_zygote_install <wrapper-rel-src> <loader-rel-src> <jar-rel-src>`: one guarded ledger transaction. Validate the three relative payload paths and ownership, preserve original `/system/bin/app_process` to `/system/bin/app_process.hmg-seogaemo-stock` before replacing it, install the wrapper, loader and JAR with exact modes/owners, establish `enable`/`enabled` boot state, and prepare the 3-start/120-second guard. On any failure, roll back using the first baselines. `sg_revert_all` restores all six paths and guard state after full drift preflight. The wrapper always execs preserved stock even when preloading is disabled. The API must capture/export dynamic `starts` updates. The merged shared `common.sh` implements this transaction and records the dynamic `starts` counter before restore.
- `sg_dex_cache_track <system-jar> <app-package>`: before enabling, baseline any existing optimized artifacts attributable to this JAR globally and in the app's `app_hmg-seogaemo-wireless-dex` directory; record generated paths/bytes in the ledger after activation; `sg_revert_all` restores only them. The module calls it before `sg_zygote_install` for `/system/framework/hmg-seogaemo-wireless-hook.jar` and `com.daudio.app.dipo`. If tracking cannot be safe, fail the install before modifying `app_process`.

No direct `cp`, `mv`, `rm`, `setprop`, remount, service restart, or cache deletion occurs in this module root script. The module-local fake in `test/run.sh` checks the module sequence; the shared `tools/test-runtime.py` exercises the real transaction and restore implementation.

## Copied source and AGPL notice

Copied HMG-API paths (destinations relative to this module's `payload/`):

- `mods/wireless-carplay-aa/src/hmgapi/mods/wireless/{Wireless,AaTurns}.java`, `dipo/{Bridge,CpTurns,Dipo}.java` → `src/hmgapi/mods/wireless/` as `.java.src`; `AaTurns` drops an HMG-API-only projection event.
- `core/runtime/java/hmgapi/hook/{Bridge,Hooks,Points,LoadedApp,MethodHook,ModEntry,ModContext,NativePatch,Car,Cluster,AppPartLoader}.java` → `src/hmgapi/hook/` as `.java.src`; four are trimmed, and new `HmgApi.java` replaces the generic loader.
- `core/runtime/java/hmgapi/engine/MemRecipe.java` → `src/hmgapi/engine/` as `.java.src`, unchanged.
- `core/loader/loader.c`, `core/zygote/zygote.c`, `core/loader/syslib-stubs.sh` → `native/`, modified for HMG-Seogaemo paths and without `hmgapid` spawning.
- `mods/wireless-carplay-aa/native/leafiaaac/{leafiaaac.c,build.sh}` → `native/`; output path modified. `data/libleafiaaac.so` is rebuilt from source, not an upstream binary copy.
- `mods/wireless-carplay-aa/recipes/{aa-tbt-48ee793a,aa-tbt-a0bea1c4,carplay-core-wifi,carplay-audio-formats,carplay-utils-converter}.recipe` → `recipes/` unchanged.
- `mods/wireless-carplay-aa/stubs/com/{broadcom,tcc}/...` (six Java compile-time signature stubs and README) → `stubs/` as `.java.src`, unchanged, not in the runtime JAR.

Original headers remain; modified files have a `Modified by HMG-Seogaemo` marker. Root `NOTICE.md` credits HMG-API (VysseCodes), NEMESIS (xwtk), HMG Leafia Plus, and Xposed design lineage. The repository is GNU AGPL-3.0 (`LICENSE`). The core mapper is an independent implementation; `UPSTREAM.md` tracks the HMG-API reference paths.

## On-car validation and recovery

The 2026-11-04 permission repair reconciles already-applied assets before returning
and checks world-read/search bits in status. DiPO runs as UID 1000: readable recipe
files under a 0700 ancestor still fail registration. HMG-API
`core/runtime/java/hmgapi/engine/LiveState.java` publishes readable recipes and
searchable ancestors; no additional audio properties or policy edits were found
necessary. The upstream one-attempt NativePatch behavior remains: a failed native
write may be partial, so a full head-unit restart is required after repair.

After installing and applying, inspect these exact paths with `ls -ldn`:

```sh
ls -ldn /system /system/etc /system/etc/hmg-seogaemo
ls -ldn /system/etc/hmg-seogaemo/wireless-carplay-and-aa
ls -ldn /system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes
ls -ldn /system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes/*.recipe
ls -ldn /system/lib/libleafiaaac.so
```

Project directories should be 0755 UID/GID 0/0 and the five recipes/converter
0644 UID/GID 0/0; stock ancestors must permit traversal. Then fully restart and
test music, Siri and calls while checking native recipe logs. A readable recipe
and loaded zygote loader cannot prove the patch matched native memory. Existing
legacy directories have unknown creation provenance and retain their observed
metadata as the restore baseline, even if that baseline is 0700. Newly tracked
directories are removed only when empty and unchanged; unrelated contents block
cleanup and retain recovery material.

1. Before enabling, save a fresh Car Report and stock app_process, feature file/XML, three persistent properties plus backing-file presence, four `.odex` hashes, and any existing optimized DEX entries. Verify NEMESIS full-root ADB recovery works while Android is down.
2. Build locally, install through NEMESIS, run `hmg-seogaemo-root wireless-carplay-and-aa precheck` then `apply`. Export/review the change ledger. Confirm the stock ADB switch is `0`, then fully restart the head unit; ignition cycling alone may not restart Android.
3. Stream HMG-Seogaemo-Wireless, DiPO, Iap2, GPM and kernel logs. Test hotspot association/DHCP, wireless CarPlay music/Siri/calls, wireless Android Auto and a USB drive, cluster/HUD guidance, and native recipe messages. Status is `on` only with current validated zygote and all five app records, matching installed payload hashes, ready gates, loaded Java parts and applied native recipes (or GPM's explicit stock exemption). Loader mapping alone proves no readiness. Missing evidence is unknown; an older loaded generation requires restart. Status/export gathers bounded current app records and both log-ring halves through `sg_projection_runtime`; successful revert removes generated root diagnostics. Repeat after another boot and a package update.
4. Revert through `hmg-seogaemo-root wireless-carplay-and-aa revert`, export ledger, fully restart, compare every baseline byte/absence/property, and test stock wired projection. Uninstall NEMESIS package only after restore succeeds. Fake-root drift test must refuse before any partial restore.
5. On boot failure, after three unsuccessful zygote starts the wrapper should stop preloading and still exec stock. ADB/NEMESIS root can call the root dispatcher to revert, inspect/export ledger, then restart. If dispatcher is unavailable, use a saved ledger and stock executable through NEMESIS full-root recovery; never restore an older firmware's `app_process` over a newer one. The runtime owner must provide and test the exact emergency recovery command before release.

Off-car build/fake-root tests cannot prove KOR Dalvik offsets, native recipe matches, working wireless sessions, or head-unit boot recovery. The shared API and off-car gates pass; do not release before on-car validation passes.


## CarPlay turn-by-turn: lane fix, fallback icons, developer toggle

- **Lane guidance (our own raw 0x5204 decoder).** Bridge forwards CarPlay-channel bytes unchanged,
  then feeds the bounded, Android-free CpLaneDecoder and posts decoded data through Dipo.post
  to the TBT handler. Link/control messages may be split or coalesced; link/header checksums,
  TLV lengths, scalar widths, eight lanes and sixteen signed angles per lane are checked.
  Invalid messages are ignored with one essential error line per 30 seconds. Description bytes
  are counted only. Wire references: carplay-tbt-external/iap2-protocol.md; this is our own
  implementation, with no third-party source copied.
  CpTurns retains the last sixteen entries keyed by component and LaneGuidanceIndex, since
  lane windows arrive before the route update. Only the current 0x5201 p16 entry is displayed
  while p18 is true and state is nonzero (the maneuver-count/state show gate also applies).
  No fallback to another index or 0x5202 linked lane is used. Missing current entries and hidden
  lanes blank the old frame; state 0, stop, disconnect and source changes clear the cache.
  Existing angle/status modes produce the 81-byte 0xF1 frame; auto highlights preferred lanes
  when supplied, and highlight 1000 means none. Invalid angles produce no arrow. Refresh resends
  the last frame. tbt-lanes records contain raw numeric lane fields, description byte count,
  current index/count and all 81 frame bytes. Developer mode logs entries and index matches.
  0x5204 has no distance; route updates supply it.
  **Keep the stock parser disabled:** redirecting 0x5204 to libiap2session's lane handler crashed
  libIap2Acc.so in convertSubGroup on the car. The unused iap2-lane-msgid.recipe remains
  packaged/owned for compatibility with existing baselines; Wireless.iap2 never applies it.
  No mods class implements a hook interface; the Dalvik cross-dex rule below still applies.
  Physical cluster/HUD rendering, per-app ordering and source transitions need on-car checks.
  Stock navigation's existing NAVI_TBT hook now captures allowed 81-byte 0xF1 calls to
  updateLaneInfo/updateTPLaneInfo without changing arguments or delivery. CarLaneCapture uses
  Observations directly in com.mnsoft.navi's own runtime directory, with bounded tbt-lanes
  records keyed source=car-nav and the full frame hex (including 0x43-0x50 bitmasks). Identical
  consecutive frames are deduplicated; developer logs are deduplicated and limited to one
  per three seconds. Suppressed calls are never captured. Compare these ground-truth records
  before changing encoding: CpTurns.laneIcon is the single angle-to-stock-arrow mapping
  function. Current HMG-API-derived arrows/statuses are not proven identical to stock bytes.
- **No silent skips.** `CpTurns.turn()` used icon 0 to stop everything (no turn, HUD, trip). An unmapped maneuver now uses
  `fallbackIcon` (direction arrow, else straight) and logs `no exact icon for maneuver type ...` once per combination. A missing maneuver
  still sends HUD distance and trip, and asks the phone to resend (Stop then Start), at most once in 30 s.
- **Developer toggle.** Verbose TBT / iAP2 logs (`TBT ...`, `bridge: iAP2 in ...`, `TBT cluster ...` traces) follow the property
  `persist.hmg_seogaemo.dev`, read every 3 s, so no restart. Off (default) keeps only one-line state and failures. From adb, with full root:

      adb shell cp /system/xbin/nemesis /data/local/tmp/nemesis-d
      adb shell /data/local/tmp/nemesis-d root -c 'setprop persist.hmg_seogaemo.dev 1'   # on
      adb shell /data/local/tmp/nemesis-d root -c 'setprop persist.hmg_seogaemo.dev 0'   # off
      adb shell getprop persist.hmg_seogaemo.dev
      adb logcat -s HMG-Seogaemo-Wireless:*
- `Wireless.NAVI_TBT` now also skips the car navigation's `update_lane_info` while a phone guides (its free-drive writes would overwrite our lane frames).
- **Observation records.** `tbt-icons.*` / `tbt-lanes.*` (summary + about 10 MiB of append-only journals, with the phone's instruction texts; kept permanently so the icon table keeps improving) in DiPO's runtime directory, format and the `tbt-reset` verb in
  "Turn-by-turn observation records and `tbt-reset`" below. The module page shows `info.tbt_inexact` / `info.tbt_blank`
  and a confirmed reset. Match kinds: `exact` (table), `band` (types 1/2/47-50 by angle), `angle` (any other type by angle,
  roundabouts by nearest exit icon), `semantic` (type only, e.g. keep left/right, enter roundabout, arrive), `blank` (icon 64: distances, lanes and
  trip still go out). Fallback never uses "straight" for a turn. A 1 s refresh (`CpTurns.refresh`) resends the last state while guidance is active;
  evidence for it: the car's own navigation calls `updateTBTSubInfo` every 0.5-0.75 s, and no keep-alive or handshake call is documented
  in the API inventory (`updateAlive` exists but phone guidance deliberately never claims the cluster).

- **Dalvik cross-dex rule (learned on the car, 2026-11-04).** The hook classes (`hmgapi.hook.*`) are loaded in the zygote from the
  boot-time jar, while each app's mods are loaded from its own dex copy. A mods class that *implements a new hook-package interface*
  is pre-verified against the copy in its own dex and then fails to link at runtime ("Class resolved by unexpected DEX" →
  `NoClassDefFoundError`), which took down DiPO's whole part (`part.dipo=failed`, no wireless CarPlay). Do not add callback
  interfaces in `hmgapi.hook` for mods to implement; use static fields/flags or existing types (`MethodHook`, `Runnable`).
  Check with `dexdump` that no new `hmgapi/mods` class has a `hmgapi/hook` superclass/interface other than `MethodHook`.
- **HUD list resend (2026-11-05, owner report).** With route guidance started right at CarPlay connect,
  the HUD later froze its arrow and road text while its distance kept counting down and the cluster
  stayed correct; restarting the route fixed it. The 0xF2 HUD list was only sent on change and cached
  as sent with no delivery signal, while the HUD distance (`update_tbt_sub_info_2c`) goes out every push.
  `CpTurns.refresh` now resends the unchanged 0xF2 frame every 2 s (`HUD_RESEND_MS`), independent of
  phone updates; `hudBlank` clears it. The exact trigger (missed frame vs HUD restart) is unproven.

## Runtime contract details

### Zygote payload upgrades (2026-11-04)

`sg_zygote_install` compares the selected wrapper, loader and JAR on every
apply, including desired-on reconciliation after OTA and NEMESIS USB postinstall.
An applied install may advance only from its recorded applied bytes/metadata;
unknown drift conflicts before any payload write. The stock executable backup
and every original file baseline remain immutable through all generations.
A durable `zygote-upgrade` journal publishes intended bytes/metadata before
mutation. Guards also recognize those intended states after interruption, so
retry converges and stock restore can undo a partial upgrade. A different
selected generation during a pending upgrade is refused; restore or resume the
same selected payload first. The journal remains until all new applied markers
and ledger entries are committed.

Upgrade captures then invalidates tracked Dalvik caches, writes loader and JAR,
and atomically replaces the wrapper last. It records restart-required before
the first mutation; the old running zygote still holds its previous inodes.
Cache deletions remain journaled until their applied-absence markers are
committed, including paths no longer visible in cache globs after interruption.
No zygote service restart is performed. A same-payload reapply makes no payload
or cache writes. Projection reapply reasserts all three owned properties using
`sg_prop_set`; status checks all three.

(OTA rollback and update-health interaction: [ota-implementation.md](ota-implementation.md), "Entry dispatch and damaged-payload recovery". Uninstall always restores ORIGINAL stock, never the previous applied generation.)

### Unrecorded `app_process` swap (2026-11-04)

`sg_zygote_foreign` is a read-only guard for a module that owns
`/system/bin/app_process`. The swap counts as owned when the module holds a file
baseline for it (or an upgrade journal). Without that record nothing we wrote is
recoverable, so the guard returns conflict (3) when any of exactly these exists:
`app_process.hmg-seogaemo-stock`, our hook `.so` or `.jar`, or an `app_process`
whose MD5 differs from the module's `known_stock.N=<ro.build.product glob>:<path>:<md5>`
declaration for the current product (no matching declaration means no judgment).
Every offender is listed on the single `info.restore_conflict` line.

The wrapper's own guard state in the module state directory (`enable`, `enabled`,
`starts`, `disable`) is not evidence. The live wrapper recreates `starts` after a
revert (its watcher opens it with `O_CREAT`, up to 120 s after the zygote started),
`touch disable` is its documented manual off switch, and a car may hold stale files
of an earlier install. `sg_zygote_guard_sweep` removes exactly those four names
(never anything else, one ledger row `zygote_guard_sweep` each) while the module has
no swap record and `sg_zygote_foreign` passed; a directory in their place is a
conflict. `sg_revert_all` (module revert, whole-package restore, the NEMESIS uninstall
hook), the `lib/stock.sh` "nothing applied" path and the wireless fresh `apply` call it,
so stale guard files are never recorded as stock originals either. Module `status`,
`precheck` and the stock preflight only read.

A revert that restored an applied `app_process` leaves the running zygote on the
wrapper until the head unit restarts. `sg_revert_all` therefore records the
per-module restart marker (`sg_restart_required`, best effort) after such a restore;
while `sg_restart_pending` holds (same kernel boot), the wireless module's `status` and
`revert` and the stock restore report `pending-restart`/`head_unit`, not `off` and not a
conflict, even if the wrapper meanwhile recreated `starts`.

`sg_revert_all` calls the guard first, so module revert, whole-package restore and the
NEMESIS uninstall hook refuse on real evidence; `lib/stock.sh` also runs it in the
all-module preflight and for modules with no snapshots, before anything is reverted.
Module `status`, `precheck` and `apply` report the same conflict. Nothing is modified,
no desired state changes, and the owner resolves it (for example by restoring stock from
the saved copy) before retrying. The wireless module declares
`known_stock.1=NEEV.KOR.*.260902:/system/bin/app_process:33f798115689d1859e8d2159dc88e7dc`
(the stock build seen on the Ioniq 5 KOR car before and after HMG-API/this module).
The apply-path orphaned-backup check remains.

### Runtime results and diagnostics (2026-11-04)

The hook publishes diagnostics independently of loader mapping. Zygote (UID 0)
uses `/data/nemesis/backup/hmg-seogaemo/runtime/wireless-carplay-and-aa/`.
Each hooked app uses `Context.getDir("hmg-seogaemo-wireless-runtime", 0)`, normally
`/data/data/<package>/app_hmg-seogaemo-wireless-runtime/`, owned by that app's UID
(DiPO commonly UID 1000). No app writes into root's private state and no shared
world-writable directory is needed. These are generated diagnostics, never stock
baselines or an authorization channel. Root prepares the private runtime parent
through the package API before enabling the loader. Root reads only the fixed projection app
packages: `com.daudio.app.dipo`, `com.tcc.iap2server`,
`com.google.android.projection.sink`, `com.mnsoft.navi`, `com.android.btsettings`.

Each process writes `p-<pid>-<start_ticks>.txt` atomically via `.txt.tmp`.
UTF-8 `key=value` lines, first `=` is the separator; keys `[a-zA-Z0-9_.-]+`,
no escaping, duplicate keys/control characters/unterminated records rejected.
Values are sanitized to one line. Format 1 metadata: `format=1`, `boot`, `pid`,
`start_ticks` (field 22 of `/proc/<pid>/stat`), `uid`, `package`, `process`,
`payload_jar_sha256`, `payload_loader_sha256`. Payload hashes are captured in
zygote before forks, identifying the loaded generation even after an OTA replaces
disk files. Zygote writes `gate.state=initializing|ready|off` and `gate.reason`
for its identity/map/seal/stock-hash/boot-hook decision. App records inherit
`gate.state`/`gate.reason` from the zygote;
`part.<name>.state=initializing|loaded|failed|off`, `.reason` and `.missing`
describe Java installation. Parts are `dipo`, `iap2`, `gpm_usb`, `gpm_tbt`,
`navigation`, `btsettings`; `.missing` lists absent stable IDs, comma separated.
`native.<library-basename>.state=registered|applied|failed`, `.reason` and
`.errno` describe recipe registration and the single native apply attempt.
Registration is waiting for a library, never proof of application. Errno 0
means no syscall error captured; failures include numeric errno and common
symbolic names (e.g. `13:EACCES`). Errors may follow partial hook/native writes;
restart, never retry native writes in the same process. `event.*` holds bounded
late failures (e.g. iAP2 bind/cluster/callback errors).
For stock GPM turn-by-turn (`stepofspec >= 999`),
`native.libautoreceiver_infobank.so.applicability=stock` and the part reason
explicitly identify why no AA recipe is registered.

Snapshots are capped at 64 KiB, 256 fields, 8192 characters/value. The reader
rejects oversized/malformed records. The process also mirrors Java tag messages
directly into `p-<pid>-<start_ticks>.log`, rotating to `.log.1` at 64 KiB
(each message capped at 2048 characters), preserving them before logcat wraps.
This keeper requires no logcat reader thread or service in zygote. Native startup
messages preceding Java initialization remain logcat-only; native recipe failures
are also recorded through Java. Retain at most eight process stems per directory,
including stale boots and interrupted temporary files; oldest artifacts are pruned
on publication (best effort across simultaneous processes). Files are 0600,
directories 0700; root can include them in a future export. Diagnostic
I/O/permission/storage errors are caught and never stop or enable hooks. Missing
diagnostics therefore mean unknown readiness, never success. Cleanup/export wiring
belongs to the root consumer; app-private files may survive module revert.
At status/export time root gathers only validated current app-private files from
those fixed packages into `$SG_DATA_ROOT/runtime/wireless-carplay-and-aa/apps/<package>/`.
Use bounded reads/copies (64 KiB/file), reject symlinks/nonregular files/incorrect
UIDs, and never trust paths from the record. The collector includes `.txt` and
`.log`; include `.log.1` for the older half of the ring. Across all packages
select at most 32 current files for export. Stale boots/PIDs must not replace
current failures.

Status must check expected boot, live PID/start ticks, expected UID/package/process,
loaded payload hashes versus installed payload, `gate.state=ready`, and all relevant
parts/native results. Absent processes/results are pending/unknown; a gate/part/recipe
failure must not be reported as on. `part.*=loaded` means initialization returned,
not a successful wireless session. In particular Core/Audio/Utils in DiPO and AA
in GPM must be applied (or explicitly deemed inapplicable by the consumer) before
claiming those native capabilities. Reject symlinks, nonregular files and unexpected
ownership; never source/eval the record as shell. These app-owned reports are
diagnostic evidence, not a defense against a compromised OEM app. Seal validation
still checks only `product\nhash-id\n`; additional seal lines remain compatible.

#### Turn-by-turn observation records and `tbt-reset` (2026-11-04)

DiPO keeps two always-on, bounded turn-by-turn records in its app-private runtime directory,
`/data/data/com.daudio.app.dipo/app_hmg-seogaemo-wireless-runtime/`: `tbt-icons.*` (which icon
each maneuver combination got, with the phone's instruction text) and `tbt-lanes.*` (lane data
seen and the 81-byte frame sent). Purpose: build the maneuver → icon table from real drives (the
iAP2 maneuver list is MFi-only). The data stays on the head unit and leaves it only when the user
exports logs and shares them; instruction texts may contain road names. They are generated
diagnostics: not a baseline, not ledgered, not an authorization channel. Files are UTF-8, 0600,
owned by the app UID.

- **Summary** `tbt-icons.txt` / `tbt-lanes.txt`: `format=2`, `kind=tbt-icons|tbt-lanes`, then one
  line per key `key|detail|count|first_ms|last_ms|samples` (epoch ms; `samples` = up to 3 distinct
  instruction texts joined by ` ¦ `, each at most 80 characters, empty for lanes; `|`, `¦` and
  control characters never occur in a field; key/detail at most 400 characters). Readers also
  accept the 5-field `format=1`. At most 1024 (icons) / 512 (lanes) keys and 1 MiB: at the limit
  the least-used, then oldest keys are evicted. Rewritten atomically (`.txt.tmp`, rename) at most
  every 5 min and at route end; read back at the next start so counts and texts survive restarts.
  Only this summary-sized aggregate is held in DiPO's memory.
- **Journal** `tbt-icons.journal` (+ `.journal.1` … `.journal.5`) and `tbt-lanes.journal`
  (+ `.journal.1` … `.journal.3`), oldest last: every sighting appended as
  `ms|key|detail|sample`, never rewritten, flushed at most every 30 s (sooner past 32 KiB pending)
  and at route end. A shard rotates at 1 MiB and the oldest is deleted, so the journals stay within
  6 + 4 MiB (about 10 MiB in all). A torn last line after a crash is ignored by readers.

Icon key `type=<n> junction=<n> side=<n> exit~<deg rounded to 15>` (plus `state=<n>` for type 12),
detail `icon=<n> kind=exact|band|angle|semantic|blank exit=<exact deg>`, sample
`<instruction>[ -> <road after>]`. Lane key `n=<lanes> [s<status> a[angles] h[highlight]]...`,
detail `mode= sent= marks= frame=<all 81 bytes sent, hex> laneIndex= laneCount= distNext= nextStr=`.
The files are not pruned with the per-process `p-*` stems and are not copied by `status` (too
large for the per-status gather). The exporter (`lib/diagnostics.sh`) copies the fixed names
directly from DiPO's directory into `diagnostics/tbt/`, whole up to 1 MiB + 64 KiB each, outside the
32-log budget and without line redaction (generated data, no credentials). Revert cleanup removes
them as plain regular files of `owns_generated_dirs` (well within its 64-file limit).

Status gains two optional read-only lines, computed by the same bounded reader run that
produces the status (no extra process): `info.tbt_inexact=<N>` (combinations whose `kind` is not
`exact`) and `info.tbt_blank=<M>` (of them, `kind=blank`), digits only, both or neither; absent
when the records cannot be read safely. Old consumers ignore them; the app treats absence as
unknown.

New fixed verb, only for `wireless-carplay-and-aa`: `hmg-seogaemo-root wireless-carplay-and-aa tbt-reset`
(exactly this argv; any other module or argument is `Invalid command`, exit 2). It takes the
operation lock, writes no journal or desired state, and runs `sg_projection_runtime tbt-reset`,
whose root reader deletes only `tbt-icons.txt`, `tbt-lanes.txt`, their `.tmp` and the fixed journal
shard names above in DiPO's runtime directory: every candidate must be a plain regular file owned by the app UID in a directory
owned by it (lstat; symlinks, other types and foreign owners refuse the whole reset before
anything is deleted). Output is the usual three lines (`state=on|failed`, `restart=none`,
`detail=removed N files`); nothing else is touched.

The consumer uses `sg_projection_runtime prepare|status|applying|clear` in the
package API, restricted to the projection owner. `prepare` creates the runtime
parent and module directory as root:root 0700 before loader installation. The
API invokes the packaged API 19 Java reader (SHA-256 through MessageDigest,
KitKat libcore lstat for UID/type); it never executes record contents. Expected
app UID comes from the fixed package's data directory, and proc UID, cmdline,
record process and start ticks must agree. Only the package main process or its
colon subprocesses qualify. Every current record is considered, so a ready
record cannot hide another current failure. Zygote plus all five packages and
their relevant parts/native recipes are required for `on`. Missing evidence is
`unknown`; an apply receipt uses `pending-restart` for missing evidence so the
dispatcher can retain enabled intent. Old loaded hashes mean `pending-restart`.
A fresh apply also records the boot id (`assets-repaired-boot`, the restart marker):
until the boot changes, the running zygote is the stock one, so module status stays
`pending-restart`/`head_unit` even with no or stale records. `unknown` is reserved for
missing evidence after a restart. The app shows the observed state (pill, tiles) and
offers a confirmed head-unit restart on the pending-restart notice.

Collection replaces only generated app copies, never baselines, with at most
32 files of 64 KiB across the fixed packages; malformed, stale, aliased or
incorrectly owned inputs are omitted. Current old-generation records remain
exportable as restart evidence. `clear` removes the generated root module tree
after successful revert (including an already reverted module), and removes an
empty runtime parent. App-private originals may remain. Export probes status
first, then includes the fixed app-copy depth and `.log.1`.
The reader runs with `ANDROID_DATA` fixed to the module's private `vm/` tree,
including its 0700 root-owned `dalvik-cache/`, so executing the reader never
leaves a global Dalvik cache entry. Successful clear removes this generated
cache along with the diagnostic copies.
The fixed reader artifact is checked beneath the canonical selected package
directory, allowing OTA's `update/current` package pointer.

The reader is a root Dalvik process that can hang, so the API never runs it
inline. It requires a non-empty `BOOTCLASSPATH` (the inherited value, else the
`export BOOTCLASSPATH` line of `/init.environ.rc`; the value must start with
`/system/` and use only path-list characters) and otherwise reports
`state=unknown` without starting the VM. It starts the reader in the background
with stdout and stderr in private files directly under the data root, waits with
`sg_bounded_wait <pid> <seconds>` (1-second polls, 15 seconds by default,
`SG_READER_TIMEOUT` for tests, capped at 120), and on expiry kills the reader and
its whole process tree and returns 124. `sg_kill_tree <pid> [depth]` is a subshell
function: it snapshots toolbox `ps` (PID/PPID fields), collects target and all
descendants (default depth 3) before signalling anything, sends TERM to every
member, waits one second and sends KILL to every member, so no member is
skipped when an intermediate process ignores TERM or children are reparented.
The following `wait` on the target returns once KILL is delivered.
Output must be exactly one `state=`, `restart=` and `detail=` line with a known
state/restart pair and a clean exit (1 only with `state=failed`); anything else,
or a timeout, is `state=unknown` (`pending-restart`/`head_unit` for `applying`).
`status` therefore never keeps `operation.lock` longer than the limit. Hang-up, interrupt
and termination of the caller run the `EXIT` trap, which kills the reader's whole tree with
`sg_kill_tree` (also `core/update.sh` and `core/hmg-seogaemo-map.sh` for their VMs, whose
verifier runs as a direct child of the updater shell so the trap can reach it) and removes
`reader.out.<pid>` and `reader.err.<pid>`. A run killed before its trap leaves them behind;
the next run deletes exactly `reader.{out,err}.<digits>` files in the private data root whose
PID is not alive. `clear`
that cannot run the reader (timeout, no boot class path, exit 126 or above)
removes the root-only generated tree directly; a reader refusal (exit 1) still
fails. `sg_bounded_wait` and `sg_kill_tree` are generic and are also used for the
mapper and the update verifier. Callers under `set -e` must write
`sg_bounded_wait ... && code=0 || code=$?`.
