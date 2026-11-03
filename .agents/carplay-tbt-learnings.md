Status: current - field notes ported from the HMG-API wireless mod work (2026-11-03). Nothing here is verified on a car unless it says so.

# CarPlay turn-by-turn and Wi-Fi: what we learned

## Code that came over
`payload/src/hmgapi/mods/wireless/dipo/CpTurns.java.src` and `Dipo.java.src` carry these fixes (AGPL, adapted from HMG-API; marked in the file header). Host test: `modules/wireless-carplay-and-aa/test/cpturns/run.sh` (39 cases, part of the module test).
- Icons: the exact table returned 0 for many iAP2 maneuver types, and `turn()` then skipped the whole update (distance, HUD, trip). Now a banded fallback by angle and side picks the nearest confirmed basic arrow, sharp/slight types keep their meaning, left-hand traffic is handled. Only cluster bytes confirmed in the HMG-API cluster notes are sent (basic 0x41-0x49, highway exit 0x51/0x52, 0xac-0xae, roundabouts, arrival 0x70-0x72, ferry 0xb0). Not used yet: via-point arrival 0x73-0x75, highway ramp straight 0x56.
- Lanes: modes `auto` (default), `off`, `rec`, `dim0`, `dim3` (`Dipo.laneMode()`, fixed to `auto` here). Phone lane status 2 = preferred lane, item 3 = lanes usable for the route. Owner saw several lanes highlighted: auto should show only the preferred lane; verify with Naver.
- HUD distance: km/mi rounds to one decimal (cap 4095), decimal commas parse, null items and empty arrays are guarded, `Dipo.post()` logs any Throwable.
- Trace: `Dipo.trace()` (fixed on) logs numbers only (route guidance, maneuver, lane, and `turn skip ... no icon for maneuver ... type X side Y junction Z exit E`). No road or destination names. Here it goes to logcat tag `HMG-Seogaemo-Wireless`; HMG-API also copied it to a USB folder with a root service (not ported, see below).

## Open problems (need a drive log)
- Updates sometimes do not reach the cluster. Hypothesis: `Maneuver.read` resets the exit angle each update, icon becomes 0, `turn()` returns early. Fix idea: keep the previous angle for the same maneuver type.
- Lanes are not cleared when the phone stops sending lane guidance (item 18 ignored).
- Maneuver may arrive before the route update; Naver's real lane status values are unknown.
- NEEV.KOR hook map: if trace lines `T ...` never appear, the hooks are off.

## Not ported: trace export to USB
HMG-API's `svc/trace.sh` (a root service) waited for the setting, created `WirelessTrace/` on a mounted stick and copied the rotating trace every 20 s (`hmgapid usb` only lists folders that already exist, so the service must create it). Here `services=` is empty and the root script has fixed verbs only. Needs a design decision: an `export-trace usb` verb on the Logs page (one-shot `logcat -d -s HMG-Seogaemo-Wireless`, but the 4.4 main buffer holds only ~10 s on this unit, so a streaming service is required) versus a service owned by the module.

## Wi-Fi: client and CarPlay access point
- Hotspot: wlan0 `192.168.0.1/24` (hostapd). Stock hotspot menu says "check Bluelink" and cannot be used.
- HMG-API's `wifi.client` also set `persist.wifi.state=3`; with that and wireless CarPlay both on, CarPlay failed to connect even with the car joined to nothing. Each alone worked. HMG-Seogaemo's `wifi-client` now sets the feature file and state=3 (the NEEV KOR featurexml has no Wi-Fi line); it excludes `wireless-carplay-and-aa`, so this combination cannot be enabled together here.
- AOSP enables STA by first stopping the AP; one radio, so STA and AP at once is not expected. Hyundai's mode 3 is unverified.
- Wireless adb over the car AP: needs the AP's SSID and password (HMG-API's ap-info mod logged them to its private log; never share it), then `adb connect 192.168.0.1:5555`; ADB over USB puts the USB port in device mode, so USB sticks stop being recognized (turn it off and apply). The Engineering Mode ADB TCP switch must stay off (it assigns eth0 192.168.0.123 and breaks DHCP).
- Diagnostic worth porting as a read-only status: `persist.wifi.state`, interfaces, hostapd and wpa_supplicant processes, wifi files with passwords removed, dmesg wifi lines, snapshot at start, at AP up, then each minute; compare client off versus on. HMG-API's `wifi-diag` mod did exactly that (not ported yet).

## Rules we kept hitting
- Head-unit shell has no `tr`, `sed`, `awk`, `tail`; `make test` lints it.
- Never publish Wi-Fi passwords, VINs or per-car data; trace logs are numbers only.
- A mod signed with another key is a different mod; uninstall the old one first.
