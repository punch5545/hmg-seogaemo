# hmg-seogaemo

HMG-Seogaemo: an app + root package for Hyundai/Kia/Genesis **Gen5W** head units, installed
through **NEMESIS**, made of toggleable modules. Target setup is **NEMESIS + HMG-Seogaemo only**
(HMG-API and HMG Leafia Plus are not installed alongside; they are upstream references).

## Before starting work

1. Read `.agents/README.md` (index + reading order), then the docs it marks **current**,
   at least `module-runtime-contract.md` and `module-architecture.md`. Docs marked
   *historical* explain past decisions; do not implement from them.
2. If `AGENTS.local.md` exists, read and follow it (per-user, gitignored).
3. Upstream references are not in git. Clone them yourself into `tmp/` (gitignored):
   `git clone https://github.com/xwtk/hmg_nemesis tmp/hmg_nemesis`,
   `git clone https://github.com/VysseCodes/HMG-API tmp/HMG-API`,
   `git clone https://github.com/VysseCodes/HMG-Leafia-Plus tmp/HMG-Leafia-Plus`.
   Many `.agents` citations point into `tmp/`. Car reports go in `tmp/car-reports/` (never commit them).
4. Add new findings to `.agents/` (and list new docs in `.agents/README.md`).

## Target device (verified facts)

- Hyundai Ioniq 5, Korea. `ro.build.product` = `NEEV.KOR.0000.V039.001.260902`; match it with the
  glob `NEEV.KOR.*.260902` (descriptor `os=` and scripts). NEMESIS compares `os=` to
  `ro.build.product` with globs; an exact `NEEV.KOR.260902` never matches.
- onebin `neev_kr`, Android 4.4.2 (API 19, Dalvik), Telechips, display 1920x720 hdpi (density 1.5).
- Owner-verified: HMG-API's car map + wireless mod gave a working hotspot + wireless CarPlay on this
  car; the stock firmware has no Wi-Fi client menu.

## Hard rules

- **Android 4.4 only:** API 19, Java 1.7, framework views, no AndroidX/Kotlin/Compose/Material.
  Compile against `platforms/android-19`. APK v1 (JAR) SHA1withRSA signing.
- **Head-unit shell = toolbox + mksh.** No `awk`, `sed`, `tail`, `head`, `cut`, `tr`, `wc`, `sort`,
  `find`, `stat`, `sha256sum`, `tar`, `unzip`, `busybox`. Hashing on the unit: toolbox `md5`
  (`<hex>  <path>`). `make test` lints every on-car script. Full list: contract, "Shell tools".
- **Modules never mutate the system directly.** All writes go through the package API in
  `nemesis/package/lib/common.sh` (`sg_prop_set`, `sg_file_put`, `sg_file_replace_line`,
  `sg_file_delete`, `sg_bind_mount`, `sg_service_restart`, `sg_zygote_install`,
  `sg_dex_cache_track`, `sg_revert_all`), which validates ownership (`module.properties`
  `owns_props`/`owns_files`/`services`), baselines, writes, verifies and appends to the ledger.
  Module scripts may only read. Need a new kind of write? Add an API function to the runtime and
  the contract; never work around it in a module.
- **Exact restore on uninstall is the top priority.** Restores are guarded: only undo bytes/values
  we wrote; if the target changed since (e.g. an official firmware update), report conflict, keep
  backups and fail the uninstall closed. Baselines are bound to `ro.build.product`.
- **Root channel:** the app is unprivileged and calls only `hmg-seogaemo-root <id> <verb>` with
  fixed, validated arguments. NEMESIS root is reachable by any UID; never add a verb that takes
  free-form shell or paths.
- **No key material in git.** Only `keys/ota-manifest-public.pem` is committed. Release secrets live
  in the GitHub environment `release` (tags `v*`); owners keep a local backup.
- **Licensing:** `modules/wireless-carplay-and-aa` contains AGPL-3.0 code adapted from HMG-API
  (see `NOTICE.md`). Keep original headers, mark modified files. The repository LICENSE is **not
  decided yet**; distribution must be AGPL-3.0-compatible. Everything else is our own code: do not
  copy upstream code elsewhere without an owner decision.
- Commit messages: plain, no AI attribution / `Co-Authored-By` trailers.
- **Never translate or transliterate proper nouns** in UI strings or docs, in any locale: keep
  `HMG-Seogaemo`, `NEMESIS`, `CarPlay`, `Android Auto`, `Wi-Fi`, `ADB`, `USB`, `GitHub`, `HMG-API`,
  `Hyundai`/`Kia`/`Genesis`, `Gen5W`, model/firmware ids as written (e.g. KO: "무선 CarPlay",
  not "무선 카플레이"; "HMG-Seogaemo 버전", not "새개모 버전"). Translate only the surrounding words.

## Layout

