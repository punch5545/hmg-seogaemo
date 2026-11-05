Status: current — NEEV KOR lane transport audit (2026-11-05) plus firmware-confirmed `stTPLaneInfo` layout (2026-11-05). Physical rendering is still unverified.

# Cluster lane frame (81-byte `stTPLaneInfo`)

Target: `NEEV.KOR.0000.V039.001.260902`. No car access or experiments. Confidence: **H** = explicit bytecode/constants/native instructions; **M** = inferred quick-call target or structural interpretation; **U** = unresolved / external claim only. Related: arrow image IDs [cluster-lane-arrows.md](cluster-lane-arrows.md); turn icons/units [cluster-tbt-icons.md](cluster-tbt-icons.md); binary index [navi-lib-index.md](navi-lib-index.md); upstream send behaviour [carplay-tbt-external/hkmc-cluster-tbt.md](carplay-tbt-external/hkmc-cluster-tbt.md) section 5 (not independent stock validation).

## 1. Firmware-confirmed layout
Source: decrypted stock navigation engine `libExSLNavi.so` (buildID c9fc6b0b…, ARM32 Thumb-2, exported C++ symbols intact; not in the repo, see `tmp/navi-re/`). `NaviDrivingAssistManager::ParserLaneInfoJson(stTPLaneInfo&, std::string)` at vaddr `0x16056b8` parses lane JSON into the struct; `stTPLaneInfo::init` (`0x15ffdaa`) zeroes bytes `0x00..0x50`, so the struct is **81 bytes** (0x51). H.

JSON keys read (exact strings): `lane`, `info`, `totalLaneNumber`, `condition`, `skip`, `busPriority`, `hipass`, `nonHipass`, `hipassLaneNumber`, `remainDistance`, `remainDistanceUnit`, `individualDirection`, `overpass`, `underpass`, `leftPocket`, `rightPocket`. Each trailer field is a JSON array of lane indices OR-ed into a little-endian u16 bitmask (bit per lane).

| Offset | Size | Field (firmware key) | Evidence |
| --- | ---: | --- | --- |
| `0x00` | 1 | `0xF1` subtype (set by the sender, not this parser) | our builder; M |
| `0x01` | 1 | `totalLaneNumber` (lane count, clamped) | `strb [r4,#1]` @`0x160576e`, key@`0x1605750` H |
| `0x02` | 1 | pockets (`leftPocket`/`rightPocket` packed) | `strb [r4,#2]`, keys@`0x1605cbc`/`0x1605dac` H |
| `0x03..0x12` | 16 | per-lane status, one byte per lane (loop) | per-lane pointer write (not `[r4,#imm]`); matches our `[3+k]` M |
| `0x13..0x42` | 48 | per-lane arrows (loop) | our builder `[19+3k]`; parser loop M |
| `0x43` | 2 | **overpass** (고가차도) u16 LE bitmask | `strh [r4,#0x43]`, key `overpass` H |
| `0x45` | 2 | **underpass** (지하차도) | `strh [r4,#0x45]`, key `underpass` H |
| `0x47` | 2 | **busPriority** (버스전용차로) | `strh [r4,#0x47]`, key `busPriority` H |
| `0x49` | 2 | **skip** (닫힌/통과 불가 차선) | `strh [r4,#0x49]`, key `skip` H |
| `0x4b` | 2 | **hipass** (하이패스) | `strh [r4,#0x4b]`, key `hipass` H |
| `0x4d` | 2 | **hipassLaneNumber** (하이패스 차선번호) | `strh [r4,#0x4d]`, key `hipassLaneNumber` H |
| `0x4f` | 2 | **nonHipass** (하이패스 아님/일반 톨) | `strh [r4,#0x4f]`, key `nonHipass` H |

- **This refutes the third-party `nav_mapping.py` trailer order** (0x47=hipass, 0x49=busPriority, 0x4b=nonHipass, 0x4d=individualDirection, 0x4f=possibility). There is no `possibility` u16; `individualDirection` is a JSON key but not one of the seven trailer words (applied elsewhere). The earlier "claimed" 7-pair trailer (overpass, underpass, hipass, busPriority, nonHipass, individualDirection, possibility at 0x43..0x50) is superseded.
- Per-lane status byte semantics (2 bits per arrow, internal 0/1/3/2 -> wire 0/1/2/3) and arrow image IDs: [cluster-lane-arrows.md](cluster-lane-arrows.md).
- Owner observation (2026-11-05, stock navigation, no phone projection): cluster and HUD showed bus-only lanes, hi-pass (ETC) lanes, non-hi-pass toll lanes and recommended-lane highlighting. The 81-byte frame is the only lane payload to the cluster, so the trailer bitmasks and per-arrow recommendation are in use.
- Still to confirm: exact status/arrow code values against captured stock `source=car-nav` 81-byte frames (recorded by the lane-decoder work), matched to real on-road lanes and to the engine `[Lane]` logs (section 6).

