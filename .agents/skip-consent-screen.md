Status: implemented off-car, 2026-11-03. Owner reports `local.hkmc.factory82`
worked on their NEEV KOR 260902 car; HMG-Seogaemo's implementation still needs a car test.

# Startup consent screen investigation

The module skips the driver-safety notice at startup. The driver remains responsible for safe operation. The owner approved the `restore=reboot` class: disabling stops future calls, and a full head-unit restart is required to restore the stock notice. This module makes no persistent stock file or property changes.

## Reference package

NEMESIS Manager's source defines the read-only Store index as `https://portal.xwtk.cloud/nemesis/v1/store/catalog` and package descriptors as `/store/packages/<id>` (`tmp/hmg_nemesis/Sources/manager/src/com/nemesis/manager/Portal.java`). On 2026-11-03 the Store host did not resolve from this workspace and the web reader could not open it. No Store descriptor or ZIP could be obtained or inspected, so its listing, current artifact, and ZIP contents are unverified.

The public [HKMC Factory 82 repository](https://github.com/armandodollia/nemesis-package-builds) identifies the likely package as `local.hkmc.factory82`, author `armandodollia`, release `4.2.1`, MIT license. The [release notes](https://github.com/armandodollia/nemesis-package-builds/releases/tag/v4.2.1) say 4.2.1 changes metadata/build only; the service remains v4.2. The source descriptor previously exposed `types=application,service`, a diagnostic shell app, a restarting `apply` service, and a preuninstall script. The exact 4.2.1 descriptor and preuninstall script were not accessible.

The readable [service source](https://raw.githubusercontent.com/armandodollia/nemesis-package-builds/main/packages/hkmc-factory82/svc/watch.sh) polls for startup, screen wake, QuickBoot marker loss, and a long resume gap. It waits for factory Binder registration and boot readiness, then issues `service call com.hkmc.factory 82`, with one limited retry for a boot-incomplete exception. It records diagnostics under `/data/nemesis/state/local.hkmc.factory82/` and a session marker at `/data/local/tmp/hkmc.factory82.session`. The diagnostic app can export data to USB. The readable script has no property change, feature XML edit, settings DB key, stock APK patch, or stock-file replacement. Because the ZIP was unavailable, this is a statement about the readable source, not a complete audit of the release artifact.

The [package README](https://github.com/armandodollia/nemesis-package-builds) says only an earlier v4 build was observed to dismiss the screen on a 2022 Kia EV6. Version 4.2.1 was simulated but not vehicle-tested. It explicitly says Binder transaction 82 is undocumented, a zero Parcel is not proof of dismissal, and uninstall stops future invocations but does not establish reversal of any persistent effect.

## NEEV KOR evidence and design boundary

The owner's private Car Report resolves `framework.factory_service.hide_consent_screen` to `IMicomFactoryService.hideConsentScreen()` on `NEEV.KOR.0000.V039.001.260902`. The same point is already in `core/hookpoints/framework.json` and `maps/hyundai/ioniq5/kor/260902/map.json`; the bundled map hashes `/system/framework/framework.odex` as `ffe86fa1540c8dea3b5499b1191bb727`. The owner installed `local.hkmc.factory82` on this car and observed that it skipped the screen. This establishes the behavior of the reference package, not of our new module. HMG-API's `docs/vehicle-commands.md` records no known inverse.

`modules/skip-consent-screen/` declares the required core point, `service_calls=com.hkmc.factory:82`, `watch=boot,screen_on`, and `restore=reboot`. Apply checks API 19, `NEEV.KOR.*.260902`, the exact framework.odex MD5, and absence of `local.hkmc.factory82`; it makes no Binder call until the confirmed enable has committed desired state. The one package reconcile service then invokes the module on boot and display off-to-on transitions, while enabled. The module waits for boot completion and factory Binder registration and issues transaction 82 through `sg_service_call`. It retries once only for the known boot-incomplete exception. The API validates the exact declared pair, bounds the call to 15 seconds, and records each response and outcome in the ledger/export.

Revert changes desired state to off. If a call happened under the current kernel boot ID, status remains `pending-restart head_unit` with `info.desired=off`; the UI keeps the toggle off and explains the restart requirement. After a new kernel boot ID, status is off. The call's full effects remain undocumented, so this restore class claims stock behavior after restart, not a proven inverse transaction.

## On-car verification

1. Export the current NEMESIS ledger and record the exact product/framework hash. Uninstall `local.hkmc.factory82`, reboot, and check that the stock notice appears.
2. Install a release-signed HMG-Seogaemo build. Confirm the new module is off. Enable it via `ConfirmDialog`, then reboot. Verify the startup notice is skipped and inspect the `service_call` ledger row.
3. Turn the screen off and on twice. Verify one call per observed wake and that the notice remains skipped. Check service CPU/log growth over a parked session.
4. Disable the module. Verify the toggle is off with a pending head-unit restart, no further calls occur on screen wake, and the stock notice returns after reboot.
5. Re-enable, reboot, and uninstall HMG-Seogaemo through NEMESIS. Reboot and verify the original notice returns. Test a changed framework hash and installed `local.hkmc.factory82` as fail-closed gates. Export and compare ledgers before and after.
