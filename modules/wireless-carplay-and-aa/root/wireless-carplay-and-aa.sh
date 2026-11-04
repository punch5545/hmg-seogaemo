#!/bin/sh
# Modified by HMG-Seogaemo: single-car port of HMG-API wireless projection.
set -eu
. "$HMG_SEOGAEMO_BASE/lib/common.sh"
umask 077

FEATURE=/system/onebin/neev_kr/featurexml.cfg
ASSET=/system/etc/hmg-seogaemo/wireless-carplay-and-aa

path() { sg_print "${SG_WIRELESS_TEST_ROOT:-}$1"; }
exists() { [ -f "$(path "$1")" ]; }
value() { getprop "$1"; }
fail() { sg_status failed none "$1"; }
conflict() { sg_status conflict none "$1"; }
incompatible() { sg_status incompatible none "$1"; }

compatibility() {
    map_state=$(/system/bin/sh "$HMG_SEOGAEMO_BASE/bin/hmg-seogaemo-root" map status 2>/dev/null) || map_state= # android44-shell: allow -- fixed dispatcher beneath the runtime-supplied package root
    sg_print "$map_state" | grep -Fxq 'state=on' ||
        { incompatible 'verified car map required'; return 4; }
    [ "$(value ro.mobis.onebindir)" = neev_kr ] || { incompatible 'unsupported onebin'; return 4; }
    [ "$(value ro.build.version.release)" = 4.4.2 ] || { incompatible 'requires Android 4.4.2'; return 4; }
    for owner in hmgapi leafia nemesis-adb-tcp; do
        if sg_owner_present "$owner"; then conflict "external owner: $owner"; return 3; fi
    done
    if exists /system/bin/app_process.hmgapi-stock; then conflict 'HMG-API app_process backup present'; return 3; fi
    if exists /system/bin/app_process.hmg-seogaemo-stock && ! sg_applied /system/bin/app_process &&
       [ ! -f "$HMG_SEOGAEMO_STATE/zygote-upgrade/ready" ]; then
        conflict 'orphaned app_process backup'; return 3
    fi
    exists "$FEATURE" || { incompatible 'featurexml.cfg absent'; return 4; }
    for name in com.hkmc.software.connectivity.carplay_wireless com.hkmc.software.connectivity.androidauto_wireless com.hkmc.software.wifihotspot; do
        target=/system/etc/permissions/$name.xml
        if exists "$target" && ! grep -Fq "<feature name=\"$name\"" "$(path "$target")"; then
            incompatible "feature XML content mismatch: $name"; return 4
        fi
        old="-/system/etc/permissions/$name.xml"
        active=$(grep -Fxc -e "$old" "$(path "$FEATURE")" || :)
        inactive=$(grep -Fxc -e "#$old" "$(path "$FEATURE")" || :)
        [ "$((active + inactive))" -le 1 ] || { incompatible "featurexml line mismatch: $name"; return 4; }
    done
    exists /system/bin/app_process || { incompatible 'app_process absent'; return 4; }
    for name in app_process libhmg_seogaemo_wireless_hook.so hmg-seogaemo-wireless-hook.jar libleafiaaac.so; do
        [ -s "$HMG_SEOGAEMO_MODULE_DIR/data/$name" ] || { fail "missing payload: $name"; return 1; }
    done
    case "$(value persist.service.adb.tcp.enable)" in
        1) conflict 'stock ADB switch conflicts with hotspot'; return 3 ;;
    esac
}

put_assets() {
    sg_file_put /system/lib/libleafiaaac.so data/libleafiaaac.so 644 0:0 || return 1
    for name in aa-tbt-48ee793a aa-tbt-a0bea1c4 carplay-core-wifi carplay-audio-formats carplay-utils-converter; do
        sg_file_put "$ASSET/recipes/$name.recipe" "recipes/$name.recipe" 644 0:0 || return 1
    done
    for name in com.hkmc.software.connectivity.carplay_wireless com.hkmc.software.connectivity.androidauto_wireless com.hkmc.software.wifihotspot; do
        target=/system/etc/permissions/$name.xml
        if ! exists "$target"; then sg_file_put "$target" "feature/$name.xml" 644 0:0 || return 1; fi
        old="-/system/etc/permissions/$name.xml"
        new="#$old"
        if grep -Fqx -e "$old" "$(path "$FEATURE")"; then
            sg_file_replace_line "$FEATURE" "$old" "$new" || return 1
        elif grep -Fqx -e "$new" "$(path "$FEATURE")"; then
            :
        fi
    done
}

assets_readable() {
    for recipe in aa-tbt-48ee793a aa-tbt-a0bea1c4 carplay-core-wifi carplay-audio-formats carplay-utils-converter; do
        sg_public_readable "$(path "$ASSET/recipes/$recipe.recipe")" || return 1
    done
    sg_public_readable "$(path /system/lib/libleafiaaac.so)"
}

