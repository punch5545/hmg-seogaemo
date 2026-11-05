Source: stock-restore.md. Historical: 2026-11-04 car-session module-switch incident fixes and owner recovery steps; the fixed behaviors now live in stock-restore.md and the runtime contract.

## Module-switch incident recovery (2026-11-04 car session)

- Reported-state fixes: Wi-Fi feature removal now reports pending disable explicitly on status/revert (previously
  pending-restart without `info.desired=off`); the dispatcher accepts that bounded receipt for exact restores as well as
  `restore=reboot`; EN/KO restart text distinguishes direction. CarPlay disk revert reports desired off even while a restart
  is still needed; the dispatcher persists that receipt before the next plan step, the app continues successful
  pending-restart steps and shows one combined result with the strongest restart requirement. Saved intent participates
  in exclusion planning even when the live module is off. Structured refusals expose the reason.
- A missing module build baseline returns false without logging or creating state; a recorded different product still logs
  conflict and blocks restore. A snapshot-free module revert is idempotent, including stale desired-on after an earlier
  completed restore; remaining restore material keeps firmware, content and metadata guards. Failed restore diagnostics name
  stage/target in the root receipt and app. Regression: shipped CarPlay script with no build/baselines, saved desired on,
  dispatch paused → stock restore succeeds; the Java planner/dispatcher test covers an explicit Wi-Fi switch from that
  paused stale-intent state and verifies dispatch resumes.
- ADB export chmodded the timestamp subtree but left its parent 0700; ADB now opens the export root for shell traversal
  after copying (USB unchanged). Current manifests/ledger sources hold feature XML, properties, executables, hook payloads,
  call receipts and map metadata, not Wi-Fi credentials; no redaction needed, exports stay an intentional diagnostic copy.
- The ASCII semicolon issue came from `sg_clean`'s whitelist omitting `;`; all module root text was audited as ASCII and the
  character is preserved at source.
- Owner recovery: install the release-signed fixed NEMESIS USB ZIP (wrapper `install --update` path; see
  `research-nemesis-packaging.md`); in the app confirm Restore stock state, restart if requested, then confirm Wi-Fi client
  enable and restart if requested; connect via stock Wi-Fi settings and reopen the app. Direct Wi-Fi enable also reverts stale
  CarPlay intent and clears the failed-restore pause. A previously accepted update check continues after the new boot,
  Wi-Fi readiness and usable network (app must be opened); otherwise confirm a new update check. Genuine drift still blocks
  restore: export the retained ledger instead of forcing old bytes.
- Still to validate on the car: physical reboot, stock Wi-Fi menu visibility, TLS/network, OTA continuation. Static
  Broadcom capability checks show stock software support, not physical radio health.

