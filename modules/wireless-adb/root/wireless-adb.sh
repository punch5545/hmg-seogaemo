#!/bin/sh
set -efu
. "$SEOGAEMO_BASE/lib/common.sh"

STOCK_PROP=persist.service.adb.tcp.enable
PORT_PROP=persist.adb.tcp.port

wa_compatible() {
    [ "$(getprop ro.build.version.sdk)" = 19 ] || return 1
    case "$(getprop ro.build.product)" in NEEV.KOR.*.260902) return 0 ;; esac
    return 1
}

wa_owners_clear() {
    for owner in nemesis-adb-tcp hmgapi leafia; do
        if sg_owner_present "$owner"; then
            WA_CONFLICT="$owner owns ADB properties"
            return 1
        fi
    done
    return 0
}

# adbd checks the transient override before the persistent TCP port.
wa_effective_port() {
    value=$(getprop service.adb.tcp.port)
    if [ -z "$value" ]; then value=$(getprop "$PORT_PROP"); fi
    printf '%s' "$value"
}

wa_ipv4() {
    case "$1" in ''|*[!0-9.]*|.*|*.|*..*) return 1 ;; esac
    old_ifs=$IFS
    IFS=.
    set -- $1
    IFS=$old_ifs
    [ "$#" -eq 4 ] || return 1
    for octet do
        case "$octet" in ''|*[!0-9]*|????*) return 1 ;; esac
        [ "$octet" -le 255 ] || return 1
    done
}

wa_ifconfig_ip() {
    output=$(ifconfig "$1" 2>/dev/null) || return 1
    set -- $output
    next=0
    for token do
        if [ "$next" -eq 1 ]; then
            candidate=${token#addr:}
            candidate=${candidate%%/*}
            if wa_ipv4 "$candidate"; then printf '%s' "$candidate"; return 0; fi
            next=0
        fi
        case "$token" in
            inet) next=1 ;;
            addr:*)
                candidate=${token#addr:}
                candidate=${candidate%%/*}
                if wa_ipv4 "$candidate"; then printf '%s' "$candidate"; return 0; fi
                ;;
        esac
    done
    return 1
}

wa_netcfg_ip() {
    netcfg 2>/dev/null | while read -r name state address rest; do
        [ "$name" = "$1" ] || continue
        candidate=${address%%/*}
        if wa_ipv4 "$candidate"; then printf '%s' "$candidate"; return 0; fi
    done
}

wa_iface_ip() {
    wa_ifconfig_ip "$1" || wa_netcfg_ip "$1"
}

wa_old_eth_address() {
    address=$(wa_iface_ip eth0) || return 1
    [ "$address" = 192.168.0.123 ]
}

wa_facts() {
    # KitKat softap.conf is binary; omitting this key renders the UI fallback.
    wlan_ip=$(wa_iface_ip wlan0) || wlan_ip=
    if [ -n "$wlan_ip" ]; then
        if [ "$wlan_ip" = 192.168.0.1 ] || [ "$(getprop init.svc.hostapd)" = running ]; then
            printf 'info.hotspot_ip=%s\ninfo.adb_endpoint=%s:5555\n' "$wlan_ip" "$wlan_ip"
        else
            printf 'info.client_ip=%s\ninfo.client_adb_endpoint=%s:5555\n' "$wlan_ip" "$wlan_ip"
        fi
    fi
}

wa_report() {
    if block=$(sg_status "$1" "$2" "$3"); then code=0; else code=$?; fi
    printf '%s\n' "$block"
    wa_facts
    return "$code"
}

wa_observed() {
    [ "$(getprop "$STOCK_PROP")" = 0 ] && [ "$(getprop "$PORT_PROP")" = 5555 ]
}

wa_status() {
    if ! wa_owners_clear; then wa_report conflict none "$WA_CONFLICT"; return $?; fi
    if ! wa_compatible; then
        wa_report incompatible none 'Requires NEEV.KOR.260902 on Android 4.4'; return $?
    fi
    stock_applied=0
    port_applied=0
    if sg_applied "$STOCK_PROP"; then stock_applied=1; fi
    if sg_applied "$PORT_PROP"; then port_applied=1; fi
    if [ "$stock_applied" -eq 1 ] && [ "$port_applied" -eq 1 ]; then
        if ! wa_observed; then
            wa_report conflict none 'ADB properties changed outside Seogaemo'; return $?
        fi
        if wa_old_eth_address; then
            wa_report pending-restart head_unit 'Restart head unit to clear old eth0 address'
        else
            wa_report on adbd 'TCP ADB on port 5555; stock switch off'
        fi
    elif [ "$stock_applied" -eq 1 ] || [ "$port_applied" -eq 1 ]; then
        wa_report failed none 'ADB properties only partly applied'
    else
        wa_report off none 'Wireless ADB is off'
    fi
}

wa_apply_failed() {
    reason=$1
    current=$(wa_effective_port)
    if sg_revert_all; then
        restored=$(wa_effective_port)
        if [ "$current" != "$restored" ]; then
            sg_service_restart adbd || reason="$reason; adbd restore restart failed"
        fi
        wa_report failed none "$reason"
    else
        restore_code=$?
        if [ "$restore_code" -eq 3 ]; then
            wa_report conflict none 'ADB properties changed outside Seogaemo; baseline kept'
        else
            wa_report failed none "$reason; baseline restore failed"
        fi
    fi
}

wa_apply() {
    if ! wa_owners_clear; then wa_report conflict none "$WA_CONFLICT"; return $?; fi
    if ! wa_compatible; then
        wa_report incompatible none 'Requires NEEV.KOR.260902 on Android 4.4'; return $?
    fi
    override=$(getprop service.adb.tcp.port)
    if [ -n "$override" ] && [ "$override" != 5555 ]; then
        wa_report conflict none 'service.adb.tcp.port overrides port 5555'; return $?
    fi
    before=$(wa_effective_port)
    if ! sg_prop_set "$STOCK_PROP" 0 || ! sg_prop_set "$PORT_PROP" 5555 || ! wa_observed; then
        wa_apply_failed 'Could not apply ADB properties'; return $?
    fi
    after=$(wa_effective_port)
    if [ "$before" != "$after" ] && ! sg_service_restart adbd; then
        wa_apply_failed 'Could not restart adbd'; return $?
    fi
    sg_log 'Wireless ADB applied'
    wa_status
}

wa_revert() {
    if ! wa_owners_clear; then wa_report conflict none "$WA_CONFLICT"; return $?; fi
    before=$(wa_effective_port)
    if sg_revert_all; then :; else
        restore_code=$?
        if [ "$restore_code" -eq 3 ]; then
            wa_report conflict none 'ADB properties changed outside Seogaemo; baseline kept'
        else
            wa_report failed none 'Could not restore ADB property baseline'
        fi
        return $?
    fi
    after=$(wa_effective_port)
    if [ "$before" != "$after" ] && ! sg_service_restart adbd; then
        wa_report failed none 'Could not restart adbd after restore'; return $?
    fi
    sg_log 'Wireless ADB restored'
    wa_status
}

if [ "$#" -ne 1 ]; then
    echo 'usage: wireless-adb.sh apply|revert|status|precheck' >&2
    exit 2
fi
case "$1" in
    apply) wa_apply ;;
    revert) wa_revert ;;
    status) wa_status ;;
    precheck) wa_status ;;
    *) echo 'usage: wireless-adb.sh apply|revert|status|precheck' >&2; exit 2 ;;
esac
