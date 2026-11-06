Status: current — firmware reference for the advanced-TBT/PE14 path and the AR subsystem. Car-verified behaviour: [cluster-ar-view.md](cluster-ar-view.md), [hud-hologram.md](hud-hologram.md), [hud-junction-view.md](hud-junction-view.md).

# Navigation AR, advanced TBT, and HUD

Target: Ioniq 5 KOR / Gen5W / `NEEV.KOR.*.260902`. Input `libExSLNavi.so`, build ID `c9fc6b0ba98c35c4054041ab3ff4ebddfec8d421`. H = instructions/stock bytecode read directly; M = inference; U = unresolved. Native addresses are ELF virtual addresses with the Thumb bit removed, not runtime ASLR addresses.

## Result

- **H:** A stock native advanced-TBT/pattern call path reaches `NavigationCluster.updateCluDataNew_14(IIIII)` → `IClusterService` transaction **71** → micom function **0x1c**, with eight payload bytes. It carries picture/highlight identifiers, not route coordinates or video. On the car it draws the HUD junction view; the cluster AR view and the HUD hologram come from ordinary `updateTBTInfo` ([cluster-ar-view.md](cluster-ar-view.md), [hud-hologram.md](hud-hologram.md)).

## Proven advanced-TBT/pattern path

| Stage | Instructions / stock bytecode | Confidence |
| --- | --- | --- |
| Periodic publisher | `FrameCluster::ClusterUpdate` **0x23faedc** requires elapsed time >500 ms (`0x23faee8`); calls `InitSendData`, `updateTurnData` (`0x23faef6`), TBT list, road/lane/weather updates, then `sendClusterMessage` (`0x23faf40`). | H |
| Item update | `updateTurnData` **0x23f5d1c** calls `IsNeedUpdate` (`0x23f5dc8`), then `GetItem` (`0x23f5ddc`), `SetTurnData`, `SetTollgateData`, **SetPatternData** (`0x23f5dfa`). Concrete `IsNeedUpdate` **0x2b10548** returns byte `this+0x125` with non-null `this+4`. Failure to obtain an item can abort that publish cycle. | H |
| Pattern pack/change filter | `SetPatternData` **0x23f5660** fills `FrameCluster+0x60`. `sendClusterMessage` compares the eight-byte block to cached state (`0x23f84b6`–`0x23f84ec`); changed data sends at **0x23f850a** only if `this+0x78 == 0`. | H |
| Deferred ordering | TBT-list handling sets `+0x78` at `0x23f8450`. Short-tick branch after >200 ms (`0x23faf5a`) sends **TBT list** (`0x23faf90`), **camera-distance** (`0x23faf9a`), **pattern** (`0x23fafa8`), then clears the flag (`0x23fafae`). This branch is reached when the >500 ms branch is not taken; these are polling thresholds, not exact deadlines. | H |
| Native vector | `sendPatternData` **0x23f72dc** emits IDs **10000..10004** and calls `setClusterSignals(type=4, vector)` at `0x23f73bc`; **0x2074cb8** invokes its registered callback if present. | H |
| Manager | `callBackUpdateCluster` **0x160ca60** → manager virtual `+0x18` → `sendClusterMessage` **0x1625a58**. Non-null vehicle setting required (`0x1625aac`); OS value zero selects Android (`0x1625ab4`); type4 calls `sendClusterPattern_Android` at `0x1625af6`. | H |
| Android filter/target | `sendClusterPattern_Android` **0x16001a0** maps IDs to five ints, suppresses unchanged 20-byte data (`0x1600246`), calls vehicle-setting slot **+0x170**, internal advanced type **0**, count **5** (`0x1600252`–`0x160026a`). GOT `0x4232120` → `g_pVehicleSetting` `0x4290f18`, not AppService. Vtable **0x418a870**, address point +8, resolves slot to **sendAdvancedTBTData 0x17faef4**. | H |
| Native-to-Java boundary | `sendAdvancedTBTData`: non-null int array, type0, count >2 → JNI ID **0x17e4** (`0x17faf54`), callback slot **+0x1b4** (`0x17faf66`, common call `0x17fb068`). Its GOT resolves `g_pNativeToJavaCallBack` **0x42642c0**. Concrete callback registration/implementation into Java not fully traced. | H native; M bridge pairing |
| Matching Java receiver | `NativeCall.sendNaviInsideData` switch **0x17e4** → `com.mnsoft.navi.message.a.b`. Its packed switch starts at **0x17e4**, not zero. >=5 ints → `NavigationCluster.updateCluDataNew_14`; `NoSuchMethodError` → old three-int fallback. Shutdown check can suppress `sendNaviInsideData`. | H |
| Binder/micom | Framework five-int entry → `updateNavigationCluDataNew_14`, Binder **71 / 0x47**, old fallback **70**. Service packs eight bytes, queues handler **0x37**; `sendClu_PE_14` → `ClusterService.sendPacket(0x1c, bytes)`. | H |

