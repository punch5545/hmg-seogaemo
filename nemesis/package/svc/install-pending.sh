#!/system/bin/sh
# Completes only a request published by USB postinstall. Never installs on absence alone.
umask 077
state=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
job=$state/app-install
[ -f "$job/pending" ] || exit 0
boot_id=$(cat "${SG_BOOT_ID_FILE:-/proc/sys/kernel/random/boot_id}" 2>/dev/null)
case "$boot_id" in ''|*[!a-zA-Z0-9-]*) exit 1;; esac
worker=$job/worker
if ! mkdir "$worker" 2>/dev/null; then
    # Allow a concurrent launcher to publish its claim.
    n=0
    while [ ! -s "$worker/pid" ] && [ "$n" -lt 5 ]; do n=$((n + 1)); sleep 1; done
    holder=; holder_boot=
    { read -r holder holder_boot < "$worker/pid"; } 2>/dev/null || :
    case "$holder" in ''|*[!0-9]*) ;; *)
        if [ "$holder_boot" = "$boot_id" ] && kill -0 "$holder" 2>/dev/null &&
                grep -q install-pending.sh "/proc/$holder/cmdline" 2>/dev/null; then exit 0; fi;;
    esac
    rm -rf "$worker"
    mkdir "$worker" 2>/dev/null || exit 0
fi
echo "$$ $boot_id" > "$worker/pid" || exit 1
operation=
cleanup() {
    if [ -n "$operation" ] && [ "$(cat "$operation/pid" 2>/dev/null)" = "$$" ]; then rm -rf "$operation"; fi
    [ "$(cat "$worker/pid" 2>/dev/null)" != "$$ $boot_id" ] || rm -rf "$worker"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
request=$(cat "$job/pending") || exit 1
# A request exists only because the owner explicitly installed from USB, so a
# paused stock restore (which pauses automatic module calls) does not cancel it.
# Only a queued uninstall does.
active() {
    [ "$(cat "$job/pending" 2>/dev/null)" = "$request" ] &&
        [ "$(cat "$worker/pid" 2>/dev/null)" = "$$ $boot_id" ] &&
        [ ! -f "$state/uninstall/pending" ]
}
delay=2
ready_attempt=0
while active; do
    if [ "$(getprop sys.boot_completed)" = 1 ]; then
        case "$(pm path android 2>/dev/null)" in package:*) break;; esac
    fi
    ready_attempt=$((ready_attempt + 1))
    if [ "${HMG_SEOGAEMO_UPDATE_HEALTH:-}" = 1 ] && [ "$ready_attempt" -gt 6 ]; then exit 1; fi
    sleep "$delay"
    [ "$delay" -ge 30 ] || delay=$((delay * 2))
    [ "$delay" -le 30 ] || delay=30
done
active || exit 0
attempt_boot=; attempts=0
{ read -r attempt_boot attempts < "$job/attempts"; } 2>/dev/null || :
[ "$attempt_boot" = "$boot_id" ] || attempts=0
case "$attempts" in ''|*[!0-9]*) exit 1;; esac
while [ "$attempts" -lt 4 ] && active; do
    # Serialize with module/restore operations; a busy lock keeps this request.
    lock=$state/operation.lock
    n=0
    while ! mkdir "$lock" 2>/dev/null; do
        holder=$(cat "$lock/pid" 2>/dev/null)
        case "$holder" in ''|*[!0-9]*) ;; *)
            if ! kill -0 "$holder" 2>/dev/null; then rm -rf "$lock"; continue; fi;;
        esac
        n=$((n + 1)); [ "$n" -lt 10 ] || exit 1
        sleep 1
    done
    operation=$lock
    echo "$$" > "$lock/pid" || exit 1
    active || exit 0
    # Reread reservations under the shared lock even after stale-worker recovery.
    attempt_boot=; attempts=0
    { read -r attempt_boot attempts < "$job/attempts"; } 2>/dev/null || :
    [ "$attempt_boot" = "$boot_id" ] || attempts=0
    case "$attempts" in ''|*[!0-9]*) exit 1;; esac
    case "$(pm path com.seogaemo.hmg 2>/dev/null)" in
        package:*) echo installed > "$job/seen" || exit 1;;
        *) if [ -f "$job/seen" ]; then
            rm -f "$job/pending" || exit 1
            rm -rf "$job/payload" || exit 1
            echo 'Pending APK cancelled: installed app removed' >> "$state/reconcile.log"
            exit 0
        fi;;
    esac
    [ "$attempts" -lt 4 ] || exit 1
    attempts=$((attempts + 1))
    echo "$boot_id $attempts" > "$job/attempts.tmp.$$" &&
        mv "$job/attempts.tmp.$$" "$job/attempts" || exit 1
    if /system/bin/sh "$job/payload/bin/install-apk.sh"; then
        active || exit 0
        # The fixed helper verifies registration before reporting success.
        echo installed > "$job/seen" || exit 1
        # The APK now matches this tree: drop any OTA selection so root verbs
        # do not run a different version than the installed app.
        update=$state/update
        rm -f "$update/current" "$update/current-path" "$update/previous-path" \
            "$update/previous-version" "$update/journal" || exit 1
        rm -f "$job/pending" || exit 1
        rm -rf "$job/payload"
        echo 'Pending APK installed' >> "$state/reconcile.log"
        exit 0
    fi
    echo "Pending APK failed (attempt $attempts/4); retained for retry" >> "$state/reconcile.log"
    rm -rf "$operation"; operation=
    # The synchronous USB hook reports its first failure promptly. Its service
    # launch can spend the remaining per-boot budget without holding the hook.
    [ "$1" != install ] || break
    [ "$attempts" -lt 4 ] || break
    case "$attempts" in 1) sleep 5;; 2) sleep 10;; 3) sleep 20;; esac
done
exit 1
