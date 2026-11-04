Status: current — offline firmware reference, reviewed 2026-11-04. On-car verification is deferred.

# NEEV KOR vehicle and vendor API inventory

This reference covers the owner's Hyundai Ioniq 5 KOR head unit, `NEEV.KOR.0000.V039.001.260902`, onebin `neev_kr`, Android 4.4.2 / API 19. It inventories existing APIs for future modules. API existence does not prove working vehicle support, unprivileged Binder access, correct units or reversible writes. No car commands were executed, and no catalogue points or maps were changed.

The full private inventory is at `tmp/car-sessions/2026-11-04-devdump/analysis/api-inventory/` in the main checkout. Its 17 domain JSON files, plus `platform-other.json`, contain exact declarations, fields/constants, Binder transactions, attributed property/Settings/intent/provider callsites, manifests, init blocks and exported JNI symbols. It includes readable domain indexes, raw smali and two implementation reviews. Keep that directory out of git: its source dump contains personal data. This document contains stock API names, generic keys and sanitized findings only.

## Evidence and coverage

Inputs are the 2026-11-04 devdump's framework/app odex, available APK/JAR DEX, native libraries, rootfs init files and existing Binder analysis. The 2026-11-03 baseline distinguishes original stock `app_process` from the modified one. HMG-API and factory82 were installed at dump time; `hmgapi-hook.jar`, NEMESIS APK and replaced `app_process` are excluded as stock API evidence. Runtime observations can reflect those mods. Upstream references supplied naming context only; no upstream code was copied.

Baksmali 2.5.2 disassembled all 125 odex and three stock archives with embedded DEX: `HKMC_RadioGE.apk`, `IpodUsbdeviceManager.apk` and `com.broadcom.bt.jar`. Exact declarations, constants and explicit invokes are evidence; quick virtual/field offsets remain unresolved. A targeted deodex attempt failed on vendor field/vtable layouts, so its output is excluded. Absence of explicit callers is not proof of no runtime caller. The supplied raw DEX parser was run with `python3 -I` for Stub/signature checks. Available manifests were read with host `aapt dump xmltree`. Native inspection covers exported JNI symbols and Java native declarations; dynamic `RegisterNatives` tables and native semantics remain gaps.

| Indexed evidence | Count |
| --- | ---: |
| Odex / additional stock embedded DEX | 125 / 3 |
| Class declarations | 61,129 |
| Method declarations | 528,465 |
| Fields and constants | 348,148 |
| Captured registered Binder services / transactions | 108 / 4,358 |
| Binder Stub interfaces, including bound interfaces | 516 |
| Explicit attributed callsites / classified literals | 21,815 / 21,046 |
| Native libraries / exported JNI symbols | 367 / 957 |
| Init service, trigger and import blocks | 262 |
| Available APK manifests / missing APKs with odex | 76 / 12 |

Counts include platform/bundled libraries and repeated declarations across artifacts. Domain assignment overlaps by package/class/artifact names; do not sum per-domain totals. `platform-other.json` preserves unmatched declarations. The 108 services come from the captured ServiceManager list; bound iAP2/Bluetooth profile interfaces are additional interfaces, not invented global service names. String tracking is a lower bound: it does not fully resolve branches, argument positions, quick calls or constructed keys. Literal presence alone does not establish a usable exported action or provider.

## Risk and signature notation

- **RO**: a reviewed cached/local read, or observing an existing callback without changing its result.
- **RO***: getter semantics only; implementation side effects and permissions need review before execution.
- **W**: vehicle, UI, subscription, protocol, network or persistent state change. Unreviewed operations default to W.
- **D**: dangerous reset/shutdown/secure boot/factory/update, raw diagnostic or loss-of-control/recovery operations.

Vehicle controls below are W (vehicle-affecting); reset/security/boot/low-level control paths are D. A return value or a name starting with `get` does not establish RO. Reading a field is RO; changing it is W/D according to its use. Observing a callback is RO; invoking or rewriting it is W. Local automatic risk labels are conservative triage and remain provisional, never a root execution allowlist. Future modules need API 19/Java 7, mapped/verified targets, fixed validated root verbs, package API ownership/ledger, confirmation and exact restoration under the runtime contract.

Methods use exact DEX descriptors: `I` int, `Z` Boolean, `F` float, `J` long, `D` double, `V` void, `[` array, `Lpkg/Class;` reference. Append a method to its declaring class. Transaction numbers are decimal and specific to this firmware. Evidence shorthand `automotive` means `system/framework/automotive.odex`; `automotive-service` means its server implementation odex. App evidence means `system/app/<name>.odex` unless an archive is named.

## Useful entry points by domain

### Battery, charging and EV

`com.hkmc.hev.IHEVService`, service `com.hkmc.micom.hev`, declares these in `automotive`; `com.hkmc.micom.HEVService` in `automotive-service` supplies the reviewed cache/control implementations.

| Exact method | Tx | Risk | Use and limit |
| --- | ---: | --- | --- |
| `getHEVStatus(I)I` | 3 | RO | Existing catalogue `framework.hev_service.hev_status`; reads cached HEVFlag map. |
| `getChargingInfoGen4()[I` | 28 | RO | Clones cached charging array; schema/units need confirmation. |
| `getLastIGN3Status()Z` | 10 | RO | Cached ignition-related flag; distinct from vehicle-ready. |
| `setTargetSOC(II)V` / `setTargetSOCext(IIII)V` | 41 / 42 | W | Charging-target control; selector/range/restore unverified. |
| `setV2LMode(I)V` / `setV2XDischrgLimSocVal(I)V` | 54 / 57 | W | Changes export mode/discharge limit. |
| `setBatteryConditioning(I)V` | 61 | W | Sends MICOM packet, input narrowed to byte; not a status getter. |
| `setEVReset()V` | 60 | D | EV reset; never a verification command. |

Exact `com.hkmc.hev.HEVManager$HEVFlag` values are selectors, not declaration-order indexes:

| Constant | Decimal | Constant | Decimal |
| --- | ---: | --- | ---: |
| `DISPLAY_SOC` | 25 | `CHARGE_STATE` | 7 |
| `CHARGE_STATE_SOC_PC` | 13 | `CHARGING_INTEGRATED_REM_TIME` | 21 |
| `TARGET_SOC_SLOW` | 34 | `TARGET_SOC_QUICK` | 35 |
| `VCMS_V2LMODE` | 74 | `VCMS_V2GMODE` | 75 |
| `CHRG_V2XDISCHRGLIMSOCVAL_SYNC` | 76 | `EV_BATTERYCONDITIONING` | 94 |
| `VCMS_V2L_EST_DISCHG_RMNTIME` | 106 | `VCU_DTE_V2L_VAL` | 107 |
| `VCMS_PWRDIS` | 108 | `VCMS_CHGSTOP_STA` | 110 |

`HEVStatus.INVALID_VALUE=-1`. `HEVManager$SIGNAL_STATE` distinguishes `NOT_EXISTS=0`, `EXISTS_RECEIVED=1`, `EXISTS_NOT_RECEIVED=2`. Value, existence and freshness are separate. Names suggesting W/Wh/kWh do not prove scaling. Do not render missing signals as real zero.

The HSCAN interface `com.hkmc.telematics.hscan.tm_interface.ITM_HSCANService`, service `tm_hscan_service`, adds complementary APIs in `com.hkmc.ext.hcan.odex`. `TM_HSCANService` / `TM_HSCANServiceEV` in `com.infobank.odex` show live-versus-saved fallback branches. Exact targets and codes appear in the candidate table below. `remainChargeTime()J` calls explicit `*_min` sources, establishing minutes, while invalid encodings and freshness remain unknown.

### Climate / DATC

`com.hkmc.datc.IDATCService`, service `com.hkmc.micom.datc`, `automotive`:

| Exact method | Tx | Risk | Use |
| --- | ---: | --- | --- |
| `getClimateInfo(I)I` | 38 | RO* | Indexed state; unsupported/default values exist. |
| `getVRClimateStatus(I)F` | 41 | RO* | Floating status; selector-specific meaning. |
| `getAirCleaningStepColor()I` | 61 | RO | Categorical indicator, not PM2.5 concentration. |
| `setVRClimateStatus(III)V` | 40 | W | Three integers; shortened two-argument signature is wrong. |
| `setVRClimateFloatVal(IFI)V` | 67 | W | Integer/float/integer; valid selectors/limits unproven. |

`com.hkmc.datc.DatcStatus.getDrTempC()F` and `getDrTempStateC()I` are RO local field reads; the former is the driver-selected setpoint, not measured cabin temperature. `IDATCListener.onDatcStatus(Lcom/hkmc/datc/DatcStatus;)V` is callback tx3. Existing ambient-temperature hooks are outside temperature. New listener registration is W/resource allocation and needs cleanup. Observing an existing delivery is RO.

### Cluster / TBT / navigation

`com.hkmc.cluster.INavigationClusterListener.onHandleBatteryInfoResp([I)V` is callback tx16 (`automotive`), delivered by the real `ClusterService$NaviCluster.onHandleBatteryInfoResp([B)V` parser (`automotive-service`), then consumed by stock `HKMC_Navi_ExSL`. This is the strongest new battery telemetry candidate. The parser expands 11 bytes into eight integers:

| Index | `Navigation$BATTERY_INFO_RX` constant | Decoding |
| ---: | --- | --- |
| 0 | `XEV_SOCDIS` | Byte 0 |
| 1 | `XEV_MIN_BATT_TEMP` | Signed byte 1 promoted to int |
| 2 | `EV_AVLBL_ENRG` | Bytes 2–3, big endian |
| 3 | `VCRM_SW_VER` | Bytes 4–5, big endian |
| 4 | `DATC_OUT_TEMP_SNSR` | Byte 6, no offset here |
| 5 | `VCU_BATT_COND_IND_STA` | Byte 7 |
| 6 | `VCU_DTE_BASE` | Bytes 8–9, big endian |
| 7 | `VCU_DTE_ADD` | Byte 10 |

Units, offsets, scaling, sentinels and freshness remain unproven, including signed-temperature units. Validate array length. `IClusterService.requestBatteryInfoReq()V` tx109 and `requestDTEInfoReq()V` tx105 on `com.hkmc.micom.cluster` are W/outbound requests, excluded from getter commands. Passive telemetry can be silent until stock navigation requests it.

Existing `com.hkmc.cluster.NavigationCluster` output APIs include `updateTBTInfo(IIFFIIIII)V`, `updateRemainDis(IIII)V`, `updateLaneInfo([B)V` and `updateTPFlexibleDestinationName(Lcom/hkmc/cluster/NavigationCluster$DestinationName;)V`. All are W when invoked. Existing catalogue points cover these and other TBT variants; reuse them. DiPO's `com.daudio.app.dipo.e.u` (RouteGuidanceAnalyzer) explicitly calls these paths. This proves phone navigation → cluster flow; it does not prove vehicle range → phone flow. Respect stock navigation, SLIF/NSCC/HDA/ADAS coordination and stop/ownership lifecycle.

### Vehicle state / speed / gear / power / quickboot

`com.hkmc.micom.ISystemService` on `com.hkmc.micom.system` (`automotive`, cache bodies in `automotive-service`) exposes RO `getVehicleSpeed()I` tx33, `getGearPStatus()I` tx36, `getParkingBrakeStatus()I` tx37, `getEPBStatus()I` tx38 and `getMicomBootMode()I` tx83. Speed scales/error states and gear enums need confirmation on this model; another car's values are not a calibration source.

`com.hkmc.power.IMicomPowerService.getPowerStatus()I` tx1 on `com.hkmc.micom.power` reads an atomic cached value (RO). `isAvDisabled()Z` tx2, `isAvPowerOff()Z` tx3, `isDisplayOffMode()Z` tx11 and `isPreparingSystemOff()Z` tx18 are read-oriented flags (RO*). The local init/property inventory covers quickboot/power triggers; shutdown/reboot controls are D and not wake/read probes.

### Input / buttons / SWRC / custom keys

`com.hkmc.mode.IModeManager` on `com.hkmc.micom.mode` (`automotive`) has RO* `getCustomKeyAction()Ljava/lang/String;` tx38, `getSwrcCustomKeyAction()Ljava/lang/String;` tx39 and `getKeyAction(I)Ljava/lang/String;` tx43. `setCustomKey(Ljava/lang/String;)Z` tx37 and `setKey(ILjava/lang/String;)Z` tx42 are persistent W. Do not map selectors 3/5 to particular hardware solely from unresolved quick calls.

