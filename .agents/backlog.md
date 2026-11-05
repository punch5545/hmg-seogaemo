Status: current — open verification and development items, 2026-11-04 (after v0.1.26).

# Backlog: checks and planned work

**Maintenance rule.** This is a to-do list, not a history. When an item is done
(implemented and merged, or verified on the car), **delete it from this file** in the
same commit; record lasting findings in the linked topic document instead. Add new
items with enough context and links for an agent who has not seen the session.

Car baseline for these items: Hyundai Ioniq 5 KOR (NEEV.KOR.\*.260902), NEMESIS +
HMG-Seogaemo only. As of 2026-11-04 23:15 the car runs a local build equal to v0.1.26
(shown as 0.1.25): wireless CarPlay + wireless ADB on, wifi-client off, developer mode
off, adb at `192.168.0.1:5555` over the car hotspot. `/data/local/tmp/nemesis-d` was
removed; recreate it with `adb shell cp /system/xbin/nemesis /data/local/tmp/nemesis-d`
before root commands.

Local evidence from the 2026-11-04 drives (gitignored, on the owner's Mac):
`tmp/car-sessions/2026-11-04-tbt/` (live logcat, USB export with `diagnostics/tbt/`,
ledger, `analysis/` reports on maneuver table, raw 0x5204 lanes and follow-ups).

## 1. CarPlay turn-by-turn follow-ups

Background and fixed causes: `carplay-tbt-learnings.md`, external research
`carplay-tbt-external-research.md` (0x5204 layout, ManeuverType enum, icon codes), `wireless-carplay-and-aa.md`
(observation records, developer toggle `persist.hmg_seogaemo.dev`, Dalvik cross-dex rule),
runtime contract section "Turn-by-turn observation records and `tbt-reset`".

- [ ] **Maneuver → icon table from field data.** NAVER Map (via CarPlay) sends no exit
  angle; type-based arrows now cover 1/2/47–50. Review the recorded combinations and
  instruction texts (`tbt-icons.txt`/journal in exports) and refine: type 0 with
  junction=1 exit=180 (roundabout, icon 102), 4 U-turn, 13/14 keep, 53, ramps/exits.
  Third-party reference (reference only, do not copy):
  owner upload `nav_mapping.py` (HKMC `Navigation$DisplayType`, 81-byte lane struct
  incl. bitmasks at 0x43–0x50).
- [ ] **No-icon value.** Icon 64 renders as a "loading" indicator on the cluster and
  blank on the HUD; find a better "no symbol" value or keep the last arrow.
- [ ] **Retire HU-side captures once firmware mapping is verified.** The cluster lane frame (81 bytes),
  arrow codes and TBT icon/unit codes are being confirmed from the stock engine libExSLNavi.so
  (.agents/cluster-lane-frame.md, cluster-lane-arrows.md, cluster-tbt-icons.md). These are HU-side and
  no longer need to be *reverse-engineered* from observations. Plan: (1) on the next drive, verify the
  firmware values against captured stock `source=car-nav` 81-byte frames matched to real on-road lanes;
  (2) once verified, stop the always-on stock `source=car-nav` lane capture (CarLaneCapture) or make it
  dev-mode-only. Keep the iAP2/phone-side records (`tbt-icons`, CarPlay `tbt-lanes`): what NAVER/Apple
  actually send is not in HU firmware and still needs field data to fill the maneuver/lane tables.
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
- [ ] **Pull the decrypted stock navigation engine from the car (read-only).** `libExSLNavi.so`
  (and other native libs of `com.mnsoft.navi`, e.g. under `/data/app-lib/`) are missing from the
  2026-11-04 dump; the official update backup (V031/V036) only has `enc_libExSLNavi.so` (encrypted).
  With the car's decrypted copy we can see how the navigation's lane fields (`dir`, `idx`, `valid`)
  become the 81-byte 0xF1 frame. Locate with `ls`/`find`-free toolbox commands, `adb pull`, no writes.
- [ ] Guard test: no new `hmgapi/mods` class may extend/implement a `hmgapi/hook` type
  other than `MethodHook` (dexdump check in the module test).

## 1b. Core hook host and installable modules (branch `installable-modules`)

- [x] **On-car check of the core hook host** — done 2026-11-05 on NEEV KOR with only the
  root-side branch files swapped in (release APK kept): `hooks on, start 1`, Settings logged
  `[hook-sample] hello from com.android.settings`, revert + restart left `app_process` stock
  (md5 `33f79811…`, 9492 B, 0:2000), no extras, no wrapper lines in `dmesg`. The
  boot re-apply marked a needless restart each boot; fixed (`hsm-module.sh`,
  `sg_core_hook_ensure`, test in `test_hook_host`). Original procedure:
  (`core/hook/`, contract "Core hook host").
  Keep adb ready; CarPlay must be off first (they exclude each other). Build the package
  plus `out/hook-sample/hook-sample-0.1.0.hsm` (`make package`, then
  `python3 tools/hsm pack out/hook-sample/module --out out/hook-sample`), install with
  `python3 tools/hsm push out/hook-sample/hook-sample-0.1.0.hsm`, then
  `hmg-seogaemo-root hook-sample apply`, restart, and check: `dmesg | grep hmg-seogaemo-hook`
  (hooks on, start 1), `logcat -s HMG-Seogaemo-Hook` (zygote init, "hello from" in Settings
  and DiPO), `cat /data/nemesis/backup/hmg-seogaemo/core/hook/status`, module status
  `info.hook_*`. Then revert, restart, confirm `app_process` md5 is stock and the extras are
  gone; repeat with stock restore. Boot guard: 3 forced zygote crashes turn hooks off.
- [ ] Settings changed while on are applied by revert+apply; the zygote reads them only at
  start (restart needed). Decide whether hook modules should say so in the UI.
- [ ] On-car check with two hook modules sharing one wrapper (both `hello` lines).
- [ ] After the last hook module is off, `core/hook/core-hook.properties`, the empty
  `core/hook/mods/` and `modules/core-hook/{module.log,assets-repaired-boot}` remain; clean up.
- [ ] Installed-module status prints `info.desired=off` twice (runner + dispatcher).
- [ ] `tools/hsm pack` without `--out` writes to `./dist/` (not gitignored); default to `out/`.
- [ ] Migrate CarPlay onto the core host (plan in `module-plugin-design.md` P3).
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
  refused with "ADB state unknown" until the owner opened the Wireless ADB screen once;
  after that refresh the switch worked (2026-11-05). The plan/precheck uses a stale or
  unknown wireless-adb status instead of polling it; refresh dependents before planning.
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
