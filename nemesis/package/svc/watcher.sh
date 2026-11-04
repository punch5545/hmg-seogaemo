#!/system/bin/sh
# Dispatches boot/screen_on watch events to enabled modules. Never applies or reverts.
umask 077
base=${0%/*}
base=${base%/svc}
current=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}/update/current
if [ -L "$current" ] && [ -f "$current/svc/watcher.sh" ] && [ -r "$current/svc/watcher.sh" ] &&
        [ "$(readlink -f "$base")" != "$(readlink -f "$current")" ]; then
    exec /system/bin/sh "$current/svc/watcher.sh" "$@"
fi
. "$base/lib/common.sh"

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
# Retry state is scoped to kernel boot and event. A failed dispatch is never an
# acknowledgment. Attempts survive watcher restarts; uptime avoids wall-clock jumps.
watch_now() {
    read -r watch_uptime watch_rest < "${HMG_SEOGAEMO_WATCH_UPTIME_FILE:-/proc/uptime}" || return 1
    watch_uptime=${watch_uptime%%.*}
    case "$watch_uptime" in ''|*[!0-9]*) return 1;; esac
}
queue_screen() {
    sg_write "$boot_id" > "$state/watch/screen.$1"
    rm -f "$state/watch/retry.$1.screen_on"
}
dispatch_watch() {
    watch_id=$1; watch_event=$2
    watch_retry=$state/watch/retry.$watch_id.$watch_event
    watch_attempt=0; watch_due=0; watch_retry_boot=
    if [ -f "$watch_retry" ]; then
        read -r watch_retry_boot watch_attempt watch_due < "$watch_retry" || :
    fi
    [ "$watch_retry_boot" = "$boot_id" ] || { watch_attempt=0; watch_due=0; }
    case "$watch_attempt:$watch_due" in *[!0-9:]*|:*|*:) return 1;; esac
    [ "$watch_attempt" -lt 6 ] && [ "$watch_uptime" -ge "$watch_due" ] || return 1
    watch_attempt=$((watch_attempt + 1))
    watch_delay=5
    watch_step=1
    while [ "$watch_step" -lt "$watch_attempt" ]; do
        watch_delay=$((watch_delay * 2)); watch_step=$((watch_step + 1))
    done
    [ "$watch_delay" -le 60 ] || watch_delay=60
    watch_due=$((watch_uptime + watch_delay))
    # Reserve before invoking: a killed or restarted watcher cannot reset the budget.
    sg_write "$boot_id $watch_attempt $watch_due" > "$watch_retry.tmp.$$" &&
        mv "$watch_retry.tmp.$$" "$watch_retry" || return 1
    if /system/bin/sh "$root" "$watch_id" watch "$watch_event" >> "$log" 2>&1; then
        if [ "$watch_event" = boot ]; then
            sg_write "$boot_id" > "$state/watch/boot.$watch_id.tmp.$$" &&
                mv "$state/watch/boot.$watch_id.tmp.$$" "$state/watch/boot.$watch_id" || return 1
        else
            rm -f "$state/watch/screen.$watch_id"
        fi
        rm -f "$watch_retry"
    else
        echo "watch $watch_event failed: $watch_id (attempt $watch_attempt/6)" >> "$log"
        return 1
    fi
}

# Boot readiness: wait without a hard exit, slowly.
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
last_display=unknown
polls=0
while :; do
    watch_now || { sleep 5; continue; }
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
            dispatch_watch "$id" boot || :
        done
        if [ -n "$screen_ids" ]; then
            current_display=$(display_state)
            for id in $screen_ids; do
                if [ "$last_display" = off ] && [ "$current_display" = on ]; then
                    queue_screen "$id" || continue
                elif property_drift "$id" && [ "$(cat "$state/watch/screen.$id" 2>/dev/null)" != "$boot_id" ]; then
                    queue_screen "$id" || continue
                fi
                if [ "$(cat "$state/watch/screen.$id" 2>/dev/null)" = "$boot_id" ]; then
                    dispatch_watch "$id" screen_on || :
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