Internal type **4**, advanced type **0**, JNI ID **0x17e4**, Binder **71**, handler **0x37**, and micom function **0x1c** are distinct namespaces. No CAN arbitration ID was recovered. Do not route pattern through `CExSLVehicleSettingImpl::sendClusterData` **0x18005b0**: that inspected dispatcher does not handle type4.

`ClusterService.sendPacket(B[B)` checks micom initialized, clones the payload, and invokes an optimized vendor method with `(-113,13,1,13,funcID,copy)`. Those parameters have not been independently decoded as a physical CAN envelope.

### Five fields and exact wire layout

| ID | Java argument | Native item → output | Eight-byte payload |
| --- | --- | --- | --- |
| 10000 | `advanced_tbt_display` A | item u16 `+0x2f` → `FrameCluster+0x60` | 0–1: A high, A low |
| 10001 | `advanced_tbt_direction` B | item u16 `+0x31` → `+0x62` | 2–3: B high, B low |
| 10002 | `advanced_tbt_direction1` C | item u16 `+0x33` → `+0x64`; copied only for **1..65534** (`0x23f5688`–`0x23f5694`) | 4–5: C high, C low |
| 10003 | `pattern_guide_lane` L | output byte `+0x66`; eight item u16s at `+0x39 + 2*k` shifted by k, ORed and truncated (`0x23f56ae`–`0x23f56cc`) | 6: L & 255 |
| 10004 | `pattern_guide_color` K | item `+0x49` low nibble → output `+0x67` low nibble (`0x23f569e`–`0x23f56aa`) | 7: `(K & 15) << 4`; low nibble zero |

H for encoding, U for complete receiver domains. The three wire halfwords are **big-endian**, unlike native item storage. Lane and color copying require item **+0x4a != 0** (`0x23f5698`). The OR loop does not prove its source u16s are Boolean. `InitSendData` resets pattern halfwords (`0x23f5070/7c/82`), lane (`0x23f5090`), and color (`0x23f50c4`–`0x23f50d2`); skipped values do not automatically mean “retain previous visible value.”

### Stock origins: not ordinary turn angles

- **H:** `MakeClusterData` **0x2b11b54** checks target/context pointers, then virtual +0x8c (`0x2b11be8`) resolves via vtable **0x41cd5e0** to **IsMainGuide 0x2b10614**. Success copies returned u16s into item +0x2f/+0x31/+0x33 (`0x2b11c14/1a/20`), then lane/color (`0x2b11c30`–`0x2b11c4c`). `IsMainGuide` first tries **IsILSGuide 0x2b1268c**, then **IsJunctionGuide 0x2b13590**. Ordinary-turn generation is the fallback (`0x2b11bee`–`0x2b11c0a`).
- **H:** ILS needs non-null context/output pointers (`0x2b126b6`–`0x2b126c6`), `ILS::IsNeedGuide` and `GetItem(0,...)` (`0x2b126c8/de`). Only source subtype **11** takes advanced-word copying (`0x2b12716`); source display u32 must be unsigned **<=19999** (`0x2b12720`–`0x2b1272a`). B comes from source +0x14; subsequent direction words from +0x18/+0x1c/+0x20 skip -1 (`0x2b1272e`–`0x2b12748`). Complete valid display/direction sets remain U: passing a width/range check does not prove a receiver draws it.
- **H:** Source phase +0x170 maps **1..6 unchanged, 10→7, otherwise0** (`0x2b126ec`–`0x2b12714`). That phase feeds a separate ordinary-turn-item byte, **not ID10000**. Color/lane extraction needs source +0x171 and phase nonzero (`0x2b1274a`–`0x2b12754`); its lane conversion handles source values16..23 (`0x2b12768`–`0x2b12776`). Palette semantics remain U.
- **H:** `ILS::CInformation::ParseItem` **0x2259154** obtains `CJunctionViewILS::GetRendering` (`0x2259176`); its five-word loop copies rendering +0x18/+0x1c/+0x20/+0x24/+0x28 into source +0x10/+0x14/+0x18/+0x1c/+0x20 (`0x22591c2`–`0x22591f6`). These are map-generated rendering/symbol words, not proof B/C are degrees. **The probes' 45/315 are not validated angle encodings, and displays1/2 are not validated pattern IDs.** They are not proven invalid globally either.
- **H, producer example only:** `CRGILSList::GetPatternThreeBranch` **0x279ce08** writes rendering +0x18 = **19**, +0x1c = **20/21/22** (`0x279cecc`–`0x279cedc`, `0x279cef6`), mapping to A=19/B=20,21,22 through the copy above when the subtype11 path qualifies. This is concrete evidence of a pattern-code family, not degree-valued direction. It does not enumerate all valid patterns or establish that these examples draw on NEEV. Full generator family: `GetPatternYBranch` **0x279f7c0**, `MakePatternImageAndSymbolCode` **0x279fcd4**.
- **H:** Junction fallback lazily caches `GetJunctionAutoTurnViewConfigStatus` (`0x2b135c0`) and rejects value **1** (`0x2b135f8`–`0x2b135fe`), then requires `JunctionView::IsNeedGuide`/`GetItem` (`0x2b13600/18`). Its phase mapping is also1..6 and10→7 (`0x2b1361e`–`0x2b1364e`); this two-output fallback does not itself supply the advanced u16s.