`com.hkmc.micom.ModeKeyDispatcher.setSwrcCustomKey(Ljava/lang/String;)Z` (`automotive-service`) writes `persist.sys.key.swrccustom`. Restore paths refer to `/oem_data/mode/persist.sys.key.swrccustom`; head-unit custom key has a separate `persist.sys.key.custom` property/backing file. Existing key interception hooks already cover the dispatch path. Input-policy `com.hkmc.server.IHkmcInputPolicyService.disableKeyInput([Lcom/hkmc/server/ExceptionKeys;)Z` tx7 on `com.hkmc.inputpolicy` is D/loss of controls, not a shortcut API.

### Display / brightness / theme / day-night

RO cached `ISystemService.getRheostatLevel()I` tx93, `getClusterBrightness()I` tx97 and `getAvTailStatus()Z` tx98 provide dimmer/cluster/tail signals. Cluster brightness is not necessarily main-screen brightness. `com.hkmc.theme.IThemeScheduleService.getMode()I` tx2 on `com.hkmc.theme` is RO*; `setMode(I)V` tx1 is W. `com.hkmc.displaysettingfw.IDisplaySettingFwService.isAdmEnable()I` tx3 is RO*, `setAdmEnable(I)V` tx2 is W on `com.hkmc.displaysettingfw` (all `automotive`).

`android.app.ITelechipsDisplayManager.setOutputResolution(I)V` tx5 on `telechips_display` (`framework.odex`) is D: resolution/output changes can black out the unit. `getOutputResolution()I` tx4 is RO*. No output-control experiment is proposed.

### Audio / sound / volume / source routing

`com.hkmc.media.IMicomAudioService` on `com.hkmc.micom.audiosetup` (`automotive`) has RO* `getVolume(II)I` tx8, `getVolumeControlEnabledStream()I` tx83, `getCurStreamType()I` tx118 and `getStreamTypeParameter(I)Lcom/hkmc/media/StreamTypeParameter;` tx142. `setVolume(III)V` tx7, `setDiPOCallVolume(III)V` tx30 and `setDiPOsiriVolume(III)V` tx33 are W; call/Siri/media streams are distinct, and valid ranges are unproven.

`android.media.IAudioService.getCurrentAudioFocus()I` tx66, `getFocusedPackageName()Ljava/lang/String;` tx90 and `hasAudioFocusEntry(Ljava/lang/String;)Z` tx91 on `audio` (`framework.odex`) are RO*. Focus requests, source changes and media-key dispatch are W. Avoid publishing foreground package/device strings from runtime outputs without review.

### Phone projection / CarPlay DiPO / iAP2 / Android Auto GPM / wireless

| Declaring class | Exact method | Service / tx | Risk |
| --- | --- | --- | --- |
| `android.phoneprojection.IPPManager` | `getWirelessConnectState()I` | `phoneprojection` / 77 | RO* |
| same | `getPhoneProjectionAvState()Landroid/phoneprojection/PhoneProjectionAVState;` | same / 65 | RO* |
| same | `connectPP(ILjava/lang/String;Ljava/lang/String;I)V` | same / 53 | W |
| same | `setPPFullscreenMode(Z)V` / `getPPFullscreenMode()Z` | same / 83 / 84 | W / RO* |
| `com.hkmc.gpm.IGpmInterfaces` | `getGPMConnectedMode()I` | `gpm` / 22 | RO* |
| `com.tcc.iap2.IIap2Service` | `Iap2SvcGetState()I` | bound iAP2 / 4 | RO* |
| same | `Iap2SvcSendVehicleStatusData(Lcom/tcc/iap2/Iap2VehicleStatusMsg;)Z` | bound iAP2 / 8 | W |
| same | `Iap2SvcGetRouteGuidanceRegisteredComponentCount()I` | bound iAP2 / 81 | RO* |

IPPManager is in `framework.odex`, GPM interface in `framework2.odex`, iAP2 in `com.tcc.iap2.odex`, implementation in `Iap2ServerApp.odex`. iAP2 is bound, not a captured ServiceManager name; do not invent `service call iap2` commands. Starting/stopping route updates (tx77/78) changes subscriptions and is W.

The electric vehicle path is promising but incomplete:

- `Iap2Const$VehicleStatus.RangeElectric=11` and `RangeWarningElectric=15` exist. `Iap2InitInformationMsg` initializes their capability flags to 0. `checkSupportsFeatures()V` reads `persist.sys.iap2.vehiclestatus`, while `GetSupportVehicleStatus()Z` gates the global support flag. That property alone neither enables electric-specific flags nor supplies telemetry.
- Exact builders `getVehicelInformationGroup(Lcom/tcc/iap2/Iap2NotiMsg;)Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;` and `getVehicelStatusGroup(Lcom/tcc/iap2/Iap2NotiMsg;)Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;` retain the stock spelling. Information advertises identity/engine type; status advertises supported status IDs. Both are W when modified.
- `Iap2VehicleStatusMsg` has `mItemCnt:I`, `mItemId:[I` and `mItemValue:[I`, with fixed four-entry arrays. Tx8 checks readiness/support/native handle and serializes typed payloads for IDs 3–6 and 9–16, including electric range 11 and warning 15; IDs 7/8 have no typed payload on this path, through native `Iap2SendVehicleStatus(JLandroid/os/Parcel;)Z`. However, `InitVehicelStatus()V` / `UpdateVehicelStatus(Landroid/os/Parcel;)V` generic cache handling covers IDs 3–6. No active electric-data producer was proved; quick opcodes prevent proving absence. Capability, counts/defaults, units, freshness and outbound producer must be designed together.
- GPM `com.google.android.projection.protocol.SensorSource.reportFuelData(IIZ)V` forwards to `nativeReportFuelData(ZIZIZZ)I` (`HKMC_GPM.odex`). With null override field, presence flags are zero. `GalReceiver` initially advertises UNKNOWN fuel/EV connector arrays. Existing `gpm.sensor_source.init`/receiver hooks cover capabilities; wrapper values alone may report no data. Resolve presence/override semantics and native units before injection.
- DiPO `com.daudio.app.dipo.telechips.CarPlayService.updateVehicleInformation(ZZFJ)Lcom/daudio/app/dipo/telechips/CarPlayReturn;` is toll collection/navigation-aided driving/touch sensitivity/haptic data, **not EV SOC**. GPM's `android.intent.action.infobank.aa.client.lowbattery` describes the phone battery, not the HV battery.

