#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/bin}
[ -L /data/nemesis/backup/hmg-seogaemo/update/current ] && base=/data/nemesis/backup/hmg-seogaemo/update/current
[ "$(getprop sys.boot_completed)" = 1 ] || { echo 'Android is not ready' >&2; exit 1; }
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in
    package:*) ;;
    *) /system/bin/sh "$base/bin/install-apk.sh" || exit 1 ;;
esac
am start -n com.seogaemo.hmg/.MainActivity >/dev/null 2>&1 || { echo 'Could not open Seogaemo' >&2; exit 1; }
