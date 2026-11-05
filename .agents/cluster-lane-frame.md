Status: current — offline NEEV KOR lane transport audit, 2026-11-05; 81-byte payload semantics remain unverified because its stock producer is native.

# Cluster lane frame: what the stock dump actually proves

Target: `NEEV.KOR.0000.V039.001.260902`. No car access, source edits, or experiments were performed. Confidence: **H** = explicit bytecode/constants; **M** = inferred quick-call target or structural interpretation; **U** = unresolved / external claim only. Physical rendering is not established by any confidence label here.

**The requested exact 81-byte parser is not present in the inspected Java service.** `updateLaneInfo(byte[])` forwards an opaque array. Neither 81 bytes nor an initial `0xF1` is validated there. The stock navigation's new lane path also forwards an already-built native array. Therefore this dump establishes transport, but cannot establish that our builder matches the stock producer. Do not promote the external layout below into a verified contract.

## Evidence paths and line convention

All smali references below use physical file lines, not the embedded `.line` Java source numbers. Paths are relative to `tmp/car-sessions/2026-11-04-devdump/analysis/api-inventory/smali/`:

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

Quick field/vtable operands in this inventory are not fully deodexed; explicit invokes, constants, local-variable names and contiguous operations are stronger evidence than inferred quick-call names. See `.agents/neev-kor-api-inventory.md`.

## Complete coverage of the proposed 81-byte payload

Offsets are zero-based, hexadecimal. This table covers every byte, grouping repeated fields. **Every byte is opaque to ClusterService** (NV:12351, CH:2238, CS:5449). Meanings in the candidate column come from our builder / the supplied third-party claim, not a recovered stock parser. There are no recovered valid-value enums for this payload.

| Offset | Size | Candidate meaning / values | Stock evidence and confidence |
| --- | ---: | --- | --- |
| `00` | 1 | `F1` lane subtype | Our builder uses `F1`; MSG's older-format log mentions `F1` at MSG:13059. New path does not inspect byte 0 (NAT:21321). **U** for new-frame subtype semantics. |
| `01` | 1 | Lane count, builder `0..8` | Not inspected or bounded (NV:12351, CH:2238). **U**; older format uses the high nibble instead, see below. |
| `02` | 1 | Unknown; builder zero | No parser. Pocket-lane interpretation from the older format cannot be transferred. **U**. |
| `03..0A` | 8 | One status byte per lane `i=0..7` at `03+i`; builder `1` normal / `2` highlighted, experimental `0`/`3` | No status unpacking (CH:2238). Third-party claim: four two-bit direction statuses per byte. **U** for either rendering interpretation. |
| `0B..12` | 8 | Unknown; builder zero | No parser (CH:2238). In particular, do not label these old `recommendedDirectionInfo` bytes in the new frame. **U**. |
| `13..2A` | 24 | Three arrow bytes per lane `i`: `13+3i`, `14+3i`, `15+3i`; builder codes `0..8` | No arrow decoding or enum lookup (CH:2238). **U** for stock meaning. |
| `2B..42` | 24 | Unknown; builder zero | No parser (CH:2238); no evidence that this region is reserved or safely ignorable. **U**. |
| `43..44` | 2 | Claimed LE lane-bitmask: overpass | Not read (CH:2238); no confirming decode found. **U**. |
| `45..46` | 2 | Claimed LE lane-bitmask: underpass | Same. **U**. |
| `47..48` | 2 | Claimed LE lane-bitmask: hipass | Same. **U**. |
| `49..4A` | 2 | Claimed LE lane-bitmask: busPriority | Same. **U**. |
| `4B..4C` | 2 | Claimed LE lane-bitmask: nonHipass | Same. **U**. |
| `4D..4E` | 2 | Claimed LE lane-bitmask: individualDirection | Same. **U**. |
| `4F..50` | 2 | Claimed LE lane-bitmask: possibility | Same. **U**. |

The seven claimed trailer pairs fit mathematically into the last 14 bytes, but that does not prove endianness, signedness, bit-to-lane order, flags, or required defaults. **Verdict: neither confirmed nor refuted by this stock Java evidence.** Their correct description here is unverified, not false.

## Stock navigation and Binder path

