#!/system/bin/sh
umask 077
# NEMESIS stages the new package before invoking the old hook on update.
caller=$(cat "/proc/$PPID/cmdline" 2>/dev/null)
case "$caller" in
    *uninstall*) ;;
    *install*)
        if grep -Fxq 'id=hmg-seogaemo' /data/local/tmp/nemesis/stage/hmg-seogaemo/NEMESIS.data 2>/dev/null; then
            echo 'HMG-Seogaemo update: retaining APK and app data'
            exit 0
        fi ;;
esac
base=${0%/*}
base=${base%/scripts}
state=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
reverse=
for id in $(cat "$base/modules.list"); do reverse="$id $reverse"; done
# Preflight every snapshot before changing any module. A changed firmware or
# another owner's edits must abort the uninstall with all backups intact.
for id in $reverse; do
    active=$(cat "$state/desired/$id" 2>/dev/null)
    baseline=0
    for item in "$state/modules/$id"/prop.*/ready "$state/modules/$id"/file.*/ready "$state/modules/$id"/line.*/ready; do
        [ -f "$item" ] && baseline=1
    done
    [ "$active" = on ] || [ "$baseline" -eq 1 ] || continue
    if [ "$baseline" -eq 1 ]; then
        HMG_SEOGAEMO_STATE=$state/modules/$id
        export HMG_SEOGAEMO_STATE
        . "$base/lib/common.sh"
        sg_restore_preflight || {
            echo "HMG-Seogaemo uninstall blocked: $id baseline conflict; backups retained" >&2
            exit 1
        }
    fi
done
for id in $reverse; do
    active=$(cat "$state/desired/$id" 2>/dev/null)
    baseline=0
    for item in "$state/modules/$id"/prop.*/ready "$state/modules/$id"/file.*/ready "$state/modules/$id"/line.*/ready; do
        [ -f "$item" ] && baseline=1
    done
    [ "$active" = on ] || [ "$baseline" -eq 1 ] || continue
    sh "$base/bin/hmg-seogaemo-root" "$id" revert || {
        echo "HMG-Seogaemo uninstall blocked: $id revert failed; backups retained" >&2
        exit 1
    }
    status=$(sh "$base/bin/hmg-seogaemo-root" "$id" status) || exit 1
    case "$status" in
        *'state=off'*) ;;
        *'state=pending-restart'*'info.desired=off'*)
            grep -Fxq 'restore=reboot' "$base/modules/$id.properties" || exit 1 ;;
        *)
        echo "HMG-Seogaemo uninstall blocked: $id did not verify off" >&2; exit 1 ;;
    esac
    [ "$(cat "$state/desired/$id" 2>/dev/null)" = off ] || exit 1
    for item in "$state/modules/$id"/prop.*/ready "$state/modules/$id"/file.*/ready "$state/modules/$id"/line.*/ready; do
        [ ! -f "$item" ] || {
            echo "HMG-Seogaemo uninstall blocked: $id baseline remains" >&2; exit 1;
        }
    done
done
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in
    package:*)
        result=$(pm uninstall com.seogaemo.hmg 2>&1) || { echo "$result" >&2; exit 1; }
        case "$result" in *Success*) ;; *) echo "$result" >&2; exit 1 ;; esac
        case "$(pm path com.seogaemo.hmg 2>/dev/null)" in package:*) echo 'APK remains installed' >&2; exit 1 ;; esac ;;
esac
rm -rf "$state/desired" "$state/modules" "$state/journal" "$state/reconcile.log"
