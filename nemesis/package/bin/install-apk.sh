#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/bin}
apk=$base/app/hmg-seogaemo.apk
[ -f "$apk" ] || { echo 'HMG-Seogaemo APK missing' >&2; exit 1; }
data=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
# Helper files (pm output, timeout marker, saved property, watchdog pid) live in
# a private 0700 directory under the state tree, never in /data/local/tmp. Only
# the staged APK, which PackageManager must be able to read, is outside.
mkdir -p "$data" || { echo 'Could not prepare installer state' >&2; exit 1; }
# A previous installer killed hard (SIGKILL of the whole process group) leaves
# its directory behind. Recover the boot property it may have been holding at 0
# before reading the "old" value below, then remove it. Exact names only.
for stale in "$data"/install-apk.*; do
    stale_pid=${stale##*/install-apk.}
    case "$stale_pid" in ''|*[!0-9]*) continue;; esac
    [ -d "$stale" ] && [ ! -L "$stale" ] || continue
    ! kill -0 "$stale_pid" 2>/dev/null || continue
    stale_guard=
    [ ! -f "$stale/guard.pid" ] || read -r stale_guard < "$stale/guard.pid" || :
    case "$stale_guard" in ''|*[!0-9]*) ;; *) ! kill -0 "$stale_guard" 2>/dev/null || continue;; esac
    stale_old=
    [ ! -f "$stale/old" ] || read -r stale_old < "$stale/old" || :
    case "$stale_old" in
        ''|*[!0-9]*) ;;
        *) [ "$(getprop sys.boot_completed)" != 0 ] || [ "$stale_old" = 0 ] || setprop sys.boot_completed "$stale_old";;
    esac
    stale_apk=
    [ ! -f "$stale/staged" ] || read -r stale_apk < "$stale/staged" || :
    case "$stale_apk" in */hmg-seogaemo-install.*.apk) rm -f "$stale_apk";; esac
    rm -rf "$stale"
