Status: current — USB Wi-Fi dongle support, evidence stage (2026-11-06).

# External (USB) Wi-Fi dongle

Goal (owner, 2026-11-06): a USB Wi-Fi dongle works for both `wifi-client` (STA) and the
wireless CarPlay hotspot (AP). Today both assume the stock Broadcom radio:
`modules/wifi-client/module.sh` gates on `/system/wifi/bcmdhd.ko`, `fw_bcmdhd.bin` and `wlan0`.

## Constraints

- The tester is remote with app screen + USB only (no adb). The car has no HMG-Seogaemo yet,
  possibly another package; first install is the release `-nemesis-usb.zip`, then the Car tab
  cleanup handles foreign traces. Only release-signed builds go on that car.
- No adb means recovery is the app's Restore stock state or a USB reinstall; keep every test
  build reversible through the ledger.
- Wireless CarPlay is being moved to the shared hook host in another branch. Hotspot work for the
  dongle waits until that merges; STA work stays inside `modules/wifi-client/`.
- 0.1.33 cannot load a diagnostic `.hsm` (`raw_shell` is refused), so evidence collection ships in
  the core problem report.

## Evidence the problem report collects

`diagnostics/wifi-usb.txt` (redacted, 64 KiB): `/proc/version`, `/proc/modules`, every
`/sys/class/net` interface with its sysfs path (USB or SDIO), driver link, operstate and device
`uevent`; every `/sys/bus/usb/devices` entry with `idVendor`/`idProduct`/class/product/speed and
`DRIVER`/`PRODUCT`/`MODALIAS`; driver/firmware directory listings; `wpa_supplicant`/`hostapd`/
`dhcpcd`/`iw` binaries; permissions listing; Wi-Fi lines of `/init*.rc`; Wi-Fi/USB properties;
Wi-Fi/USB kernel log lines. `diagnostics/kernel-config.gz` copies `/proc/config.gz` (256 KiB cap)
to show whether cfg80211/mac80211 and the dongle's driver could load.

## Open questions (answer from the tester's report)

- Dongle chip and whether the kernel already has its driver (built in, `.ko` on the system, or none).
- Whether a matching `.ko` can be built: needs the exact kernel version, vermagic and config.
- Interface name, and whether the stock HAL (`libhardware_legacy`, `wifi.interface`) can drive it.
- Whether `hostapd` can run AP mode on the dongle for the CarPlay bridge.
