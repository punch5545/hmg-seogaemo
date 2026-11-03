#!/bin/sh
# Wi-Fi client feature exposure for the verified NEEV Korean firmware.
set -u
. "$HMG_SEOGAEMO_BASE/lib/common.sh"

cfg=/system/onebin/neev_kr/featurexml.cfg
xml=/system/etc/permissions/android.hardware.wifi.xml
stock='-/system/etc/permissions/android.hardware.wifi.xml'
enabled='#-/system/etc/permissions/android.hardware.wifi.xml'

result() { sg_status "$1" "$2" "$3"; exit $?; }
count_line() { grep -Fxc -e "$1" "$cfg" 2>/dev/null || :; }
cfg_stock() { [ "$(count_line "$stock")" = 1 ] && [ "$(count_line "$enabled")" = 0 ]; }
cfg_enabled() { [ "$(count_line "$stock")" = 0 ] && [ "$(count_line "$enabled")" = 1 ]; }
xml_enabled() {
    [ -f "$xml" ] &&
        cmp "$HMG_SEOGAEMO_MODULE_DIR/android.hardware.wifi.xml" "$xml" >/dev/null 2>&1
}
pm_wifi() { pm list features 2>/dev/null | grep -Fxq 'feature:android.hardware.wifi'; }

chip_seen() {
    for path in /sys/bus/sdio/devices/* /sys/class/net/wlan*; do
        [ -e "$path" ] && return 0
    done
    return 1
}

external_owner() {
    sg_owner_present hmgapi || sg_owner_present leafia ||
        [ -f /data/nemesis/apps/wireless.projection/NEMESIS.data ]
}

parse_client_ip() {
    mode=$1
    set -f
    line=
    while IFS= read -r line || [ -n "$line" ]; do
        set -- $line
        ip=
        if [ "$mode" = netcfg ]; then
            [ "$#" -gt 0 ] || continue
            [ "$1" = wlan0 ] || continue
            shift
            for field do
                case "$field" in */*) ip=${field%%/*}; break ;; esac
            done
        else
            while [ "$#" -gt 1 ]; do
                if [ "$1" = inet ]; then
                    shift
                    ip=${1#addr:}
                    break
                fi
                shift
            done
        fi
        # An AP normally owns 192.168.0.1; never report that as a client IP.
        case "$ip" in
            ''|0.0.0.0|192.168.0.1|*[!0-9.]*|.*|*..*|*.) continue ;;
            *.*.*.*) sg_print "$ip"; break ;;
        esac
    done
}

client_ip() {
    [ -e /sys/class/net/wlan0 ] || return 0
    wpa_cli -i wlan0 status 2>/dev/null | grep -Fxq 'wpa_state=COMPLETED' || return 0
    ip=$(ifconfig wlan0 2>/dev/null | parse_client_ip ifconfig)
    [ -n "$ip" ] || ip=$(netcfg 2>/dev/null | parse_client_ip netcfg)
    [ -z "$ip" ] || sg_print "$ip"
}
print_ip() { ip=$(client_ip); [ -z "$ip" ] || sg_print "info.client_ip=$ip"; }

ap_active() {
    [ "$(getprop init.svc.hostapd 2>/dev/null)" = running ] && return 0
    ifconfig wlan0 2>/dev/null | grep -Eq '(^|[ :])192\.168\.0\.1([ /]|$)'
}

projection_desired() {
    # The sibling module status reflects the runtime-owned desired state.
    [ -x "$HMG_SEOGAEMO_BASE/bin/hmg-seogaemo-root" ] || return 1
    "$HMG_SEOGAEMO_BASE/bin/hmg-seogaemo-root" wireless-carplay-and-aa status 2>/dev/null | # android44-shell: allow -- fixed dispatcher beneath the runtime-supplied package root
        grep -Eq '^state=(on|pending-restart)$'
}

precheck() {
    if projection_desired || ap_active; then sg_print "warn.carplay_active=1"; fi
    result off none ''
}

status() {
    print_ip
    if sg_applied "$cfg" && sg_applied "$xml"; then
        if pm_wifi; then result on none ''; fi
        result pending-restart head_unit 'Wi-Fi feature awaits head-unit restart'
    fi
    if sg_applied "$cfg" || sg_applied "$xml"; then
        result conflict none 'Wi-Fi files are only partly applied'
    fi
    if cfg_enabled && xml_enabled; then
        result conflict none 'Wi-Fi files changed outside this module'
    fi
    if cfg_stock; then
        if [ -e "$xml" ] && ! xml_enabled; then
            result conflict none 'Existing Wi-Fi feature XML differs'
        fi
        if pm_wifi; then result pending-restart head_unit 'Wi-Fi feature awaits removal at restart'; fi
        result off none ''
    fi
    result conflict none 'Wi-Fi feature removal line is unexpected'
}

compatible() {
    case "$(getprop ro.build.product 2>/dev/null)" in
        NEEV.KOR.*.260902) ;;
        *) result incompatible none 'Unsupported firmware' ;;
    esac
    [ -f "$cfg" ] || result incompatible none 'Stock featurexml.cfg missing'
    if ! chip_seen; then
        # wlan0 may be absent until the Wi-Fi service starts; SDIO is enough.
        result incompatible none 'No SDIO or WLAN device detected'
    fi
}

apply() {
    compatible
    if external_owner; then result conflict none 'Another package owns Wi-Fi feature files'; fi
    if sg_applied "$cfg" && sg_applied "$xml"; then status; fi
    if sg_applied "$cfg" || sg_applied "$xml" || cfg_enabled; then
        result conflict none 'Wi-Fi feature files are partly applied or externally changed'
    fi
    cfg_stock || result incompatible none 'Expected stock Wi-Fi removal line missing or duplicated'
    if [ -e "$xml" ] && ! xml_enabled; then result conflict none 'Existing Wi-Fi feature XML differs'; fi
    sg_file_put "$xml" android.hardware.wifi.xml 0644 root:root ||
        result failed none 'Could not apply Wi-Fi feature XML'
    if ! sg_file_replace_line "$cfg" "$stock" "$enabled"; then
        sg_revert_all || :
        result failed none 'Could not apply Wi-Fi feature config'
    fi
    if ! sg_applied "$cfg" || ! sg_applied "$xml"; then
        result failed none 'Could not verify applied Wi-Fi files'
    fi
    sg_log 'Wi-Fi client feature files applied; head-unit restart required'
    result pending-restart head_unit 'Wi-Fi feature awaits head-unit restart'
}

revert() {
    if ! sg_revert_all; then
        result conflict none 'Wi-Fi files changed; baselines retained'
    fi
    if pm_wifi; then result pending-restart head_unit 'Wi-Fi feature awaits removal at restart'; fi
    result off none ''
}

[ "$#" -eq 1 ] || exit 2
case "$1" in
    status) status ;;
    precheck) precheck ;;
    apply) apply ;;
    revert) revert ;;
    *) exit 2 ;;
esac
