#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/bin}
current=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}/update/current
if [ -L "$current" ] && [ -f "$current/bin/open.sh" ] && [ -r "$current/bin/open.sh" ] &&
        [ "$(readlink -f "$base")" != "$(readlink -f "$current")" ]; then
    exec /system/bin/sh "$current/bin/open.sh" "$@"
fi
[ "$(getprop sys.boot_completed)" = 1 ] || { echo 'Android is not ready' >&2; exit 1; }
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in
    package:*) ;;
    *) /system/bin/sh "$base/bin/install-apk.sh" || exit 1 ;;
esac
am start -n com.seogaemo.hmg/.MainActivity >/dev/null 2>&1 || { echo 'Could not open HMG-Seogaemo' >&2; exit 1; }
