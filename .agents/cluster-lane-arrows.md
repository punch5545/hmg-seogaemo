Status: current — lane arrow image-ID tables from the stock navigation engine; status byte and multi-arrow pairs car-verified 2026-11-05.

# Lane arrow image IDs (ConvertGuidance::getLaneItemImageID)

Source: decrypted `libExSLNavi.so` (not in the repo; [navi-lib-index.md](navi-lib-index.md)). `ConvertGuidance::getLaneItemImageID` (`0x20b48b0`) and `getLaneItemImageIDConnect` (`0x20b4da8`) map a lane's direction to the arrow image byte in the 81-byte frame (per-lane arrows at `0x13+3k`, [cluster-lane-frame.md](cluster-lane-frame.md)). Confidence **H** (direct table bytes). All tables live at base `0x3f1a514`.

Lookup inputs:
- `d` = **normalized direction index 0..8** from `convertDirectionToDirectionIndex` (`0x20b49c0`), NOT the raw engine `dir` in `[Lane] dir:N` logs.
- `eRgLaneOverlapType` selects the family: 1 simple (single arrow), 2/3/4 multi-arrow overlaps (types 2-4 use 9-byte-stride structures with recommend/connect variants).
- `N` = count of active (non-zero valid) arrows in the lane; `N>2` vs `N<=2` picks a sub-table.
- `B` = flag set by `setLaneItemImageID` when the lowest angle code is `<=2` (straight group).
- "Connect" tables are used by `getLaneItemImageIDConnect` for connected/through forms.
- `X` = `0xff` = no image (invalid combination).

## Simple lane (overlapType 1), `imageID[d]` table @ `0x3f1a514+0xac` = `0x3f1a5c0` (9 bytes)

| d | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| imageID | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 34 |

Single-lane arrows are **1..8** (then 34 for d=8): the same value space our `CpTurns.laneIcon` emits (1 straight, 2 slight right, 3 right, 4 sharp right, 5 U-turn, 6 sharp left, 7 left, 8 slight left). Our builder writes 1..8 directly. IDs **9..57 (0x09..0x39)** are component glyphs for multi-arrow lanes (overlaid per slot, car-verified below); 34-37 are the right-side U-turn family and an offset straight (d=8 is a right-side U-turn), not "many arrows" composites.

## Multi-arrow tables (overlapType 2/3/4 and Connect)
Each cell is `p=0 / p=1 / p=2..8` where a single value repeats for all nine `p` columns (a second index, usually identical). Raw bytes per table: `tmp/navi-re/generated-tables.md` (gitignored).

### Normal lookup (getLaneItemImageID)

| d | T2 N>2 B1 | T2 N>2 B0 | T2 N<=2 | T3 N>2 B1 | T3 N>2 B0 | T3 N<=2 | T4 N>2 B1 | T4 N>2 B0 | T4 N<=2 |
|---|---|---|---|---|---|---|---|---|---|
| 0 | 37 | X | 9 | 42 | X | 16 | 47 | X | 22 |
| 1 | X | X | X | 43 | X | 21/19/19 | 52 | X | 33/23/23 |
| 2 | X | X | X | 44 | 3 | 20/18/18 | 53 | 32 | 32/24/24 |
| 3 | X | X | X | 45 | 45 | 17 | 54 | 54 | 25 |
| 4 | 56 | 56 | 10 | X | X | X | 51 | 51 | 26 |
| 5 | 40 | 40 | 11 | X | X | X | 50 | 50 | 27 |
| 6 | 39 | 7 | 14/12/12 | X | X | X | 49 | 31 | 31/28/28 |
| 7 | 38 | X | 15/13/13 | X | X | X | 48 | X | 30/30/29 |
| 8 | X | X | 35 | 57 | 57 | 35 | 55 | 55 | 36/55/55 |

### Connect lookup (getLaneItemImageIDConnect)

| d | Connect2 | Connect3 | Connect4 |
|---|---|---|---|
| 0 | 9 | 16 | 22 |
| 1 | X | 21 | 33 |
| 2 | X | 20/18/18 | 32/24/24 |
| 3 | X | 20/18/18 | 32/24/24 |
| 4 | 10 | X | 26 |
| 5 | 14/12/12 | X | 31/28/28 |
| 6 | 14/12/12 | X | 31/28/28 |
| 7 | 15 | X | 30 |
| 8 | 35 | 35 | 36 |

