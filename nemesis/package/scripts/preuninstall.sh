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
# One implementation for app restore and NEMESIS removal, including OTA payloads.
SG_DATA_ROOT=$state
export SG_DATA_ROOT
/system/bin/sh "$base/core/stock.sh" restore || {
    echo 'HMG-Seogaemo uninstall blocked: restore failed; backups retained' >&2
    exit 1
}
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in
    package:*)
        result=$(pm uninstall com.seogaemo.hmg 2>&1) || { echo "$result" >&2; exit 1; }
        case "$result" in *Success*) ;; *) echo "$result" >&2; exit 1 ;; esac
        case "$(pm path com.seogaemo.hmg 2>/dev/null)" in package:*) echo 'APK remains installed' >&2; exit 1 ;; esac ;;
esac
rm -rf "$state/app-install" || exit 1
# The stock restore receipt and its pause marker belong to this installation: a
# later install must not inherit a paused watcher. uninstall/ (result, exit code, log)
# and the ledger are kept.
# USB report recording: stop its log stream and drop its state; reports already on a USB drive stay.
if [ -f "$state/recorder/logcat.pid" ]; then
    read -r recorder_pid < "$state/recorder/logcat.pid" || :
    case "$recorder_pid" in ''|*[!0-9]*) ;; *)
        ! grep -q logcat "/proc/$recorder_pid/cmdline" 2>/dev/null || kill "$recorder_pid" 2>/dev/null || : ;;
    esac
fi
rm -rf "$state/recorder"
rm -rf "$state/desired" "$state/modules" "$state/journal" "$state/reconcile.log" \
    "$state/stock" "$state/restart-cleanup"