No evidence here establishes working Apple Maps or Android Auto EV routing, charging-stop planning or SOC consumption on a phone.

### Bluetooth

`android.bluetooth.IBluetoothManager.isEnabled()Z` tx5 on `bluetooth_manager` is RO* (`framework.odex`). Bound `android.bluetooth.IBluetoothA2dp.selectStream(Landroid/bluetooth/BluetoothDevice;)Z` tx3 is W/audio routing; `getCurrentStreamingDevice()Landroid/bluetooth/BluetoothDevice;` tx4 is RO* but exposes private device identity.

Embedded `com.broadcom.bt.jar!/classes.dex` supplies `com.broadcom.bt.hfdevice.IBluetoothHfDevice`: `getAudioState(Landroid/bluetooth/BluetoothDevice;)I` tx6, `getCallStateInfo(Landroid/bluetooth/BluetoothDevice;)[I` tx15 and `getMobisBTStatus(I)I` tx41 are RO*. `connectAudio()Z` tx7, `dial(Ljava/lang/String;)Z` tx16 and `startSiri()I` tx43 are W. `sendVendorCmd(Ljava/lang/String;)Z` tx22 is D/raw vendor control. Implementation is in `Bluetooth.odex`. Never export link keys, contact/call records, phone names or addresses.

### Wi-Fi / hotspot

`android.net.wifi.IWifiManager.getWifiEnabledState()I` tx14 and `getWifiApEnabledState()I` tx30 on `wifi` (`framework.odex`) are reviewed state reads (RO). `getWifiApMode()I` tx104 is RO*. `setWifiApEnabled(Landroid/net/wifi/WifiConfiguration;Z)V` tx29 and `setWifiEnabledForPersist(ZZ)Z` tx49 are W; `resetWifiApConfiguration()V` tx98 is D/config reset. `getWifiApConfiguration()Landroid/net/wifi/WifiConfiguration;` tx31 can disclose credentials; omit from shared output.

Stock `HKMC_WifiSettings` writes `tcc.wifi.display` / `tcc.wifi.display.command` and Settings static-IP keys (attribution below). The owner previously verified hotspot + wireless CarPlay with HMG-API on this car; that does not establish HMG-Seogaemo behavior or STA/AP concurrency. The one-radio exclusion remains.

### USB / storage

`android.hardware.usb.IUsbManager.getDeviceList(Landroid/os/Bundle;)V` tx1 uses an output Bundle; `getCurrentAccessory()Landroid/hardware/usb/UsbAccessory;` tx3 is RO* on `usb` (`framework.odex`). Typed clients are preferable; accessory serials can be private. `setCurrentFunction(Ljava/lang/String;Z)V` tx15, `setMassStorageBackingFile(Ljava/lang/String;)V` tx16 and `clearUsbDebuggingKeys()V` tx19 are D/recovery, storage or authentication changes. Do not use them for discovery.

Init records include `sys.usb.config` triggers such as `carplay`, `carplay_connect`, `carplay_w_ea`, `digital_audio` and `host`. They describe role/function configuration, not commands to execute. The embedded `IpodUsbdeviceManager.apk` DEX is included in the local index.

### OTA / software update / factory / engineering

`com.hkmc.device.IEngineeringMode.getOtaMode()I` tx65 / `getOtaPct()I` tx66 on `com.hkmc.engineeringmode` are RO*; `requestDiagnose([B)V` tx5, `requestShutdown()V` tx7, `requestFactoryReset()V` tx8 and `setCanTestOnOff(Z)V` tx25 are D. `getBSoc()I` / `getFSoc()I` read Java `System.getProperty` values `sys.product.soc.bsoc` / `sys.product.soc.fsoc`; they are not proven HV battery telemetry.

`com.hkmc.factory.IMicomFactoryService` on `com.hkmc.factory` exposes `hideConsentScreen()V` tx82 (existing point, W), `responseSysFactoryReset(I)V` tx13 and `responseSysSecureBoot(I)V` tx107 (D). `responseSysUpdate(I)V` tx104 / `responseSysOtaDel(I)V` tx106 are active protocol responses (D), not getters. All these interfaces are in `automotive`.

`TelechipsSystemUpdater` explicitly invokes `android.os.RecoverySystem.installPackage(Landroid/content/Context;Ljava/io/File;)V` (D). OTA intent `com.hkmc.intent.action.CMD_START_TRY_TO_OTA_UPDATE` is a command received by `HKMC_DMClient_OTA`, not a harmless update event. Auto-update keys are useful policy evidence but do not prove a complete OTA blocker or coverage of USB/manual/CCU paths.

### Regulation / driving restrictions

`com.hkmc.regulation.IRegulationManager.getStatus()I` tx1 (existing point) and `getRegulationBStatus()I` tx2 on `com.hkmc.regulation` are cached RO reads (`automotive` / `RegulationService`). Callback spelling is `IRegulationStatusListener.onRequlationBStatusChanged(I)V`, tx2. `setOccupationConsentState(I)V` tx4 is D/policy mutation. Future UI/launch integrations must honor stock restrictions; no bypass is proposed.

### User profile

`com.hkmc.userprofile.IUserProfileService.isGuest()Z` tx40 and `getPrevUserType()I` tx3 on `com.hkmc.userprofile` are RO*. `getUserId()I` tx4 is **W/service activation**: it calls `callLocalService`, constructs `com.mobis.user_profile.intent.action.START_LOCAL_SERVICE` and updates cached state. Prefer observing `IUserProfileListener.onUserSwitched(IZ)V` callback4; the Boolean is `isDoneByReset` in server dispatch. Anonymous slot preferences can stay app-local.

`switchUser(IZZ)V` tx5 is W; `setUserPassword(ILjava/lang/String;)V` tx63 is W/security-sensitive. Password/name/photo/sync payload reads are sensitive even when RO; exclude them from logs/exports. Interfaces `automotive`, server `automotive-service`.

### Launcher / home / menu injection