### For our CpTurns lane builder
- Status (`0x03+k`) and arrow (`0x13+3k`) layout is consistent with the loop region. Builder fills `0x01` count, `0x02` pockets, status and arrows.
- CarPlay's 0x5204 carries no hi-pass/bus/toll/closed information, so `0x43..0x4f` stay 0 for projection (only stock navigation sets them). Do not fill all bits or set masks without decoded evidence.

## 2. Evidence paths (smali line convention)
Physical file lines (not embedded `.line` numbers). Paths relative to `tmp/car-sessions/2026-11-04-devdump/analysis/api-inventory/smali/`:

| Alias | File |
| --- | --- |
| NC | `automotive/com/hkmc/cluster/NavigationCluster.smali` |
| LI | `automotive/com/hkmc/cluster/NavigationCluster$LaneInformation.smali` |
| CS | `automotive-service/com/hkmc/micom/ClusterService.smali` |
| NV | `automotive-service/com/hkmc/micom/ClusterService$NaviCluster.smali` |
| CH | `automotive-service/com/hkmc/micom/ClusterService$ClusterHandler.smali` |
| TP | `automotive/com/hkmc/micom/protocol/CLUSTER$TE_TX_TP.smali` |
| CP | `automotive/com/hkmc/micom/protocol/CLUSTER.smali` |
| MS | `automotive-service/com/hkmc/micom/MicomService.smali` |
| PR | `automotive-service/com/hkmc/micom/MicomPacketRunner.smali` |
| NAT | `HKMC_Navi_ExSL/com/mnsoft/navi/NativeCall.smali` |
| MSG | `HKMC_Navi_ExSL/com/mnsoft/navi/message/a.smali` |
| DIS | `HKMC_Navi_ExSL/com/mnsoft/navi/e/a.smali` |

Quick field/vtable operands are not fully deodexed; explicit invokes, constants, local names and contiguous operations are stronger than inferred quick-call names ([neev-kor-api-inventory.md](neev-kor-api-inventory.md)).

## 3. Java/Binder transport: every byte is opaque
**No 81-byte parser exists in the Java service.** `updateLaneInfo(byte[])` forwards an opaque array: neither length 81 nor initial `0xF1` is validated (NV:12351, CH:2238, CS:5449). The stock navigation's new lane path also forwards an already-built native array. Byte meanings in section 1 therefore come from the native parser, not Java.

1. `NativeCall.sendClusterParser(byte[], int)` dispatches **type `0x17EF` (6127)** to `NavigationCluster.updateLaneInfo(p0)` without unpacking (NAT:21321, sparse switch NAT:21370); separate from payload byte `F1`. H. Byte 0 not inspected there (NAT:21321); older-format log mentions `F1` at MSG:13059.
2. `NativeCall.sendClusterData(int, int[], double[], String[], byte[])` delegates to `e/a.sendClusterData` (NAT:21142), which recognizes `0x17EF`, null-checks the byte array and forwards via a quick call without constructing lane bytes (DIS:5322, DIS:5654). H unchanged bytes; M for naming that quick call `updateLaneInfo`.
3. Public `NavigationCluster.updateLaneInfo` clones non-null input and calls `IClusterService.updateNavigationLaneInfo(byte[])` (NC:1880, NC:1929); null is logged and ignored (NC:1940), service not called. No length/header/value validation. H.
4. Binder service entry forwards via quick call (CS:13641); `NaviCluster.updateLaneInfo` puts non-null input in Bundle key `LANEINFOTP` and queues handler message **`0x52`** (NV:12351, NV:12392, NV:12406; CH constant:80). H bodies, M linking the unresolved quick target.
5. Handler extracts the array and sends it via `sendTPPacket("TP_TxHandleCon_H_UCLU_193", laneInfoData)` (CH:2238, CH:2254) without splitting, unpacking, transforming, clamping, validating or clearing. H.

