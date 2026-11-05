Status: current — offline NEEV KOR stock TBT icon and distance audit, 2026-11-05; phone maneuver translation and physical cluster rendering remain unverified.

# Stock cluster TBT icons and distance encoding

Source: read-only stock navigation engine `libExSLNavi.so`, ARM32 Thumb-2, 69,612,252 bytes, ELF build ID `c9fc6b0ba98c35c4054041ab3ff4ebddfec8d421`. Target stock dump: `NEEV.KOR.0000.V039.001.260902`. No car access or experiment. The binary is not committed. Scratch disassemblies and indexes are under `tmp/navi-re/`.

**H** = explicit native instructions, table bytes, resolved resource name, or explicit Java bytecode; **M** = semantic/structural inference; **U** = unresolved. All native addresses below are hexadecimal **vaddr = file offset** in this ELF's `.text`, `.rodata`, and `.data`. A resource name establishes the HU's maneuver picture identity, not the physical cluster's pixels or support for an arbitrary injected value. Every recovered native table below is **HU-side**. Phone/iAP2 interpretation is discussed separately.

The ordinary arrows agree with HMG-API. The broader claim does not: Gen5W's recovered switch falls back to **8**, not 64; the roundabout family is sparse and has a separate alternate-side family; highway entry/exit codes include **160–165**. `172` is a conditional straight interchange variant, `176` is ferry, and the meanings of `173–175` remain unverified. Units are **0=m, 1=km, 2=mi, 3=ft, 4=yd**, reversing the question's tentative km/m order.

## How the tables were joined

The input is the engine's internal **RG icon index**, not a cluster wire code, phone maneuver enum, or HUD output image ID. Join the same input index in the following independent functions:

| Function / role | Entry vaddr/offset | Table vaddr/offset | Confidence |
| --- | --- | --- | --- |
| `Navi::CLUSTER::Turn::CInformation::RGIconIDToClusterDispType(ushort)` — main display code | `0x2b10f0c` | `0x2b10f1a`, 182 LE u16 entries | H |
| `FrameCluster::ConvertRGIcoidToClusterID(ushort,bool)` — list/display conversion | `0x23f62c0` | `0x23f62d0`, 182 LE u16 entries | H |
| `GuideResource::convertTBTIconToHUD(...)` — named maneuver resource | `0x1a73968` | `0x1a739b6`, 185 LE u16 entries | H |

For each `TBH`, `target = table_base + 2 * LE_u16(table_base + 2 * RG)`. Do not read the disassembler's instructions inside the jump tables as code. The main switch's TBH is at `0x2b10f16`; input above 181 goes to `0x2b111fc`.

For resource literals, follow `ldr r1,[pc,...]` to the pool, read a signed LE 32-bit displacement, and resolve **pool_value + address_of_ADD_PC + 4**. For example: pool `0x1a74520` contains bytes `d9 b9 ec 01`; `add r1,pc` at `0x1a73b32` resolves `0x393f50f`, `ba_drawable_rg_ic_turn_front`. The pool address itself is not the relative base.

The main alternate-side selector is a halfword at `this+0x132`; `MakeClusterData` stores `Navi::PI::Admin::GetPassDir()` there at `0x2b11bfe` (call `0x2b11bee`). The list conversion takes a bool. Below, **A/B mean selector zero/nonzero**, respectively: numeric branches H, exact equivalence to the phone's `side` enum U. Do not silently equate pass direction with steering-wheel location.

## Ordinary turns, arrival, and fallback

Resource names in this table omit `ba_drawable_rg_ic_`. Repeated RG inputs share a display branch; the named resource witness is the first listed RG. Numeric mappings and resource identities are H; the slight/sharp wording derived from front/back is M.