repair_assets() {
    if ! assets_readable; then
        # DiPO registers once: retain restart intent even if repair is interrupted.
        sg_restart_required || return 1
    fi
    for recipe in aa-tbt-48ee793a aa-tbt-a0bea1c4 carplay-core-wifi carplay-audio-formats carplay-utils-converter; do
        sg_payload_dirs "$ASSET/recipes/$recipe.recipe" || return 1
    done
    put_assets || return 1
}

apply() {
    compatibility || return $?
    sg_projection_runtime prepare || { fail 'private runtime directory unavailable'; return 1; }
    if sg_applied /system/bin/app_process || [ -f "$HMG_SEOGAEMO_STATE/zygote-upgrade/ready" ]; then
        sg_dex_cache_track /system/framework/hmg-seogaemo-wireless-hook.jar com.daudio.app.dipo || return 1
        sg_zygote_install data/app_process data/libhmg_seogaemo_wireless_hook.so data/hmg-seogaemo-wireless-hook.jar || {
            result=$?
            if [ "$result" -eq 3 ]; then conflict 'payload upgrade blocked by drift; inspect change ledger'; return 3; fi
            fail 'payload upgrade incomplete; retry selected build or restore stock'; return 1
        }
        repair_assets || { fail 'asset repair failed; inspect change ledger'; return 1; }
        sg_prop_set persist.ium.support carplay &&
            sg_prop_set persist.sys.iap2.wifi t &&
            sg_prop_set persist.iap2.iden.wireless t || { fail 'property reapply failed'; return 1; }
        status applying; return $?
    fi
    if ! put_assets ||
       ! sg_prop_set persist.ium.support carplay ||
       ! sg_prop_set persist.sys.iap2.wifi t ||
       ! sg_prop_set persist.iap2.iden.wireless t ||
       ! sg_dex_cache_track /system/framework/hmg-seogaemo-wireless-hook.jar com.daudio.app.dipo ||
       ! sg_zygote_install data/app_process data/libhmg_seogaemo_wireless_hook.so data/hmg-seogaemo-wireless-hook.jar; then
        sg_revert_all || sg_log 'rollback failed; ledger and baselines retained'
        if ! sg_restore_material; then sg_projection_runtime clear || return 1; fi
        fail 'apply failed; inspect change ledger'; return 1
    fi
    sg_status pending-restart head_unit 'restart unit to load projection features and hooks'
}

revert() {
    if ! sg_applied /system/bin/app_process && [ ! -f "$HMG_SEOGAEMO_STATE/zygote-upgrade/ready" ]; then
        # An interrupted apply can have feature/property changes without the wrapper.
        sg_revert_all || { conflict 'partial apply restore blocked; ledger retained'; return 3; }
        sg_projection_runtime clear || { fail 'runtime diagnostics cleanup failed'; return 1; }
        sg_status off none 'disabled'; return 0
    fi
    sg_revert_all || { conflict 'restore blocked by drift; ledger retained'; return 3; }
    sg_projection_runtime clear || { fail 'runtime diagnostics cleanup failed'; return 1; }
    sg_print info.desired=off
    sg_status pending-restart head_unit 'restart unit to finish restoration'
}

status() {
    if sg_restore_material; then sg_print info.restore_material=present
    else sg_print info.restore_material=absent; fi
    if ! command -v sg_applied >/dev/null 2>&1; then
        sg_status not-implemented none 'not implemented'; return 0
    fi
    if [ -f "$HMG_SEOGAEMO_STATE/zygote-upgrade/ready" ]; then
        fail 'payload upgrade incomplete; retry selected build or restore stock'; return 1
    fi
    if ! sg_applied /system/bin/app_process; then sg_status off none 'disabled'; return 0; fi
    if [ "$(value persist.service.adb.tcp.enable)" = 1 ]; then conflict 'stock ADB switch conflicts with hotspot'; return 3; fi
    if [ "$(value persist.ium.support)" != carplay ] ||
       [ "$(value persist.sys.iap2.wifi)" != t ] ||
       [ "$(value persist.iap2.iden.wireless)" != t ]; then fail 'projection property drift'; return 1; fi
    assets_readable || { fail 'projection assets unreadable; reapply and restart required'; return 1; }
    if sg_restart_pending; then
        runtime_result=$(sg_projection_runtime status) || { sg_print "$runtime_result"; return 1; }
        sg_status pending-restart head_unit 'restart unit to register repaired native recipes'; return 0
    fi
    sg_projection_runtime "${1:-status}"
}

[ "$#" -eq 1 ] || exit 2
if ! command -v sg_applied >/dev/null 2>&1; then
    case "$1" in
        status|precheck) sg_status not-implemented none 'not implemented' ;;
        apply|revert) sg_status failed none 'package mutation API unavailable' ;;
        *) exit 2 ;;
    esac
    exit $?
fi
case "$1" in
    precheck) if sg_applied /system/bin/app_process; then status; else compatibility && sg_status off none 'compatible'; fi ;;
    apply) apply ;;
    revert) revert ;;
    status) status ;;
    *) exit 2 ;;
esac
