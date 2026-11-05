#!/system/bin/sh
# HSM-REPORTS recorder and car check. Reached only through the fixed root verbs
#   recorder status|enable|disable|tick     preflight run
# Read-only on the unit: it writes only under its own state directory and, while the owner has
# turned recording on, into <usb>/HSM-REPORTS. See .agents/reports-recorder.md.
umask 077
base=${0%/*}
base=${base%/core}
SG_DATA_ROOT=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
HMG_SEOGAEMO_BASE=$base
HMG_SEOGAEMO_STATE=$SG_DATA_ROOT/recorder
export SG_DATA_ROOT HMG_SEOGAEMO_BASE HMG_SEOGAEMO_STATE
. "$base/lib/common.sh"
. "$base/lib/preflight.sh"

group=${1:-}
verb=${2:-}
state=$HMG_SEOGAEMO_STATE
spool=$state/spool
FOLDER=HSM-REPORTS
KEEP_BOOTS=20
FLUSH_SECONDS=${HMG_SEOGAEMO_RECORDER_FLUSH:-120}
SNAPSHOT_SECONDS=${HMG_SEOGAEMO_RECORDER_SNAPSHOT:-600}
case "$FLUSH_SECONDS$SNAPSHOT_SECONDS" in *[!0-9]*|'') FLUSH_SECONDS=120; SNAPSHOT_SECONDS=600;; esac

reply() { sg_print "state=$1" "restart=none" "detail=$(sg_clean "$2")"; }

# First mounted USB drive, same candidates and mount test as the changes export.
usb_base() {
    for rc_drive in ${SG_USB_DIRS:-/storage/usb0 /storage/usb1 /storage/usb2 /mnt/media_rw/usb0 /mnt/media_rw/usb1 /mnt/media_rw/usb2}; do
        sg_mount_present "$rc_drive" || continue
        sg_print "$rc_drive"
        return 0
    done
    return 1
}

# One redaction pass for everything that reaches the USB drive (never per collector).
hsm_redact() {
    while IFS= read -r rc_line || [ -n "$rc_line" ]; do
        case "$rc_line" in
            *wifimac:' 'EMPTY|*wifimac:' 'SET) sg_print "$rc_line" ;;
            *wifimac*"[]") sg_print 'ro.boot.wifimac: EMPTY' ;;
            *wifimac*"[]"*|*wifimac=) sg_print 'wifimac: EMPTY' ;;
            *wifimac*) sg_print 'wifimac: SET' ;;
            *[Ss][Ee][Rr][Ii][Aa][Ll]*|*[Bb][Tt][Aa][Dd][Dd][Rr]*|*[Ss][Ss][Ii][Dd]*|*[Pp][Ss][Kk]*|*[Pp][Aa][Ss][Ss][Ww]*|*[Ss][Ee][Cc][Rr][Ee][Tt]*|*[Tt][Oo][Kk][Ee][Nn]*|*[Cc][Rr][Ee][Dd][Ee][Nn]*|*[Ii][Mm][Ee][Ii]*|*[Ii][Mm][Ss][Ii]*|*[Ii][Cc][Cc][Ii][Dd]*|*[Vv][Ii][Nn]=*|*[Hh][Ww][Aa][Dd][Dd][Rr]*|*link/ether*|*[Pp][Hh][Oo][Nn][Ee]?[Nn][Uu][Mm]*)
                sg_print '[redacted]' ;;
            *[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]*)
                sg_print '[redacted]' ;;
            *) sg_print "$rc_line" ;;
        esac
    done
}

# Keep the last $3 blocks of 4 KiB of $1 in $2 (redacted); a partial first line is dropped.
put_tail() {
    [ -f "$1" ] || return 0
    rc_bytes=$(sg_file_size "$1") || return 1
    rc_blocks=$(((rc_bytes + 4095) / 4096))
    rc_skip=0
    [ "$rc_blocks" -le "$3" ] || rc_skip=$((rc_blocks - $3))
    dd if="$1" bs=4096 skip="$rc_skip" count="$3" 2>/dev/null > "$state/put.raw.$$" || return 1
    {
        if [ "$rc_skip" -gt 0 ]; then IFS= read -r rc_frag || :; fi
        hsm_redact
    } < "$state/put.raw.$$" > "$2.tmp.$$" || { rm -f "$state/put.raw.$$" "$2.tmp.$$"; return 1; }
    rm -f "$state/put.raw.$$"
    mv "$2.tmp.$$" "$2"
}

# Redact stdin into $1 atomically.
put_stdin() {
    hsm_redact > "$1.tmp.$$" && mv "$1.tmp.$$" "$1" || { rm -f "$1.tmp.$$"; return 1; }
}

event() { sg_print "$(date +%s) $1" >> "$spool/events.log" 2>/dev/null || :; }

logcat_pid() {
    rc_pid=
    # The file has no trailing newline, so read reports EOF; the value is validated below.
    { read -r rc_pid < "$state/logcat.pid"; } 2>/dev/null || :
    case "$rc_pid" in ''|*[!0-9]*) return 1;; esac
    kill -0 "$rc_pid" 2>/dev/null || return 1
    grep -q logcat "${SG_PROC_DIR:-/proc}/$rc_pid/cmdline" 2>/dev/null || return 1
    sg_print "$rc_pid"
}

start_logcat() {
    logcat_pid > /dev/null && return 0
    mkdir -p "$spool" || return 1
    # The head unit's main log buffer holds seconds, so a bounded filtered stream runs while
    # recording is on. Only tags that explain this package's behaviour are kept.
    logcat -v time -f "$spool/logcat.txt" -r 128 -n 2 '*:S' \
        HMG-Seogaemo-Wireless:V HMG-Seogaemo:V DiPO:V GPM:V Iap2:V iap2:V \
        WifiHW:V WifiStateMachine:V WifiService:V WifiNative:V WifiMonitor:V WifiSettings:V \
        wpa_supplicant:V hostapd:V dnsmasq:V AndroidRuntime:E ActivityManager:W '*:F' \
        > /dev/null 2>&1 < /dev/null &
    sg_write "$!" > "$state/logcat.pid"
}

stop_logcat() {
    rc_stop=$(logcat_pid) || return 0
    kill "$rc_stop" 2>/dev/null || :
    rm -f "$state/logcat.pid"
}

# Prepare <usb>/HSM-REPORTS and prove it is writable.
prepare_usb() {
    rc_root=$1/$FOLDER
    sg_path_safe "$rc_root" || return 1
    mkdir -p "$rc_root" || return 1
    sg_print 'HSM-REPORTS: written by HMG-Seogaemo while "Record reports to USB" is on.' \
        '' \
        'Send the newest boot-NNNN folder (or this whole folder) when you ask for help.' \
        'Serial numbers, Bluetooth and Wi-Fi addresses, network names and passwords are removed.' \
        'It is safe to delete this folder. Turn recording off in Settings to stop writing here.' \
        '' \
        'HSM-REPORTS: HMG-Seogaemo가 "USB에 보고서 기록"이 켜져 있는 동안 기록합니다.' \
        '도움을 요청할 때 가장 최근 boot-NNNN 폴더(또는 이 폴더 전체)를 보내 주세요.' \
        '시리얼 번호, 블루투스/Wi-Fi 주소, 네트워크 이름, 비밀번호는 지워서 저장합니다.' \
        > "$rc_root/README.txt" || return 1
    : > "$rc_root/.write-test.$$" || return 1
    rm -f "$rc_root/.write-test.$$"
}

seq_text() {
    rc_n=$1
    case "${#rc_n}" in 1) rc_n=000$rc_n;; 2) rc_n=00$rc_n;; 3) rc_n=0$rc_n;; esac
    sg_print "$rc_n"
}

next_boot_seq() {
    rc_boot=$(cat "${SG_BOOT_ID_FILE:-/proc/sys/kernel/random/boot_id}" 2>/dev/null)
    case "$rc_boot" in ''|*[!a-zA-Z0-9-]*) rc_boot=process-$$;; esac
    rc_known=$(cat "$state/boot" 2>/dev/null)
    rc_seq=$(cat "$state/seq" 2>/dev/null)
    case "$rc_seq" in ''|*[!0-9]*) rc_seq=0;; esac
    if [ "$rc_known" != "$rc_boot" ]; then
        rc_seq=$((rc_seq + 1))
        sg_write "$rc_seq" > "$state/seq.tmp.$$" && mv "$state/seq.tmp.$$" "$state/seq" || return 1
        sg_write "$rc_boot" > "$state/boot.tmp.$$" && mv "$state/boot.tmp.$$" "$state/boot" || return 1
        rm -f "$state/last-snapshot" "$state/last-flush"
        event "boot $rc_seq started"
    fi
    sg_print "$rc_seq"
}

collect_facts() {
    sg_print "time=$(date)" "uptime=$(cat /proc/uptime 2>/dev/null)" "kernel=$(cat /proc/version 2>/dev/null)"
    grep '^package-version=' "$base/NEMESIS.data" 2>/dev/null
    sg_print "root-package=$(cat "$base/VERSION" 2>/dev/null)"
    sg_print "--- properties"
    for rc_prop in ro.build.product ro.build.version.incremental ro.build.version.release ro.board.platform \
            ro.mobis.onebindir ro.mobis.targetdir sys.boot_completed persist.service.adb.tcp.enable \
            persist.wifi.state persist.ium.support persist.sys.iap2.wifi persist.iap2.iden.wireless \
            wlan.driver.status tcc.wifi.vendor wifi.interface init.svc.hostapd init.svc.wpa_supplicant \
            init.svc.p2p_supplicant; do
        sg_print "$rc_prop=$(getprop "$rc_prop")"
    done
    sg_print "--- kernel command line (selected words)"
    for rc_word in $(cat "${SG_CMDLINE_FILE:-/proc/cmdline}" 2>/dev/null); do
        case "$rc_word" in
            androidboot.wifimac=) sg_print 'androidboot.wifimac: EMPTY' ;;
            androidboot.wifimac=*) sg_print 'androidboot.wifimac: SET' ;;
            androidboot.mode=*|daudio_hw_ver=*|vehicle_info=*|country_info=*|daudio_info_id=*|daudio_info_mode=*) sg_print "$rc_word" ;;
        esac
    done
    sg_print "--- wifi buses"
    sg_print "sdio devices: $(pf_count_entries "${SG_SDIO_DIR:-/sys/bus/sdio/devices}" '*')"
    for rc_card in "${SG_MMC_DIR:-/sys/bus/mmc/devices}"/*; do
        [ -e "$rc_card" ] && sg_print "mmc device ${rc_card##*/} type=$(cat "$rc_card/type" 2>/dev/null)"
    done
    sg_print "--- wifi files"
    ls -l "${SG_TARGET_ROOT:-}/system/wifi" 2>&1
    sg_print "--- feature files"
    for rc_x in "${SG_TARGET_ROOT:-}"/system/etc/permissions/*wifi* "${SG_TARGET_ROOT:-}"/system/etc/permissions/*carplay* \
            "${SG_TARGET_ROOT:-}"/system/etc/permissions/*androidauto*; do
        [ -f "$rc_x" ] && sg_print "${rc_x##*/}"
    done
    sg_print "--- interfaces"
    ifconfig 2>&1
    sg_print "--- usb"
    sg_print "mounted: $(usb_base)"
}

