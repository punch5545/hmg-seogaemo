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

The package API must baseline each owned path before writing it, record applied bytes and metadata, bind baselines to `ro.build.product`, and verify exact restoration. `sg_revert_all` must preflight **all** targets for drift before the first restore write, refuse if any changed externally, retain baselines/ledger, and abort uninstall. Reapply/update must never replace the first baseline.

| On-car path or property | Apply and restore |
| --- | --- |
| `/system/onebin/neev_kr/featurexml.cfg` | Comment each exact CarPlay wireless, AA wireless, and hotspot removal line if present. Restore original bytes/mode/owner. |
| `/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml` | Add if absent; restore exact bytes or absence. |
| `/system/etc/permissions/com.hkmc.software.connectivity.androidauto_wireless.xml` | Same. |
| `/system/etc/permissions/com.hkmc.software.wifihotspot.xml` | Same. |
| `/system/lib/libleafiaaac.so` | Put rebuilt converter; restore bytes or absence. |
| `/system/etc/seogaemo/wireless-carplay-and-aa/recipes/aa-tbt-48ee793a.recipe`, `aa-tbt-a0bea1c4.recipe`, `carplay-core-wifi.recipe`, `carplay-audio-formats.recipe`, `carplay-utils-converter.recipe` | Put five recipes; restore each exact original or absence and remove newly created empty directories. |
| `/system/bin/app_process` | `sg_zygote_install` installs wrapper; restore exact stock executable bytes/mode/owner. |
| `/system/bin/app_process.seogaemo-stock` | `sg_zygote_install` copies the current original here before swap; restore prior bytes or absence. |
| `/system/lib/libseogaemo_wireless_hook.so`, `/system/framework/seogaemo-wireless-hook.jar` | `sg_zygote_install` installs runtime; restore exact bytes or absence. |
| `/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/enable`, `enabled`, `starts`, `disable` | `sg_zygote_install` owns guard state. The wrapper increments `starts`, resets it after 120 seconds alive and obeys `disable`; the API must track/export/restore all generated state. |
| `persist.ium.support`, `persist.sys.iap2.wifi`, `persist.iap2.iden.wireless` plus `/data/property/` backing files | Set `carplay`, `t`, `t`; restore live and backing state, including absent vs empty. |
| Potential `/data/dalvik-cache/system@framework@seogaemo-wireless-hook.jar@classes.dex` and `/data/data/com.daudio.app.dipo/app_seogaemo-wireless-dex/` | Android may generate optimized DEX. Package API must track and remove only this module's generated entries and restore any pre-existing bytes. Dalvik can regenerate cleanly after stock restoration. |

No stock app APK/odex, manufacturer native library file, `build.prop`, Wi-Fi client state, ADB property, firewall rule, or service is changed. Android user data created during phone sessions is outside this firmware transaction.

## Shared API integration

- `sg_zygote_install <wrapper-rel-src> <loader-rel-src> <jar-rel-src>`: one guarded ledger transaction. Validate the three relative payload paths and ownership, preserve original `/system/bin/app_process` to `/system/bin/app_process.seogaemo-stock` before replacing it, install the wrapper, loader and JAR with exact modes/owners, establish `enable`/`enabled` boot state, and prepare the 3-start/120-second guard. On any failure, roll back using the first baselines. `sg_revert_all` restores all six paths and guard state after full drift preflight. The wrapper always execs preserved stock even when preloading is disabled. The API must capture/export dynamic `starts` updates. The merged shared `common.sh` implements this transaction and records the dynamic `starts` counter before restore.
- `sg_dex_cache_track <system-jar> <app-package>`: before enabling, baseline any existing optimized artifacts attributable to this JAR globally and in the app's `app_seogaemo-wireless-dex` directory; record generated paths/bytes in the ledger after activation; `sg_revert_all` restores only them. The module calls it before `sg_zygote_install` for `/system/framework/seogaemo-wireless-hook.jar` and `com.daudio.app.dipo`. If tracking cannot be safe, fail the install before modifying `app_process`.

