#!/bin/sh
set -eu
. "$HMG_SEOGAEMO_BASE/lib/common.sh"
KEY=persist.sys.key.swrccustom
ACTION=com.seogaemo.hmg.USER_BUTTON_SHORTCUT

ub_gate() {
    [ "$(getprop ro.build.version.sdk)" = 19 ] || { sg_status incompatible none 'Requires Android 4.4'; return $?; }
    case "$(getprop ro.build.product)" in
        NEEV.KOR.*.260902) ;;
        *) sg_status incompatible none 'Requires NEEV KOR 260902'; return $? ;;
    esac
    for owner in hmgapi leafia; do
        if sg_owner_present "$owner"; then
            sg_status conflict none 'Another package owns user button hooks'; return $?
        fi
    done
}

ub_desired_on() { [ "$(cat "$SG_DATA_ROOT/desired/user-button-shortcut" 2>/dev/null)" = on ]; }
ub_baseline() { [ -f "$HMG_SEOGAEMO_STATE/prop.$KEY/ready" ]; }

ub_status() {
    ub_gate || return $?
    if ub_baseline; then
        sg_check_build || { sg_status conflict none 'Firmware changed; backup retained'; return $?; }
        if sg_applied "$KEY"; then
            sg_status on none 'User button opens HMG-Seogaemo'
        elif ub_desired_on; then
            sg_print 'info.desired=on'
            sg_status pending-restart head_unit 'Button reassignment pending; watcher will restore shortcut'
        else
            sg_status off none 'User button shortcut is off; restore baseline retained'
        fi
    elif [ "$(getprop "$KEY")" = "$ACTION" ]; then
        sg_status conflict none 'Shortcut assignment has no restore baseline'
    else
        sg_status off none 'User button shortcut is off'
    fi
}

ub_apply() {
    ub_gate || return $?
    if ub_baseline; then
        sg_check_build || { sg_status conflict none 'Firmware changed; backup retained'; return $?; }
    elif [ "$(getprop "$KEY")" = "$ACTION" ]; then
        sg_status conflict none 'Shortcut assignment has no restore baseline'; return $?
    fi
    if sg_prop_set "$KEY" "$ACTION"; then
        ub_status
    else
        code=$?
        if [ "$code" -eq 3 ]; then
            sg_status conflict none 'User button changed outside HMG-Seogaemo; backup retained'
        else
            sg_status failed none 'Could not assign user button; backup retained'
        fi
    fi
}

ub_revert() {
    if sg_revert_all; then
        sg_status off none 'Previous user button assignment restored'
    else
        code=$?
        if [ "$code" -eq 3 ]; then
            sg_status conflict none 'User button changed outside HMG-Seogaemo; backup retained'
        else
            sg_status failed none 'Could not restore user button; backup retained'
        fi
    fi
}

ub_watch() {
    case "$1" in boot|screen_on) ;; *) return 2;; esac
    ub_desired_on || return 3
    [ ! -f "$SG_DATA_ROOT/stock/paused" ] || return 3
    ub_gate || return $?
    ub_baseline || { sg_status failed none 'Missing original button baseline'; return $?; }
    sg_check_build || { sg_status conflict none 'Firmware changed; backup retained'; return $?; }
    if [ "$(getprop "$KEY")" != "$ACTION" ]; then
        sg_prop_set "$KEY" "$ACTION" || { sg_status failed none 'Could not reassign user button; backup retained'; return $?; }
    fi
    ub_status
}

[ "$#" -ge 1 ] || exit 2
case "$1" in
    status) [ "$#" -eq 1 ] || exit 2; ub_status ;;
    precheck) [ "$#" -eq 1 ] || exit 2; sg_print 'warn.assignment=1'; ub_status ;;
    apply) [ "$#" -eq 1 ] || exit 2; ub_apply ;;
    revert) [ "$#" -eq 1 ] || exit 2; ub_revert ;;
    watch) [ "$#" -eq 2 ] || exit 2; ub_watch "$2" ;;
    *) exit 2 ;;
esac
