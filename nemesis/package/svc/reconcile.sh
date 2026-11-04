#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/svc}
journal=/data/nemesis/backup/hmg-seogaemo/update/journal
if [ "${HMG_SEOGAEMO_UPDATE_HEALTH:-}" != 1 ] && [ -f "$journal" ]; then
    IFS= read -r phase < "$journal" || :
    case "$phase" in phase=applying|phase=auto-rollback|phase=rollback)
        /system/bin/sh "$base/core/update.sh" rollback || exit 1 ;;
    esac
fi

current=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}/update/current
if [ -L "$current" ] && [ -f "$current/svc/reconcile.sh" ] && [ -r "$current/svc/reconcile.sh" ] &&
        [ "$(readlink -f "$base")" != "$(readlink -f "$current")" ]; then
    exec /system/bin/sh "$current/svc/reconcile.sh" "$@"
fi
state=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
root=$base/bin/hmg-seogaemo-root
log=$state/reconcile.log
mkdir -p "$state" || exit 1
# A failed stock restore retains desired on for the failed module, but must
# never automatically reapply it on the next boot.
[ ! -f "$state/stock/paused" ] || exit 0
boot=0
while [ "$(getprop sys.boot_completed)" != 1 ]; do
    boot=$((boot + 1))
    [ "$boot" -lt 60 ] || { echo 'boot timeout' >> "$log"; exit 1; }
    sleep 2
done
reverse=
for id in $(cat "$base/modules.list"); do reverse="$id $reverse"; done
failed=0
missing_apk=0
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in package:*) ;; *) missing_apk=1; echo 'APK missing: reverting modules' >> "$log";; esac
for id in $reverse; do
    [ ! -f "$state/stock/paused" ] || exit "$failed"
    desired=$(cat "$state/desired/$id" 2>/dev/null)
    [ "$desired" = on ] && [ "$missing_apk" -eq 0 ] && continue
    baseline=0
    for item in "$state/modules/$id"/prop.*/ready "$state/modules/$id"/file.*/ready "$state/modules/$id"/line.*/ready; do
        [ -f "$item" ] && baseline=1
    done
    [ "$baseline" -eq 1 ] || continue
    if HMG_SEOGAEMO_RECONCILE=1 /system/bin/sh "$root" "$id" revert >> "$log" 2>&1; then
        echo "reverted $id" >> "$log"
    else echo "revert failed: $id" >> "$log"; failed=1; fi
done
for id in $(cat "$base/modules.list"); do
    [ ! -f "$state/stock/paused" ] || exit "$failed"
    [ "$missing_apk" -eq 0 ] || continue
    [ "$(cat "$state/desired/$id" 2>/dev/null)" = on ] || continue
    if HMG_SEOGAEMO_RECONCILE=1 /system/bin/sh "$root" "$id" apply >> "$log" 2>&1; then
        echo "applied $id" >> "$log"
    else echo "apply failed: $id" >> "$log"; failed=1; fi
done
exit "$failed"