No direct `cp`, `mv`, `rm`, `setprop`, remount, service restart, or cache deletion occurs in this module root script. The module-local fake in `test/run.sh` checks the module sequence; the shared `tools/test-runtime.py` exercises the real transaction and restore implementation.

## Copied source and AGPL notice

Copied HMG-API paths (destinations relative to this module's `payload/`):

- `mods/wireless-carplay-aa/src/hmgapi/mods/wireless/{Wireless,AaTurns}.java`, `dipo/{Bridge,CpTurns,Dipo}.java` → `src/hmgapi/mods/wireless/` as `.java.src`; `AaTurns` drops an HMG-API-only projection event.
- `core/runtime/java/hmgapi/hook/{Bridge,Hooks,Points,LoadedApp,MethodHook,ModEntry,ModContext,NativePatch,Car,Cluster,AppPartLoader}.java` → `src/hmgapi/hook/` as `.java.src`; four are trimmed, and new `HmgApi.java` replaces the generic loader.
- `core/runtime/java/hmgapi/engine/MemRecipe.java` → `src/hmgapi/engine/` as `.java.src`, unchanged.
- `core/loader/loader.c`, `core/zygote/zygote.c`, `core/loader/syslib-stubs.sh` → `native/`, modified for Seogaemo paths and without `hmgapid` spawning.
- `mods/wireless-carplay-aa/native/leafiaaac/{leafiaaac.c,build.sh}` → `native/`; output path modified. `data/libleafiaaac.so` is rebuilt from source, not an upstream binary copy.
- `mods/wireless-carplay-aa/recipes/{aa-tbt-48ee793a,aa-tbt-a0bea1c4,carplay-core-wifi,carplay-audio-formats,carplay-utils-converter}.recipe` → `recipes/` unchanged.
- `mods/wireless-carplay-aa/stubs/com/{broadcom,tcc}/...` (six Java compile-time signature stubs and README) → `stubs/` as `.java.src`, unchanged, not in the runtime JAR.

Original headers remain; modified files have a `Modified by HMG-Seogaemo` marker. Root `NOTICE.md` credits HMG-API (VysseCodes), NEMESIS (xwtk), HMG Leafia Plus, and Xposed design lineage. The repository is GNU AGPL-3.0 (`LICENSE`). The core mapper is an independent implementation; `UPSTREAM.md` tracks the HMG-API reference paths.

## On-car validation and recovery

1. Before enabling, save a fresh Car Report and stock app_process, feature file/XML, three persistent properties plus backing-file presence, four `.odex` hashes, and any existing optimized DEX entries. Verify NEMESIS full-root ADB recovery works while Android is down.
2. Build locally, install through NEMESIS, run `hmg-seogaemo-root wireless-carplay-and-aa precheck` then `apply`. Export/review the change ledger. Confirm the stock ADB switch is `0`, then fully restart the head unit; ignition cycling alone may not restart Android.
3. Stream Seogaemo-Wireless, DiPO, Iap2, GPM and kernel logs. Test hotspot association/DHCP, wireless CarPlay music/Siri/calls, wireless Android Auto and a USB drive, cluster/HUD guidance, and native recipe messages. Status is `on` only if zygote maps contain the loader. Repeat after another boot and a package update.
4. Revert through `hmg-seogaemo-root wireless-carplay-and-aa revert`, export ledger, fully restart, compare every baseline byte/absence/property, and test stock wired projection. Uninstall NEMESIS package only after restore succeeds. Fake-root drift test must refuse before any partial restore.
5. On boot failure, after three unsuccessful zygote starts the wrapper should stop preloading and still exec stock. ADB/NEMESIS root can call the root dispatcher to revert, inspect/export ledger, then restart. If dispatcher is unavailable, use a saved ledger and stock executable through NEMESIS full-root recovery; never restore an older firmware's `app_process` over a newer one. The runtime owner must provide and test the exact emergency recovery command before release.

Off-car build/fake-root tests cannot prove KOR Dalvik offsets, native recipe matches, working wireless sessions, or head-unit boot recovery. The shared API and off-car gates pass; do not release before on-car validation passes.