1. `NativeCall.sendClusterParser(byte[], int)` dispatches **type `0x17EF` (6127)** to `NavigationCluster.updateLaneInfo(p0)` without unpacking the array (NAT:21321, sparse switch NAT:21370). This dispatch key is separate from any payload byte `F1`. **H**.
2. The other native-facing entry, `NativeCall.sendClusterData(int, int[], double[], String[], byte[])`, explicitly delegates to `e/a.sendClusterData` (NAT:21142). That dispatcher recognizes `0x17EF`, checks its byte-array argument for null and forwards it through a quick call; it does not construct lane bytes (DIS:5322, DIS:5654). **H** for unchanged bytes; **M** for naming that quick call `updateLaneInfo`.
3. Public `NavigationCluster.updateLaneInfo` clones non-null input and explicitly calls `IClusterService.updateNavigationLaneInfo(byte[])` (NC:1880, NC:1929). It logs and ignores null (NC:1940); it does not call the service for null. No length/header/value validation. **H**.
4. Binder service entry forwards to its navigation object through a quick call (CS:13641); `NaviCluster.updateLaneInfo` places non-null input into Bundle key `LANEINFOTP` and queues handler message **`0x52`** (NV:12351, NV:12392, NV:12406; CH constant:80). **H** for the bodies, **M** for linking the unresolved quick target.
5. Handler extracts that array and explicitly sends it through `sendTPPacket("TP_TxHandleCon_H_UCLU_193", laneInfoData)` (CH:2238, CH:2254). It does not split, unpack, transform, clamp, validate or clear it. **H**.

## Micom envelope and clearing

`sendTPPacket` requires a non-null cluster protocol object; otherwise it logs and returns. It clones the array, looks up the TP function and drops/logs an unknown function (`-1`), otherwise sends type `0x80` (CS:5449). `CLUSTER.getTPValue` delegates to the TP enum map (CP:56); the enum assigns **value `0x1A`**, not decimal 193 or enum ordinal `0x19`, to `TP_TxHandleCon_H_UCLU_193` (TP:433, TP:723, TP:753). **H** for map/constants; **M** for identifying the quick lookup as `getTPValue`.

`ClusterService.sendPacket(byte, byte, byte[])` drops output until micom initialization, clones the data and calls the transport with sender `C3`, receiver `4D`, type `80`, function-high `4D`, function-low `80 OR 1A = 9A` (CS:5338). `MicomService.sendPacket` passes data to the packet runner after byte-range checks (MS:2964). Runner builds a single buffer (PR:2180):

```text
FF C3 4D 80 4D 9A LEN_H LEN_L <unchanged lane array> 00
LEN = array.length + 1
For 81 lane bytes: LEN=0x0052; total envelope=90 bytes.
```

**H** for buffer construction and no Java fragmentation; **M** for the quick-call transport chain. The final `00` is explicitly appended; do not invent a checksum interpretation. Runner sends one buffer under a lock, logs/drops when disconnected and disconnects on IOException (PR:1015). Downstream daemon/micom/CAN framing, splitting and rendering are outside this evidence.

Service-side `NaviCluster.updateLaneInfo(null)` calls `sendClearTP()` and clears the last-info string (NV:12429). That clear sends **two zero bytes to `getTPIndex()` for the current mode**, not a fixed lane target (CS:3352, CS:4500). Public `NavigationCluster.updateLaneInfo(null)` never reaches this branch. An all-zero 81-byte payload with byte 0 `F1` is also non-null and never invokes this branch; its visual clearing behavior is unproven. **H**. No count-zero clear rule is present.

## The older LaneInformation path is a different format

`message/a.v(byte[])` parses an older array into `LaneInformation` (MSG:12596): lane count from `input[1] >> 4`, left/right pockets from the high/low nibbles of `input[2]`, eight direction bytes from `03..0A`, eight recommendation bytes from `0B..12`, then one byte each at `13` possible, `14` overpass, `15` underpass, `16` bus-only. Its logs explicitly name these fields (MSG:13129, MSG:13399, MSG:13619, MSG:13647, MSG:13675, MSG:13703). **H** for offsets/log labels; field names at quick offsets are corroborated by LI's declarations and parcel order.

`LaneInformation` has eight-byte `directionInfo` and `recommendedDirectionInfo` arrays and four byte-valued metadata fields, with no arrow enum names (LI:34, LI:74). `isValid()` bounds lane count `0..8`, left/right pockets `0..4`, without direction/metadata checks (LI:196). The client validates this object (NC:8807). These bounds apply to the old object API, not to the raw array API. **H** for checks, **M** for field-name attribution through quick offsets.