| Path | What |
|---|---|
| `app/` | APK `com.seogaemo.hmg`: `ui/` (Home `HomeView` pager, `ui/settings` page components), `module/` (registry, `ModuleManager`, `RootClient`, contract types), `core/update/` (OTA) |
| `modules/<id>/` | one module = one dir: `module.properties`, `src/` (`com.seogaemo.hmg.modules.*`), `res/` (`<snake_id>_*` strings), `root/<id>.sh`, optional `payload/`, `build/build.sh`, `test/run.sh` |
| `nemesis/package/` | NEMESIS package `hmg-seogaemo`: `NEMESIS.data`, `bin/` (`hmg-seogaemo-root`, `open.sh`, `install-apk.sh`), `lib/common.sh`, `core/update.sh`, `scripts/` (stable hook wrappers), `svc/reconcile.sh` |
| `nemesis/usb/` | USB wrapper descriptor (`packages/hmg-seogaemo.zip`) |
| `tools/` | module generator, packager, root-script lint, tests, `testlib/fakebin` (toolbox shims) |
| `keys/` | OTA manifest public key only |
| `.agents/` | design, contracts, research, per-module notes |

The module list/graph is generated at build time from `modules/*/module.properties`; there is no
hand-edited list. Current modules: `wifi-client` (excludes `wireless-carplay-and-aa`),
`wireless-adb` (independent; offers "Connect to another Wi-Fi"), `wireless-carplay-and-aa`.
Device state lives under `/data/nemesis/backup/hmg-seogaemo/` (desired state, baselines, ledger).

## Build, test, release

- `make` builds `out/seogaemo.apk`; `make test` runs generator, package, runtime, update, lint and
  every `modules/*/test/run.sh` (tests put `tools/testlib/fakebin` first on PATH and use `mksh`
  when installed); `make package` writes `out/dist/` (`hmg-seogaemo-<v>-nemesis-package.zip`,
  `-nemesis-usb.zip`, `-ota.zip`, `.apk`, `SHA256SUMS`, plus signed manifest when
  `OTA_MANIFEST_PRIVATE_KEY` is set); `make verify-dist`; `make clean`.
- Local builds are signed with a throwaway debug key. **Install only release-signed builds on a
  car you want to update OTA later**: a signer change forces uninstall and loses app data.
- Requirements: JDK 17, Android SDK (`platforms;android-19`, `build-tools;34.0.0`), python3,
  clang + lld (carplay native payload), optional `mksh`.
- CI (`ci.yml`) uses the runner's preinstalled SDK; `android-actions/setup-android@v3` breaks on the
  removed `tools` package.
- Release: bump `VERSION`, commit, push tag `vX.Y.Z` (must equal `VERSION`). `release.yml` builds
  with the release keystore, checks the APK certificate SHA-256, signs the manifest, uploads to a
  draft release, verifies the asset list, then publishes.
- UI screenshots: AVD `hmg_1920x720` (1920x720, 240 dpi, API 28 image; no NEMESIS, so module
  status shows unavailable). README screenshots are the Korean UI.

## On-car operations

- First install: release `hmg-seogaemo-<v>-nemesis-usb.zip` unzipped to an empty FAT32/exFAT stick.
- Full root over adb: `adb shell cp /system/xbin/nemesis /data/local/tmp/nemesis-d` then
  `adb shell /data/local/tmp/nemesis-d root -c '<cmd>'` (plain `nemesis root -c` from adb is a weak
  root). Export the change ledger:
  `adb shell /data/local/tmp/nemesis-d root -c '/data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root export adb'`
  then `adb pull /data/local/tmp/hmg-seogaemo-export/`. The app's Logs page exports to USB.
- Test modules one at a time, lowest risk first (`wireless-adb` → `wifi-client` →
  `wireless-carplay-and-aa`), keep adb reachable, export the ledger before and after, and verify
  revert restores the exact originals before moving on.

## Known gaps / risks (not yet verified on a car)

- Nothing has run on a real head unit yet; all tests use fakes and an emulator.
- `wireless-carplay-and-aa` replaces `/system/bin/app_process` (boot guard disables it after 3
  quick restarts). Dalvik offsets were proven on a US EV6 only. Keep an adb recovery path ready.
- `wifi-client` STA vs the CarPlay AP on one radio: treated as mutually exclusive; concurrency is
  unknown. `persist.wifi.state=3` is deliberately not set yet (to be tested later).
- OTA: TLS 1.2 to GitHub on 4.4 with bundled roots (`update_roots.pem`), the reboot/resume flow
  through `wifi-client`, and rollback are untested on a car.
- `wifi-client` and `wireless-carplay-and-aa` both own `/system/onebin/neev_kr/featurexml.cfg`.
  This is safe only because they exclude each other (one is fully reverted before the other
  applies). Never remove that exclusion without moving to line-level ownership first.
- An official firmware OTA while modules are applied makes restores conflict by design.
- Removing only the APK (not via NEMESIS) skips the uninstall hook; reconcile must clean up.

## Working conventions

- Parallel work: one Paseo/git worktree per area; ownership is in the contract's ownership table.
  Shared files (`MainActivity`, `hmg-seogaemo-root`, `lib/common.sh`, `Makefile`,
  `tools/test-*.py`) belong to the runtime owner; others list needed hunks instead of editing.
- Merge feature branches into `main` with `--no-ff`; keep worktrees after merging.
- Interface changes go into `.agents/module-runtime-contract.md` first, then code.