**No mandatory periodic PE14 keepalive was found:** both native layers suppress unchanged data. The >500 ms publisher is not a >500 ms resend of identical patterns. A single call can still fail because of wrong identifiers, guidance/view ownership, absent companions, or competing writers (M hypotheses, not demonstrated causes). The >200 ms list/camera/pattern sequence is real; its necessity for rendering is U. `FrameCluster+0x78` is deferred-list state, **not phone projection**; `setPhoneProjectionState` **0x23f531c** writes +0x20 instead. Projection suppression of patterns is unproven.

## Trigger conditions: no fixed 300 m established

- **H:** ILS `IsNeedGuide` **0x2258c28** gets target `(2,2,0)` and requires `IsNeedRGDisplay` (`0x2258c40/4a`). Draw-delay handling uses main target `(1,1,0)`, guide step **>3**, matching RG code, and a user-option flag (`0x2258c6e`–`0x2258cb4`); its other branch compares route sublink indices (`0x2258cbe`–`0x2258cd2`).
- **H:** Junction `IsNeedGuide` **0x2541aac** has generation/platform branches, display eligibility, road-dependent continuity distance `GetContDist` (`0x2541b86`), junction-info availability (`0x2541b8c`), and route-point checks. These do not establish a universal threshold.
- **H:** Separate AR `IsNeedGuide` **0x2531766** delegates `(1,1,0)` to **IsNeedRGDisplay 0x22bf402**; `IsActiveARMode` **0x2531838** examines `(2,1,0)` / `(2,2,0)` and target-change state. These are not proven gates of PE14.
- **H:** `IsNeedRGDisplay` needs target +0x2d0 enabled, guide step **1..6**, and that step's `GetNeedDisplay` flag (**0x22be2f4**). Guide-step selection **0x22c21e4** uses road-dependent `GetGuideDist` **0x22be350** / `GetDynamicGuideDist` **0x22bea74**.
- **H:** AR **next-turn** predicate **0x2531788**, comparison `0x2531808`–`0x2531830`, uses **1.5 × a road-selected user-option threshold**; live option value and relation to the owner's main-turn onset are U.

## Named AR subsystem: geometry and no-op notifications

