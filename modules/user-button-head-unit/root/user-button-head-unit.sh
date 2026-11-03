#!/bin/sh
set -eu
. "$HMG_SEOGAEMO_BASE/lib/common.sh"
KEY=persist.sys.key.custom
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

ub_status() {
    ub_gate || return $?
    if [ -f "$HMG_SEOGAEMO_STATE/prop.$KEY/ready" ]; then
        if sg_applied "$KEY"; then
            sg_status on none 'User button opens HMG-Seogaemo'
        else
            sg_status conflict none 'User button changed outside HMG-Seogaemo; backup retained'
        fi
    elif [ "$(getprop "$KEY")" = "$ACTION" ]; then
        sg_status conflict none 'Shortcut assignment has no restore baseline'
    else
        sg_status off none 'User button shortcut is off'
    fi
}

ub_apply() {
    ub_status || return $?
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

[ "$#" -eq 1 ] || exit 2
case "$1" in
    status) ub_status ;;
    precheck) sg_print 'warn.assignment=1'; ub_status ;;
    apply) ub_apply ;;
    revert) ub_revert ;;
    *) exit 2 ;;
esac
