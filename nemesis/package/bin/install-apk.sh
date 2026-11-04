#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/bin}
apk=$base/app/hmg-seogaemo.apk
[ -f "$apk" ] || { echo 'HMG-Seogaemo APK missing' >&2; exit 1; }
old=$(getprop sys.boot_completed)
# pm install can hang while sys.boot_completed is forced to 0; bound it. The
# limit is overridable only by digits (host-test seam).
limit=${HMG_SEOGAEMO_PM_TIMEOUT:-120}
case "$limit" in ''|*[!0-9]*) limit=120;; esac
# PackageManager cannot read APKs under the 0700 OTA state tree; stage a readable copy.
staged=${HMG_SEOGAEMO_APK_STAGE:-/data/local/tmp}/hmg-seogaemo-install.$$.apk
out=$staged.out
cp "$apk" "$staged" && chmod 644 "$staged" || { rm -f "$staged"; echo 'Could not stage APK' >&2; exit 1; }
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
pm_pid=; guard=
reap() {
    if [ -n "$pm_pid" ] && kill -0 "$pm_pid" 2>/dev/null; then
        kill $(descendants "$pm_pid") "$pm_pid" 2>/dev/null
    fi
    [ -z "$guard" ] || kill "$guard" 2>/dev/null
    pm_pid=; guard=
}
# Every exit, including TERM, stops pm and restores the boot property.
# (mksh reports the trap body's status, so carry the original one through.)
trap 'exit_code=$?; reap; rm -f "$staged" "$out" "$out.timeout"; restore || exit 1; exit "$exit_code"' 0
trap 'exit 1' 1 2 3 15
setprop sys.boot_completed 0 || exit 1
[ "$(getprop sys.boot_completed)" = 0 ] || { echo 'Could not set sys.boot_completed' >&2; exit 1; }
rm -f "$out.timeout"
pm install -r "$staged" > "$out" 2>&1 < /dev/null &
pm_pid=$!
(
    n=0
    while [ "$n" -lt "$limit" ]; do
        sleep 1
        kill -0 "$pm_pid" 2>/dev/null || exit 0
        n=$((n + 1))
    done
    : > "$out.timeout"
    kill $(descendants "$pm_pid") "$pm_pid" 2>/dev/null
) > /dev/null 2>&1 < /dev/null &
guard=$!
wait "$pm_pid"
pm_code=$?
kill "$guard" 2>/dev/null
wait "$guard" 2>/dev/null
pm_pid=; guard=
result=$(cat "$out" 2>/dev/null)
# Restore before reporting, whatever happened (including the watchdog firing).
restore || exit 1
[ ! -f "$out.timeout" ] || { echo "pm install timed out after ${limit}s" >&2; exit 1; }
[ "$pm_code" -eq 0 ] || { echo "$result" >&2; exit 1; }
case "$result" in *Success*) ;; *) echo "$result" >&2; exit 1 ;; esac
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in package:*) ;; *) echo 'APK install did not register' >&2; exit 1 ;; esac