| Cluster code | RG input | Picture / meaning | Main branch vaddr/offset | Resource vaddr/offset | Confidence |
| --- | --- | --- | --- | --- | --- |
| 65 | 0 | Straight: `turn_front` | `0x2b11086` | `0x393f50f` | H |
| 66 | 4, 7 | Slight/front-right: `turn_right_front` | `0x2b1108a` | `0x393f587` | H identity; M slight |
| 67 | 2, 8 | Right: `turn_right` | `0x2b1108e` | `0x393f548` | H |
| 68 | 9 | Sharp/back-right: `turn_right_back` | `0x2b11092` | `0x393f62f` | H identity; M sharp |
| 69 | 10 | Sharp/back-left: `turn_left_back` | `0x2b11096` | `0x393f651` | H identity; M sharp |
| 70 | 1, 11 | Left: `turn_left` | `0x2b1109a` | `0x393f52c` | H |
| 71 | 3, 12 | Slight/front-left: `turn_left_front` | `0x2b1109e` | `0x393f565` | H identity; M slight |
| 72 / 73 | 5 | U-turn A/B: `turn_back` / `turn_back_right` | `0x2b110a2` | `0x393f5cc` / `0x393f5aa` | H |
| 112 | 103 | Destination/arrival approach | `0x2b11160`; `GetDestPointDisplayType` at `0x2b11912` | HUD default resource; no directional picture recovered | H destination; U exact artwork |
| 113 / 114 | 115 / 114 | Other destination variants | `0x2b11164` / `0x2b11168` | Not resolved | H codes; U left/right artwork |
| 3 | Arrival state, not RG input | Arrived | `SetTurnData` at `0x23f5c36–0x23f5c52` | Not resolved | H state; U physical artwork |
| 8 | Default/unmapped or out-of-range RG | Fallback; visual blank/loading semantics unresolved | `0x2b111fc`; list `0x23f643c` | Not applicable | H fallback; U rendering |
| 64 | Legacy Gen3 default only | No Gen5W proof of none/loading | Gen3 `0x2b10a28`; table `0x4046dd8` | Not applicable | H Gen3; U Gen5W semantics |

`FrameCluster::setClusterArrived(bool)` (`0x23f55a8`) stores the arrival flag at `this+0x0a`. `SetTurnData` checks it, writes display byte **3** at `0x23f5c40–0x23f5c44` for counter values 0 through 4, then clears flag/counter. Normal display comes from `stItem+0x29` to frame `+0x28` at `0x23f5cac–0x23f5cb0`. `SetCaseDestWayPoint` (`0x2b11a74`) calls the destination converter, stores display at `0x2b11aa0`, and zeros direction/fraction. Thus **112 approach versus 3 arrived** is independently established.

Legacy `RGIconIDToClusterDispTypeForGen3` (`0x2b10a18`) resolves pool `0x2b10a2c` bytes `b2 63 53 01` using ADD at `0x2b10a22` to the byte table `0x4046dd8`. It maps straight RG0 to **2**, left RG1 to **7**, right RG2 to **4**, destination RG103 to **35**, with **64** fallback. This is a different numbering family: its fallback cannot validate `CpTurns.NO_ICON=64` for Gen5W. Nor does the evidence establish that replacing 64 with 8 would visually clear this cluster.

## Highway, ramps, divergence, and fork

Here C means `CNaviConfig::IsConnectPlatform()` is nonzero (call target `0x1fe242c`), not proof that this particular car takes that branch. Resource strings are exact HU identifiers with their common prefixes omitted. Avoid reducing the long overpass/ground names to a generic off-ramp.

| Code | RG input | Picture / resource identity | Main branch vaddr/offset | Resource vaddr/offset | Confidence |
| --- | --- | --- | --- | --- | --- |
| 160 | 23 | `ba_highway_drawable_ic_entry_straight` | `0x2b111aa` | `0x393fc81` | H |
| 161 | 25 | `entry_right` | `0x2b111ae` | `0x393fcc9` | H |
| 162 | 24 | `entry_left` | `0x2b111b2` | `0x393fca7` | H |
| 163 | 26 | `exit_straight` | `0x2b111b6` | `0x393fcec` | H |
| 164 | 28, 31, 62 | Exit/split/merge right; RG28 `exit_right` | `0x2b111c8` | `0x393fd32`; RG31 split `0x393fd9b` | H |
| 165 | 27, 30, 61 | Exit/split/merge left; RG27 `exit_left` | `0x2b111cc` | `0x393fd11`; RG30 split `0x393fd74` | H |
| 172 if C, otherwise 163 | 29 | `ic_straight` interchange variant | `0x2b111ba–0x2b111c6` | `0x393fd54` | H |
| 176 | 86, 87 | Ferry: `ba_drawable_rg_ic_turn_inferry` | `0x2b111d0` | `0x393ffd3` | H |
| 81 | 38 | `diverge_left_overpass_change_right_ground` | `0x2b110b4` | `0x393f96e`, HUD ADD `0x1a73db4` | H |
| 82 | 37 | `diverge_right_overpass_change_left_ground` | `0x2b110b8` | `0x393fe82`, HUD ADD `0x1a74068` | H |
| 86 if C, otherwise 88 | 34 | HUD flag selects `diverge_left_overpass_change_right_ground` / `overpass_center_straight` | `0x2b110c8–0x2b110d4` | `0x393f96e` / `0x393f9af`; ADD `0x1a73d8c` / `0x1a73da0` | H branches/names; M cross-flag interpretation |
| 87 | 36 | `diverge_left_ground_go_right_overpass` | `0x2b110c4` | `0x393fe45` | H |
| 88 | 35 | `diverge_right_ground_go_left_overpass` | `0x2b110d6` | `0x393fe08` | H |
| 84 / 85 | 43 / 42 | Change right / left | `0x2b110bc` / `0x2b110c0` | `0x393ff4d` / `0x393ff27` | H |
| 89 if C, otherwise 91 | 39 | `tunnel_road_narrow` | `0x2b110de` | `0x393fec3` | H |
| 90 / 91 | 41 / 40 | `2lane_right` / `2lane_left` | `0x2b110da` / `0x2b110ec` | `0x393ff07` / `0x393fee8` | H |
| 173, 174, 175 | No recovered input | Claimed forks/ramps remain unresolved | Not emitted by either inspected RG switch | No resolved resource join | U |