The existing map resolves `launcher.menu_list` to `com.android.launcher3.view.a.g`: `load()Z`, `by:Ljava/util/List;` and `mLock:Ljava/lang/Object;`. `launcher.menu_entry` is `com.android.launcher3.view.a.Q` with constructor `<init>(Lcom/android/launcher3/view/a/g;Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;III)V`. Editing/adding menu entries is W; RO list observation alone does not establish persistence. These existing points are in `Launcher_WP3.odex`.

Companion `com.hkmc.system.app.homesetting.allmenuedit.z.load()Z` and static `cs:Ljava/util/List;` in `HKMC_HomeSetting_WP.odex` cover menu editing/persistence. Reuse existing `homesetting` points. New `com.hkmc.mode.IModeManager.launchActivity(Landroid/content/Intent;)V` tx3 (`automotive`) is W/foreground launch through mode coordination. Arbitrary packages are not proven allowed; any future module should use fixed validated components and respect rear view/regulation.

Launcher Sports widget constructs `com.hkmc.intent.action.LAUNCH_APPLICATION` with intended `global.mode=177`. Its putExtra/send follow-up is quick-op unresolved; this is evidence of an intended Sports request, not proof of an exported generic launch contract. Missing launcher APK prevents checking exportedness, filters, permissions and resources.

### Voice recognition

`android.voicerecognition.IVRManager.getVRStatus()I` tx1 on `voicerecognition` (`framework.odex`) returns static cached state in `services.odex` (RO); `getVRKakaoStatus()Z` tx3 is RO*. `setVRStatus(I)V` tx2 is W/session coordination.

The distinct vehicle actuator interface `com.hkmc.vr.IVRService` (`automotive`) exposes existing `set_StrgWhlHeated_Cmd_AVN(I)V` (W) and `set_Window_ALL_UPDOWN(I)V` tx29 (D/moving closures). Voice-session state is not vehicle-control permission. Missing `HKMC_VoiceHandler.apk` limits manifest/resource verification.

### Radio / media

`com.hkmc.media.IRadioService` on `com.hkmc.micom.radio` (`automotive`) has RO* `getLastAMFrequency()I` tx22, `getLastFMFrequency()I` tx23 and `getTuningValue()I` tx63. `directTune(IZII)Z` tx50 and `writePresetWithoutTune(IBIB)V` tx47 are W, the latter persistent. Units and band enums need confirmation. `HKMC_RadioGE.apk` embedded DEX is indexed; `RadioService` and `RadioServiceV2` both exist, so choose the active implementation before treating base-class behavior as proof.

## Cross-cutting keys, intents, providers, init and JNI

The private JSON retains exact caller class/method, artifact, evidence line and string registers. The examples below are useful search anchors; RO applies to observing/reading, while sending commands, assigning keys or modifying providers is W/D.

| Kind / exact identifier | Stock reader, writer or consumer | Risk / confidence |
| --- | --- | --- |
| Property `persist.sys.iap2.vehiclestatus` / `persist.sys.iap2.vehicleinfo` / `persist.sys.iap2.route` / `persist.iap2.iden.wireless` | `Iap2InitInformationMsg.checkSupportsFeatures()V`, `Iap2ServerApp` | RO read; W capability write; no live EV values. |
| Property `persist.sys.key.custom` | `ModeKeyDispatcher.setCustomKey(Ljava/lang/String;)Z` writes; `ButtonSettings` `CustomButtonSetting.getCustomKey()V` reads | W setter, RO read; backing file restoration also needed. |
| Property `persist.sys.key.swrccustom` | `ModeKeyDispatcher.setSwrcCustomKey(Ljava/lang/String;)Z` writes; `getSwrcCustomKeyAction()Ljava/lang/String;` reads | W setter, RO read; separate steering binding. |
| Property `tcc.wifi.display` / `tcc.wifi.display.command` | `HKMC_WifiSettings` `com.hkmc.wifisettings.ay` methods `a(Lcom/hkmc/wifisettings/ay;I)V` / `ae(I)V` write, including command `wifioff` | W; obfuscated signatures require local callsite inspection. |
| Settings.System `wifi_use_static_ip`, `wifi_static_ip`, `wifi_static_gateway`, `wifi_static_netmask`, `wifi_static_dns1` | `WifiIpSettings.bE()Z` / `bF()V` read; `bG()V` persists; `onPreferenceChange(Landroid/preference/Preference;Ljava/lang/Object;)Z` writes toggle | RO reads, W writes; stock validation/rollback is more than a key write. |
| Settings `btn_n_left` / `btn_n_right` and driver suffixes | `CustomButtonSettingN$NBtnCustom.get(ILandroid/content/Context;I)I` / `set(ILandroid/content/Context;II)V` in `ButtonSettings` | RO get, W set; namespace in exact callsite JSON. |
| Settings `button_settings_seek` / `tune_knob_function` and profile suffixes | `SwrcUnDnBtnActivity$SwrcUpDnBtn` / `TuneKnobFunction$Interface` in `ButtonSettings` | RO read, W write. |
| Settings `phoneprojection_setting_enable_usb_cable` / `phoneprojection_setting_enable_wireless` with profile variants | Projection settings code, attributed in domain JSON | RO read, W persistent write; not proof of hardware enablement. |
| Settings `gen_auto_update` / `gen_con_auto_update` | `HKMC_DMClient_OTA` `UAClientConsts.y(Landroid/content/Context;)Z` / `ao.z(Landroid/content/Context;)Z` and `OTAClient` `f.a.da()Z` / `db()Z` read | RO read, W policy write; not comprehensive OTA blocking. |
| Receive `android.net.wifi.WIFI_AP_STATE_CHANGED` | `PhoneProjection` wireless service receiver `g.onReceive`; service registers at onCreate | RO observation; AP lifecycle. |
| Receive `android.hardware.usb.action.CARPLAY_DEVICE_ATTACHED` / `CARPLAY_DEVICE_DETACHED` | `HKMC_DiPO` `CarPlayService$1.onReceive` | RO observation; USB lifecycle. |
| Action `com.hkmc.intent.action.LAUNCH_APPLICATION`, intended extra `global.mode=177` | `Launcher_WP3` Sports widget `com.android.launcher3.view.by.1.onClick(View)V` | W send; quick follow-up unresolved; exportedness unproven. |
| Command `com.hkmc.intent.action.CMD_START_TRY_TO_OTA_UPDATE` | `HKMC_DMClient_OTA` `mof.EventReceiver.onReceive` | D/update flow; do not send for verification. |
| URI `content://com.hkmc.daudio.app.ev.db/history` | EV `DrivingHistoryDBProvider` | RO query/W mutation; personal driving history, no broad query proposed. |
| URI `content://com.hkmc.telematics.service.util.db.VehicleSettingProvider/vehicle_info` and `/service_info` | EV utility readers in `HKMC_TAppEV3` | RO query/W mutation; payload may contain identity. Missing APK limits exportedness checks. |
| URI `content://com.mnsoft.navi/State_Table` | EV navigation-state reader in `HKMC_TAppEV3` | RO query/W mutation; provider permissions/schema require confirmation. |
| Provider `com.hkmc.system.app.homesetting.provider.HomeSettingProvider` | `HKMC_HomeSetting_WP` | RO query/W mutation; inspect available manifest for authority/security, not class name alone. |
| Init services `wpa_supplicant` / `p2p_supplicant` | `rootfs_init.tcc803x.wifi.broadcom.rc.txt` | W start/stop; RO inspecting definition/status. |
| Init property triggers `sys.usb.config=carplay` / `host` / `digital_audio` and variants | Private `init.json` gives exact source files, lines and bodies | W/D when activated, depending on recovery/storage effect. |
| JNI exports in `libIap2Acc.so` | `Java_com_tcc_iap2server_Iap2Service_*`; Java `Iap2SendVehicleStatus(JLandroid/os/Parcel;)Z` visible | W outbound protocol; exact export list local. |
| JNI exports in `libIap2Ea.so` | `Java_com_tcc_iap2_Iap2EaNative_*` | W transport; signatures/semantics need Java/native matching. |
| `libtcciap_*_jni.so` | `JNI_OnLoad` / `JNI_OnUnload`, no public Java_ names in inspected exports | Unreviewed W; dynamic registration unresolved, not evidence of absent JNI. |