The old service serializer makes **24 bytes**: `17 F1 (count<<4) (left<<4|right) <8 direction> <8 recommended> <possible> <overpass> <underpass> <bus>` (CS:16389). `NaviCluster.updateTPLaneInfo(byte[])` copies that data to the quick field corresponding to `mTPLaneInfoData` and schedules message `0x0F` (NV:20096). **However `sendTPLaneInfo()` immediately returns** (NV:10878). Do not substitute this object API for the new raw lane path: there is no active sender in that method on this firmware. **H** for copy/message/no-op, **M** for quick field attribution.

The log banner says `0x4F(0xF1)` even for this older path (MSG:13059). That shared label is not evidence that its byte positions describe the new 81-byte array. Nor does old `recommendedDirectionInfo` prove a two-bit-per-arrow new status scheme.

## Arrow and highlight semantics: known versus claimed

No named new-frame lane arrow enum or new-frame status unpacker was found in the inspected `com.hkmc.cluster` classes or these navigation call paths. Our `CpTurns.laneIcon()` mapping is **H for our code only / U for stock meaning**:

| Code | Our name |
| --- | --- |
| 0 | No arrow / unused slot |
| 1 | Straight |
| 2 | Slight right |
| 3 | Right |
| 4 | Sharp right |
| 5 | U-turn, either side |
| 6 | Sharp left |
| 7 | Left |
| 8 | Slight left |

The third-party claim would mean three occupied arrow slots need separate two-bit statuses, rather than a single lane-wide `1` or `2`. **If** its groups are least-significant-slot first, three normal arrows would need `0x15` and three recommended arrows `0x2A`; current `1`/`2` only populates slot 0. That is a useful testable hypothesis, not a decoded stock fact: group order, slot association, `0`/`3` meaning and the fourth group are unknown. **U**.

The supplied native log `rd:216 [lane:0 level:0 vari:1 over:1 skip:0 dir:9 idx:7 valid:1]` has no matching field-to-byte construction in the inspected smali. In particular `dir:9` must not be treated as arrow code 9 or converted through our `laneIcon()` without evidence. Native road/lane model flags, their transformations, lane ordering and any filtering remain **U**.

## CpTurns comparison and recommended changes

Reference: `modules/wireless-carplay-and-aa/payload/src/hmgapi/mods/wireless/dipo/CpTurns.java.src` (`lanes`:268; frame:294; `laneOne`:351; `laneIcon`:384; blank frame:827). Existing `.agents/carplay-tbt-external/hkmc-cluster-tbt.md` section 5 describes upstream sending behavior; it is not independent stock validation.

| Finding | Confidence / recommendation |
| --- | --- |
| Current builder emits exactly 81 bytes, count at `01`, lane marks at `03+i`, arrows at `13+3i`, with `0B..12` and `2B..50` zero. | **H** for builder. Stock compatibility remains **U**; preserve as a baseline capture rather than labeling it verified. |
| `laneOne` collapses any route-angle list to `along=true`, then chooses one mark for the entire lane; it does not retain per-arrow recommendation identity. | **H** for information loss. If stock status is per-slot, this is the concrete encoding defect to fix: retain each mapped angle's recommendation and pack status by its verified slot. Do not apply speculative shifts from this audit. |
| `dim3` sends value 3 and `dim0` sends 0 as experimental lane marks. | **H** for code; visual meanings **U**. Do not describe them as established stock dimming states. Gate any default change on native/capture evidence. |
| Metadata trailer, including claimed possibility/individual-direction masks, is always zero. | **H** for omission, **U** whether zero means unknown, absent, impossible or disabled. Do not fill all bits or set masks until decoded. |
| Eight-lane cap and three-arrow cap discard additional inputs; both U-turn directions share code 5. | **H** for code; no evidence proves these violate the new stock format. Verify capacity and direction enum before changing them. |
| Blank frame is `F1` plus 80 zeros. | **H** for code. It does not invoke `sendClearTP`; retain a separate test for actual cluster clearing. Null through the public API is a no-op, so it is not a replacement. |

**No new stock-proven numeric encoding bug can be asserted from this smali alone.** The per-arrow information loss is real in our implementation, but whether it affects this wire format depends on the missing producer. Recommended next implementation step is to retain enough input information to support verified encoding, then change bytes only after decoding native stock construction or obtaining paired input/output captures. No builder change is made by this document.

