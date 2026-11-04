# Extras: virtual exhaust/driving sound and manual battery preconditioning (Hyundai/Kia/Genesis)

Compiled 2026-11-05. Secondary focus; evidence is thinner than for TBT. Source IDs refer to `sources.md`.

## 1. Virtual exhaust and driving sound (e-ASD, N Active Sound+)

### What is confirmed
- **Ioniq 5 N has N Active Sound+.** It plays a synthesized 2.0-litre turbo sound in the cabin and through exterior speakers. Public descriptions give 10 speakers (8 inside, 2 outside), up to about 85 dB, and three sound modes (Ignition, Evolution, Supersonic). Source: E3 (press and search snippets, 2023 to 2024). Confidence **M**.
- **Ioniq 5 N, Ioniq 6 N and Ioniq 9 have simulated gearshifts and engine sounds that can be switched off.** Source: E2, Carscoops, 2026-04-28. The article does not give a menu path and does not mention Kia or Genesis. Confidence **M**.
- **Hyundai plans to extend the simulated shifts and sounds to more mainstream EVs but not base models**, per E2 (April 2026). Timing and models not confirmed. Confidence **M**.
- **Kia EV6 GT (2025) virtual shift and engine sounds can be turned off entirely.** Source: E4 (InsideEVs via inkl). Confidence **L** (secondary aggregator, GT trim only).
- **Genesis e-ASD** ("Electric Active Sound Design") is attributed to the Genesis GV60 in one search snippet. The page could not be confirmed. Confidence **L**.

### What is not confirmed
- **Standard Ioniq 5 / EV6 exhaust-type sound and its setting.** Searches found no official or owner source with a menu path. One low-quality page calls the Ioniq 5's cabin sound "Active Sound Design (ASD)", which is not reliable. Confidence on any menu path: **none**.
- **Whether e-ASD exists on the standard Ioniq 5.** Not found. Treat as unknown.
- **Head-unit vs vehicle control.** Not found in any public source. No source gives the settings location. Treat as unknown.

### Relevance to our project
- No public source ties the sound to a CAN frame or to a head-unit API. HMG-API's own catalogue has no sound/exhaust entries that were read in this pass. If we ever need it, the first step is a read-only log of the settings app's sound page on a car, with the owner's approval.
- Pedestrian-alert sound (AVAS) is a separate regulatory system. Do not conflate it with the driving sound.

### Suggested next search (not done)
- Owner forums for "EV6 GT engine sound setting" and Korean terms "가상 엔진음 설정" or "N 액티브 사운드 끄기" on Clien, Bobaedream and Naver cafes, with the 2025 to 2026 model years.

## 2. Manual battery preconditioning on Ioniq 5 and EV6

### Confirmed (multiple independent sources)
- **No manual start button on the MY2023 Ioniq 5.** Preconditioning cannot be started manually and is tied to the integrated navigation. Source: E1, Electrive, 2023-03-24 (updated 2024-10-28), tested on MY2023 Ioniq 5 AWD. Confidence **M-H** (single test, but consistent with the petition and owner reports).
- **Petition for a manual button** (E5, change.org). Says the limit on 2022 to 2024 Ioniq 5 and EV6 with Gen-5W software is 20% state of charge. The 20% figure is a petition claim. Confidence **L**.
- **Trigger on the navigation** (E1, E6, and Genesis forum notes): a destination is a DC charger, or the car's range-out logic adds a charging stop. The car starts pre-heating when the charging stop is set as a waypoint, once the car is ready to drive. Confidence **M**.

### From HMG-API (owner-tested on a US EV6, and field notes)
Source: `tmp/HMG-API/docs/vehicle-commands.md` section 4 and `docs/micom-map.md`. **H** for what HMG-API does; **M** for thresholds that come from a community tool (Launchpad) and owner reports.

- **Signals**: head-unit navigation sends distance to charger and time over M-CAN frame **0x4ED Navigation_Precond_Status** (distance, time, station event). The car reports the battery conditioning indicator as `IBCMService.getBattCondIndSta` (1 on, 0 off) and the BMS flag in frame 0x2AD BMS_Precond_Status.
- **Start sequence (from HMG-API)**: send distance, then time, then station value `2,3` once. The station value is reset by the car about 120 ms after any set, so it must not be re-sent. A 5-second keep-alive resends only distance and time. Resending the station too repeated the start and left the indicator off on a US EV6. **H** (owner test on EV6 260507).
- **Checks before start**: refused when the car is not READY (HEV `EV_RDY`, 44), when Battery Conditioning in EV settings is off (HEV `EV_BATTERYCONDITIONING`, 94, value 2 = on), or when the indicator already shows conditioning. **H**
- **Temperature gate (from Launchpad field notes, not BMS calibration)**: pack minimum temperature threshold EV6 and Ioniq 6 20 °C (68 °F), Ioniq 5 21 °C (70 °F). Read via `IClusterService.requestBatteryInfoReq` and `onHandleBatteryInfoResp` (index 1). That Binder path is absent on Ioniq 5 USA 260903 per HMG-API's catalogue. **M**
- **Timing**: EV6 can take up to 5 minutes to switch on (Launchpad field note); Ioniq 5 about 60 to 70 seconds. HMG-API times out at 5 minutes on EV6 and 2 minutes on others. **M**
- **Route values**: the BMS uses the distance and time to set the heating. Ranges 0 to 2046 km and 0 to 1022 min. Values 2047 and 1023 mean "no route". Defaults without a real route: 9 km and 18 min (Launchpad's). **H** for the project's code, **M** for the default.
- **Open question (HMG-API)**: whether the car's own navigation overrides mod-set values while it has a route. Not tested.
- **Risk note from HMG-API**: the BMS spends energy heating or cooling the pack when fed route values that describe no real route. Conflicting automatic and manual requests must be arbitrated. **H** (project guidance).

### Other mentions
- **ironiq** (dragz/ironiq, ESP32 MicroPython, M-CAN) has a preconditioning toggle frame and battery-temperature readout. It lists no CAN IDs in its README. Confidence the project works on a car: **L** (not verified). Source A12.
- **Community guides** (recharged.com, sophiecress.com, autoblog 2022) describe the navigation-based method and say menu names change by software version (for example "Battery Conditioning Mode" or "Winter Mode" in older builds). Confidence **L**; not checked against a manual.
- **Owner reports**: a user found preconditioning disabled in a setup menu after an update. Worth checking if the feature seems off. Confidence **L**.
- **Launchpad** (paytondevs) is the public name of the community tool that does manual preconditioning on the car. Only referenced through HMG-API's docs; no repository found in this pass. **L** for its code.

### What we should not do without owner approval
- Writing the 0x4ED or station frames, or calling the conditioning Binder methods, changes battery energy use on the car. The project's own rules require a confirm dialog and a ledger entry for anything that changes vehicle state.

## 3. Sources for these extras

See `sources.md` section E (E1 to E6) and A6, A5, A7, A12. Not found: a Hyundai or Kia official document for manual preconditioning, and any public CAN dump showing the start sequence on Ioniq 5.