### Micom envelope
`sendTPPacket` needs a non-null cluster protocol object (else logs/returns), clones the array, looks up the TP function (drops/logs unknown `-1`), sends type `0x80` (CS:5449). `CLUSTER.getTPValue` delegates to the TP enum map (CP:56); the enum assigns **`0x1A`** (not decimal 193 or ordinal `0x19`) to `TP_TxHandleCon_H_UCLU_193` (TP:433, TP:723, TP:753). H map/constants; M identifying the quick lookup as `getTPValue`.
`ClusterService.sendPacket(byte, byte, byte[])` drops output until micom init, clones data, calls the transport with sender `C3`, receiver `4D`, type `80`, function-high `4D`, function-low `80 OR 1A = 9A` (CS:5338). `MicomService.sendPacket` passes data to the packet runner after byte-range checks (MS:2964). Runner builds one buffer (PR:2180):

```text
FF C3 4D 80 4D 9A LEN_H LEN_L <unchanged lane array> 00
LEN = array.length + 1
For 81 lane bytes: LEN=0x0052; total envelope=90 bytes.
```

H buffer construction and no Java fragmentation; M quick-call chain. The final `00` is explicitly appended; do not invent a checksum interpretation. Runner sends one buffer under a lock, logs/drops when disconnected, disconnects on IOException (PR:1015). Downstream daemon/micom/CAN framing, splitting and rendering are outside this evidence.

### Clearing
Service-side `NaviCluster.updateLaneInfo(null)` calls `sendClearTP()` and clears the last-info string (NV:12429): sends **two zero bytes to `getTPIndex()` for the current mode**, not a fixed lane target (CS:3352, CS:4500). Public `NavigationCluster.updateLaneInfo(null)` never reaches this branch. An all-zero 81-byte payload with byte 0 `F1` is non-null and never invokes it; its visual clearing is unproven. H. No count-zero clear rule is present.

## 4. Older `LaneInformation` path (different format; do not substitute)
- `message/a.v(byte[])` parses an older array (MSG:12596): lane count `input[1] >> 4`; left/right pockets from high/low nibbles of `input[2]`; eight direction bytes `03..0A`; eight recommendation bytes `0B..12`; then one byte each at `13` possible, `14` overpass, `15` underpass, `16` bus-only. Logs name these fields (MSG:13129, MSG:13399, MSG:13619, MSG:13647, MSG:13675, MSG:13703). H offsets/labels; names at quick offsets corroborated by LI declarations and parcel order.
- `LaneInformation` has eight-byte `directionInfo` and `recommendedDirectionInfo` and four byte metadata fields, no arrow enum names (LI:34, LI:74). `isValid()` bounds lane count `0..8`, pockets `0..4`, no direction/metadata checks (LI:196); client validates it (NC:8807). Applies to the object API, not the raw array API. H checks, M field-name attribution.
- Old service serializer makes **24 bytes**: `17 F1 (count<<4) (left<<4|right) <8 direction> <8 recommended> <possible> <overpass> <underpass> <bus>` (CS:16389). `NaviCluster.updateTPLaneInfo(byte[])` copies it to the quick field for `mTPLaneInfoData` and schedules message `0x0F` (NV:20096), **but `sendTPLaneInfo()` immediately returns** (NV:10878): no active sender on this firmware. H copy/message/no-op, M quick field.
- The log banner `0x4F(0xF1)` is shared by both paths (MSG:13059) and is not evidence that old byte positions describe the new array; old `recommendedDirectionInfo` does not prove the new status scheme.

## 5. Our arrow codes and hypotheses (superseded where firmware is known)
No named new-frame lane arrow enum or status unpacker exists in the inspected `com.hkmc.cluster` classes. Our `CpTurns.laneIcon()`: 0 none/unused, 1 straight, 2 slight right, 3 right, 4 sharp right, 5 U-turn either side, 6 sharp left, 7 left, 8 slight left (H our code; stock value space for simple lanes is 1..8, see [cluster-lane-arrows.md](cluster-lane-arrows.md)).
- Hypothesis (third-party claim; consistent with the 2-bit-per-arrow packing in cluster-lane-arrows.md): three occupied arrow slots need separate two-bit statuses. If groups are least-significant-slot first, three normal arrows would be `0x15` and three recommended `0x2A`; current `1`/`2` populates only slot 0. Group order, slot association, `0`/`3` meaning and the fourth group are unknown. U.