## Missing evidence to collect later

Obtain the exact stock `libExSLNavi.so` and dependencies from the installed navigation app's actual native library directory (candidate `/data/app-lib/com.mnsoft.navi*/`, or its resolved package native-library path). No pull was executed. Keep binaries private under `tmp/`. Trace the JNI/native producer for callback type `0x17EF`, both `sendClusterParser` and `sendClusterData`, and the format/log strings for `rd`, `lane`, `level`, `vari`, `over`, `skip`, `dir`, `idx`, `valid`.

Required native answers: actual length and `F1` construction; count/pocket layout; arrow enum and ordering; per-arrow status packing and recommended-versus-possible policy; meaning/defaults/endianness of all bytes through `50`; mapping of logged lane fields; lane trimming/order; count-zero/clear output and lifecycle. If symbols/strings do not expose them, a later authorized passive capture of stock method arguments paired with native lane logs is needed. Native producer evidence still will not prove downstream physical rendering or CAN arbitration; that requires separate evidence.

## Owner observation (2026-11-05): the trailer bytes are used

On the car, with stock navigation (no phone projection), the cluster and HUD showed
bus-only lanes, hi-pass (ETC) lanes and non-hi-pass toll lanes, plus recommended-lane
highlighting. The 81-byte frame is the only lane payload to the cluster, so the trailer
pairs `0x43..0x50` (claimed overpass/underpass/hipass/busPriority/nonHipass/
individualDirection/possibility) and per-arrow recommendation are **in use**, not dead.
This raises confidence in the third-party layout from U toward plausible, but the exact
bit-to-lane order, endianness and which pair is which are still unverified. Do not hardcode
the claim: confirm it against captured stock `source=car-nav` 81-byte frames (added in the
lane-decoder work) matched to the real on-road lanes before populating these bytes for CarPlay.

## Owner drive 2026-11-04: stock navigation engine lane fields (input side only)

From the live log (`tmp/car-sessions/2026-11-04-tbt/live/logcat-mac.log`), the stock engine
(`com.mnsoft.navi`, `Navi: [NEngRG][P][Lane]`) logs each lane set *before* it becomes the
81-byte frame. No 81-byte output is in this log (`updateLaneInfo` logged only `[B@...`), and
there is no parallel CarPlay 0x5204 to cross-map (dev mode off on the first drive; the lane
recipe crashed iap2server on the second), so this is the producer's **input**, not a verified
byte mapping. Format: `[Lane] rd:<m> [lane:<t> level:<l> vari:<v> over:<o> skip:<s> (dir:<d> idx:<i> valid:<vv>)...]... RHD:<r>`.

Observed, with hypotheses (confidence **M**, from position/context only):
- `rd` = distance to the lane point in metres (counts down). `RHD` = right-hand-drive flag (0 here).
- Per lane, repeated `dir`/`idx`/`valid` triples = the arrows drawn in that lane; `over` tracks the
  group (1 = one arrow, 2/3 = multi-arrow lanes). `(dir,idx)` pairs seen: (0,1) dominant in through
  lanes = **straight**; (1,2), (3,3), (6,5), (6,10), (9,7), (9,14), (11,8), (0,16)+(3,18). `idx`
  (1..18) looks like an arrow-sprite index incl. combinations; `dir` a direction class. Exact
  mapping to our `laneIcon` 0..8 is **unproven**.
- `vari:1` marks the lane that leads along the route (the highlighted/recommended lane); it sits on
  the turn lane in these samples.
- A 12-lane toll-plaza set (`rd:294`) shows `lane:2 valid:2` lanes interleaved with `skip:1` lanes:
  **`lane:<type>` is the lane category** (0 normal, 2 a special/ETC/hi-pass/bus type the owner saw
  coloured on cluster+HUD) and **`skip:1` = closed/unusable**. This is the engine-side origin of the
  trailer bitmasks (`0x43..0x50`); it confirms those bytes carry hi-pass/bus/toll/closed state, but
  not their byte offsets or bit order. Capture the stock 81-byte `source=car-nav` frames (now
  recorded) at the same toll plaza to pin the encoding.

## FIRMWARE-CONFIRMED: 81-byte stTPLaneInfo layout (2026-11-05)

