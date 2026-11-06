Status: current — USB Wi-Fi dongle support; `wifi` module roles implemented off-car (2026-11-07), CarPlay side open.

# External (USB) Wi-Fi dongle

Goal (owner, 2026-11-06): a USB Wi-Fi dongle works for both the Wi-Fi client (STA) and the
wireless CarPlay hotspot (AP). Order chosen by the owner: driver first, then the CarPlay
hotspot. Implemented (owner-approved flow, 2026-11-07): the bundled `wifi` module (formerly
`wifi-client`) gives each radio one role, `builtin_role` and `usb_role` = `off|client|carplay_ap`,
with at most one `carplay_ap`; design, files and tests in [wifi.md](wifi.md). The CarPlay module
still assumes the stock Broadcom hotspot on `wlan0` (see CarPlay-side changes below).

Reference: `tmp/HKG_5W_USB_Wlan` (GRT47, no license file: facts only, never copied). It loads
Realtek `88x2bu`/`8851bu` modules built for `3.18.24-tcc` from its package directory, names the
interface `rtwlan0` so stock `wlan0` stays untouched, and runs its own hostapd (stock binary copy,
`driver=nl80211`), dnsmasq, `wpa_supplicant -Dnl80211` and `dhcpcd` with iptables/ip rule glue.
It never writes `/system`; everything it changes is gone after a restart.

Kernel modules (2026-11-07, build-verified only): `kernel/usb-wifi/build.sh` builds `88x2bu.ko`
(RTL8812BU/8822BU, cilynx `04ed700`) and `8851bu.ko` (RTL8831BU/8851BU, fofajardo `9e37e7a`) for
tcc803x and tcc897x in Docker with AOSP `arm-eabi-4.7` against kernel.org 3.18.24, using the car
`.config` verbatim as `autoconf.h` (tcc803x has LPAE, so `dma_addr_t` is 64-bit). Each `.ko` has
vermagic `3.18.24-tcc SMP preempt mod_unload ARMv7 p2v8` and no symbol missing from a vanilla
3.18.24 `vmlinux` built from the same config (modpost + `nm -u`); Telechips-only exports are not
covered. Our patches: `rtwlan%d` names, nl80211, no MP mode, unplug marks the device gone before
netdev teardown, and usbfs hiding (`hide_usbfs=1`: replays the device `remove` uevent so ueventd
deletes `/dev/bus/usb/BBB/DDD` while bound, so Android Auto cannot send AOA requests). Not yet
loaded on a car; the configs are third-party dumps not yet compared with our car's
`/proc/config.gz`. Not ported: the reference's DHCP 67/1067 rewrite, extra 8851bu USB IDs and
its `0bda:1a2b` CD-ROM mode switch.

## Constraints

- The tester is remote with app screen + USB only (no adb). The car has no HMG-Seogaemo yet,
  possibly another package; first install is the release `-nemesis-usb.zip`, then the Car tab
  cleanup handles foreign traces. Only release-signed builds go on that car.
- No adb means recovery is the app's Restore stock state or a USB reinstall; keep every test
  build reversible through the ledger.
- Owner decision (2026-11-07, supersedes the separate `usb-wifi` module of 2026-11-06): driver,
  USB client and USB hotspot are roles of the `wifi` module; the CarPlay module consumes the
  published hotspot and owns any hooks (through the shared core hook host). Not a change inside
  `modules/wireless-carplay-and-aa` from this branch.
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
- A matching `.ko` builds (see Kernel modules above); whether it loads and runs on our car is open.
- Interface name, and whether the stock HAL (`libhardware_legacy`, `wifi.interface`) can drive it.
- Whether `hostapd` can run AP mode on the dongle for the CarPlay bridge.

## CarPlay/AA hotspot path (read-only analysis, 2026-11-06)

Full report: `tmp/external-wifi-carplay-ap.md` (stock code from the NEMESIS US 260507 dex build
via jadx; KOR 260902 equivalence still needs mapping).

