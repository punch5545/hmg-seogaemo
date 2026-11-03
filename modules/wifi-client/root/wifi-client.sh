#!/bin/sh
# Wi-Fi client feature exposure for the verified NEEV Korean firmware.
set -u
. "$SEOGAEMO_BASE/lib/common.sh"

cfg=/system/onebin/neev_kr/featurexml.cfg
xml=/system/etc/permissions/android.hardware.wifi.xml
stock='-/system/etc/permissions/android.hardware.wifi.xml'
enabled='#-/system/etc/permissions/android.hardware.wifi.xml'
marker=$SEOGAEMO_STATE/applied

result() { sg_status "$1" "$2" "$3"; exit $?; }
expected_xml() {
    cat <<'EOF'
<permissions>
    <feature name="android.hardware.wifi" />
</permissions>
EOF
}
count_line() { grep -Fxc -e "$1" "$cfg" 2>/dev/null || :; }
cfg_stock() { [ "$(count_line "$stock")" = 1 ] && [ "$(count_line "$enabled")" = 0 ]; }
cfg_enabled() { [ "$(count_line "$stock")" = 0 ] && [ "$(count_line "$enabled")" = 1 ]; }
xml_enabled() { [ -f "$xml" ] && expected_xml | cmp - "$xml" >/dev/null 2>&1; }
applied_intact() {
    [ -f "$SEOGAEMO_STATE/applied.cfg" ] &&
        [ -f "$SEOGAEMO_STATE/applied.xml" ] &&
        cmp "$SEOGAEMO_STATE/applied.cfg" "$cfg" >/dev/null 2>&1 &&
        cmp "$SEOGAEMO_STATE/applied.xml" "$xml" >/dev/null 2>&1
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

client_ip() {
    [ -e /sys/class/net/wlan0 ] || return 0
    wpa_cli -i wlan0 status 2>/dev/null | grep -Fxq 'wpa_state=COMPLETED' || return 0
    # An AP normally owns 192.168.0.1; never report that as a client address.
    ifconfig wlan0 2>/dev/null | awk '
        /inet addr:/ { for (i=1; i<=NF; i++) if ($i ~ /^addr:/) { sub(/^addr:/, "", $i); ip=$i } }
        /inet / && !/inet addr:/ { for (i=1; i<NF; i++) if ($i=="inet") ip=$(i+1) }
        END { if (ip ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ && ip != "0.0.0.0" && ip != "192.168.0.1") print ip }
    '
}
print_ip() { ip=$(client_ip); [ -z "$ip" ] || printf 'info.client_ip=%s\n' "$ip"; }

ap_active() {
    [ "$(getprop init.svc.hostapd 2>/dev/null)" = running ] && return 0
    ifconfig wlan0 2>/dev/null | grep -Eq '(^|[ :])192\.168\.0\.1([ /]|$)'
}

projection_desired() {
    # The sibling module status reflects the runtime-owned desired state.
    [ -x "$SEOGAEMO_BASE/bin/seogaemo-root" ] || return 1
    "$SEOGAEMO_BASE/bin/seogaemo-root" wireless-carplay-and-aa status 2>/dev/null |
        grep -Eq '^state=(on|pending-restart)$'
}

precheck() {
    if projection_desired || ap_active; then printf 'warn.carplay_active=1\n'; fi
    result off none ''
}

status() {
    print_ip
    if cfg_enabled && xml_enabled; then
        [ -f "$marker" ] || result conflict none 'Wi-Fi files changed outside this module'
        applied_intact || result conflict none 'Wi-Fi files differ from applied state'
        if pm_wifi; then result on none ''; fi
        result pending-restart head_unit 'Wi-Fi feature awaits head-unit restart'
    fi
    [ -f "$marker" ] && result conflict none 'Wi-Fi files differ from applied state'
    if cfg_stock; then
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

restore_files() {
    rc=0
    sg_file_restore "$cfg" || rc=1
    sg_file_restore "$xml" || rc=1
    return "$rc"
}

apply() {
    compatible
    if external_owner; then result conflict none 'Another package owns Wi-Fi feature files'; fi
    if [ -f "$marker" ]; then
        if cfg_enabled && xml_enabled && applied_intact; then status; fi
        result conflict none 'Applied Wi-Fi files changed externally'
    fi
    cfg_stock || result incompatible none 'Expected stock Wi-Fi removal line missing or duplicated'
    if [ -e "$xml" ] && ! xml_enabled; then result conflict none 'Existing Wi-Fi feature XML differs'; fi

    sg_file_baseline "$cfg" || result failed none 'Could not save feature config baseline'
    sg_file_baseline "$xml" || result failed none 'Could not save Wi-Fi XML baseline'
    sg_remount rw || result failed none 'Could not remount system writable'
    tmp=$cfg.sg-wifi-client.$$
    edited=$tmp.edited
    xtmp=$xml.sg-wifi-client.$$
    ok=1
    if sed 's|^-/system/etc/permissions/android\.hardware\.wifi\.xml$|#-/system/etc/permissions/android.hardware.wifi.xml|' "$cfg" > "$edited" &&
       cp -p "$cfg" "$tmp" && cat "$edited" > "$tmp" &&
       mv "$tmp" "$cfg" && sg_file_mark_applied "$cfg" &&
       expected_xml > "$xtmp" &&
       chmod 644 "$xtmp" && mv "$xtmp" "$xml" &&
       sg_file_mark_applied "$xml" &&
       cfg_enabled && xml_enabled; then ok=0; fi
    rm -f "$tmp" "$edited" "$xtmp"
    if [ "$ok" -ne 0 ]; then
        restore_files || :
        sg_remount ro || :
        result failed none 'Could not apply and verify Wi-Fi files'
    fi
    if ! ( cp -p "$cfg" "$SEOGAEMO_STATE/applied.cfg" &&
           cp -p "$xml" "$SEOGAEMO_STATE/applied.xml" &&
           : > "$marker" ); then
        sg_remount rw || :
        restore_files || :
        sg_remount ro || :
        result failed none 'Could not record applied state'
    fi
    if ! sg_remount ro; then result failed none 'Could not remount system read-only'; fi
    sg_log 'Wi-Fi client feature files applied; head-unit restart required'
    result pending-restart head_unit 'Wi-Fi feature awaits head-unit restart'
}

revert() {
    if [ ! -f "$marker" ]; then status; fi
    # The helper accepts either our marked bytes or the already restored
    # baseline, allowing a retry after a partial restore without masking drift.
    sg_remount rw || result failed none 'Could not remount system writable'
    if ! restore_files; then
        sg_remount ro || :
        result conflict none 'Wi-Fi files changed; baselines retained'
    fi
    if ! sg_remount ro; then result failed none 'Could not remount system read-only'; fi
    # sg_file_restore verifies exact bytes, original mode/owner, or absence.
    sg_baseline_clear || result failed none 'Could not clear restored baselines'
    rm -f "$marker" "$SEOGAEMO_STATE/applied.cfg" "$SEOGAEMO_STATE/applied.xml" ||
        result failed none 'Could not clear applied state'
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