## Per-lane status byte (0x03+k), car-verified
**2 bits per arrow slot**, slot 0 in bits 0-1, slot 1 in bits 2-3, slot 2 in bits 4-5. `00` = slot not drawn (blank), `01` = normal (white), `10` = recommended (blue), `11` = inactive (grey). Slots mix freely: `0x0d` is a white straight + grey left, `0x07` a grey straight + white left. Other examples: one normal arrow `0x01`; two normal `0x05`; second arrow recommended `0x09`; three normal `0x15`. A slot whose bits are `00` is not drawn even when its arrow byte is set: sending `0x01`/`0x02` with two arrows draws only slot 0 (the 2026-11-05 CarPlay bug: U-turn+left lane showed only the U-turn, lit blue). Car-verified by direct frame injection, owner reading cluster/HUD.

## Car-verified glyphs and multi-arrow pairs (2026-11-05)
Method: `tmp/lane-probe/` (throwaway, gitignored) runs a dex via root `app_process` that calls `com.hkmc.cluster.NavigationCluster(null).updateLaneInfo(byte[81])`; the owner read the cluster while CarPlay guidance was active but its lane data absent (our module only rewrites the frame when CarPlay lane data changes; with route guidance off the cluster draws no lanes). Arrows in a lane are overlaid; each multi-arrow lane needs the matching **component** glyphs, not the single-lane glyphs 1..8 (5+7 overlaid look wrong).

Single glyphs seen alone: 9 straight (offset right), 10 short U-turn (component), 11 sharp (135°) left, 12 short left (component), 13 slight left, 14 left, 15 thin slight left, 16 straight (offset left), 17 sharp right, 18 right, 19 slight right, 20/21 longer 18/19, 22 straight (centre), 34 right-side U-turn (left-hand traffic), 35 short right-side U-turn, 36 half of 35 offset right, 37 straight offset right.

Owner-approved pairs (slot order as listed, status `0x05`/`0x15` normal):

| Lane directions | Image IDs |
|---|---|
| U-turn + left | 10, 14 |
| U-turn + slight left | 10, 15 |
| straight + left / slight left / sharp left / U-turn | 9, 12 / 9, 13 / 9, 11 / 9, 10 |
| left + slight left | 12, 13 |
| straight + right / slight right / sharp right | 16, 18 / 16, 19 / 16, 17 |
| right + slight right | 18, 21 |
| left + right / slight left + slight right | 31, 32 / 30, 33 |
| U-turn + right / U-turn + slight right (no KR use seen) | 26, 32 / 26, 33 |
| left + straight + right | 28, 22, 24 |
| slight left + straight + right / left + straight + slight right | 30, 22, 24 / 28, 22, 33 |
| sharp left + straight + right / left + straight + sharp right | 27, 22, 24 / 28, 22, 25 |
| slight left + straight + slight right (closest; slightly long, 29/23 too short) | 30, 22, 33 |
| left + slight left + straight | 12, 13, 9 |
| straight + slight right + right | 16, 19, 18 |
| U-turn + left + straight / U-turn + straight + right / U-turn + left + right | 26, 31, 22 / 26, 22, 24 / 26, 31, 32 |

Rejected (look wrong): 5+7, 10+12, 10+13, 10+18, 14+18, 12+18, 26+24, 26+20, 9+14, 9+15, 22+27 (prefer 9+11), 28+24 (prefer 31+32), 28+32 (reads as staggered left-then-right), 28+29+22, 28+30+22, 29+22+23, 13+22+19, 15+22+21, 22+23+24, 26+28+22, 26+28+24, 31+22+32. More than three arrows cannot be encoded (three slots per lane).

## Still needs the car / phone
- Which picture IDs 23-33 and 38-57 draw alone.
- The full raw `dir` -> normalized `d` map is in `tmp/navi-re/enum-*.thumb.txt`; label it against the `[Lane] dir:N idx:M` drive logs ([cluster-lane-frame.md](cluster-lane-frame.md) section 6).