collect_status() {
    sg_print "--- map"
    /system/bin/sh "$base/bin/hmg-seogaemo-root" map status 2>&1
    sg_print "--- modules"
    for rc_id in $(sg_module_list "$base"); do
        sg_print "[$rc_id] desired=$(cat "$SG_DATA_ROOT/desired/$rc_id" 2>/dev/null)"
        /system/bin/sh "$base/bin/hmg-seogaemo-root" "$rc_id" status 2>&1
    done
}

snapshot() {
    rc_dest=$1
    { collect_facts; } 2>&1 | put_stdin "$rc_dest/facts.txt"
    { hmg_seogaemo_preflight; } 2>&1 | put_stdin "$rc_dest/check.txt"
    { collect_status; } 2>&1 | put_stdin "$rc_dest/status.txt"
    date +%s > "$state/last-snapshot"
}

# The hook's per-process records and log ring (gate decision, loaded parts, native patches);
# same fixed area, depth and names the changes export reads.
copy_runtime() {
    rc_n=0
    for rc_src in "$SG_DATA_ROOT"/runtime/* "$SG_DATA_ROOT"/runtime/*/* "$SG_DATA_ROOT"/runtime/*/*/* \
            "$SG_DATA_ROOT"/runtime/wireless-carplay-and-aa/apps/*/*; do
        [ -f "$rc_src" ] && sg_path_safe "$rc_src" || continue
        case "${rc_src##*/}" in *.log|*.log.1|*.txt|result|module-output) ;; *) continue;; esac
        rc_n=$((rc_n + 1))
        [ "$rc_n" -le 32 ] || break
        rc_rel=${rc_src#"$SG_DATA_ROOT/runtime/"}
        case "$rc_rel" in */*) rc_dir=$1/runtime/${rc_rel%/*};; *) rc_dir=$1/runtime;; esac
        mkdir -p "$rc_dir" && put_tail "$rc_src" "$rc_dir/${rc_rel##*/}" 16
    done
}