| Evidence | Interpretation | Confidence |
| --- | --- | --- |
| Proxy `GetAugmentedRealityTurnInfo` **0x21eaa58**, calls `0x21eaa84/90` → adaptor **0x214636c**; adaptor events **0x2145a2c/4a** loop local proxies | DBus-style class names do not prove DBus IPC; these calls are local. | H |
| AR getters **0x1af23d0**, list **0x1af3298** | JSON includes geo position, remainDistance, icon/nextIcon, angles/link type and flags; `turnNode` (`0x1af26be`, key `0x1af27ba`), dynamic walls (`0x1af2ae4`), wrong-entry geometry (`0x1af2c8c`), colored lane data (`0x1af2f4e`). Query representation, not a proven vehicle wire layout. | H |
| Core `GetItem` **0x2531e88** → `GetPointOnRoute` (`0x2531f3e`), distance (`0x2531f4e`) | Geometry is stock-route anchored, richer than maneuver icon/distance. | H |
| `onAugmentedRealityUpdated` **0x1aeee84** | Literal `bx lr`. | H |
| Turn callback **0x1aefa3c**, view callback **0x1aefba4** → AppService +0x244/+0x264 (`0x1aefb1e`, `0x1aefc86`) | Notification JSON has `id` and update type. Concrete vtable **0x418af08** resolves to no-ops **0x1802102/0x1802112**. Runtime override unverified; do not claim those send geometry to Java. | H / U runtime |
| `AugmentedReality::sendExternalMessage` **0x1793474**, dispatch `0x17934ec`–`0x1793528` | Incoming status/init/calibration/animation-complete IDs0..3, not outgoing geometry. | H |
| Support predicate **0x1760da6** → getter **0x175993c**, ctor **0x1758a1c**, setter **0x1759158** | SystemVariant byte +0x32 defaults zero; live NEEV support value unknown. Not a proven PE14 gate. | H / U live |
| AR view validity **0x1965140**, comparison `0x1965152`–`0x196515c` | Inspected screen path accepts values<=1; not physical cluster-mode encoding. | H |
| State/area **0x1760494/ac**, visibility **0x17604c4** → **0x199815a** | External interface getters exist; concrete visibility notification no-op. | H |

## Cluster view mode and HUD, separately

**H:** `onClusterVIewTypeChanged` **0x17845e8** handles pending requests, phone-cluster streaming (`0x17846aa`), current app (`0x17846c4`), request mode (`0x17846d6`), and further screen/agreement/route state. Mode/ownership is worth capturing, but no mandatory PE14 companion frame is established. Concrete AppService `notifyNaviClusterViewModeChanged` **0x18020ac**, response **0x180206a**, available-status **0x18020c0** are no-ops. Physical numeric mode semantics remain U.

`ExSLMapRenderer::setAugmentedRealityMap` **0x1757342** is a scalar setter, not evidence of video transmission (H). Neither cluster-local rendering nor a head-unit secondary-display/video path is proved. PE14 itself cannot carry an image/coordinate array.

- **H:** HUD icon converter **0x1a73968** returns HU resource identifiers/names, not a demonstrated hologram-enable flag or micom code table.
- **H:** `convertTBTInfoToHUD` **0x1eadb1c** builds JSON → `g_pAppService` slot **+0x24c** at **0x1eae25e** → concrete `notifyBasicGuidanceUpdated_HUD` **0x1802106**, `bx lr`. No HUD transmission is proved there.
- **H, current module:** `CpTurns.java.src:918` sends **F2** `[0xf2,count,currentIcon,nextIcon,0,UTF16LE road...]` → `Cluster.turnListNew` → Binder **102**, `updateTPTurnListNew(byte[])`. Distance at `:969` → Binder **77**, `updateNavigationTBTSubInfo2C(9 ints)`, three `(unit,fraction,integer)` groups. This already drives basic HUD guidance; no hologram flag recovered.
- **H:** `sendClusterPatternDist_Android` **0x1609bc8** collects five fields and calls advanced type2 (`0x1609ce6`–`0x1609cf8`) → JNI **0x17e6** (`0x17fb016`); Java consumes four ints.

## Reproduction references

Bounded Thumb extracts: `tmp/navi-re/ar-*.asm`. Bytecode root `tmp/car-sessions/2026-11-04-devdump/analysis/api-inventory/smali/`:

- `HKMC_Navi_ExSL/com/mnsoft/navi/NativeCall.smali:23175`, `:24271`, `:32402`; `message/a.smali:1145`, `:1435`: matching JNI dispatch.
- `automotive/com/hkmc/cluster/NavigationCluster.smali:1504`; `IClusterService$Stub$Proxy.smali:3077`: framework/Binder71.
- `automotive-service/com/hkmc/micom/ClusterService$NaviCluster.smali:11435`, `:11608`, `:10042`: args/packing/handler/micom; `ClusterService.smali:5396`: sender.
- Stock `system/app/HKMC_Navi_ExSL.apk` and `.odex` are present. Optimized quick-vtable indices are not Binder codes; cross-check framework definitions.

ELF data/vtable reads require PT_LOAD translation: second load **file offset = vaddr−0x1000** (vaddr `0x414aa58`, file `0x4149a58`); first-load text/rodata offsets match vaddr. The blanket same-offset data statement in the mechanical index does not apply to these vtables. Literal pools printed as instructions are not executable evidence.
