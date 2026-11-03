#!/system/bin/sh
# Fixed car-map commands. The mapper runs in a separate root app_process.
umask 077
[ "$#" -eq 1 ] || exit 2
case "$1" in status|run|verify|export) ;; *) exit 2;; esac
base=${0%/*}
base=${base%/core}
SG_DATA_ROOT=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
HMG_SEOGAEMO_BASE=$base
HMG_SEOGAEMO_STATE=$SG_DATA_ROOT/core/map
export SG_DATA_ROOT HMG_SEOGAEMO_BASE HMG_SEOGAEMO_STATE
. "$base/lib/common.sh"
mapper() {
    CLASSPATH="$base/core/map/hmg-seogaemo-mapper.jar" app_process /system/bin com.seogaemo.hmg.core.map.HmgSeogaemoMapTool \
        "$1" "$base" "$SG_DATA_ROOT" "$2"
}
if [ "$1" = status ]; then
    mapper status "$SG_DATA_ROOT/car-maps/.unused"
    exit $?
fi
lock=$SG_DATA_ROOT/operation.lock
mkdir -p "$SG_DATA_ROOT" || exit 1
tries=0
while ! mkdir "$lock" 2>/dev/null; do
    if [ -f "$lock/pid" ]; then
        holder=$(cat "$lock/pid" 2>/dev/null)
        case "$holder" in ''|*[!0-9]*) ;; *)
            if ! kill -0 "$holder" 2>/dev/null; then rm -rf "$lock"; continue; fi;;
        esac
    fi
    tries=$((tries + 1))
    [ "$tries" -lt 10 ] || { printf 'state=failed\nrestart=none\ndetail=operation busy\n'; exit 1; }
    sleep 1
done
printf '%s' "$$" > "$lock/pid"
cleanup() {
    [ -n "${HMG_SEOGAEMO_MAP_WORK:-}" ] && hmg_seogaemo_map_work_cleanup
    rm -rf "$lock"
}
trap cleanup EXIT HUP INT TERM
if [ "$1" = export ]; then
    target=
    for drive in ${SG_USB_DIRS:-/storage/usb0 /storage/usb1 /storage/usb2 /mnt/media_rw/usb0 /mnt/media_rw/usb1 /mnt/media_rw/usb2}; do
        grep -q " $drive " "${SG_MOUNTS_FILE:-/proc/mounts}" 2>/dev/null || continue
        target=$drive/HMG-Seogaemo-export/$(date +%Y%m%d-%H%M%S)
        break
    done
    [ -n "$target" ] || target=${SG_EXPORT_ROOT:-/data/local/tmp/hmg-seogaemo-export}/$(date +%Y%m%d-%H%M%S)
    hmg_seogaemo_map_export "$target" || { printf 'state=failed\nrestart=none\ndetail=no verified map\n'; exit 1; }
    chmod -R 755 "$target" || exit 1
    printf 'state=on\nrestart=none\ndetail=%s\n' "$target"
    exit 0
fi
hmg_seogaemo_map_work || { printf 'state=failed\nrestart=none\ndetail=map work unavailable\n'; exit 1; }
mapper "$1" "$HMG_SEOGAEMO_MAP_WORK" > "$HMG_SEOGAEMO_MAP_WORK/result" 2>&1
code=$?
if [ "$code" -ne 0 ]; then
    dd if="$HMG_SEOGAEMO_MAP_WORK/result" bs=1 count=4096 2>/dev/null
    exit "$code"
fi
if ! hmg_seogaemo_map_publish "$HMG_SEOGAEMO_MAP_WORK"; then
    printf 'state=failed\nrestart=none\ndetail=map publish failed\n'
    exit 1
fi
dd if="$HMG_SEOGAEMO_MAP_WORK/result" bs=1 count=4096 2>/dev/null