flush() {
    rc_dest=$1
    if [ -f "$spool/logcat.txt.1" ] && [ -f "$spool/logcat.txt" ]; then
        cat "$spool/logcat.txt.1" "$spool/logcat.txt" > "$state/logcat.join.$$" 2>/dev/null &&
            put_tail "$state/logcat.join.$$" "$rc_dest/logcat.txt" 64
        rm -f "$state/logcat.join.$$"
    else
        put_tail "$spool/logcat.txt" "$rc_dest/logcat.txt" 64
    fi
    dmesg 2>/dev/null | grep -i -e bcm -e dhd -e wlan -e wifi -e sdio -e mmc -e firmware -e nvram -e cfg80211 \
        -e hostapd -e iap2 -e carplay -e usb -e panic -e segfault -e 'out of memory' > "$state/dmesg.raw.$$" 2>/dev/null
    put_tail "$state/dmesg.raw.$$" "$rc_dest/dmesg.txt" 32
    rm -f "$state/dmesg.raw.$$"
    copy_runtime "$rc_dest"
    put_tail "$SG_DATA_ROOT/reconcile.log" "$rc_dest/reconcile.log" 16
    for rc_id in $(sg_module_list "$base"); do
        case "$rc_id" in ''|*[!a-z0-9-]*|-*|*--*|*-) continue;; esac
        [ -f "$SG_DATA_ROOT/modules/$rc_id/module.log" ] || continue
        mkdir -p "$rc_dest/modules/$rc_id" && put_tail "$SG_DATA_ROOT/modules/$rc_id/module.log" "$rc_dest/modules/$rc_id/module.log" 16
    done
    put_tail "$spool/events.log" "$rc_dest/events.log" 16
    date +%s > "$state/last-flush"
}

