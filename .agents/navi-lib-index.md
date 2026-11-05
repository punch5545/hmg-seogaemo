# libExSLNavi mechanical index

Mechanical index only (no behavioural conclusions); analysis lives in [cluster-lane-frame.md](cluster-lane-frame.md), [cluster-lane-arrows.md](cluster-lane-arrows.md), [cluster-tbt-icons.md](cluster-tbt-icons.md). The binary is read-only and outside the repository; detailed text indexes are in `tmp/navi-re/`.

- ELF: 32-bit little-endian ARM EABI5 shared object, stripped; GNU build ID `c9fc6b0ba98c35c4054041ab3ff4ebddfec8d421`.
- Exact commands: `/opt/homebrew/opt/llvm/bin/llvm-objdump -h <lib>`; `llvm-objdump -p <lib>`; `/usr/bin/strings -t d -n 4 <lib>`; `llvm-nm -D --undefined-only <lib>`; `llvm-nm -D --defined-only <lib>`; `llvm-objdump -T <lib>`; `llvm-objdump -R <lib>`; `llvm-objdump -d --triple=armv7 <lib>`.
- Counts: 1,063,774 printable strings (length >=4), 30,045 requested lane keyword matches, 144,952 disassembly labels.

## Sections
Section vaddr/file-offset columns of the original index were corrupt (zeros/truncated) and are dropped; recompute with `llvm-objdump -h`.

| Name | Size |
|---|---:|
| `.interp` | `0x13` |
| `.note.gnu.build-id` | `0x24` |
| `.dynsym` | `0x2dfe00` |
| `.dynstr` | `0xa300ec` |
| `.hash` | `0x137ffc` |
| `.gnu.version` | `0x5bfc0` |
| `.gnu.version_d` | `0x1c` |
| `.gnu.version_r` | `0x60` |
| `.rel.dyn` | `0x194408` |
| `.rel.plt` | `0xd08` |
| `.plt` | `0x13a0` |
| `.text` | `0x23eb80b` |
| `.ARM.extab` | `0x351aa4` |
| `.ARM.exidx` | `0xeced8` |
| `.rodata` | `0x8e45c4` |
| `.data.rel.ro.local` | `0x311b4` |
| `.fini_array` | `0x8` |
| `.init_array` | `0x22a0` |
| `.data.rel.ro` | `0xb21b4` |
| `.dynamic` | `0x148` |
| `.got` | `0xde4c` |
| `.data` | `0x25ce4` |
| `.bss` | `0x2921b4` |
| `.comment` | `0x28` |
| `.note.gnu.gold-version` | `0x1c` |
| `.ARM.attributes` | `0x34` |
| `.shstrtab` | `0x11e` |

## First 40 specific lane/cluster/TPLane strings (file offset = hex; `strings -t d` decimal is the same number)