`wifi_ap_state` in the inspected WifiClientSettings lifecycle is a Bundle key, not evidence for a Settings table. Intent-action literals and content URI strings are candidates until registration, argument use, manifest authority and permissions are confirmed. App resources and native dynamic bindings can hide additional APIs.

## Stubbed or conditional findings

- `TM_HSCANService.getTpmsPress()[I` tx230, `getTpmsTemp()[I` tx229 and `getTpmsWP()[I` tx228 return empty arrays; `getTpmsType()I` tx227 returns 0. Tire-status flags are not pressure measurements. A TPMS dashboard is not supported by this evidence.
- `BCMServiceV2.getBattCondIndSta()I`, `getCHRG_ChgPwrDiffFlag()I` and `getVCMS_ChgPwrDerating()I` override base getters with constant 0. A populated base implementation is insufficient to establish the active car backend.
- HSCAN `getCCM_EvseMainsPwr()D` tx222, `getVCMS_EvsePwrDis()D` tx223 and `getOBC_AcPwrDis()D` tx224 have actual field values only in `CanGEN3CsmEv` among inspected backends; several other CAN backends return 0. CCM/VCMS wrappers check 511.0 invalid and can return saved data. No working charging-power/efficiency feature is claimed until backend/scaling/freshness is proved.
- Engineering `getBSoc`/`getFSoc` are Java properties, profile `getUserId` activates a service, and void `getDoorLockStatus()V` are requests. Name-based RO classification is unsafe.

## Candidate hookpoints

These are proposals only, checked absent the 325-point catalogue in this branch. Existing generic HEV status, receiver/sensor init, launcher list insertion, key interception and TBT output points are reused rather than relabelled new. No candidates were added to `core/hookpoints` or maps.

The table uses requested terms **kind / of / find**: these translate to catalogue `type / owner / match`. All rows are `method`. `of` is an existing stable class ID or a proposed class anchor listed below. **find** gives the exact stock method descriptor on that owner; translate descriptor types into `parameters`/`returns` with the exact `name`, plus member static/native flags after reflection review. Exact stock names are valid firmware evidence, not proof of portability. New class anchors use `type:class, match:{name:<exact class>}`; obfuscated DiPO additionally needs structural evidence before catalogue inclusion. Evidence names locate the defining odex; implementation evidence is separately noted.

