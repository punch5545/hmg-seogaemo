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
# NEMESIS ignores ZIP modes. Shell callers use /system/bin/sh; also permit
# Manager Launch implementations that execute kind=sh directly.
chmod 755 "$base/bin/hmg-seogaemo-root" "$base/bin/open.sh" "$base/bin/install-apk.sh" || exit 1
# The packaged APK must use the daemon path. A SUID invocation can inherit
# the app process's limited capability bounding set and fail remount/restore.
cp /system/xbin/nemesis "$base/bin/nemesis-client.tmp.$$" || exit 1
chmod 755 "$base/bin/nemesis-client.tmp.$$" || exit 1
mv "$base/bin/nemesis-client.tmp.$$" "$base/bin/nemesis-client" || exit 1
if [ "$(getprop sys.boot_completed)" = 1 ]; then
    /system/bin/sh "$base/bin/install-apk.sh" || exit 1
fi
# USB repair selects this newly installed tree; retain version dirs and baselines.
update=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}/update
rm -f "$update/current" "$update/current-path" "$update/previous-path" \
    "$update/previous-version" "$update/journal" || exit 1
[ "$(getprop sys.boot_completed)" = 1 ] || echo 'Android is down; Manager Launch can install the APK when it is up'
