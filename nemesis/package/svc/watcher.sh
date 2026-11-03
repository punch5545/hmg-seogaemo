#!/system/bin/sh
# Dispatches boot/screen_on watch events to enabled modules. Never applies or reverts.
umask 077
base=${0%/*}
base=${base%/svc}
. "$base/lib/common.sh"
current=/data/nemesis/backup/hmg-seogaemo/update/current
if [ "$base" != "$current" ] && [ -L "$current" ] && [ -f "$current/svc/watcher.sh" ]; then
    exec /system/bin/sh "$current/svc/watcher.sh"
fi
state=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
root=$base/bin/hmg-seogaemo-root
log=$state/reconcile.log
mkdir -p "$state/watch" || exit 1
boot_id=$(cat "${SG_BOOT_ID_FILE:-/proc/sys/kernel/random/boot_id}" 2>/dev/null)
case "$boot_id" in ''|*[!a-zA-Z0-9-]*) boot_id=process-$$ ;; esac
display_state() {
    power=$(dumpsys power 2>/dev/null) || { echo unknown; return; }
    case "$power" in
        *'mWakefulness=Asleep'*|*'mDisplayPowerRequest.policy=OFF'*|*'mDisplayPowerRequest=policy=OFF'*|*'mScreenOn=false'*|*'mInteractive=false'*) echo off ;;
        *'mWakefulness=Awake'*|*'mWakefulness=Dreaming'*|*'mDisplayPowerRequest.policy=BRIGHT'*|*'mDisplayPowerRequest.policy=DIM'*|*'mDisplayPowerRequest=policy=BRIGHT'*|*'mDisplayPowerRequest=policy=DIM'*|*'mScreenOn=true'*|*'mInteractive=true'*) echo on ;;
        *) echo unknown ;;
    esac
}
# Quick boot can keep both boot ID and display state unchanged. Reuse the
# fixed screen_on handler only for explicitly declared, baselined property drift.
property_drift() {
    drift_keys=
    while IFS='=' read -r drift_field drift_value; do
        [ "$drift_field" = reassert_props ] && drift_keys=$drift_value
    done < "$base/modules/$1.properties"
    drift_ifs=$IFS; IFS=,
    for drift_key in $drift_keys; do
        case "$drift_key" in ''|*[!a-zA-Z0-9_.-]*) continue;; esac
        drift_dir=$state/modules/$1/prop.$drift_key
        [ -f "$drift_dir/ready" ] && [ -f "$drift_dir/applied" ] || continue
        drift_expected=$(cat "$drift_dir/applied") || continue
        drift_actual=$(getprop "$drift_key") || continue
        if [ "$drift_actual" != "$drift_expected" ]; then IFS=$drift_ifs; return 0; fi
    done
    IFS=$drift_ifs
    return 1
}
# Boot readiness: wait without a hard exit, slowly.
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
last_display=unknown
polls=0
while :; do
    boot_ids=
    screen_ids=
    for id in $(cat "$base/modules.list" 2>/dev/null); do
        [ ! -f "$state/stock/paused" ] || continue
        [ "$(cat "$state/desired/$id" 2>/dev/null)" = on ] || continue
        [ -f "$base/modules/$id.properties" ] || continue
        while IFS='=' read -r field value; do
            [ "$field" = watch ] || continue
            case ",$value," in *,boot,*) boot_ids="$boot_ids $id" ;; esac
            case ",$value," in *,screen_on,*) screen_ids="$screen_ids $id" ;; esac
        done < "$base/modules/$id.properties"
    done
    if [ -z "$boot_ids$screen_ids" ]; then
        last_display=unknown
        interval=${HMG_SEOGAEMO_WATCH_IDLE:-300}
    else
        interval=${HMG_SEOGAEMO_WATCH_POLL:-5}
        for id in $boot_ids; do
            [ "$(cat "$state/watch/boot.$id" 2>/dev/null)" = "$boot_id" ] && continue
            sg_write "$boot_id" > "$state/watch/boot.$id" || continue
            /system/bin/sh "$root" "$id" watch boot >> "$log" 2>&1 || echo "watch boot failed: $id" >> "$log"
        done
        if [ -n "$screen_ids" ]; then
            current_display=$(display_state)
            for id in $screen_ids; do
                if { [ "$last_display" = off ] && [ "$current_display" = on ]; } || property_drift "$id"; then
                    /system/bin/sh "$root" "$id" watch screen_on >> "$log" 2>&1 || echo "watch screen_on failed: $id" >> "$log"
                fi
            done
            [ "$current_display" = unknown ] || last_display=$current_display
        fi
    fi
    polls=$((polls + 1))
    [ "${HMG_SEOGAEMO_WATCH_MAX_POLLS:-0}" -eq 0 ] ||
        [ "$polls" -lt "$HMG_SEOGAEMO_WATCH_MAX_POLLS" ] || exit 0
    sleep "$interval"
done