| Proposed stable ID | kind | of | find: exact name + descriptor | Risk | Evidence |
| --- | --- | --- | --- | --- | --- |
| `framework.navigation_cluster_listener.on_battery_info_resp` | method | `framework.navigation_cluster_listener` | `onHandleBatteryInfoResp([I)V` | RO observer / W invoke | `automotive` callback16; actual parser `automotive-service` `ClusterService$NaviCluster`. |
| `framework.hscan_service.get_v2l_remaining_time` | method | `framework.hscan_service` | `getVCMS_V2LEstDischgRmnTime()I` | RO | `com.hkmc.ext.hcan`, tx221; `com.infobank` wrapper/cache branches. |
| `framework.hscan_service.get_charge_connect_status` | method | `framework.hscan_service` | `getConnectStatus()I` | RO | Same, tx84; EV plug-signal branches; unavailable also 0. |
| `framework.hscan_service.get_charge_remaining_time` | method | `framework.hscan_service` | `remainChargeTime()J` | RO | Same, tx75; EV explicit minute sources. |
| `framework.hscan_service.get_ev_display_soc` | method | `framework.hscan_service` | `remainBatteryPercentage()F` | RO | Same, tx74; live display SOC/saved fallback. |
| `framework.datc_status.get_driver_temperature_c` | method | `framework.datc_status` | `getDrTempC()F` | RO | `automotive` local field getter; pair validity state. |
| `framework.datc_service.get_air_cleaning_color` | method | `framework.datc_service` | `getAirCleaningStepColor()I` | RO | `automotive` tx61; `automotive-service` no visible send. |
| `framework.regulation_service.get_regulation_b_status` | method | `framework.regulation_service` | `getRegulationBStatus()I` | RO | `automotive` tx2; `RegulationService` cached value. |
| `framework.voice_recognition_service.get_status` | method | `framework.voice_recognition_service` | `getVRStatus()I` | RO | `framework` tx1; `services` static cache. |
| `framework.user_profile_listener.on_user_switched` | method | `framework.user_profile_listener` | `onUserSwitched(IZ)V` | RO observer / W invoke | `automotive` callback4; `UserProfileService` dispatcher. |
| `iap2.init_info.vehicle_information_group` | method | `iap2.init_info` | `getVehicelInformationGroup(Lcom/tcc/iap2/Iap2NotiMsg;)Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;` | W modified | `Iap2ServerApp` identity/engine capability builder. |
| `iap2.init_info.vehicle_status_group` | method | `iap2.init_info` | `getVehicelStatusGroup(Lcom/tcc/iap2/Iap2NotiMsg;)Lcom/tcc/iap2/Iap2NotiMsg$Iap2ObjGroup;` | W modified | `Iap2ServerApp` electric status capability builder. |
| `iap2.binder_service.send_vehicle_status` | method | `iap2.binder_service` | `Iap2SvcSendVehicleStatusData(Lcom/tcc/iap2/Iap2VehicleStatusMsg;)Z` | W | `com.tcc.iap2` tx8; `Iap2ServerApp` actual serializer/native call. |
| `gpm.sensor_source.native_report_fuel` | method | `gpm.sensor_source` | `nativeReportFuelData(ZIZIZZ)I` | W | `HKMC_GPM` private native boundary; presence semantics unresolved. |
| `dipo.route_guidance.parse_update` | method | `dipo.route_guidance` | `a(ILjava/lang/Object;)V` | RO observer / W rewrite | `HKMC_DiPO` `e.c` route model parser; structural matcher still needed. |
| `framework.phoneprojection_service.connect` | method | `framework.phoneprojection_service` | `connectPP(ILjava/lang/String;Ljava/lang/String;I)V` | W | `framework` tx53; pairing/session changes. |
| `framework.phoneprojection_service.set_fullscreen` | method | `framework.phoneprojection_service` | `setPPFullscreenMode(Z)V` | W | `framework` tx83; getter tx84. |
| `framework.mode_service.launch_activity` | method | `framework.mode_service` | `launchActivity(Landroid/content/Intent;)V` | W | `automotive` tx3; mode-coordinated dispatch. |
| `framework.mode_key_dispatcher.set_swrc_custom` | method | `framework.mode_key_dispatcher` | `setSwrcCustomKey(Ljava/lang/String;)Z` | W | `automotive-service` property write/backing restore path. |
| `framework.bluetooth_a2dp.select_stream` | method | `framework.bluetooth_a2dp` | `selectStream(Landroid/bluetooth/BluetoothDevice;)Z` | W | `framework` bound profile tx3. |

Proposed additional class anchors (not catalogue additions); the existing ModeKeyDispatcher class anchor is reused:

| of ID | Exact class find name |
| --- | --- |
| `framework.navigation_cluster_listener` | `com.hkmc.cluster.INavigationClusterListener` |
| `framework.voice_recognition_service` | `android.voicerecognition.IVRManager` |
| `framework.user_profile_listener` | `com.hkmc.userprofile.IUserProfileListener` |
| `iap2.binder_service` | `com.tcc.iap2.IIap2Service` |
| `dipo.route_guidance` | `com.daudio.app.dipo.e.c`; name-only match is provisional |
| `framework.phoneprojection_service` | `android.phoneprojection.IPPManager` |
| `framework.mode_service` | `com.hkmc.mode.IModeManager` |
| `framework.bluetooth_a2dp` | `android.bluetooth.IBluetoothA2dp` |

DiPO parser evidence includes route/destination/current-road/distance/ETA/maneuver/lane model handling and method `a(ILjava/lang/Object;)V`. A portable find rule must combine method shape with verified class strings/call relationships after reliable deodex; no untested structural rule is presented as ready. Stable IDs here are proposals, and a module may need additional Stub/asInterface, model, validity or listener points before use.

## Feature ideas and API dependencies

| Feature slice | APIs needed | Risk and remaining work |
| --- | --- | --- |
| 1. Battery / charging status page | Existing `getHEVStatus` flags 25/7/13/21, HSCAN `getConnectStatus` / `remainChargeTime` | RO; establish validity/freshness/scaling and distinguish disconnected, unavailable and zero. |
| 2. Conditioning / battery-temperature insight | Passive `onHandleBatteryInfoResp` plus HEV flag94 | RO observation; decode units/sentinels, verify stock producer and live cadence. Avoid stub BCMV2 getters. |
| 3. V2L status / remaining-time display | HEV flags74/76/106/107, HSCAN `getVCMS_V2LEstDischgRmnTime` | RO; saved IGN-off fallback must be labeled. No export control. |
| 4. EV routing exploration via iAP2 | Both capability builders, tx8 vehicle-status serializer, valid range producer and model arrays | W protocol work; electric flags default off, generic cache incomplete, units/phone acceptance unverified. Property alone insufficient. |
| 5. Android Auto EV telemetry exploration | Existing receiver/`SensorSource.init` plus `reportFuelData` / native boundary | W; resolve presence flags, fuel/connector advertising, native semantics and producer before claiming routing. |
| 6. Cluster TBT for CarPlay / Android Auto | Existing NavigationCluster outputs and GPM callbacks, DiPO route parser/lifecycle | RO observation or W output; avoid stale/competing navigation, verify enums/units/start-stop cleanup. |
| 7. Climate shortcut / local overlay | `DatcStatus.getDrTempC` + validity, `getVRClimateStatus`, reviewed `setVRClimateStatus(III)V` or `setVRClimateFloatVal(IFI)V` | Overlay RO; shortcut W/vehicle-affecting, needs selector limits, confirmation and restore behavior. |
| 8. Custom key actions / fixed app launch | Separate custom/SWRC getters/setters, existing key hooks, `launchActivity`, launcher + home edit-list points | W; exact property/backing-file restore, fixed components, rear-view/voice/regulation coordination. |
| 9. OTA status and blocking research | `getOtaMode` / `getOtaPct`, auto-update key readers and actual DMClient/RecoverySystem entry paths | Status RO*; blocking W and separately reviewed, must cover manual/USB/CCU flows and never interrupt an active flash. |
| 10. Projection status / fullscreen shortcut | IPP wireless/AV state, `setPPFullscreenMode` + getter | Status RO*; fullscreen W, persistence and split/rear-view restoration unresolved. |
| Audio source / volume companion | Micom volume/stream getters, audio focus, bound A2DP `selectStream` | Read display RO*; routing W, preserve calls/Siri/focus and volume limits. |
| Stock-aware anonymous profile / voice behavior | Passive `onUserSwitched`, `getVRStatus`, regulation A/B status | RO observation; anonymous app-local settings only, no activation getter or personal profile export. |
| Day/night theme coordination | Tail/rheostat/cluster brightness and theme mode | RO observation; W theme change only after stock schedule and restore semantics are understood. |

