#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/scripts}
# NEMESIS treats a model match with another firmware as partial.
# ro.build.product looks like NEEV.KOR.0000.V039.001.260902 (same glob as descriptor os=).
case "$(getprop ro.build.product)" in
    NEEV.KOR.*.260902) ;;
    *) echo 'Unsupported firmware' >&2; exit 1 ;;
esac
if [ "$(getprop sys.boot_completed)" = 1 ]; then
    exec /system/bin/sh "$base/bin/install-apk.sh"
fi
echo 'Android is down; Manager Launch can install the APK when it is up'