# Keep the newest $KEEP_BOOTS boot folders of this car on the stick.
prune() {
    rc_n=0
    set +f
    for rc_b in "$1"/boot-*; do [ -d "$rc_b" ] && rc_n=$((rc_n + 1)); done
    for rc_b in "$1"/boot-*; do
        [ -d "$rc_b" ] || continue
        [ "$rc_n" -gt "$KEEP_BOOTS" ] || break
        case "${rc_b##*/}" in boot-[0-9][0-9][0-9][0-9]) ;; *) continue;; esac
        [ "$rc_b" != "$2" ] || continue
        sg_path_safe "$rc_b" && rm -rf "$rc_b" && rc_n=$((rc_n - 1))
    done
}

age_over() {
    rc_t=$(cat "$1" 2>/dev/null)
    case "$rc_t" in ''|*[!0-9]*) return 0;; esac
    rc_now=$(date +%s)
    [ "$rc_now" -ge "$rc_t" ] || return 0
    [ $((rc_now - rc_t)) -ge "$2" ]
}

do_tick() {
    start_logcat
    rc_seq=$(next_boot_seq) || return 1
    rc_usb=$(usb_base) || {
        [ ! -f "$state/usb-present" ] || { rm -f "$state/usb-present"; event "usb removed"; }
        return 0
    }
    if [ ! -f "$state/usb-present" ]; then
        sg_write "$rc_usb" > "$state/usb-present"
        event "usb mounted"
        rm -f "$state/last-snapshot" "$state/last-flush"
    fi
    prepare_usb "$rc_usb" || { event "usb not writable"; return 1; }
    rc_product=$(getprop ro.build.product)
    case "$rc_product" in ''|*[!a-zA-Z0-9._-]*) rc_product=unknown-car;; esac
    rc_car=$rc_usb/$FOLDER/$rc_product
    rc_id=$(cat "$state/boot" 2>/dev/null)
    # A folder of an earlier recording of another boot (same numbers on a reused stick) is never mixed in.
    while :; do
        rc_dest=$rc_car/boot-$(seq_text "$rc_seq")
        [ -d "$rc_dest" ] || break
        [ "$(cat "$rc_dest/.boot" 2>/dev/null)" != "$rc_id" ] || break
        rc_seq=$((rc_seq + 1))
        sg_write "$rc_seq" > "$state/seq.tmp.$$" && mv "$state/seq.tmp.$$" "$state/seq" || return 1
    done
    mkdir -p "$rc_dest" || return 1
    [ -f "$rc_dest/.boot" ] || sg_write "$rc_id" > "$rc_dest/.boot" || return 1
    rc_wrote=0
    if age_over "$state/last-snapshot" "$SNAPSHOT_SECONDS"; then snapshot "$rc_dest"; rc_wrote=1; fi
    if age_over "$state/last-flush" "$FLUSH_SECONDS"; then flush "$rc_dest"; rc_wrote=1; prune "$rc_car" "$rc_dest"; fi
    [ "$rc_wrote" -eq 0 ] || sync
    return 0
}