## Missing files and research gaps

The 12 skipped large APKs are `HKMC_Climate`, `HKMC_EngineerMode`, `HKMC_NatureSound`, `HKMC_RearCamera`, `HKMC_Sports`, `HKMC_TAppEV3`, `HKMC_VehicleSetting`, `HKMC_VoiceHandler`, `Launcher_WP3`, `RightScreenHome`, `SoundSettings` and `WelcomeGoodbye`. Their odex is available, so declarations are indexed. Missing manifests/resources prevent confirming exported activities/services/receivers, permissions/shared UID, intent filters/provider authorities, layouts/menu IDs, feature overlays and localized choices. Do not guess resource IDs from Java strings.

Other gaps: concrete runtime CAN/server selection, getter permissions, error/unsupported/saved states, telemetry freshness, native dynamic registration/packet units, full quick-call dataflow, provider schemas/security and projection phone acceptance. Native symbols alone do not prove safe semantics. APK/JAR embedded DEX is included where present, so the old odex-only 497-Stub count is superseded by 516; this still is not a claim to have recovered every firmware API.

## On-car verification to-do

**Deferred; none of these commands has been executed for this task.** At a later owner-approved session, park safely and keep power stable. Use ordinary read permissions; denied Binder or file access is a finding, not a reason to bypass permissions. Commands below run `adb` on the host and only toolbox-compatible reads on the unit. They do not copy a root helper, register listeners, request fresh CAN packets, toggle properties, send broadcasts or alter stock state.

Confirm firmware and selected generic values (avoid full `getprop`/settings/provider/device dumps that can disclose identifiers):

```sh
adb shell getprop ro.build.product
adb shell getprop ro.mobis.onebindir
adb shell getprop ro.build.version.release
adb shell service list
adb shell getprop persist.sys.iap2.vehiclestatus
adb shell getprop persist.sys.iap2.vehicleinfo
adb shell getprop persist.sys.iap2.route
adb shell getprop init.svc.wpa_supplicant
adb shell getprop init.svc.p2p_supplicant
```

These exact getter transactions have reviewed cached/read-oriented bodies. Run once, inspect exceptions and compare with stock UI; do not poll blindly. No getter argument below is a hardware write selector:

```sh
adb shell service call com.hkmc.micom.hev 3 i32 25
adb shell service call com.hkmc.micom.hev 3 i32 7
adb shell service call com.hkmc.micom.hev 3 i32 13
adb shell service call com.hkmc.micom.hev 3 i32 74
adb shell service call com.hkmc.micom.hev 3 i32 94
adb shell service call com.hkmc.micom.hev 3 i32 106
adb shell service call com.hkmc.micom.hev 3 i32 107
adb shell service call com.hkmc.micom.hev 28
adb shell service call com.hkmc.micom.system 33
adb shell service call com.hkmc.micom.system 36
adb shell service call com.hkmc.micom.system 37
adb shell service call com.hkmc.micom.system 38
adb shell service call com.hkmc.micom.system 83
adb shell service call com.hkmc.micom.system 93
adb shell service call com.hkmc.micom.system 97
adb shell service call com.hkmc.micom.system 98
adb shell service call com.hkmc.micom.power 1
adb shell service call com.hkmc.regulation 1
adb shell service call com.hkmc.regulation 2
adb shell service call voicerecognition 1
adb shell service call wifi 14
adb shell service call wifi 30
adb shell service call tm_hscan_service 74
adb shell service call tm_hscan_service 75
adb shell service call tm_hscan_service 84
adb shell service call tm_hscan_service 221
```

Code lookup: HEV3 is `getHEVStatus(I)I` with constants above; HEV28 is `getChargingInfoGen4()[I`. System33/36/37/38/83/93/97/98 are speed/gear-P/parking-brake/EPB/boot/rheostat/cluster-brightness/tail. Power1 is `getPowerStatus()I`; regulation1/2 are A/B status; voice1 is `getVRStatus()I`; Wi-Fi14/30 are STA/AP state; HSCAN74/75/84/221 are float SOC, long minutes, cable state and V2L remaining time. HSCAN values may be saved; do not infer live freshness. Binder replies include an exception header and encoded values/arrays; typed offline decoding is required for float/long and invalid states. A successful reply does not establish units or accuracy. Getters can log/cache internally; no vehicle actuation was visible in the reviewed bodies.

Do **not** send callback16 to `com.hkmc.micom.cluster`; it is a listener-interface transaction. No shell command here invokes requestBatteryInfo109, profile getUserId4, factory hideConsent82, iAP2 send8, setters, scans, shutdown/reset or secure-boot operations. Passive battery/profile/DATC callback verification needs a separately reviewed API19 observer/hook with lifecycle cleanup; registration itself is W. Observe stock production first. Establish concrete backend and compare measurements without changing vehicle controls to force a test.

Pull the missing APKs later with these host commands. The destination directory is created on the host; `adb pull` only reads the head unit. Keep the resulting files local and out of git. If unreadable, record the denial and plan a separate reviewed collection step.

```sh
mkdir -p tmp/car-sessions/2026-11-04-devdump/missing-apks
adb pull /system/app/HKMC_Climate.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_EngineerMode.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_NatureSound.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_RearCamera.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_Sports.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_TAppEV3.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_VehicleSetting.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/HKMC_VoiceHandler.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/Launcher_WP3.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/RightScreenHome.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/SoundSettings.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
adb pull /system/app/WelcomeGoodbye.apk tmp/car-sessions/2026-11-04-devdump/missing-apks/
```

After collection, inspect manifests/resources on the host, resolve authority/exportedness/permissions, revisit obfuscated matchers, and validate each proposed module's required hooks and restoration design. On-car implementation or control testing remains separate from this inventory.
