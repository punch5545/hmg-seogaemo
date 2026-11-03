Status: partly superseded — visual measurements remain useful; the old mock controls and emulator capture recipe are historical.

# Gen5W UI guidelines

Target one screen: 1920×720 px at 240 dpi (1280×480 dp), landscape. Use framework Java views and API 19 methods. `dp()` applies display density only; do not apply HMG-API's extra `K = 0.66` scale.

## Dark design tokens

| DESIGN.md token | Car UI mapping |
| --- | --- |
| `colors.ink` / `colors.primary` `#171717` | Main dark surface `#171717` over near-black canvas `#111111` |
| `colors.on-primary` `#ffffff` | Light primary text `#f5f5f5` |
| `colors.body` / `colors.mute` | Secondary text `#9a9a9a` |
| `colors.hairline` | Dark 1dp divider and border `#343434` |
| `colors.link` `#0070f3` | Selected sidebar, enabled toggle, primary action |
| `colors.error` `#ee0000` | Destructive Restart head unit tile only |
| `rounded.sm` / `rounded.md` | 6dp buttons and 8dp tiles |
| `typography.display-*` / `caption-mono` | Medium sans headings and localized labels at natural width; mono only for technical identifiers and numbers |

Use only Android `sans-serif`, `sans-serif-medium`, and `monospace`; do not bundle fonts. Avoid `TextView.setLetterSpacing()` because it is newer than API 19. Hairline borders supply depth without a heavy shadow. Colors and controls are visually scaled for a dark car display, not copied at web sizes.

## NEMESIS measurements

Paths below are relative to repository root. Dimensions are dp unless marked sp.

| Element | NEMESIS reference | Current UI/design reference |
| --- | --- | --- |
| Header | 60dp; `tmp/hmg_nemesis/Sources/manager/res/layout/title_bar.xml:5` | 60dp |
| Header button | 72×60dp; `tmp/hmg_nemesis/Sources/manager/res/layout/title_bar.xml:47` | 132×60dp Home back button / 72×60dp close |
| Header text | 28sp; `tmp/hmg_nemesis/Sources/manager/res/values/styles.xml:18` | 28sp |
| List row | min 70dp; `tmp/hmg_nemesis/Sources/manager/res/layout/list_row.xml:5`; 26sp/22sp at `:15`, `:21` | 70dp sidebar rows, 75dp content rows; 23–24sp titles |
| Tile grid | 4 columns × 2 rows; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:35-36` | Six portrait cards per Home page |
| Tile | 204×166dp; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:244-286` | About 174dp wide portrait Home card at target size |
| Tile icon / label | 66dp icon, 25sp label; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:264-280` | 66dp monogram, 21–24sp labels to fit long action names |
| Bottom bar / buttons | 60dp bar and buttons, 21sp label; `tmp/hmg_nemesis/Sources/manager/res/layout/activity_list.xml:40-61` | 60dp buttons, 21sp labels |
| Density conversion | plain `TypedValue.applyDimension()`; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/Ui.java:94-97` | same, no extra scale |

Home demo cards have visual press states and no vehicle command; only Settings navigates. Settings category taps change the visible pane; module toggles execute through `ModuleManager` and the root dispatcher. Logs exports the change ledger to USB. General → Language persists: it stores `system`, `en`, or `ko` in SharedPreferences, updates `Configuration.locale` before rebuilding views, and recreates the Activity. Default strings are in `app/res/values/strings.xml`; Korean strings are in `app/res/values-ko/strings.xml`. The header and launcher draw the source logo proportions from `tmp/brand/logo-source.png`: two `#101016` squares on a white square, with no VectorDrawable.

Settings sidebar sections use short, non-focusable sans-serif labels (App / 앱 and Module settings / 모듈 설정). A stronger full-width hairline and 24dp top gap distinguish module pages from core categories; clickable rows remain 70dp and retain the blue selected marker. The sidebar remains scrollable.

## API 19 compatibility

Build with the installed `platforms/android-19/android.jar`: `aapt` links resources against it, `javac` uses `-source 1.7 -target 1.7 -bootclasspath`, and `d8` uses `--min-api 19 --lib` (`tmp/hmg_nemesis/Sources/manager/Makefile:24-36`). Keep Java compilation and dex generation in one recipe to avoid a stale dex (`Makefile`). The final APK is SHA1withRSA/SHA1 **v1 JAR signed**, then zipaligned, using the same JDK algorithm override (`app/allow-sha1.security:1-2`). Check with `apksigner verify --verbose --min-sdk-version 19 out/seogaemo.apk`.

The manifest uses `minSdkVersion=19`, `targetSdkVersion=19`, `GET_TASKS`, `allowBackup=false`, `singleTask`, and a style inheriting `Theme.NoTitleBar.Fullscreen` (`tmp/hmg_nemesis/Sources/manager/AndroidManifest.xml:6-23`; `res/values/styles.xml:3-6`). The close button catches `SecurityException` from `finish()` and falls back to `moveTaskToBack(true)`, matching NEMESIS's `Ui.java:23-35`. Do not add AndroidX, Compose, `VectorDrawable`, ripple, `setLetterSpacing`, or framework APIs above 19. Keep locale selection on `Configuration.locale` plus `Resources.updateConfiguration()` before rebuilding views.

