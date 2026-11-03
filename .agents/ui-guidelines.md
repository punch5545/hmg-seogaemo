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
| `typography.display-*` / `caption-mono` | Medium sans headings with slightly condensed `textScaleX`; mono for system labels and values |

Use only Android `sans-serif`, `sans-serif-medium`, and `monospace`; do not bundle fonts. Avoid `TextView.setLetterSpacing()` because it is newer than API 19. Hairline borders supply depth without a heavy shadow. Colors and controls are visually scaled for a dark car display, not copied at web sizes.

## NEMESIS measurements

Paths below are relative to repository root. Dimensions are dp unless marked sp.

| Element | NEMESIS reference | This mock |
| --- | --- | --- |
| Header | 60dp; `tmp/hmg_nemesis/Sources/manager/res/layout/title_bar.xml:5` | 60dp |
| Header button | 72×60dp; `tmp/hmg_nemesis/Sources/manager/res/layout/title_bar.xml:47` | 132×60dp Home back button / 72×60dp close |
| Header text | 28sp; `tmp/hmg_nemesis/Sources/manager/res/values/styles.xml:18` | 28sp |
| List row | min 70dp; `tmp/hmg_nemesis/Sources/manager/res/layout/list_row.xml:5`; 26sp/22sp at `:15`, `:21` | 70dp sidebar rows, 75dp content rows; 23–24sp titles |
| Tile grid | 4 columns × 2 rows; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:35-36` | 4×2 |
| Tile | 204×166dp; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:244-286` | 204×166dp |
| Tile icon / label | 66dp icon, 25sp label; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/MainActivity.java:264-280` | 66dp monogram, 21–24sp labels to fit long action names |
| Bottom bar / buttons | 60dp bar and buttons, 21sp label; `tmp/hmg_nemesis/Sources/manager/res/layout/activity_list.xml:40-61` | 60dp buttons, 21sp labels |
| Density conversion | plain `TypedValue.applyDimension()`; `tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/Ui.java:94-97` | same, no extra scale |

Home action buttons have visual press states and no vehicle command. Settings category taps change the visible pane; toggles change UI state in memory only. Apply, Cancel, and Export logs are visual controls only. General → Language is the sole persisted setting: it stores `system`, `en`, or `ko` in SharedPreferences, updates `Configuration.locale` before rebuilding views, and recreates the Activity. Default strings are in `app/res/values/strings.xml`; Korean strings are in `app/res/values-ko/strings.xml`. The header and launcher draw the source logo proportions from `tmp/brand/logo-source.png`: two `#101016` squares on a white square, with no VectorDrawable.

## API 19 compatibility

Build with the installed `platforms/android-19/android.jar`: `aapt` links resources against it, `javac` uses `-source 1.7 -target 1.7 -bootclasspath`, and `d8` uses `--min-api 19 --lib` (`tmp/hmg_nemesis/Sources/manager/Makefile:24-36`). Keep Java compilation and dex generation in one recipe to avoid a stale dex (`Makefile`). The final APK is SHA1withRSA/SHA1 **v1 JAR signed**, then zipaligned, using the same JDK algorithm override (`app/allow-sha1.security:1-2`). Check with `apksigner verify --verbose --min-sdk-version 19 out/seogaemo.apk`.

The manifest uses `minSdkVersion=19`, `targetSdkVersion=19`, `GET_TASKS`, `allowBackup=false`, `singleTask`, and a style inheriting `Theme.NoTitleBar.Fullscreen` (`tmp/hmg_nemesis/Sources/manager/AndroidManifest.xml:6-23`; `res/values/styles.xml:3-6`). The close button catches `SecurityException` from `finish()` and falls back to `moveTaskToBack(true)`, matching NEMESIS's `Ui.java:23-35`. Do not add AndroidX, Compose, `VectorDrawable`, ripple, `setLetterSpacing`, or framework APIs above 19. Keep locale selection on `Configuration.locale` plus `Resources.updateConfiguration()` before rebuilding views.

Intentional differences: our package name, launcher category, and fixed landscape orientation serve this standalone mock; NEMESIS's network, storage, boot, receiver, and service declarations are omitted because no corresponding behavior exists here. Our dark background uses `#111111` within NEMESIS's fullscreen theme parent. `<meta-data android:name="android.max_aspect" android:value="2.7" />` avoids Android 28 emulator letterboxing at 1920×720; Android 4.4 ignores this later-platform compatibility metadata. Android 19 resource linking and Java compilation passed, and the APK installed on Android 28. A physical Gen5W and an Android 19 runtime remain untested.

## Build and capture

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
# Tap Settings (1440,565), then 한국어 in General (1080,550).
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1440 565
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1080 550
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/settings-general.png
# Tap the header Home button (1820,45), then capture Korean Home.
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1820 45
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/home.png
# Tap Settings, Wireless (160,330), then Logs (155,440).
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 1440 565
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 160 330
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/settings-wireless.png
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 shell input tap 155 440
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 exec-out screencap -p > assets/settings-logs.png
~/Library/Android/sdk/platform-tools/adb -s emulator-5580 emu kill
```

The PNGs come from the Android 28 arm64 emulator at the requested pixel size/density. No API 19 runtime image or physical car was available for runtime verification.

## Portrait Home mock (2026-11-03)

The new Home uses six portrait tiles per page in a horizontal `HorizontalScrollView` pager, with swipe, arrow buttons, and page dots. The visual demo list lives in `HomeView`; only Settings navigates. `HomeTile` and the empty `Module.homeTiles()` implementations establish the module metadata seam without displaying disabled modules as live actions. At 1920×720, the pager viewport is about 1104dp wide and each card is about 174dp wide. Page widths must be synchronized after the view is laid out: `onSizeChanged()` can run before pages are appended, leaving one-pixel or screen-width pages when Home is recreated after the language change. The Korean screenshots in `assets/home.png`, `assets/home-page2.png`, and `assets/home-pressed.png` were captured on the Android 28 AVD; the last holds Restart early in the touch, before Android clears long-press feedback.
