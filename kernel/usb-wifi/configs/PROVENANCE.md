# Kernel config provenance

`tcc803x.config` and `tcc897x.config` are Linux 3.18.24 `.config` dumps taken
from Gen5W head units. They are kept byte-for-byte as data; nothing in them was
edited. `build.sh` reads them to generate the module build tree's
`auto.conf`/`autoconf.h` and to configure the export-check kernel.

| File | Platform | Cars named by the source | SHA-256 |
| --- | --- | --- | --- |
| `tcc803x.config` | Telechips tcc803x (`CONFIG_ARCH_TCC803X`, LPAE, `-fstack-protector-strong`) | EV6, Ioniq 5, K8 HEV | `d35af8a0e9d098a47720ea66de594f21691d3d9608ce388ed199e0a5f82f9913` |
| `tcc897x.config` | Telechips tcc897x (`CONFIG_ARCH_TCC897X`, no LPAE, no stack protector) | Niro EV (DE) | `5ab80920ccba78220fc174a433acbc18ec8034330bf806a75907bf73942605f7` |

Source: [GRT47/HKG_5W_USB_Wlan](https://github.com/GRT47/HKG_5W_USB_Wlan) at
commit `2e9a74c853a379139b1aab56d49d76fe56845419` (2026-10-31), files
`package_usbwifi_hotspot/kernel_build/config_tcc803x.txt` and
`config_tcc897x.txt`. That repository has no license; only these
configuration dumps were taken, as factual data about the cars' kernels. No
script, patch or code from it is used in HMG-Seogaemo.

Not yet verified by us: that these dumps match `/proc/config.gz` on our own test
car (NEEV KOR, tcc803x). The problem report collects
`diagnostics/kernel-config.gz`; compare it with `tcc803x.config` when a report
from that car is available, and record the result here.