Our package name, launcher category, and fixed landscape orientation serve the standalone app; NEMESIS service and package hooks live in `nemesis/package/`. The app has network permission for GitHub Releases updates. Our dark background uses `#111111` within NEMESIS's fullscreen theme parent. `<meta-data android:name="android.max_aspect" android:value="2.7" />` avoids Android 28 emulator letterboxing at 1920×720; Android 4.4 ignores this later-platform compatibility metadata. Android 19 resource linking and Java compilation passed, and the APK installed on Android 28. A physical Gen5W and an Android 19 runtime remain untested.

## Historical Android 28 emulator capture recipe (screen coordinates may no longer match)

```sh
make
mkdir -p /private/tmp/hmg_avd
printf 'no\n' | ANDROID_AVD_HOME=/private/tmp/hmg_avd ~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd -n hmg_1920x720 -k 'system-images;android-28;google_apis;arm64-v8a' -p /private/tmp/hmg_avd/hmg_1920x720.avd
# Set hw.lcd.width=1920, hw.lcd.height=720, hw.lcd.density=240,
# and hw.initialOrientation=landscape in config.ini.
ANDROID_AVD_HOME=/private/tmp/hmg_avd ~/Library/Android/sdk/emulator/emulator -avd hmg_1920x720 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -ports 5580,5581
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 install -r out/seogaemo.apk
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell am start -n com.seogaemo.hmg/.MainActivity
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > tmp/screens/home-en.png
# Historical taps; inspect the current Settings layout before reusing these coordinates.
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1440 565
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1080 550
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/settings-general.png
# Tap the header Home button (1820,45), then capture Korean Home.
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1820 45
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/home.png
# Historical Wireless-category taps; the current sidebar uses General, Modules, Logs, About, then module pages.
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1440 565
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 160 330
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/settings-wireless.png
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 155 440
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/settings-logs.png
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 emu kill
```

The PNGs come from the Android 28 arm64 emulator at the requested pixel size/density. No API 19 runtime image or physical car was available for runtime verification.

## Home pager (2026-11-03)

Home uses six portrait tiles per page in a horizontal `HorizontalScrollView` pager, with swipe, arrow buttons, and page dots when there is more than one page. The built-in Settings tile appears first. Runtime tiles come from `Module.homeTiles()` while their module is enabled, or when marked `alwaysVisible`; the current modules supply none. At 1920×720, the pager viewport is about 1104dp wide and each card is about 174dp wide. Page widths are synchronized after the view is laid out so Home recreation after a language change keeps the correct width. The Korean `assets/home.png` screenshot was captured on the Android 28 AVD.

## Polish audit (2026-11-03)

Baseline captures: `tmp/ui-audit/before/` at 1920×720, Korean locale.

| Screen | Observed issue |
| --- | --- |
| Home | Header and tile labels use horizontal text compression, which also affects Korean text. |
| General (top and scrolled) | Language choices and update actions are large enough to touch, but content insets repeat ad-hoc 30/28dp pairs. |
| Module management | Unavailable pills are only 96dp wide, use monospace for Korean, and have cramped text. The status line exposes `failed · IOException`. |
| Logs | Export action has sufficient height, but uses the same ad-hoc 30/28dp margins. |
| About | Values are monospace even for `HMG-Seogaemo`; labels are horizontally compressed. Row insets differ in implementation from other pages. |
| Wi-Fi client | Same pill and exception issues. Card and action text use inconsistent inner insets. |
| Wireless ADB (top and scrolled) | Same pill and exception issues. Long guidance wraps inside a fixed 60dp row with too little vertical padding. |
| Wireless CarPlay and Android Auto | Same pill and exception issues. Long card description needs a stable gap before the status pill. |

Use an 8/12/16/24/32dp spacing scale for settings controls, a fixed state-pill width sized for both locales, readable sans-serif Korean, a short localized unavailable state, consistent 8dp cards and 6dp controls, and at least 48dp touch targets. Keep diagnostic detail out of the visible status line.

Result: `SettingsUi` now supplies shared row, pill, button, and action geometry. Status pills are 144×54dp with 16dp inner horizontal padding and a 16dp gap after the card copy. Status lines use localized human messages; unknown status details go to Android logcat under `HMG-Seogaemo`. Wrapped module notes grow with content. Text compression was removed from Home and Settings labels. Final Korean captures are in `tmp/ui-audit/after/`; the seven README images in `assets/` were taken from the same final build and emulator session. An English General and Modules pass confirmed the fixed pill width fits `Unavailable`. Physical-car behavior remains unverified.

## Confirm before action (2026-11-03)

Use `com.seogaemo.hmg.ui.ConfirmDialog.show(...)` before a tap can change app preferences, module or head-unit state, export files, check/download/install an update, roll back, or start other long work. Module toggles and `ENABLE_MODULE` actions use `ModuleManager.plan` and read-only `precheck` first; list every planned enable/disable step and active warning in the dialog. `OPEN_STOCK_WIFI_SETTINGS` and navigation are direct because they do not change Seogaemo state. A previously confirmed OTA resume or restoration may continue after an Activity restart.

The helper uses the dark surface and hairline tokens, 8/12/16/24/32dp spacing, 60dp-high buttons, and a dimmed background. Cancel is the left, initially focused button. Outside taps cannot dismiss it; Back or Cancel can. Use the error color for destructive actions. A submitted confirm button becomes disabled immediately. Call `showProgress()` for long work and dismiss the dialog when it finishes. Keep EN/KO text in resources and proper nouns unchanged.