take_lock() {
    mkdir -p "$state" || return 1
    chmod 700 "$state" || return 1
    if ! mkdir "$state/lock" 2>/dev/null; then
        rc_holder=$(cat "$state/lock/pid" 2>/dev/null)
        case "$rc_holder" in ''|*[!0-9]*) ;; *)
            ! kill -0 "$rc_holder" 2>/dev/null || return 1 ;;
        esac
        rm -rf "$state/lock"
        mkdir "$state/lock" 2>/dev/null || return 1
    fi
    sg_write "$$" > "$state/lock/pid"
}

enabled() { [ -f "$state/enabled" ]; }

case "$group:$verb" in
    recorder:status)
        if enabled; then
            if rc_usb=$(usb_base); then
                sg_print state=on restart=none "info.usb=present" "info.usb_path=$rc_usb/$FOLDER" "detail=Recording to the USB drive"
            else
                sg_print state=on restart=none "info.usb=absent" "detail=Recording is on; waiting for a USB drive"
            fi
        else
            if usb_base > /dev/null; then sg_print state=off restart=none "info.usb=present" "detail=Recording is off"
            else sg_print state=off restart=none "info.usb=absent" "detail=Recording is off"; fi
        fi
        exit 0 ;;
    recorder:enable)
        rc_usb=$(usb_base) || { reply failed 'No USB drive is mounted. Insert a USB drive first.'; exit 1; }
        take_lock || { reply failed 'The recorder is busy; try again'; exit 1; }
        trap 'rm -rf "$state/lock"' 0 1 2 3 15
        prepare_usb "$rc_usb" || { reply failed 'Could not write to the USB drive'; exit 1; }
        mkdir -p "$spool" || { reply failed 'Could not prepare the recorder'; exit 1; }
        sg_write "$(date +%s)" > "$state/enabled.tmp.$$" && mv "$state/enabled.tmp.$$" "$state/enabled" ||
            { reply failed 'Could not turn recording on'; exit 1; }
        event "recording turned on"
        do_tick || { enabled && rm -f "$state/enabled"; stop_logcat; reply failed 'Could not write the first report to the USB drive'; exit 1; }
        sg_print state=on restart=none "info.usb=present" "info.usb_path=$rc_usb/$FOLDER" "detail=Recording to $FOLDER on the USB drive"
        exit 0 ;;
    recorder:disable)
        take_lock || { reply failed 'The recorder is busy; try again'; exit 1; }
        trap 'rm -rf "$state/lock"' 0 1 2 3 15
        event "recording turned off"
        rm -f "$state/enabled"
        stop_logcat
        rm -rf "$spool" "$state/last-snapshot" "$state/last-flush" "$state/usb-present"
        reply off 'Recording is off. Reports already on the USB drive were kept.'
        exit 0 ;;
    recorder:tick)
        enabled || exit 0
        take_lock || exit 0
        trap 'rm -rf "$state/lock"' 0 1 2 3 15
        do_tick || :
        exit 0 ;;
    preflight:run)
        rc_out=$(hmg_seogaemo_preflight 2>&1)
        rc_worst=ok
        rc_n=0
        while IFS='|' read -r rc_id rc_level rc_evidence rc_message; do
            [ -n "$rc_id" ] || continue
            rc_n=$((rc_n + 1))
            case "$rc_level" in
                block) rc_worst=block ;;
                warn) [ "$rc_worst" = block ] || rc_worst=warn ;;
                unknown) [ "$rc_worst" != ok ] || rc_worst=unknown ;;
            esac
            case "$rc_n" in ?) rc_key=0$rc_n;; *) rc_key=$rc_n;; esac
            sg_print "info.check_$rc_key=$rc_id|$rc_level|$rc_evidence|$rc_message"
        done <<EOF
$rc_out
EOF
        sg_print "info.verdict=$rc_worst"
        sg_print state=on restart=none "detail=$rc_n checks, worst result: $rc_worst"
        exit 0 ;;
    *) reply failed 'Invalid command'; exit 2 ;;
esac
