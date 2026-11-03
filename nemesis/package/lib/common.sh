#!/system/bin/sh
# Compile-time module helper contract. No system mutation is implemented here.
sg_log() { printf '%s\n' "$*" >&2; }
sg_prop_baseline() { sg_log 'not implemented'; return 1; }
sg_prop_set() { sg_log 'not implemented'; return 1; }
sg_prop_restore() { sg_log 'not implemented'; return 1; }
sg_file_baseline() { sg_log 'not implemented'; return 1; }
sg_file_restore() { sg_log 'not implemented'; return 1; }
sg_remount() { sg_log 'not implemented'; return 1; }
sg_baseline_clear() { sg_log 'not implemented'; return 1; }
sg_owner_present() { sg_log 'not implemented'; return 1; }
sg_status() {
    case "$1" in
        off|on|pending-restart|not-implemented) sg_code=0 ;;
        conflict) sg_code=3 ;;
        incompatible) sg_code=4 ;;
        failed) sg_code=1 ;;
        *) return 2 ;;
    esac
    case "$2" in none|adbd|head_unit) ;; *) return 2 ;; esac
    printf 'state=%s\nrestart=%s\ndetail=%s\n' "$1" "$2" "${3:-}"
    return "$sg_code"
}
