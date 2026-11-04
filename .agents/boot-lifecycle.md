Status: current — implemented off-car, 2026-11-04.

# Boot lifecycle

NEMESIS upstream `Sources/native/svc.c:136-207` forks shell services, creates
a session and disconnects inherited descriptors. Only `once` waits for exit.
The startup loop at lines 284-292 skips `once`, and the supervisor loop at
303-335 skips completed `background` services. Thus reconcile uses `background`:
one asynchronous start per service-manager startup, unless already alive, with
no automatic restart after completion. Package installation still starts every
registered service (`pkg.c:595-604`, called at 713-714), preserving desired-on
reapply after USB install/update. `util.c:709-715` defines framework readiness
only by `sys.boot_completed`; reconcile additionally checks `pm path android`.
References were read from the shared checkout's gitignored `tmp/hmg_nemesis`,
not copied into this repository.

Normal readiness retries use 2, 4, 8, 16, then 30 seconds, capped at 30 with no
permanent timeout. OTA health permits six waits and fails if still unavailable.
Existing map verification retries stay detached and reread desired state, stock
pause, app presence and payload selection before applying deferred modules. Each
retry pass costs two JVMs, so waits back off 30, 60, 120, then 300 seconds and stop
after eight passes per boot (logged as exhausted); a later boot starts fresh.

## QuickBoot evidence boundary

The private `tmp/car-sessions/2026-11-04-devdump/README.txt` identifies a single
read-only NEEV KOR snapshot. `runtime/root/rootfs_init.tcc803x.rc.txt:224-230`
sets `tcc.hibernate.property=reloaded` and `tcc.mobis.AMS.state=late` on
QuickBoot. The captured `runtime/cmd/getprop.txt` has `reloaded` and `normal`,
and `sys.micom_ready=true`. These are states, not monotonic session identifiers;
a poller may miss transitions. `rootfs_init.daudio.rc.txt:99-101` starts
`log_summary` at QuickBoot but supplies no session counter.

`analysis/BINDER-SERVICES.md:2354-2388` maps `com.hkmc.micom.power` to
`IMicomPowerService`: transaction 1 is the read-only `getPowerStatus()`, with
listener registration at 12/13. The snapshot supplies neither value semantics
nor a trace proving an ignition cycle changes the getter while Android remains
awake. No raw vendor power transaction or guessed property trigger was added.
Same-kernel QuickBoot that preserves display-on state remains an explicit
limitation for consent dismissal. Verify ignition off/on traces before choosing
a new signal; existing declared property-drift handling is unaffected.

## Deferred APK request

USB postinstall retains the APK and its fixed helper under private
`app-install/payload/` and publishes a pending request. Reconcile completes it
before missing-APK cleanup. The worker shares the operation lock with module
and restore operations, honors uninstall pending but not stock pause (an explicit USB request), and reserves at most four
attempts per boot with 5/10/20-second delays. Failure retains the request and
APK for the next boot. The synchronous Android-up hook spends one attempt;
the service launch can use the remaining budget. A seen-installed receipt
prevents a pending update from resurrecting an intentionally removed app;
absence without a request always follows the existing cleanup path.

`install-apk.sh` forces `sys.boot_completed=0` around `pm install`, which now
also happens at boot. It runs pm in the background under a 120 second watchdog
that TERMs pm and its `/proc` descendants, waits a 5 second grace period and then
sends KILL. The property is restored on every exit path (normal, failure,
watchdog, TERM/HUP/INT), keeping the original exit status. SIGKILL of the
installer cannot run a trap, so the watchdog also polls the installer pid: when
it is gone the watchdog stops pm, restores the saved property value and removes
the staging. If the whole process group was killed, the next run first restores
the property from the saved value (`old` file of a dead `install-apk.<pid>`
directory) before reading it. The helper files (pm output, timeout marker, saved
value, watchdog pid) live in the private 0700 `$SG_DATA_ROOT/install-apk.<pid>`
directory; only the staged APK, named `hmg-seogaemo-install.<pid>.<epoch>.<random>.apk`,
is in `/data/local/tmp` because PackageManager must read it.
Export diagnostics read logs with 4 KiB `dd` blocks from regular files, write
probe output to files rather than pipes, and have the 10 second probe guard stop
the probe's descendants, so a lingering grandchild cannot stall an export. Raw
(unredacted) bytes are staged only in the private 0700
`$SG_DATA_ROOT/diagnostics-tmp.<pid>` directory, never in the export folder; it is
removed on EXIT/HUP/INT/TERM. Only redacted, capped files reach the export. A
SIGKILLed export leaves its staging directory behind; the next export sweeps
exact `diagnostics-tmp.<digits>` directories of dead pids.
Block `skip=`/`count=` use POSIX block units; toolbox `dd` semantics are
unverified on the car, so check one truncated log in an on-car export.

## Remaining car checks

Verify USB installation with Android down, delayed PackageManager readiness,
failed installation followed by a cold boot retry, only one installer during
overlapping service launches, and no reinstall after APK-only removal. Check
the boot property is restored after both successful and failed PM calls.

Confirm cold boot launches reconcile without a Manager action, delayed Android
startup eventually reapplies desired modules, install/update still reapplies,
map-dependent modules wait for verification, interrupted OTA recovery runs, and
APK-only removal restores baselines. Capture service logs and ledger before and
after, and verify no automatic calls after a paused restore.
Delay `com.hkmc.factory` registration and confirm failed boot calls remain
retryable, a successful call retires the boot event, every actual Binder attempt
appears in the ledger, and failed wake calls retry while the display stays on.