- Our bridge starts the AP: on iPhone SPP connect, DiPO calls
  `WifiManager.setWifiApEnabledForConnectivity(getWifiApConfiguration(), true)`; below that,
  system_server/netd/hostapd on `wlan0` (bcmdhd, `192.168.0.1/24`) are stock (AOSP-inferred).
- iAP2 credentials: `com.tcc.iap2server` `OnSendWifiInformation()` reads
  `getWifiApConfiguration()` ConnectivitySSID/ConnectivityPSK and security in its own process; no
  channel, BSSID or IP. Not hooked by us today.
- Android Auto (GPM) reads the same SSID/PSK but finds host IP and BSSID by the hardcoded name
  `wlan0` (port 40515): the main blocker for a dongle.
- Unknown: which interface CarPlay Bonjour/AirPlay binds (native), and whether the car firewall
  (`iptrules`) drops `rtwlan0`.
- Chosen design: the `wifi` module owns driver, hostapd, DHCP and the firewall rule and publishes the
  hotspot; the CarPlay module reads it (`sg_peer_info wifi <key>`) and hooks
  `WifiManager.getWifiApConfiguration` (iap2server, GPM), GPM's interface lookup and DiPO's stock
  AP start. Decide by actual radio presence, not car model: the reference lists Ioniq 5 as having
  no built-in Wi-Fi, while the owner's KOR Ioniq 5 runs a `wlan0` hotspot.

## Runtime API

Implemented in `lib/common.sh` (off-car host tests only, `tools/test-transient.py`): `sg_kmod_load`,
`sg_daemon_start`, `sg_net_rule`, `sg_iface_addr`, with the read helpers `sg_kmod_loaded` and
`sg_daemon_running`. Manifest keys, validation, ownership and revert rules are normative in
[module-runtime-contract.md](module-runtime-contract.md#transient-effects). Added for the `wifi` module:
`sg_revert_transient`, `sg_daemon_restart`, and peer info (`sg_peer_publish`, `sg_peer_info`, contract
[Peer info](module-runtime-contract.md#peer-info)). None of it has run on a car: toolbox `insmod`/`rmmod`,
the KitKat `iptables`/`ip` binaries and daemon detachment from the NEMESIS root shell still need on-car
checks with the `wifi` USB roles.

## Interface for the CarPlay module

The hotspot to project over is published by `wifi` (root side; contract table "Peer info"): `radio`
(`usb|builtin|none`), `interface`, `ip`, `ssid`, `passphrase`, `channel`, `ready` (`1|0|stock`). The USB hotspot is
`rtwlan0`, `192.168.50.1/24`, DHCP `192.168.50.10-99`, 5 GHz channel 36, WPA2-PSK, SSID `HMG-Seogaemo-<4 hex>`;
credentials stay fixed across boots. `radio=builtin`, `ready=stock` means the stock hotspot as today.

## CarPlay-side changes still needed (not made in this branch)

1. Read the peer info in the CarPlay root script (apply/watch) and copy what hooks need into its own runtime record
   (system-uid hooks cannot read `/data/nemesis/backup/hmg-seogaemo`).
2. With `radio=usb`: hook `WifiManager.getWifiApConfiguration` in `com.tcc.iap2server` and GPM to return the published
   SSID/passphrase (WPA2), make DiPO's `setWifiApEnabledForConnectivity` a no-op that reports success instead of
   starting the stock `wlan0` AP, and replace GPM's hardcoded `wlan0` lookup (host IP, BSSID; port 40515) with
   `rtwlan0`/`192.168.50.1`. Then check which interface CarPlay Bonjour/AirPlay binds.
3. Refuse or warn in CarPlay `precheck`/`apply` when `wifi` publishes `radio=none` while `wifi` is on with
   `builtin_role=client` (today only the app planner turns one of them off; a direct root call is not refused).
4. Declare in the contract that the CarPlay module starts the hotspot only through DiPO's `WifiManager` call, and add a
   status line for the hotspot it uses (`info.hotspot_radio`).
5. The hook targets need mapped hookpoint IDs (KOR 260902 equivalence of the US build analysis is open).
