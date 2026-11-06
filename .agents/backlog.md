Status: current — open verification and development items.

# Backlog: checks and planned work

**Maintenance rule.** To-do list, not history. Delete an item in the commit that completes it (merged, or verified on the car); record lasting findings in the linked topic doc. New items need enough context and links for an agent without the session.

Car baseline: Ioniq 5 KOR (NEEV.KOR.\*.260902), NEMESIS + HMG-Seogaemo only. The car runs release v0.1.28: wireless CarPlay, wireless ADB, skip-consent-screen and user-button-shortcut on, wifi-client off, developer mode on (`persist.hmg_seogaemo.dev=1`), adb at `192.168.0.1:5555` over the hotspot, `/data/local/tmp/nemesis-d` present (recreate with `adb shell cp /system/xbin/nemesis /data/local/tmp/nemesis-d` if missing).

Local evidence (gitignored, owner's Mac): `tmp/car-sessions/2026-11-04-tbt/` (live logcat, USB export with `diagnostics/tbt/`, `analysis/` reports); `tmp/car-sessions/2026-11-05-drive/` (0.1.28 drive logcat, exports, `drive-notes.txt` with the lane-injection results). Lane injection tool: `tmp/lane-probe/` (`lanes.sh`, `trailer.sh`, `LaneProbe call <method> <ints>`).

## 1. CarPlay turn-by-turn follow-ups

Background: `carplay-tbt-learnings.md`, `carplay-tbt-external-research.md` (0x5204 layout, ManeuverType enum, icon codes), `wireless-carplay-and-aa.md` (observation records, developer toggle `persist.hmg_seogaemo.dev`, Dalvik cross-dex rule), runtime contract "Turn-by-turn observation records and `tbt-reset`".

- [ ] **Maneuver → icon table from field data.** NAVER Map (via CarPlay) sends no exit
  angle; type-based arrows now cover 1/2/47–50. Review the recorded combinations and
  instruction texts (`tbt-icons.txt`/journal in exports) and refine: type 0 with
  junction=1 exit=180 (roundabout, icon 102), 4 U-turn, 13/14 keep, 53, ramps/exits.
  Third-party reference (reference only, do not copy):
  owner upload `nav_mapping.py` (HKMC `Navigation$DisplayType`, 81-byte lane struct
  incl. bitmasks at 0x43–0x50).
- [ ] **TBT icons from firmware (ramps/roundabout); verify on car.** Icon 8 (none) and highway 160–165 are car-verified (`cluster-tbt-icons.md`). Firmware: highway entry/exit use 160–165 (not only 81/82/86), still to be used by `exactIcon`; roundabout has a main family (96/97/99/100/102/104/105/107) and an alternate-side family (192/193/195/196/198/200/201/203). Extending `exactIcon`/ramp/roundabout needs the phone-side driving-side/alt-side selector; verify against captured stock frames and drive logs first.
- [ ] **Retire HU-side captures once firmware mapping is verified.** Lane frame (81 bytes), arrow codes and TBT icon/unit codes are being confirmed from stock libExSLNavi.so (`cluster-lane-frame.md`, `cluster-lane-arrows.md`, `cluster-tbt-icons.md`), so HU-side values no longer need reverse-engineering from observations. Plan: (1) next drive, check firmware values against captured stock `source=car-nav` 81-byte frames matched to real on-road lanes; (2) then stop the always-on `source=car-nav` capture (CarLaneCapture) or make it dev-mode-only. Keep the iAP2/phone-side records (`tbt-icons`, CarPlay `tbt-lanes`): what NAVER/Apple send is not in HU firmware and still needs field data for the maneuver/lane tables.
- [ ] **Verify CarPlay lane glyph pairs, per-arrow status and lane distance on the car.** Implemented in `CpTurns` (`LaneGlyphs`, `laneValues`, `sendLaneDist`); host-tested only. Check on a drive with multi-direction lanes: pairs/triples draw as in [cluster-lane-arrows.md](cluster-lane-arrows.md), only the route arrow is blue, the distance box beside the strip tracks the turn distance and clears (`65535,15,15,0` is unverified) when lanes go away.
- [ ] **Capture what drives the cluster AR route view / HUD hologram turn (stock nav).**
  Owner saw (2026-11-06) stock navigation show an AR route view on the cluster and a
  hologram left/right turn on the HUD (hologram mode) from ~300 m before a turn; CarPlay
  guidance shows neither. Offline RE ([navi-ar-guidance.md](navi-ar-guidance.md)) found no
  causal sink and no fixed 300 m; `updateCluDataNew_14` (Binder 71) is a stock-reachable
  candidate but our guessed one-shot probes showed nothing and no stock call is in any log.
  Next drive: stock navigation through left/right turns with
  `adb logcat -v time -s ClusterService` keeping every `NaviCluster.*` line, passenger
  noting AR/hologram onset/end against distance (500/300/100/0 m) and the HUD/cluster
  modes. PE14 present at onset → replay stock values for CarPlay; absent → cluster/HUD
  likely draws from ordinary TBT, compare stock vs CarPlay TBT/F2/distance calls.
- [ ] **Lane distance box shows the TBT distance.** Car, 0.1.30: `updateRemainDis` gets the next-maneuver distance even when the lane strip is for an intermediate junction ([cluster-lane-frame.md](cluster-lane-frame.md) 1b). Proposed: send it only when the lane strip belongs to the current maneuver (a 0x5202 p12 link when the app sets one; otherwise the lane highlight angles agree with the maneuver direction), else the clear pattern; first check on the car that `65535,15,15,0` empties the box.
- [ ] **Map app in observation keys.** Record the route-guidance SourceName (NAVER,
  Apple Maps, Kakao, …) with each observation; apps differ (angles, types).
- [ ] **Status "unknown" with a second DiPO record.** `HmgSeogaemoProjectionRuntime`
  status can report `native.libCarPlayCore.so=registered waiting for library` from a
  second DiPO record in the same boot; base readiness on the process that loaded the
  libraries.
- [ ] **Transient "assets unreadable" during reapply.** `status` reports failed while
  `put_assets` is still writing recipes (seen right after `nemesis install --update`);
  report in-progress instead, or make status wait for the operation lock.
- [ ] **Trip distance vs next-turn distance.** `updateTBTSubInfo` carries remaining
  trip distance (km, 0.1 steps); confirm on the cluster/HUD which field shows the
  next-turn distance and that units switch correctly under 1 km.
- [ ] **Pull the decrypted stock navigation engine from the car (read-only).** `libExSLNavi.so` (and other native libs of `com.mnsoft.navi`, e.g. under `/data/app-lib/`) is missing from the 2026-11-04 offline dump; the official update backup (V031/V036) only has encrypted `enc_libExSLNavi.so`. The car's decrypted copy would show how lane fields (`dir`, `idx`, `valid`) become the 81-byte 0xF1 frame. Locate with toolbox `ls` (no `find`), `adb pull`, no writes.
- [ ] Guard test: no new `hmgapi/mods` class may extend/implement a `hmgapi/hook` type
  other than `MethodHook` (dexdump check in the module test).

## 1b. Core hook host and installable modules

- [ ] **Finish the on-car check of the core hook host** (`core/hook/`, contract "Core hook host"). Single hook module verified on NEEV KOR 2026-11-05 (root-side branch files only, release APK kept): `hooks on, start 1`, Settings logged `[hook-sample] hello from com.android.settings`, revert + restart left `app_process` stock (md5 `33f79811…`, 9492 B, 0:2000), no extras, no wrapper lines in `dmesg`. Still open: repeat the revert check with stock restore, and the boot guard (3 forced zygote crashes turn hooks off). Procedure: adb ready, CarPlay off first (mutually exclusive); `make package`, `python3 tools/hsm pack out/hook-sample/module --out out/hook-sample`, `python3 tools/hsm push out/hook-sample/hook-sample-0.1.0.hsm`, `hmg-seogaemo-root hook-sample apply`, restart; check `dmesg | grep hmg-seogaemo-hook`, `logcat -s HMG-Seogaemo-Hook` ("hello from" in Settings and DiPO), `/data/nemesis/backup/hmg-seogaemo/core/hook/status`, module status `info.hook_*`; then revert, restart, confirm stock `app_process` md5 and extras gone.
- [ ] Settings changed while on are applied by revert+apply; the zygote reads them only at
  start (restart needed). Decide whether hook modules should say so in the UI.
- [ ] On-car check with two hook modules sharing one wrapper (both `hello` lines).
- [ ] After the last hook module is off, `core/hook/core-hook.properties`, the empty
  `core/hook/mods/` and `modules/core-hook/{module.log,assets-repaired-boot}` remain; clean up.
- [ ] Installed-module status prints `info.desired=off` twice (runner + dispatcher).
- [ ] `tools/hsm pack` without `--out` writes to `./dist/` (not gitignored); default to `out/`.
- [ ] Migrate CarPlay onto the core host (plan in `installable-modules.md` Core hook host).
  Until then CarPlay and hook modules exclude each other.

## 2. Older open items

- [ ] `nemesis root -c` with long commands fails with "daemon write failed"; find the
  limit and keep app/root commands below it (split or script file).
- [ ] Restore stock stops at the first module conflict and leaves later modules
  not attempted; decide whether to continue with the others (`stock-restore.md`).
- [ ] Show "/system stays rw until restart" (remount-ro EBUSY) in the UI/status, not
  only in `module.log`.
- [ ] (With the UI/UX redesign) Wireless ADB shows "확인 불가" / `adbd 상태: stopped`
  when the app polls during the boot re-apply's adbd restart (about 70 s after boot):
  `wireless-adb.sh` status reports `failed` while `init.svc.adbd` is not `running`, and
  the app never re-polls. Report a transient "applying" state and re-poll in the app.
- [ ] (With the UI/UX redesign) Switching wifi-client -> CarPlay from the CarPlay row
  refuses with "ADB state unknown" until the Wireless ADB screen has been opened once.
  The plan/precheck uses a stale or unknown wireless-adb status instead of polling it;
  refresh dependents before planning.
- [ ] On-car checks not yet done: wifi-client disable exact restore (+restart);
  both user buttons after restart and after visiting stock settings
  (`user-button-shortcut.md`); full NEMESIS uninstall leaves the unit byte-identical
  (`stock-restore.md`); QuickBoot/ignition-only cycle behaviour (`boot-lifecycle.md`).

## 3. Planned features (owner-approved direction)

Evidence and candidate hooks: `neev-kor-api-inventory.md`.

- [ ] Vehicle firmware OTA blocking.
- [ ] CarPlay per-component switches (audio, TBT, Siri, …).
- [ ] USB ADB role switch.
- [ ] Battery diagnostics, read-only first; any control needs owner approval.
- [ ] EV information to the phone over iAP2 (EV routing).
- [ ] Virtual exhaust / driving sound in the cabin: research the head-unit audio path and the
  vehicle-speed/accelerator signals available to it (and any stock sound feature to reuse).
- [ ] Manual battery preconditioning (start battery conditioning without a navigation
  destination to a charger); find the BMS/thermal request the stock EV navigation sends.