The list converter agrees on 160–165 and 176 (`0x23f6520–0x23f6538`) but RG29 always yields 163 and RG34 always yields 88. RG60 also yields 163 even though its HUD resource is front; these are many-to-one display mappings, not a bijection between all resource names and wire values.

`SetImprovedArrowTypeFork` (`0x2b1597c`) is not evidence for 173/174: on its gated branch it writes **display 25** at `0x2b15a0e` and direction **36 or 12** at `0x2b15a1e–0x2b15a22` for internal RG3/RG4. Gates include nation 8 and platform 11/14/17. H for writes, U for the exact rendered fork. Searches of the relevant cluster functions found no validating 173–175 assignment; this is not proof those values are unsupported everywhere.

## Roundabouts: eight directions, two families

Names below are the selector-zero resource suffix following `ba_drawable_rg_ic_turn_ring_`. The alternate HUD branch has a separately named circular-direction variant. A/B codes are H from both switches; corresponding nominal directions are H from resource names; assigning specific phone angles or exit ordinals remains U. In particular **97 is right-front in family A, not the alternate-family straight code**.

| RG | A / B cluster code | Picture direction | Main branch vaddr/offset | List branch vaddr/offset | Resource vaddr/offset; ADD-PC instruction | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| 15 | 96 / 192 | `front` | `0x2b110f0` | `0x23f6486` | `0x393f6ee`; `0x1a73c50` | H |
| 19 | 97 / 193 | `rightfront` | `0x2b110fe` | `0x23f6490` | `0x393f832`; `0x1a73cf8` | H |
| 17 | 99 / 195 | `right` | `0x2b1110c` | `0x23f649a` | `0x393f788`; `0x1a73ca4` | H |
| 20 | 100 / 196 | `rightback` | `0x2b1111a` | `0x23f64a4` | `0x393f890`; `0x1a73d22` | H |
| 18 | 102 / 198 | `turnback` | `0x2b11128` | `0x23f64ae` | `0x393f7d5`; `0x1a73cce` | H |
| 21 | 104 / 200 | `leftback` | `0x2b11136` | `0x23f64b8` | `0x393f8ec`; `0x1a73d4c` | H |
| 16 | 105 / 201 | `left` | `0x2b11144` | `0x23f64c2` | `0x393f73b`; `0x1a73c7a` | H |
| 22 | 107 / 203 | `leftfront` | `0x2b11152` | `0x23f64cc` | `0x393f948`; `0x1a73d76` | H |

**98, 101, 103 and 106 are not outputs of these switches**. This does not prove the cluster cannot draw them. There is no recovered first/second/third-exit ordinal table here. “96–106 by angle” is therefore not an accurate description of the stock table. Current `roundaboutIcon` already uses 107; its phone angle bands and `side==1 ? 97` straight choice need separate validation.

## Other outputs of the recovered main RG switch

This records the remaining nondefault branches so unlabelled values are not mistaken for absent ones. It is the output set of this converter, not an exhaustive enumeration of every later display override or every cluster-supported icon.

