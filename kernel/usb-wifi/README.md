# USB Wi-Fi kernel modules

Reproducible build of Realtek USB Wi-Fi drivers for Hyundai/Kia/Genesis Gen5W
head units running Linux `3.18.24-tcc` (vermagic
`3.18.24-tcc SMP preempt mod_unload ARMv7 p2v8`):

| Module | Chips | Platforms |
| --- | --- | --- |
| `88x2bu.ko` | RTL8812BU, RTL8822BU | tcc803x, tcc897x |
| `8851bu.ko` | RTL8831BU, RTL8851BU | tcc803x, tcc897x |

## Build

Needs Docker (on Apple Silicon: OrbStack or Docker Desktop with amd64
emulation) and network access.

```sh
sh kernel/usb-wifi/build.sh          # JOBS=N to limit parallel make jobs
```

Output (gitignored): `out/tcc803x/*.ko`, `out/tcc897x/*.ko`, `out/SHA256SUMS`,
and per module a `.undefined-symbols.txt` list. Downloads are cached in
`out/cache/`; kernel trees live in the Docker volume
`hmg-seogaemo-usb-wifi-work` (macOS bind mounts are case-insensitive, the
kernel tree is not). The first run builds a full vanilla kernel per platform
and takes a while; later runs reuse it.

The script fails unless, for every module:

- the `vermagic` in `.modinfo` equals the car's string exactly;
- modpost, run against `Module.symvers` of a full 3.18.24 `vmlinux` built from
  the same car config, reports no undefined symbol, and every `nm -u` symbol of
  the `.ko` is in that export list.

## How the build tree matches the car

- Sources: kernel.org `linux-3.18.24.tar.xz` (sha256 checked), AOSP
  `arm-eabi-4.7` (kitkat-release, pinned commit, hash over the extracted
  files because Gitiles archives are not byte-stable), driver commits pinned
  in `build.sh`. Base image pinned by digest in `Dockerfile`.
- `include/config/auto.conf` and `include/generated/autoconf.h` are written
  from the car's `.config` (`configs/`, see `configs/PROVENANCE.md`) rather
  than from vanilla Kconfig output, which would drop Telechips symbols and
  change ABI-relevant values. Left out: `NEED_MACH_IO_H` and `WAKELOCK`
  (their headers exist only in the Telechips tree). `CC_STACKPROTECTOR_STRONG`
  becomes the regular protector because GCC 4.7 has no
  `-fstack-protector-strong`. `<mach/irqs.h>` is generated with the
  platform's `NR_IRQS` (tcc803x 256, tcc897x 160).
- The export check uses the same car config with the Telechips machine
  replaced by the generic ARMv7 `virt` machine. The script checks that CPU,
  SMP, preempt, LPAE, `MODULE_UNLOAD`, cfg80211 and USB settings stay equal
  to the car's. Telechips-only exports are not covered, and the drivers do not
  use any.

## Driver patches

`patches/<driver>-NNNN-*.patch` (our own, applied in order with `git apply`):

1. `hmg-gen5w-platform`: Gen5W platform block: little-endian, cfg80211/nl80211
   (`CONFIG_IOCTL_CFG80211`, for `hostapd`/`wpa_supplicant -Dnl80211`), no MP
   test mode, no PHY parameter files read from the file system, fixed
   `__DATE__`/`__TIME__` for byte-reproducible objects. 8851bu also drops the
   efuse override file; 88x2bu keeps upstream's optional
   `/system/etc/wifi/wifi_efuse_88x2bu.map` (its debug code needs the path;
   the file does not exist on the car, so the chip's efuse is used).
2. `rtwlan-interface-names`: interfaces are `rtwlan0`, `rtwlan1`, ... so the
   stock `wlan0` is untouched (still overridable with `ifname=`/`if2name=`).
3. `mark-unplugged-device-before-teardown`: on a physical unplug, the device is
   flagged surprise-removed before the net devices are unregistered, so
   `ndo_stop` and cfg80211 AP/STA teardown skip I/O to hardware that is gone.
4. `hide-usbfs-node-while-bound`: while bound, the driver replays the USB
   device's `remove` uevent so ueventd deletes `/dev/bus/usb/BBB/DDD` and
   Android Auto's usbfs probing (AOA vendor requests) cannot reach the dongle;
   `add` is replayed when the driver unbinds from a still-attached device.
   Disable with the module parameter `hide_usbfs=0`.
5. `build-on-linux-3.18-with-gcc-4.7` (8851bu only): `strscpy()` for kernels
   before 4.3, a plain counter where upstream casts a `u16` to `atomic_t`, and
   the hex parser kept outside `CONFIG_PHL_TEST_SUITE` because the
   power-save debug commands use it with MP mode off.

DHCP port translation stays out of the drivers.

## Licenses

The drivers are GPL-2.0: [cilynx/rtl88x2bu](https://github.com/cilynx/rtl88x2bu)
and [fofajardo/rtl8851bu](https://github.com/fofajardo/rtl8851bu). The
corresponding source of every `.ko` built here is the pinned upstream commit
in `build.sh` plus the patches in `patches/`, which are GPL-2.0 like the code
they modify. This directory's build scripts are part of HMG-Seogaemo
(AGPL-3.0). Pins and provenance are also listed in `UPSTREAM.md`.

## Not verified yet

Compile-time and ABI checks only. Loading on a car, interface naming, AP/STA
operation, hot unplug while up, and the usbfs hiding with Android Auto have
not been tested on a head unit.
