#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/svc}
journal=/data/nemesis/backup/hmg-seogaemo/update/journal
if [ "${HMG_SEOGAEMO_UPDATE_HEALTH:-}" != 1 ] && [ -f "$journal" ]; then
    IFS= read -r phase < "$journal" || :
    case "$phase" in phase=applying|phase=auto-rollback|phase=rollback)
        sh "$base/core/update.sh" rollback || exit 1 ;;
    esac
fi
current=/data/nemesis/backup/hmg-seogaemo/update/current
if [ "$base" != "$current" ] && [ -L "$current" ]; then
    exec sh "$current/svc/reconcile.sh"
fi
state=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
root=$base/bin/hmg-seogaemo-root
log=$state/reconcile.log
mkdir -p "$state" || exit 1
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
    desired=$(cat "$state/desired/$id" 2>/dev/null)
    [ "$desired" = on ] && [ "$missing_apk" -eq 0 ] && continue
    baseline=0
    for item in "$state/modules/$id"/prop.*/ready "$state/modules/$id"/file.*/ready; do
        [ -f "$item" ] && baseline=1
    done
    [ "$baseline" -eq 1 ] || continue
    if sh "$root" "$id" revert >> "$log" 2>&1; then
        echo "reverted $id" >> "$log"
    else echo "revert failed: $id" >> "$log"; failed=1; fi
done
for id in $(cat "$base/modules.list"); do
    [ "$missing_apk" -eq 0 ] || continue
    [ "$(cat "$state/desired/$id" 2>/dev/null)" = on ] || continue
    if sh "$root" "$id" apply >> "$log" 2>&1; then
        echo "applied $id" >> "$log"
    else echo "apply failed: $id" >> "$log"; failed=1; fi
done
[ "$failed" -eq 0 ] || exit "$failed"
[ "${HMG_SEOGAEMO_UPDATE_HEALTH:-0}" = 1 ] && exit 0
if [ "$missing_apk" -eq 1 ]; then
    while :; do
        sleep 60
        case "$(pm path com.seogaemo.hmg 2>/dev/null)" in
            package:*) exec sh "$base/svc/reconcile.sh" ;;
        esac
    done
fi
watch_ids=
for id in $(cat "$base/modules.list"); do
    [ -f "$base/modules/$id.properties" ] || continue
    grep -Eq '^watch=(boot|screen_on|boot,screen_on|screen_on,boot)$' "$base/modules/$id.properties" || continue
    watch_ids="$watch_ids $id"
done
[ -n "$watch_ids" ] || exit 0
for id in $watch_ids; do
    [ "$(cat "$state/desired/$id" 2>/dev/null)" = on ] || continue
    grep -Eq '^watch=(boot|boot,screen_on|screen_on,boot)$' "$base/modules/$id.properties" || continue
    sh "$root" "$id" watch boot >> "$log" 2>&1 || echo "watch boot failed: $id" >> "$log"
done
display_state() {
    power=$(dumpsys power 2>/dev/null) || { echo unknown; return; }
    case "$power" in
        *'mDisplayPowerRequest=policy=OFF'*|*'mDisplayPowerRequest=policy=0'*|*'mScreenOn=false'*|*'mInteractive=false'*) echo off ;;
        *'mDisplayPowerRequest=policy=BRIGHT'*|*'mDisplayPowerRequest=policy=DIM'*|*'mScreenOn=true'*|*'mInteractive=true'*) echo on ;;
        *) echo unknown ;;
    esac
}
last_display=$(display_state)
polls=0
while [ "${HMG_SEOGAEMO_WATCH_ONCE:-0}" != 1 ]; do
    sleep 5
    current_display=$(display_state)
    if [ "$last_display" = off ] && [ "$current_display" = on ]; then
        for id in $watch_ids; do
            [ "$(cat "$state/desired/$id" 2>/dev/null)" = on ] || continue
            grep -Eq '^watch=(screen_on|boot,screen_on|screen_on,boot)$' "$base/modules/$id.properties" || continue
            sh "$root" "$id" watch screen_on >> "$log" 2>&1 || echo "watch screen_on failed: $id" >> "$log"
        done
    fi
    [ "$current_display" = unknown ] || last_display=$current_display
    polls=$((polls + 1))
    [ "${HMG_SEOGAEMO_WATCH_MAX_POLLS:-0}" -eq 0 ] ||
        [ "$polls" -lt "$HMG_SEOGAEMO_WATCH_MAX_POLLS" ] || break
done
exit 0