| Code | RG input / description | Main branch vaddr/offset | Resource witness vaddr/offset | Confidence |
| --- | --- | --- | --- | --- |
| 76 | 97; picture unresolved | `0x2b110b0` | HUD default | H code; U picture |
| 115, 116, 117 | 105/176, 110/178, 109/177; waypoint variants | `0x2b1116c`, `0x2b11170`, `0x2b11174` | Not resolved | H codes; U exact artwork |
| 118, 119, 120 | 179, 181, 180; waypoint variants | `0x2b11178`, `0x2b1117c`, `0x2b11180` | Not resolved | H codes; U exact artwork |
| 143 if support flag, otherwise 65 | 99; `turn_bifurcation` | `0x2b11184` (flag `this+0x188`) | `0x393fc5e` | H identity; M support-flag role |
| 144, 145 | 84, 77; `turn_right_side`, `turn_left_side` | `0x2b11192`, `0x2b11196` | `0x393fbb8`, `0x393faef` | H |
| 146, 148 | 69, 67; `turn_left_side_main`, `turn_right_side_main` | `0x2b1119a`, `0x2b111a2` | `0x393fa4b`, `0x393fa24` | H |
| 147, 149 | 82, 74; `turn_right_side_2branch`, `turn_left_side_2branch` | `0x2b1119e`, `0x2b111a6` | `0x393fc04`, `0x393fb10` | H |
| 178 | 50; toll booths | `0x2b111d4` | `0x394015f` regional `_fr` witness | H |
| 181 if C, otherwise 183 | 48, 49; facility variants | `0x2b111dc` | HUD default | H codes; U precise picture |
| 181 if C, otherwise 8 | 89, 91–96; facility variants | `0x2b111ea` | RG89 rest-area regional witness `0x3940061` | H codes; M group meaning |
| 182 / 184 | 98 / 88; picture unresolved | `0x2b111d8` / `0x2b111f8` | HUD default | H codes; U picture |

## Distance units and scaling

Two native paths independently compare unit strings: `NaviDrivingAssistManager::convertDistance` (`0x1609e88`, ASCII) and `FrameCluster::convertDistance` (`0x23f59f8`, UTF-16). They agree exactly. `15` is the unmatched-unit fallback; **idle** is not established as its complete semantic meaning.

| HU unit code | Unit | ASCII string vaddr/offset | Literal pool vaddr/offset; raw LE bytes | ADD-PC vaddr/offset | UTF-16 corroboration vaddr/offset | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | m | `0x386ab51` | `0x160a09c`; `85 0b 26 02` | `0x1609fc8` | `0x3b727de` | H |
| 1 | km | `0x389a09f` | `0x160a0a0`; `c3 00 29 02` | `0x1609fd8` | `0x3b75712` | H |
| 2 | mi | `0x389a0a8` | `0x160a0a4`; `bc 00 29 02` | `0x1609fe8` | `0x3b7570c` | H |
| 3 | ft | `0x389a0ae` | `0x160a0a8`; `b2 00 29 02` | `0x1609ff8` | `0x3b73670` | H |
| 4 | yd | `0x389a0ab` | `0x160a0ac`; `9f 00 29 02` | `0x160a008` | `0x3b7366a` | H |
| 15 | Unknown/unmatched unit | Immediate at `0x160a014` | Not applicable | Not applicable | Fallback instruction `0x23f5aa8` | H fallback; U all idle semantics |

The native manager calls `ExSLUtility::convertDistanceString` (`0x15e8ab0`) and parses the resulting numeric text. It clamps to 0..65535 (double constants `0x160a080` and `0x160a088`), truncates the whole value to u16 at `0x1609f84`, initializes fraction to zero at `0x1609f8c`, and if there is a positive fractional part computes **truncate(value * 10) % 10** at `0x1609faa–0x1609fc2`. `FrameCluster::DivideOverAndUnderDistance` (`0x23f58e4`) also separates formatted whole and fractional portions. **H** for the split; unit-selection thresholds and formatting/rounding policy are not established here. The generic splitter does not restrict fraction extraction to km/mi.