- `0x2f28d9` — `_ZN12ExSLNaviData15rgRouteGuidanceEv`
- `0x2f9acc` — `_ZN16GuidanceUiHelper21getLaneInfoLeftMarginEi`
- `0x2f9afb` — `_ZN16GuidanceUiHelper17setLaneInfoMarginEbbibb`
- `0x2fac37` — `_ZN16GuidanceUiHelper14updateLaneInfoEv`
- `0x34f4ca` — `_ZN4Navi8LaneInfoD2Ev`
- `0x34f4e0` — `_ZN4Navi8LaneInfoD1Ev`
- `0x3507c0` — `_ZN4Navi8LaneInfo5clearEv`
- `0x3507da` — `_ZN4Navi8LaneInfoC2Ev`
- `0x3507f0` — `_ZN4Navi8LaneInfoC1Ev`
- `0x388727` — `_ZN12stTPLaneInfo4initEv`
- `0x388740` — `_ZN12stTPLaneInfoeqERS_`
- `0x389a9e` — `_ZN24NaviDrivingAssistManager18ParserLaneInfoJsonER12stTPLaneInfoSs`
- `0x389ae2` — `_ZN24NaviDrivingAssistManager31sendClusterLaneInfoData_AndroidESt6vectorIN7NaviEng12_ClusterDataESaIS2_EE`
- `0x389b4c` — `_ZN24NaviDrivingAssistManager29sendClusterLaneInfoData_LinuxESt6vectorIN7NaviEng12_ClusterDataESaIS2_EE`
- `0x3a2c61` — `_ZN4Navi8LaneInfoaSERKS0_`
- `0x3ad186` — `_ZN4Navi5Guide14reducelaneInfoERNS_8LaneInfoEt`
- `0x3cc417` — `_ZN16CGuidanceService14reducelaneInfoERN4Navi8LaneInfoEt`
- `0x4022b2` — `_ZN23CExSLVehicleSettingImpl23sendClusterLaneInfoDataEPhi`
- `0x403c1e` — `_ZN19CExSLAppServiceImpl12sendLaneInfoEN4Navi15clusterLaneInfoE`
- `0x403d91` — `_ZN19CExSLAppServiceImpl32notifyNaviClusterViewModeChangedEN11NaviAdapter7Cluster17ecClusterViewTypeE`
- `0x4049b6` — `_ZN19CExSLAppServiceImpl30notifyLoadColorLaneInfoUpdatedESs`
- `0x412ed8` — `_ZN19CEngineeringControl20dealerModeImportPlanEv`
- `0x4700c7` — `_ZN16CGuidanceControl14reduceLaneInfoERN4Navi8LaneInfoEj`
- `0x485dde` — `_ZN4ccos4navi5guide26IHGuidanceVCRMInfoListener19onLoadColorLaneInfoERKSs`
- `0x4a281b` — `_ZNK4ccos4navi5guide25HAugmentedRealityTurnInfo16getColorLaneInfoEv`
- `0x4a285f` — `_ZN4ccos4navi5guide16HARColorLaneInfoC1ERKS2_`
- `0x4a288d` — `_ZNK4ccos4navi5guide16HARColorLaneInfo12getLaneCountEv`
- `0x4a28c4` — `_ZNK4ccos4navi5guide16HARColorLaneInfo11getLaneListEv`
- `0x4a2998` — `_ZN4ccos4navi5guide16HARColorLaneInfoD1Ev`
- `0x4a4ace` — `_ZN14GuidanceEngine17updateBusLaneInfoERKyN4ccos4navi5guide19HGuidanceUpdateTypeERN4Navi10CameraInfoE`
- `0x4a89bc` — `_ZN4Navi8LaneInfoC2ERKS0_`
- `0x4a89d6` — `_ZN4Navi8LaneInfoC1ERKS0_`
- `0x4a91d0` — `_ZN14GuidanceEngine18convertHtoLaneInfoERKN4ccos4navi5guide9HLaneInfoE`
- `0x4a9217` — `_ZNK4ccos4navi5guide9HLaneInfo17getRightHandDriveEv`
- `0x4a924b` — `_ZNK4ccos4navi5guide9HLaneInfo17getRemainDistanceEv`
- `0x4a927f` — `_ZNK4ccos4navi5guide9HLaneInfo11getGeoCoordEv`
- `0x4a92ad` — `_ZNK4ccos4navi5guide9HLaneInfo12getLaneItemsEv`
- `0x4a9442` — `_ZN4ccos4navi5guide9HLaneInfoC1Ev`
- `0x4a9464` — `_ZNK4ccos4navi5guide9HGuidance11getLaneInfoERKyRNS1_9HLaneInfoE`
- `0x4a94a4` — `_ZN4ccos4navi5guide9HLaneInfoD1Ev`

JNI/dynsym matches for Lane/Cluster/TPLane/updateNavigation are in `tmp/navi-re/jni-lane.txt`. Use `strings-lane.tsv`, `xref-lane.txt`, and `disasm.index.tsv` under `tmp/navi-re/` to locate candidates in `disasm.txt`. Convert offsets with `section_vaddr + (file_offset - section_file_offset)`. This index does not state behavioral conclusions.
