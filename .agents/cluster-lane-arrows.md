Status: current — lane arrow image-ID tables extracted from the stock navigation engine, 2026-11-05.

# Lane arrow image IDs (ConvertGuidance::getLaneItemImageID)

Source: decrypted `libExSLNavi.so` (not in the repo; index in [navi-lib-index.md](navi-lib-index.md)).
`ConvertGuidance::getLaneItemImageID` (`0x20b48b0`) and `getLaneItemImageIDConnect` (`0x20b4da8`)
map a lane's direction to the arrow image byte placed in the 81-byte frame (per-lane arrows at
`0x13+3k`, see [cluster-lane-frame.md](cluster-lane-frame.md)). Confidence **H** (direct table bytes).

Lookup inputs:
- `d` = **normalized direction index 0..8** from `convertDirectionToDirectionIndex` (`0x20b49c0`),
  NOT the raw engine `dir` in the `[Lane] dir:N` logs.
- `eRgLaneOverlapType` selects a family: 1 = simple (single arrow), 2/3/4 = multi-arrow overlaps.
- `N` = count of active (non-zero valid) arrows in the lane; `N>2` vs `N<=2` picks a sub-table.
- `B` = a flag set by `setLaneItemImageID` when the lowest angle code `<=2` (straight group).
- The "Connect" tables are used by `getLaneItemImageIDConnect` for connected/through forms.
- `X` = `0xff` = no image (invalid combination). All tables live at base `0x3f1a514`.

## Simple lane (overlapType 1), table @ 0x3f1a5c0 (9 bytes)

| d | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| imageID | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 34 |

Single-lane arrows are **1..8** (then 34 for d=8). This is the same value space our
`CpTurns.laneIcon` emits (1=straight, 2 slight-right, 3 right, 4 sharp-right, 5 U-turn, 6 sharp-left,
7 left, 8 slight-left). So for ordinary single-arrow lanes our codes line up with stock; the exact
picture each ID draws is a cluster resource, to be confirmed against captured `source=car-nav`
frames and the engine `[Lane] dir:_ idx:_` logs. IDs **9..57 (0x09..0x39)** are multi-arrow /
combined forms produced only by the overlap/connect tables below; `0x22`(34), `0x23`(35),
`0x24`(36), `0x25`(37) are the "many arrows" composite images.

## Multi-arrow tables (overlapType 2/3/4 and Connect)

Each cell is `p=0 / p=1 / p=2..8` where a single value repeats for all nine `p` columns (a second
index; usually identical). Raw bytes per table are in `tmp/navi-re/generated-tables.md` (gitignored).

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

## Per-lane status byte (0x03+k)

The status is **2 bits per arrow direction** within the byte. Internal valid codes `0/1/3/2`
map to wire values `0/1/2/3` (2 = the preferred/recommended arrow). This matches our builder's
`laneOne` status (1 normal, 2 highlighted) for the common case; the special lane types
(hi-pass / bus / toll / closed) are carried by the trailer bitmasks `0x43..0x4f`, not this byte.

## What still needs the car / phone

- Which picture each image ID (1..57) actually draws on this cluster: verify against captured stock
  frames. Our single-arrow 1..8 almost certainly match; the composite IDs (9..57) only appear for
  multi-arrow lanes we would rarely synthesize from CarPlay.
- The full raw `dir` → normalized `d` map (`convertDirectionToDirectionIndex`) is in
  `tmp/navi-re/enum-*.thumb.txt`; label it against the `[Lane] dir:N idx:M` drive logs when verifying.