Source: decrypted stock navigation engine `libExSLNavi.so` (buildID c9fc6b0b…, ARM32 Thumb-2,
exported C++ symbols intact). Function `NaviDrivingAssistManager::ParserLaneInfoJson(stTPLaneInfo&, std::string)`
at vaddr `0x16056b8` parses the lane JSON into the struct; `stTPLaneInfo::init` (`0x15ffdaa`) zeroes
bytes `0x00..0x50`, so the struct is **81 bytes** (0x51). The `.so` is NOT in the repo; see the
index in [navi-lib-index.md](navi-lib-index.md) / `tmp/navi-re/`. Confidence **H** (direct bytecode).

The lane data reaches the engine as JSON. Keys read by the parser (exact strings from the binary):
`lane`, `info`, `totalLaneNumber`, `condition`, `skip`, `busPriority`, `hipass`, `nonHipass`,
`hipassLaneNumber`, `remainDistance`, `remainDistanceUnit`, `individualDirection`, `overpass`,
`underpass`, `leftPocket`, `rightPocket`. Each trailer field is a JSON array of lane indices that
the parser OR-s into a little-endian u16 bitmask (bit per lane).

| Offset | Size | Field (firmware key) | Evidence |
| --- | ---: | --- | --- |
| `0x00` | 1 | `0xF1` subtype (set by the sender, not this parser) | our builder; **M** |
| `0x01` | 1 | `totalLaneNumber` (lane count, clamped) | `strb [r4,#1]` @`0x160576e`, key@`0x1605750` **H** |
| `0x02` | 1 | pocket lanes (`leftPocket`/`rightPocket` packed) | `strb [r4,#2]`, keys@`0x1605cbc`/`0x1605dac` **H** |
| `0x03..0x12` | 16 | per-lane status, one byte per lane (loop) | written via per-lane pointer (not `[r4,#imm]`); matches our `[3+k]` **M** |
| `0x13..0x42` | 48 | per-lane arrows (loop) | our builder `[19+3k]`; parser loop **M** |
| `0x43` | 2 | **overpass** (고가차도) u16 LE bitmask | `strh [r4,#0x43]`, key `overpass` **H** |
| `0x45` | 2 | **underpass** (지하차도) | `strh [r4,#0x45]`, key `underpass` **H** |
| `0x47` | 2 | **busPriority** (버스전용차로) | `strh [r4,#0x47]`, key `busPriority` **H** |
| `0x49` | 2 | **skip** (닫힌/통과 불가 차선) | `strh [r4,#0x49]`, key `skip` **H** |
| `0x4b` | 2 | **hipass** (하이패스) | `strh [r4,#0x4b]`, key `hipass` **H** |
| `0x4d` | 2 | **hipassLaneNumber** (하이패스 차선번호) | `strh [r4,#0x4d]`, key `hipassLaneNumber` **H** |
| `0x4f` | 2 | **nonHipass** (하이패스 아님/일반 톨) | `strh [r4,#0x4f]`, key `nonHipass` **H** |

**This refutes the third-party `nav_mapping.py` trailer order.** That file claimed
0x47=hipass, 0x49=busPriority, 0x4b=nonHipass, 0x4d=individualDirection, 0x4f=possibility. The
firmware instead stores overpass/underpass/busPriority/skip/hipass/hipassLaneNumber/nonHipass at
0x43/0x45/0x47/0x49/0x4b/0x4d/0x4f. There is no `possibility` u16; `individualDirection` is a key
but is not one of these seven trailer words (it is applied elsewhere). The owner-observed hi-pass,
bus-only and toll lanes on the cluster/HUD are exactly these bitmasks.

### For our CpTurns lane builder
- The per-lane status (`0x03+k`) and arrow (`0x13+3k`) layout our builder uses is consistent with
  the loop region; confirm the exact status/arrow code values against captured stock `source=car-nav`
  frames (now recorded) — the arrow code enum still comes from `ConvertGuidance::getLaneItemImageID`
  (`0x20b48b0`), not yet decoded here.
- To show hi-pass / bus-only / toll / closed lanes for CarPlay we must populate `0x43..0x4f`, but
  CarPlay's 0x5204 does not carry them, so those stay 0 for projection (only the stock navigation
  sets them). `0x01` count, `0x02` pocket, status and arrows are what we can fill from CarPlay.
