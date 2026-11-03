#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/bin}
apk=$base/app/hmg-seogaemo.apk
[ -f "$apk" ] || { echo 'HMG-Seogaemo APK missing' >&2; exit 1; }
old=$(getprop sys.boot_completed)
# PackageManager cannot read APKs under the 0700 OTA state tree; stage a readable copy.
staged=/data/local/tmp/hmg-seogaemo-install.$$.apk
cp "$apk" "$staged" && chmod 644 "$staged" || { rm -f "$staged"; echo 'Could not stage APK' >&2; exit 1; }
restored=0
restore() {
    if [ "$restored" -eq 0 ]; then
        if ! setprop sys.boot_completed "$old" || [ "$(getprop sys.boot_completed)" != "$old" ]; then
            echo 'Could not restore sys.boot_completed' >&2
            return 1
        fi
        restored=1
    fi
}
trap 'rm -f "$staged"; restore || exit 1' 0
trap 'exit 1' 1 2 3 15
setprop sys.boot_completed 0 || exit 1
[ "$(getprop sys.boot_completed)" = 0 ] || { echo 'Could not set sys.boot_completed' >&2; exit 1; }
result=$(pm install -r "$staged" 2>&1) || { echo "$result" >&2; exit 1; }
restore || exit 1
case "$result" in *Success*) ;; *) echo "$result" >&2; exit 1 ;; esac
case "$(pm path com.seogaemo.hmg 2>/dev/null)" in package:*) ;; *) echo 'APK install did not register' >&2; exit 1 ;; esac
