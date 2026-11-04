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
rm -rf "$state/desired" "$state/modules" "$state/journal" "$state/reconcile.log"
