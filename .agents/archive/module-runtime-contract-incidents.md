Historical: root causes and incident notes removed from `module-runtime-contract.md`. The present rules live there; do not implement from this file.

# Module runtime contract: past incidents

## `remount ro` EBUSY (Ioniq 5, 2026-11-04)

Replacing the running `app_process` or removing a loaded JAR leaves the old file open, so `remount ro` fails with EBUSY until the next restart. The change was then failed, which left the wrapper written but not ledgered; the rollback failed the same way and blocked `revert`, `stock restore` and the next `apply` (`orphaned app_process backup`). Fix: `sg_remount_ro` treats this as a warning.

## 0700 recipe directory (Ioniq 5, 2026-11-04)

Module scripts run with umask 077, which left `/system/etc/hmg-seogaemo/<module>/recipes` at 0700. The hook runs as the system user inside DiPO, got EACCES on every recipe, and the native audio patch (AAC/Opus over Wi-Fi) was never applied, so the phone did not offer CarPlay as an audio output. Fix: `sg_mkdir_target` creates 0755 directories; CarPlay apply repairs ancestors of already-applied assets.

## noglob in a module shell (v0.1.25)

`wireless-adb.sh` used `set -efu`, so `sg_baseline_clear` (`rm -rf "$STATE"/prop.* ...`) matched nothing: the module reported `state=off` but its restore material stayed, the restore failed in the fresh-shell re-check, and dispatch stayed paused. Fix: lint rejects noglob in module scripts and every glob-dependent runtime function enables globbing itself.

## Boot-bound zygote hook gate

A gate bound to the boot ID left the Java hooks off on every cold boot: the zygote starts about two seconds after boot, while that boot's map verification finishes one to three minutes later (the native library still loaded, so `status` read "hooks loaded"). Fix: the gate checks only product and stock hash ID (first two seal lines).

## Other fixed items

- Interrupted atomic writes (`<name>.hmg-seogaemo.<digits>`) used to keep a created directory non-empty and fail its `rmdir` (rc 3) forever; restore now sweeps them.
- Builds before 0.1.23 created project namespace directories unjournaled; `sg_legacy_dirs_cleanup` removes the empty ones at whole-package restore.
- `sg_clean` once dropped ASCII semicolons from status detail text.
- USB sticks mount write-back; a stick pulled right after "success" held 0-byte files. Fix: `sg_export_seal`.
- Path names were changed before any build was deployed, so no migration from earlier names exists.
