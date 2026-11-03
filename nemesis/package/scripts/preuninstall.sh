#!/system/bin/sh
umask 077
# NEMESIS stages the new package before invoking the old hook on update.
caller=$(cat "/proc/$PPID/cmdline" 2>/dev/null)
case "$caller" in
    *uninstall*) ;;
    *install*)
        if grep -Fxq 'id=seogaemo' /data/local/tmp/nemesis/stage/seogaemo/NEMESIS.data 2>/dev/null; then
            echo 'Seogaemo update: retaining APK and app data'
            exit 0
        fi ;;
esac
case "$(pm path com.seogaemo.app 2>/dev/null)" in package:*) ;; *) exit 0 ;; esac
result=$(pm uninstall com.seogaemo.app 2>&1) || { echo "$result" >&2; exit 1; }
case "$result" in *Success*) ;; *) echo "$result" >&2; exit 1 ;; esac
case "$(pm path com.seogaemo.app 2>/dev/null)" in package:*) echo 'APK remains installed' >&2; exit 1 ;; esac
