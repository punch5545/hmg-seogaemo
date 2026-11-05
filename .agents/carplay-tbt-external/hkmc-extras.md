# Extras: virtual driving sound and manual battery preconditioning (Hyundai/Kia/Genesis)

Compiled 2026-11-05; secondary focus, thinner evidence than TBT. Source IDs E1-E6, A5-A7, A12 in [sources.md](sources.md).

## 1. Virtual exhaust / driving sound (e-ASD, N Active Sound+)

Confirmed:
- **Ioniq 5 N has N Active Sound+**: synthesized 2.0 L turbo sound in cabin and exterior speakers; 10 speakers (8 in, 2 out), up to ~85 dB, modes Ignition / Evolution / Supersonic (E3, press/snippets 2023-2024). M.
- **Ioniq 5 N, Ioniq 6 N, Ioniq 9** have simulated gearshifts/engine sounds that can be switched off (E2, Carscoops 2026-04-28; no menu path, no Kia/Genesis). M.
- Hyundai plans to extend simulated shifts/sounds to more mainstream EVs, not base models (E2; timing/models unconfirmed). M.
- **Kia EV6 GT (2025)** virtual shift/engine sounds can be turned off (E4, InsideEVs via inkl; GT only). L.
- **Genesis e-ASD** ("Electric Active Sound Design") attributed to GV60 in one snippet; page not confirmed. L.

Not confirmed (treat as unknown): standard Ioniq 5 / EV6 exhaust-type sound and its setting (one low-quality page calls the Ioniq 5 cabin sound "Active Sound Design (ASD)"); whether e-ASD exists on the standard Ioniq 5; head-unit vs vehicle control and settings location.

Relevance: no public source ties the sound to a CAN frame or head-unit API; HMG-API's catalogue has no sound/exhaust entry read in this pass. First step if needed: read-only log of the settings app's sound page on a car, with owner approval. Pedestrian-alert sound (AVAS) is a separate regulatory system.
Not done: owner-forum searches ("EV6 GT engine sound setting", "가상 엔진음 설정", "N 액티브 사운드 끄기" on Clien, Bobaedream, Naver cafes, MY2025-2026).

## 2. Manual battery preconditioning on Ioniq 5 / EV6

Confirmed:
- **No manual start on MY2023 Ioniq 5**; preconditioning is tied to integrated navigation (E1 Electrive 2023-03-24, updated 2024-10-28, MY2023 AWD test). M-H.
- Petition for a manual button (E5, change.org): claims a 20% state-of-charge limit on 2022-2024 Ioniq 5/EV6 with Gen-5W software. L.
- Trigger (E1, E6, Genesis forum): navigation destination is a DC charger, or range-out logic adds a charging stop set as waypoint, once the car is ready to drive. M.

From HMG-API (owner-tested on a US EV6; `tmp/HMG-API/docs/vehicle-commands.md` section 4, `docs/micom-map.md`). H for what it does; M for thresholds (Launchpad community tool, owner reports):
- **Signals:** HU navigation sends distance to charger and time over M-CAN **0x4ED Navigation_Precond_Status** (distance, time, station event). Car reports conditioning indicator as `IBCMService.getBattCondIndSta` (1 on, 0 off) and BMS flag in frame 0x2AD BMS_Precond_Status.
- **Start sequence:** send distance, then time, then station value `2,3` once. Car resets the station value ~120 ms after any set, so do not re-send. 5-second keep-alive resends only distance and time; resending station too repeated the start and left the indicator off on a US EV6 (owner test EV6 260507).
- **Pre-checks:** refuse when car not READY (HEV `EV_RDY`, 44), when Battery Conditioning in EV settings is off (HEV `EV_BATTERYCONDITIONING`, 94, value 2 = on), or when indicator already shows conditioning.
- **Temperature gate (Launchpad field notes, not BMS calibration):** minimum pack temperature EV6/Ioniq 6 20 C (68 F), Ioniq 5 21 C (70 F), read via `IClusterService.requestBatteryInfoReq` / `onHandleBatteryInfoResp` (index 1). That Binder path is absent on Ioniq 5 USA 260903 per HMG-API's catalogue. M.
- **Timing:** EV6 up to 5 min to switch on (Launchpad), Ioniq 5 ~60-70 s; HMG-API times out at 5 min on EV6, 2 min others. M.
- **Route values:** BMS uses distance/time to set heating; ranges 0-2046 km, 0-1022 min; 2047 and 1023 = "no route". Defaults without a real route: 9 km, 18 min (Launchpad). H code, M default.
- **Open (HMG-API):** whether the car's own navigation overrides mod-set values while it has a route; untested.
- **Risk (HMG-API):** BMS spends energy heating/cooling the pack on route values describing no real route; conflicting automatic and manual requests must be arbitrated.

Other:
- **ironiq** (dragz/ironiq, ESP32 MicroPython M-CAN): preconditioning toggle frame and battery-temperature readout; README lists no CAN IDs; unverified on a car. L (A12).
- Community guides (recharged.com, sophiecress.com, autoblog 2022): navigation-based method; menu names vary by software ("Battery Conditioning Mode", "Winter Mode" in older builds). L.
- One owner found preconditioning disabled in a setup menu after an update; check if the feature seems off. L.
- **Launchpad** (paytondevs) is the community tool for manual preconditioning; known only via HMG-API docs, no repo found. L.

Do not, without owner approval: write 0x4ED or station frames or call the conditioning Binder methods (changes battery energy use; project rules require confirm dialog and ledger entry).
Not found: a Hyundai/Kia official document for manual preconditioning; any public CAN dump of the start sequence on Ioniq 5.
