#!/bin/sh
set -eu

repo=$(CDPATH= cd "$(dirname "$0")/../../.." && pwd)
car_shell=${HMG_SEOGAEMO_TEST_SHELL:-$(command -v mksh || command -v sh)}
export HMG_SEOGAEMO_TEST_COMMON="$repo/nemesis/package/lib/common.sh"
. "$HMG_SEOGAEMO_TEST_COMMON"
work=$(mktemp -d "${TMPDIR:-/tmp}/wifi-client-test.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
mkdir -p "$work/base/lib" "$work/bin" "$work/system/onebin/neev_kr" \
    "$work/base/bin" \
    "$work/system/wifi" \
    "$work/system/etc/permissions" "$work/sys/bus/sdio/devices" \
    "$work/sys/class/net" "$work/state"
cfg=$work/system/onebin/neev_kr/featurexml.cfg
xml=$work/system/etc/permissions/android.hardware.wifi.xml
# The car's stock featurexml.cfg has no Wi-Fi removal line; it must stay untouched.
sg_print "before" "#-/system/etc/permissions/com.hkmc.software.wifihotspot.xml" "after" > "$cfg"
cp "$cfg" "$work/original.cfg"
mkdir "$work/props"
# Supported stock capability remains present while SDIO/WLAN are powered off.
sg_print broadcom > "$work/vendor"
sg_print driver > "$work/system/wifi/bcmdhd.ko"
sg_print firmware > "$work/system/wifi/fw_bcmdhd.bin"
sg_print NEEV.KOR.0000.V039.001.260902 > "$work/product"
: > "$work/owners"
: > "$work/pm-feature"

cat > "$work/base/lib/common.sh" <<'EOF'
. "$HMG_SEOGAEMO_TEST_COMMON"
sg_log() { :; }
sg_snapshot() {
    name=${1##*/}
    if [ ! -e "$HMG_SEOGAEMO_STATE/$name.saved" ] && [ ! -e "$HMG_SEOGAEMO_STATE/$name.absent" ]; then
        if [ -e "$1" ]; then cp -p "$1" "$HMG_SEOGAEMO_STATE/$name.saved";
        else : > "$HMG_SEOGAEMO_STATE/$name.absent"; fi
    fi
}
sg_mark() {
    name=${1##*/}
    cp -p "$1" "$HMG_SEOGAEMO_STATE/$name.applied"
    sg_print "$name" >> "$TEST_WORK/marks"
}
sg_applied() {
    case "$1" in
        /*) name=${1##*/}
            [ -f "$HMG_SEOGAEMO_STATE/$name.applied" ] && [ -f "$1" ] &&
                cmp -s "$HMG_SEOGAEMO_STATE/$name.applied" "$1" ;;
        *) [ -f "$HMG_SEOGAEMO_STATE/prop.$1.applied" ] &&
            [ "$(getprop "$1")" = "$(cat "$HMG_SEOGAEMO_STATE/prop.$1.applied")" ] ;;
    esac
}
sg_prop_set() {
    [ "$1" = persist.wifi.state ] || return 2
    [ ! -e "$TEST_WORK/fail-prop" ] || return 1
    if [ ! -f "$HMG_SEOGAEMO_STATE/prop.$1.live" ]; then
        getprop "$1" > "$HMG_SEOGAEMO_STATE/prop.$1.live"
    fi
    setprop "$1" "$2" || return 1
    sg_print "$2" > "$HMG_SEOGAEMO_STATE/prop.$1.applied"
    sg_print "prop.$1" >> "$TEST_WORK/marks"
}
sg_file_put() {
    [ "$3" = 0644 ] && [ "$4" = root:root ] || return 1
    sg_snapshot "$1" || return 1
    cp -p "$HMG_SEOGAEMO_MODULE_DIR/$2" "$1" || return 1
    sg_mark "$1"
}
sg_file_replace_line() {
    [ "$(grep -Fxc -e "$2" "$1")" = 1 ] || return 1
    sg_snapshot "$1" || return 1
    while IFS= read -r line || [ -n "$line" ]; do
        [ "$line" != "$2" ] || line=$3
        sg_print "$line"
    done < "$1" > "$1.tmp" || return 1
    cat "$1.tmp" > "$1" || return 1
    rm "$1.tmp"
    sg_mark "$1"
}
sg_revert_all() {
    for path in "$TEST_WORK/system/onebin/neev_kr/featurexml.cfg" "$TEST_WORK/system/etc/permissions/android.hardware.wifi.xml"; do
        name=${path##*/}
        [ -f "$HMG_SEOGAEMO_STATE/$name.saved" ] || [ -f "$HMG_SEOGAEMO_STATE/$name.absent" ] || continue
        if [ -f "$HMG_SEOGAEMO_STATE/$name.saved" ] && [ -f "$path" ] && cmp -s "$path" "$HMG_SEOGAEMO_STATE/$name.saved"; then continue; fi
        if [ -f "$HMG_SEOGAEMO_STATE/$name.absent" ] && [ ! -e "$path" ]; then continue; fi
        sg_applied "$path" || return 1
    done
    for path in "$TEST_WORK/system/onebin/neev_kr/featurexml.cfg" "$TEST_WORK/system/etc/permissions/android.hardware.wifi.xml"; do
        name=${path##*/}
        if [ -f "$HMG_SEOGAEMO_STATE/$name.saved" ]; then cp -p "$HMG_SEOGAEMO_STATE/$name.saved" "$path";
        elif [ -f "$HMG_SEOGAEMO_STATE/$name.absent" ]; then rm -f "$path"; fi
    done
    prop=persist.wifi.state
    if [ -f "$HMG_SEOGAEMO_STATE/prop.$prop.live" ]; then
        current=$(getprop "$prop")
        [ "$current" = "$(cat "$HMG_SEOGAEMO_STATE/prop.$prop.live")" ] || sg_applied "$prop" || return 1
        setprop "$prop" "$(cat "$HMG_SEOGAEMO_STATE/prop.$prop.live")" || return 1
    fi
    [ ! -e "$TEST_WORK/fail-clear" ] || return 1
    rm -f "$HMG_SEOGAEMO_STATE"/*.saved "$HMG_SEOGAEMO_STATE"/*.absent "$HMG_SEOGAEMO_STATE"/*.applied "$HMG_SEOGAEMO_STATE"/*.live
}
sg_owner_present() { grep -Fxq "$1" "$TEST_WORK/owners"; }
sg_status() {
    sg_print "state=$1" "restart=$2" "detail=${3:-}"
    case "$1" in conflict) return 3;; incompatible) return 4;; failed) return 1;; esac
    return 0
}
EOF
cat > "$work/bin/getprop" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
case "$1" in ro.build.product) cat "$TEST_WORK/product";; init.svc.hostapd) cat "$TEST_WORK/hostapd" 2>/dev/null || :;;
    tcc.wifi.vendor) cat "$TEST_WORK/vendor";;
    persist.wifi.state) cat "$TEST_WORK/props/$1" 2>/dev/null || sg_print "";; esac
EOF
cat > "$work/bin/setprop" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
[ "$1" = persist.wifi.state ] || { echo "unexpected setprop $1" >&2; exit 1; }
sg_print "$2" > "$TEST_WORK/props/$1"
EOF
cat > "$work/bin/pm" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
[ "$*" = 'list features' ] && [ -s "$TEST_WORK/pm-feature" ] && sg_print "feature:android.hardware.wifi"
exit 0
EOF
cat > "$work/bin/ifconfig" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
cat "$TEST_WORK/ifconfig" 2>/dev/null || :
EOF
cat > "$work/bin/netcfg" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
cat "$TEST_WORK/netcfg" 2>/dev/null || :
EOF
cat > "$work/base/bin/hmg-seogaemo-root" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
[ "$1 $2" = 'wireless-carplay-and-aa status' ] || exit 2
if [ -f "$TEST_WORK/projection-on" ]; then sg_print "state=on";
else sg_print "state=off"; fi
EOF
chmod +x "$work/bin/"*
chmod 644 "$work/base/bin/hmg-seogaemo-root"

# Rewrite only fixed filesystem roots in a private copy; featurexml entries
# remain the actual absolute paths used on the car.
sed -e "s|/system/bin/sh|$car_shell|g" \
    -e "s|^xml=/system/|xml=$work/system/|" \
    -e "s|/system/wifi/|$work/system/wifi/|g" \
    -e "s|/sys/class/net/wlan0|$work/sys/class/net/wlan0|" \
    "$repo/modules/wifi-client/module.sh" > "$work/wifi-client.sh"

mkdir -p "$work/base/files"
cp "$repo/modules/wifi-client/files/android.hardware.wifi.xml" "$work/base/files/android.hardware.wifi.xml"
export TEST_WORK=$work HMG_SEOGAEMO_BASE=$work/base HMG_SEOGAEMO_STATE=$work/state HMG_SEOGAEMO_MODULE_DIR=$work/base
# Keep the test harness on the host PATH, but execute the module with only
# toolbox-like commands. Forbidden names are explicit failing stubs.
mkdir "$work/allowed"
for tool in cat cmp cp dd grep rm; do
    ln -s "$(command -v "$tool")" "$work/allowed/$tool"
done
ln -s "$repo/tools/testlib/fakebin/md5" "$work/allowed/md5"
ln -s "$(command -v python3)" "$work/allowed/python3"
for tool in getprop setprop pm ifconfig netcfg; do
    ln -s "$work/bin/$tool" "$work/allowed/$tool"
done
for tool in awk sed tail head cut tr wc sort uniq xargs find stat readlink sha256sum sha1sum tar unzip busybox toybox; do
    cat > "$work/allowed/$tool" <<'EOF'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
echo "forbidden tool called: ${0##*/}" >&2
exit 99
EOF
    chmod +x "$work/allowed/$tool"
done
run() { PATH=$work/allowed "$car_shell" "$work/wifi-client.sh" "$1" > "$work/output"; }
assert_state() { grep -Fxq "state=$1" "$work/output" || { cat "$work/output"; exit 1; }; }
assert_line() { grep -Fxq "$1" "$work/output" || { cat "$work/output"; exit 1; }; }

prop_value() { cat "$work/props/persist.wifi.state" 2>/dev/null || :; }

# No SDIO/WLAN devices exist: precheck and apply must still work with radio off.
run status; assert_state off
run precheck; assert_state off
run apply; assert_state pending-restart; assert_line restart=head_unit
[ "$(wc -l < "$work/marks")" -eq 2 ]
cmp -s "$work/original.cfg" "$cfg"
[ -f "$xml" ]
[ "$(prop_value)" = 3 ]
run apply; assert_state pending-restart
[ "$(wc -l < "$work/marks")" -eq 2 ]
sg_print "yes" > "$work/pm-feature"
run status; assert_state on
: > "$work/sys/class/net/wlan0"
sg_print "wlan0: inet addr:10.7.0.15 Bcast:10.7.0.255 Mask:255.255.255.0" > "$work/ifconfig"
run status; assert_line info.client_ip=10.7.0.15; assert_line info.client_adb_endpoint=10.7.0.15:5555
: > "$work/ifconfig"
sg_print "wlan0 UP 10.7.0.16/24 0x00001043" > "$work/netcfg"
run status; assert_line info.client_ip=10.7.0.16
sg_print "wlan0 UP 192.168.0.1/24 0x00001043" > "$work/netcfg"
run status
if grep -q '^info.client_ip=' "$work/output"; then exit 1; fi
sg_print "wlan0 UP 0.0.0.0/0 0x00001002" > "$work/netcfg"
run status
if grep -q '^info.client_ip=' "$work/output"; then exit 1; fi
: > "$work/netcfg"
run revert; assert_state pending-restart; assert_line info.desired=off
run status; assert_state pending-restart; assert_line info.desired=off
cmp -s "$work/original.cfg" "$cfg"
[ ! -e "$xml" ]
[ -z "$(prop_value)" ]
: > "$work/pm-feature"
run status; assert_state off

sg_print WRONG.KOR.260902 > "$work/product"
if run apply; then exit 1; fi
assert_state incompatible
if run precheck; then exit 1; fi
assert_state incompatible
sg_print NEEV.KOR.0000.V039.001.260902 > "$work/product"
rm "$work/sys/class/net/wlan0"
# Live enumeration alone is insufficient, including unrelated SDIO devices.
: > "$work/sys/bus/sdio/devices/chip"
: > "$work/sys/class/net/wlan0"
for file in bcmdhd.ko fw_bcmdhd.bin; do
    mv "$work/system/wifi/$file" "$work/$file.saved"
    for verb in precheck apply; do
        if run "$verb"; then exit 1; fi
        assert_state incompatible
    done
    : > "$work/system/wifi/$file"
    for verb in precheck apply; do
        if run "$verb"; then exit 1; fi
        assert_state incompatible
    done
    mv "$work/$file.saved" "$work/system/wifi/$file"
done
for vendor in '' realtek; do
    sg_print "$vendor" > "$work/vendor"
    for verb in precheck apply; do
        if run "$verb"; then exit 1; fi
        assert_state incompatible
    done
done
[ ! -e "$xml" ]
[ -z "$(prop_value)" ]
[ "$(wc -l < "$work/marks")" -eq 2 ]
sg_print broadcom > "$work/vendor"
rm "$work/sys/bus/sdio/devices/chip" "$work/sys/class/net/wlan0"
run precheck; assert_state off
sg_print "leafia" > "$work/owners"
if run apply; then exit 1; fi
assert_state conflict
if run precheck; then exit 1; fi
assert_state conflict
: > "$work/owners"
# HMG-API's bundled wireless mod does not own the client feature.
sg_print "hmgapi" > "$work/owners"
run precheck; assert_state off
: > "$work/owners"

# Unowned changes are refused by precheck, status and apply alike.
sg_print "3" > "$work/props/persist.wifi.state"
if run status; then exit 1; fi
assert_state conflict
if run precheck; then exit 1; fi
assert_state conflict
if run apply; then exit 1; fi
assert_state conflict
: > "$work/props/persist.wifi.state"
sg_print "<permissions/>" > "$xml"
if run status; then exit 1; fi
assert_state conflict
if run precheck; then exit 1; fi
assert_state conflict
if run apply; then exit 1; fi
assert_state conflict
rm "$xml"
[ ! -s "$work/marks.after" ] || exit 1

sg_print "running" > "$work/hostapd"
run precheck; assert_line warn.carplay_active=1
: > "$work/hostapd"
run precheck
if grep -q '^warn\.' "$work/output"; then exit 1; fi
: > "$work/projection-on"
run precheck; assert_line warn.carplay_active=1
rm "$work/projection-on"

# A failed property write rolls back the XML.
: > "$work/fail-prop"
if run apply; then exit 1; fi
assert_state failed
[ ! -e "$xml" ]
rm "$work/fail-prop"

run apply; assert_state pending-restart
sg_print "drift" >> "$xml"
if run revert; then exit 1; fi
assert_state conflict
[ -e "$work/state/android.hardware.wifi.xml.applied" ]

# A cleanup failure after exact restore must be retryable.
cp "$work/state/android.hardware.wifi.xml.applied" "$xml"
: > "$work/fail-clear"
if run revert; then exit 1; fi
assert_state conflict
[ ! -e "$xml" ]
[ -z "$(prop_value)" ]
rm "$work/fail-clear"
run revert; assert_state off
cmp -s "$work/original.cfg" "$cfg"

echo 'wifi-client module tests passed'