done
old=$(getprop sys.boot_completed)
# pm install can hang while sys.boot_completed is forced to 0; bound it. The
# limits are overridable only by digits (host-test seams).
limit=${HMG_SEOGAEMO_PM_TIMEOUT:-120}
case "$limit" in ''|*[!0-9]*) limit=120;; esac
grace=${HMG_SEOGAEMO_PM_GRACE:-5}
case "$grace" in ''|*[!0-9]*) grace=5;; esac
work=$data/install-apk.$$
rm -rf "$work"
mkdir "$work" && chmod 0700 "$work" || { echo 'Could not prepare installer state' >&2; exit 1; }
out=$work/out
echo "$old" > "$work/old" || { rm -rf "$work"; echo 'Could not prepare installer state' >&2; exit 1; }
# PackageManager cannot read APKs under the 0700 OTA state tree; stage a readable
# copy under a name that cannot be predicted (pid, clock, $RANDOM).
staged=${HMG_SEOGAEMO_APK_STAGE:-/data/local/tmp}/hmg-seogaemo-install.$$.$(date +%s).${RANDOM:-0}.apk
echo "$staged" > "$work/staged"
if [ -e "$staged" ] || [ -L "$staged" ]; then rm -rf "$work"; echo 'Could not stage APK' >&2; exit 1; fi
cp "$apk" "$staged" && chmod 644 "$staged" || { rm -rf "$staged" "$work"; echo 'Could not stage APK' >&2; exit 1; }
restored=0
restore() {
    if [ "$restored" -eq 0 ]; then
        if ! setprop sys.boot_completed "$old" || [ "$(getprop sys.boot_completed)" != "$old" ]; then
            echo 'Could not restore sys.boot_completed' >&2
            return 1
        fi
        restored=1
    fi
}
# Best effort: toolbox has no pkill, so walk /proc for descendants (3 levels).
descendants() {
    frontier=$1; found=; level=0
    while [ "$level" -lt 3 ] && [ -n "$frontier" ]; do
        next=
        for st in /proc/[0-9]*/stat; do
            line=
            { read -r line < "$st"; } 2>/dev/null || continue
            set -- ${line#*) }
            for parent in $frontier; do
                [ "$2" = "$parent" ] || continue
                child=${st#/proc/}
                next="$next ${child%/stat}"
            done
        done
        found="$found$next"; frontier=$next; level=$((level + 1))
    done
    echo $found
}
# TERM pm and its descendants, wait up to $grace seconds, then KILL what is left.
stop_pm() {
    victims="$(descendants "$1") $1"
    kill $victims 2>/dev/null
    stop_wait=0
    while [ "$stop_wait" -lt "$grace" ]; do
        stop_alive=
        for victim in $victims; do
            ! kill -0 "$victim" 2>/dev/null || stop_alive=1
        done
        [ -n "$stop_alive" ] || return 0
        sleep 1
        stop_wait=$((stop_wait + 1))
    done
    kill -9 $victims 2>/dev/null
    return 0
}
pm_pid=; guard=
reap() {
    # Stop the watchdog first so it cannot race the restore below.
    [ -z "$guard" ] || kill "$guard" 2>/dev/null
    if [ -n "$pm_pid" ] && kill -0 "$pm_pid" 2>/dev/null; then
        stop_pm "$pm_pid"
    fi
    pm_pid=; guard=
}
# Every exit, including TERM, stops pm and restores the boot property.
# (mksh reports the trap body's status, so carry the original one through.)
trap 'exit_code=$?; reap; rm -rf "$staged" "$work"; restore || exit 1; exit "$exit_code"' 0
trap 'exit 1' 1 2 3 15
setprop sys.boot_completed 0 || exit 1
[ "$(getprop sys.boot_completed)" = 0 ] || { echo 'Could not set sys.boot_completed' >&2; exit 1; }
installer=$$
# A rollback installs an older versionCode, which PackageManager refuses without -d.
# Only the rollback callers set HMG_SEOGAEMO_PM_DOWNGRADE=1; a normal install never downgrades.
if [ "${HMG_SEOGAEMO_PM_DOWNGRADE:-}" = 1 ]; then
    pm install -r -d "$staged" > "$out" 2>&1 < /dev/null &
else
    pm install -r "$staged" > "$out" 2>&1 < /dev/null &
fi
pm_pid=$!
(
    # Watchdog. It outlives a SIGKILLed installer on purpose: when the parent
    # is gone it stops pm and restores the boot property itself.
    trap '' 1
    n=0
    while :; do
        sleep 1
        if ! kill -0 "$installer" 2>/dev/null; then
            stop_pm "$pm_pid"
            setprop sys.boot_completed "$old"
            rm -rf "$staged" "$work"
            exit 0
        fi
        kill -0 "$pm_pid" 2>/dev/null || exit 0
        n=$((n + 1))
        if [ "$n" -ge "$limit" ]; then
            : > "$work/timeout"
            stop_pm "$pm_pid"
            exit 0
        fi
    done
) > /dev/null 2>&1 < /dev/null &
guard=$!
echo "$guard" > "$work/guard.pid"
wait "$pm_pid"
pm_code=$?
kill "$guard" 2>/dev/null
wait "$guard" 2>/dev/null
pm_pid=; guard=
result=$(cat "$out" 2>/dev/null)
timed_out=0
[ ! -f "$work/timeout" ] || timed_out=1
# Restore before reporting, whatever happened (including the watchdog firing).
restore || exit 1
[ "$timed_out" -eq 0 ] || { echo "pm install timed out after ${limit}s" >&2; exit 1; }
[ "$pm_code" -eq 0 ] || { echo "$result" >&2; exit 1; }
case "$result" in *Success*) ;; *) echo "$result" >&2; exit 1 ;; esac
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in package:*) ;; *) echo 'APK install did not register' >&2; exit 1 ;; esac