## 6. Stock engine lane log (owner drive 2026-11-04, input side only)
From `tmp/car-sessions/2026-11-04-tbt/live/logcat-mac.log`: stock engine (`com.mnsoft.navi`, `Navi: [NEngRG][P][Lane]`) logs each lane set before it becomes the frame. No 81-byte output in this log (`updateLaneInfo` logged only `[B@...`), and no parallel CarPlay 0x5204 (dev mode off on the first drive; the lane recipe crashed iap2server on the second): producer **input**, not a verified byte mapping. Format: `[Lane] rd:<m> [lane:<t> level:<l> vari:<v> over:<o> skip:<s> (dir:<d> idx:<i> valid:<vv>)...]... RHD:<r>`.
Observed hypotheses (M, position/context only):
- `rd` = distance to lane point in metres (counts down); `RHD` = right-hand-drive flag (0 here).
- Per lane, repeated `dir`/`idx`/`valid` triples = arrows drawn in that lane; `over` tracks the group (1 = one arrow, 2/3 = multi-arrow). `(dir,idx)` pairs seen: (0,1) dominant in through lanes = straight; (1,2), (3,3), (6,5), (6,10), (9,7), (9,14), (11,8), (0,16)+(3,18). `idx` (1..18) looks like an arrow-sprite index incl. combinations; `dir` a direction class. Mapping to `laneIcon` 0..8 unproven. `dir:9` must not be treated as arrow code 9.
- `vari:1` marks the lane leading along the route (highlighted/recommended); on the turn lane in these samples.
- A 12-lane toll-plaza set (`rd:294`) shows `lane:2 valid:2` lanes interleaved with `skip:1`: **`lane:<type>` is the lane category** (0 normal, 2 special/ETC/hi-pass/bus, coloured on cluster+HUD) and **`skip:1` = closed/unusable**. Engine-side origin of the trailer bitmasks; confirms hi-pass/bus/toll/closed state (offsets now known from section 1). Capture stock `source=car-nav` frames at the same plaza to pin bit order.
- Supplied native log `rd:216 [lane:0 level:0 vari:1 over:1 skip:0 dir:9 idx:7 valid:1]` has no matching field-to-byte construction in the smali. Native lane model flags, ordering and filtering remain U.

## 7. CpTurns comparison and recommended changes
Reference: `modules/wireless-carplay-and-aa/payload/src/hmgapi/mods/wireless/dipo/CpTurns.java.src` (`lanes`:268; frame:294; `laneOne`:351; `laneIcon`:384; blank frame:827).

| Finding | Confidence / recommendation |
| --- | --- |
| Builder emits exactly 81 bytes, count at `01`, marks at `03+i`, arrows at `13+3i`; `0B..12` and `2B..50` zero. | H builder. Preserve as baseline capture; stock compatibility unproven. |
| `laneOne` collapses any route-angle list to `along=true`, one mark for the entire lane; loses per-arrow recommendation. | H information loss. If stock status is per-slot (cluster-lane-arrows.md), this is the concrete encoding defect: retain each mapped angle's recommendation and pack status by verified slot. No speculative shifts. |
| `dim3` sends 3 and `dim0` sends 0 as experimental lane marks. | H code; visual meanings U. Not established stock dimming states; gate any default change on capture evidence. |
| Metadata trailer always zero. | H omission; U whether zero means unknown/absent/impossible/disabled. |
| Eight-lane and three-arrow caps discard input; both U-turn directions share code 5. | H code; no evidence these violate the stock format. Verify capacity and direction enum first. |
| Blank frame is `F1` plus 80 zeros. | H code; does not invoke `sendClearTP`; keep a separate test for real cluster clearing. Null via the public API is a no-op, so not a replacement. |

No stock-proven numeric encoding bug can be asserted from the smali alone. Next step: retain enough input information for verified encoding, change bytes only after decoding native construction or paired input/output captures.

## 8. Remaining evidence to collect
- Stock `libExSLNavi.so` and dependencies from the installed navigation app's native library dir (candidate `/data/app-lib/com.mnsoft.navi*/`); not pulled for the smali audit, kept private under `tmp/`. Already traced: `ParserLaneInfoJson`, `getLaneItemImageID` ([cluster-lane-arrows.md](cluster-lane-arrows.md)). Still open: JNI producer for callback `0x17EF` (both `sendClusterParser`/`sendClusterData`) and the format strings for `rd`, `lane`, `level`, `vari`, `over`, `skip`, `dir`, `idx`, `valid`.
- Open native answers: bytes `0x03..0x12` exact status layout (16 bytes vs builder's 8), full raw `dir` -> index map, lane trimming/order, count-zero/clear output and lifecycle. If symbols do not expose them, a later authorized passive capture of stock method arguments paired with native lane logs.
- Native evidence will not prove downstream physical rendering or CAN arbitration; needs separate evidence.
