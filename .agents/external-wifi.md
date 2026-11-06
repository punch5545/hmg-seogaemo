Status: current — USB Wi-Fi dongle support; `wifi` module roles and the CarPlay/AA side implemented off-car (2026-11-07), not run on a car.

# External (USB) Wi-Fi dongle

Goal (owner, 2026-11-06): a USB Wi-Fi dongle works for both the Wi-Fi client (STA) and the
wireless CarPlay hotspot (AP). Order chosen by the owner: driver first, then the CarPlay
hotspot. Implemented (owner-approved flow, 2026-11-07): the bundled `wifi` module (formerly
`wifi-client`) gives each radio one role, `builtin_role` and `usb_role` = `off|client|carplay_ap`,
with at most one `carplay_ap`; design, files and tests in [wifi.md](wifi.md). The CarPlay module uses the
published USB hotspot (see Interface for the CarPlay module below).

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
  published hotspot and owns any hooks (through the shared core hook host), implemented in
  `modules/wireless-carplay-and-aa` (CarPlay side below).
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

## CarPlay side (implemented off-car, 2026-11-07)

Normative rules: contract [Peer info](module-runtime-contract.md#peer-info). In `modules/wireless-carplay-and-aa`:

- `Wireless.zygoteInit` reads `<data root>/peer/wifi` as root in the zygote (`UsbHotspot`); only `radio=usb` with valid
  values counts, and the credentials stay in the mod loader's statics (runtime record: `hotspot.radio`, `hotspot.reason`,
  never the passphrase). A changed role takes effect after a head-unit restart, as both modules declare.
- DiPO `Bridge.ensureHotspot` skips `setWifiApEnabledForConnectivity` and the attach goes on.
- Part `usb_hotspot` (iAP2 server and GPM, framework/libcore hooks, no map points, so no KOR hookpoint mapping is needed):
  `WifiManager.getWifiApConfiguration()` returns the published SSID/passphrase in `SSID`/`preSharedKey` and, where the
  framework has them, `ConnectivitySSID`/`ConnectivityPSK`, with only `KeyMgmt` bit 4 (`WPA2_PSK`, which the iAP2
  server logs as such). That `getAuthType()` returns it assumes AOSP 4.4 logic; the car's framework source is not available.
  In GPM only, `NetworkInterface.getName()`/`getDisplayName()` report the adapter as `wlan0` and a real `wlan0` as
  `wlan0-builtin`, so GPM's host locator (first site-local IPv4 interface named `wlan0`; BSSID from its MAC) picks
  `rtwlan0`/`192.168.50.1`. A failed part shows in the module status; it never disables other parts.
- Without a published USB hotspot nothing loads or changes: the stock `wlan0` path is the car-verified one.
- Root: `info.hotspot_radio` and the built-in client refusal in `apply` (warning in `precheck`).

## Still unverified on a car

1. The whole USB path: hostapd/dnsmasq on `rtwlan0`, the iPhone joining with the credentials iAP2 sends, and whether
   CarPlay Bonjour/AirPlay (native) binds or advertises on `rtwlan0`.
2. Android Auto reaching `192.168.50.1:40515` (GPM logs `findHostInterfaceAndIp`), and the phone accepting the adapter's
   MAC as BSSID.
3. Whether the car firewall (`iptrules`) still drops anything on `rtwlan0` beyond the `wifi` module's INPUT accept rule.
4. That Dalvik hooks on `NetworkInterface` getters and `WifiManager.getWifiApConfiguration` take effect in those apps
   (trivial getters could be inlined if a caller is compiled before the hook; hooks install at app load).