`sendClusterSubInfoData_Android` (`0x1624aec`) invokes that converter at `0x1624c00`, then sends signal **5000=unit** (`0x1624d8a`), **5005=whole** (`0x1624d9c`), **5001=fraction** (`0x1624db2`). `FrameCluster::sendTurnSubData` (`0x23f777c`) independently extracts unit from the low nibble at `0x23f77be–0x23f77c6`, whole from the halfword at `0x23f77d2–0x23f77da`, and fraction from the high nibble at `0x23f77e6–0x23f77f0` into those same signals. H. This internal native struct's nibble order is not the Java serializer's wire byte order.

### Public Java API corroboration

These HU smali files are under `tmp/car-sessions/2026-11-04-devdump/analysis/api-inventory/smali/`; line numbers are physical file lines. Explicit Binder invokes, parameter labels, and arithmetic are H; unresolved quick-call target attribution remains M.

| API / evidence | Encoding established | Location | Confidence |
| --- | --- | --- | --- |
| `NavigationCluster.updateTBTSubInfo(FIIIIIIFII)` | First argument is actual distance float; third is unit; accepts 0..4 or 15 (despite stale log text saying 0..3) | `automotive/com/hkmc/cluster/NavigationCluster.smali:7127`; Binder call `:7362` | H |
| `NaviCluster.getDistanceUnderPoint(FI)` | `int((distance * 10) % 10)`; unit parameter unused | `automotive-service/com/hkmc/micom/ClusterService$NaviCluster.smali:864` | H |
| `NaviCluster.updateTBTSubInfo(FIIIIIIFII)` | Normal path packs unit in high nibble/fraction in low nibble of byte 0; whole at bytes 5–6; distance error -2/-3 sets fractional nibble 15; -1/-3 sets whole 0xffff | Same file `:18283` | H |
| `ClusterService.getFloatToShort(F)` | Truncates float to int, then casts to short; does not multiply by 10 | `automotive-service/com/hkmc/micom/ClusterService.smali:2894` | H |
| `NavigationCluster.updateTBTSubInfo2C(IIIIIIIII)` | Three groups `(unit, F, I)` forwarded unchanged through explicit Binder call | `automotive/com/hkmc/cluster/NavigationCluster.smali:7678` | H |
| `NaviCluster.updateTBTSubInfo2C(IIIIIIIII)` | For point k, bytes `3k..3k+2` are `((unit&15)<<4)|(F&15)`, `(I>>8)&255`, `I&255`; whole field is 16 bits, big-endian | `automotive-service/com/hkmc/micom/ClusterService$NaviCluster.smali:18529` | H |

Consequently **1.2 km** is `updateTBTSubInfo(1.2f, ..., unit=1, ...)`, or one SubInfo2C group **(1, 2, 1)**, whose serialized bytes are `12 00 01`. It is not whole=12 with an implied global tenths scale. For 350 m the group is `(0,0,350)` → `00 01 5e`. H for encoding. Fractional nibble width does not make values 10–15 ordinary decimal digits.

## Comparison with our hook and phone-side boundary

References: `modules/wireless-carplay-and-aa/payload/src/hmgapi/hook/Cluster.java.src` and `modules/wireless-carplay-and-aa/payload/src/hmgapi/mods/wireless/dipo/CpTurns.java.src`. No implementation was changed during this audit.

| Current claim / behavior | Verdict | Confidence / boundary |
| --- | --- | --- |
| Straight 65; right 66/67/68; left 71/70/69; U-turn 72/73 | Correct HU code family. Exact angle cutoffs and side selection still need phone evidence. | H HU; U phone policy |
| `NO_ICON=64` (`CpTurns:655`) | Not validated for Gen5W; recovered Gen5W converter defaults to 8. Gen3 fallback 64 is a separate enum. No loading picture established. | H distinction; U physical clearing |
| 81/82/86 as generic off-ramps | Too broad: explicit overpass/ground divergence names and platform-dependent 86/88 branch. | H HU; U mapping arbitrary phone exits |
| 96..106 roundabout-by-angle | Incorrect as a stock enumeration: eight codes end at 107; separate B family starts at 192. Current `roundaboutIcon:703` already includes 107. | H HU |
| `side==0 ? 96 : 97` for straight roundabout | Stock A/B straight pair is 96/192; 97 denotes A right-front. Whether phone `side` should select A/B needs validation. | H HU; U phone selector |
| Phone type 5/9 →172; 52→174; 53→173 | 172 is the conditional straight interchange code, not a complete on-ramp family. 173/174 are not validated by these stock functions. | H HU 172; U phone translation / 173–175 |
| Arrival 112, arrived state 3, ferry 176 | Native evidence supports these meanings; phone type/state identity is separate. | H HU; U phone provenance |
| `Cluster.UNIT_M=0`, KM=1, MI=2, FT=3, YD=4 | Already correct. Phone distance-unit numbers at `CpTurns:541` belong to a different enum. | H HU / own source; U phone specification |
| `sendHudDist:876` sends `(unit,tenths,whole)` | Correct argument order and decimal split. Hook rounds km/mi to nearest tenth and caps whole at 4095; stock Java field carries 16 bits. Neither hook policy is mandated by that transport. | H HU / own source; U exact formatter equivalence |

This binary can establish HU-generated numeric families, resource names and distance arithmetic. It cannot establish the phone's iAP2 type/angle convention, which phone field supplies exit angle, all legal driving-side values, or what this physical cluster draws for injected/unsupported values. Do not turn those U findings into a verified phone mapping.

## Raw table witnesses and reproduction

Each row below is consecutive raw bytes from the binary, not disassembly. Halfwords are little-endian. These samples preserve the critical joins without committing the proprietary binary.

| Table region vaddr/offset | Inputs | Raw bytes | Confidence |
| --- | --- | --- | --- |
| `0x2b10f1a` | Main RG0..5 | `b6 00 c0 00 ba 00 c2 00 b8 00 c4 00` | H |
| `0x2b10f38` | Main RG15..22 | `eb 00 15 01 f9 00 07 01 f2 00 00 01 0e 01 1c 01` | H |
| `0x23f62ee` | List RG15..22 | `db 00 f9 00 e5 00 ef 00 e0 00 ea 00 f4 00 fe 00` | H |
| `0x1a739d4` | HUD RG15..22 | `3d 01 52 01 67 01 7c 01 91 01 a6 01 bb 01 d0 01` | H |
| `0x2b10f48` | Main RG23..31 | `48 01 4c 01 4a 01 4e 01 59 01 57 01 50 01 59 01 57 01` | H |
| `0x2b10f5e` | Main RG34 | `d7 00` → `0x2b110c8` | H |
| `0x2b10fc6` | Main RG86 | `5b 01` → `0x2b111d0` | H |
| `0x2b10fe8` | Main RG103 | `23 01` → `0x2b11160` | H |

Reproduce with `/opt/homebrew/opt/llvm/bin/llvm-objdump -d --triple=thumbv7 --start-address=0x2b10f0c --stop-address=0x2b11200 libExSLNavi.so`; read bytes with Python `struct.unpack_from('<H', binary, base+2*index)`. Reuse `tbt-main.asm`, `tbt-cluster-convert.asm`, `tbt-hud.asm(.annotated)`, `tbt-frame-main.asm`, `tbt-sendturn.asm`, `tbt-fork.asm`, and `tbt-distance/{convert-and-tp,subinfo,format-dispatch}.asm`. Original annotated PC-literal results were checked against the binary; resource IDs returned by HUD conversion are deliberately not substituted for the cluster codes.

## Match against our builder (2026-11-05)

- **Distance units CONFIRMED match:** our `Cluster.UNIT_M=0, UNIT_KM=1, UNIT_MI=2, UNIT_FT=3,
  UNIT_YD=4` equal the firmware enum. `updateTBTSubInfo2C(unit, fractional, whole)` with tenths for
  km/mi is correct (1.2 km → `(1,2,1)`).
- **Ordinary turn arrows match:** 65 straight, 66/67/68 right slight/turn/sharp, 71/70/69 left, 72/73
  U-turn, 112 arrive, 3 arrived, 176 ferry — all firmware-confirmed.
- **To revisit in our code:** the none/fallback icon. The Gen5W switch falls back to **8**, not 64;
  64 is only the legacy Gen3 default and is not proven to clear this cluster. Our `CpTurns.NO_ICON=64`
  may be why icon 64 rendered as "loading" — try the HU's own none handling. Highway entry/exit use
  **160–165** (not only 81/82/86); roundabout has a main family (96/97/99/100/102/104/105/107) and a
  separate alternate-side family (192/193/195/196/198/200/201/203). Refine `exactIcon`/ramp/roundabout
  mapping from these once verified on the car.
- Still phone-side (field data only): which iAP2 ManeuverType/angle NAVER/Apple actually send.
