#!/bin/sh
set -eu

repo=$(CDPATH= cd "$(dirname "$0")/../../.." && pwd)
car_shell=$(command -v mksh || command -v sh)
work=$(mktemp -d "${TMPDIR:-/tmp}/wifi-client-test.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
mkdir -p "$work/base/lib" "$work/bin" "$work/system/onebin/neev_kr" \
    "$work/base/bin" \
    "$work/system/etc/permissions" "$work/sys/bus/sdio/devices" \
    "$work/sys/class/net" "$work/state"
cfg=$work/system/onebin/neev_kr/featurexml.cfg
xml=$work/system/etc/permissions/android.hardware.wifi.xml
printf 'before\n-/system/etc/permissions/android.hardware.wifi.xml\nafter\n' > "$cfg"
cp "$cfg" "$work/original.cfg"
: > "$work/sys/bus/sdio/devices/chip"
printf '%s\n' NEEV.KOR.0000.V039.001.260902 > "$work/product"
: > "$work/owners"
: > "$work/pm-feature"

cat > "$work/base/lib/common.sh" <<'EOF'
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
    printf '%s\n' "$name" >> "$TEST_WORK/marks"
}
sg_applied() {
    name=${1##*/}
    [ -f "$HMG_SEOGAEMO_STATE/$name.applied" ] && [ -f "$1" ] &&
        cmp -s "$HMG_SEOGAEMO_STATE/$name.applied" "$1"
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
        printf '%s\n' "$line"
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
    [ ! -e "$TEST_WORK/fail-clear" ] || return 1
    rm -f "$HMG_SEOGAEMO_STATE"/*.saved "$HMG_SEOGAEMO_STATE"/*.absent "$HMG_SEOGAEMO_STATE"/*.applied
}
sg_owner_present() { grep -Fxq "$1" "$TEST_WORK/owners"; }
sg_status() {
    printf 'state=%s\nrestart=%s\ndetail=%s\n' "$1" "$2" "${3:-}"
    case "$1" in conflict) return 3;; incompatible) return 4;; failed) return 1;; esac
    return 0
}
EOF
cat > "$work/bin/getprop" <<'EOF'
#!/bin/sh
case "$1" in ro.build.product) cat "$TEST_WORK/product";; init.svc.hostapd) cat "$TEST_WORK/hostapd" 2>/dev/null || :;; esac
EOF
cat > "$work/bin/setprop" <<'EOF'
#!/bin/sh
echo 'wifi-client must not change properties' >&2
exit 1
EOF
cat > "$work/bin/pm" <<'EOF'
#!/bin/sh
[ "$*" = 'list features' ] && [ -s "$TEST_WORK/pm-feature" ] && printf 'feature:android.hardware.wifi\n'
exit 0
EOF
cat > "$work/bin/ifconfig" <<'EOF'
#!/bin/sh
cat "$TEST_WORK/ifconfig" 2>/dev/null || :
EOF
cat > "$work/bin/netcfg" <<'EOF'
#!/bin/sh
cat "$TEST_WORK/netcfg" 2>/dev/null || :
EOF
cat > "$work/bin/wpa_cli" <<'EOF'
#!/bin/sh
cat "$TEST_WORK/wpa-status" 2>/dev/null || :
EOF
cat > "$work/base/bin/hmg-seogaemo-root" <<'EOF'
#!/bin/sh
[ "$1 $2" = 'wireless-carplay-and-aa status' ] || exit 2
if [ -f "$TEST_WORK/projection-on" ]; then printf 'state=on\n';
else printf 'state=off\n'; fi
EOF
chmod +x "$work/bin/"*
chmod +x "$work/base/bin/hmg-seogaemo-root"

# Rewrite only fixed filesystem roots in a private copy; featurexml entries
# remain the actual absolute paths used on the car.
sed -e "s|^cfg=/system/|cfg=$work/system/|" \
    -e "s|^xml=/system/|xml=$work/system/|" \
    -e "s|/sys/bus/sdio/devices/\*|$work/sys/bus/sdio/devices/*|" \
    -e "s|/sys/class/net/wlan\*|$work/sys/class/net/wlan*|" \
    -e "s|/sys/class/net/wlan0|$work/sys/class/net/wlan0|" \
    "$repo/modules/wifi-client/root/wifi-client.sh" > "$work/wifi-client.sh"

cp "$repo/modules/wifi-client/payload/android.hardware.wifi.xml" "$work/base/android.hardware.wifi.xml"
export TEST_WORK=$work HMG_SEOGAEMO_BASE=$work/base HMG_SEOGAEMO_STATE=$work/state HMG_SEOGAEMO_MODULE_DIR=$work/base
# Keep the test harness on the host PATH, but execute the module with only
# toolbox-like commands. Forbidden names are explicit failing stubs.
mkdir "$work/allowed"
for tool in cat cmp cp grep rm; do
    ln -s "$(command -v "$tool")" "$work/allowed/$tool"
done
ln -s "$repo/tools/testlib/fakebin/md5" "$work/allowed/md5"
ln -s "$(command -v python3)" "$work/allowed/python3"
for tool in getprop setprop pm ifconfig netcfg wpa_cli; do
    ln -s "$work/bin/$tool" "$work/allowed/$tool"
done
for tool in awk sed tail head cut tr wc sort uniq xargs find stat readlink sha256sum sha1sum tar unzip busybox toybox; do
    cat > "$work/allowed/$tool" <<'EOF'
#!/bin/sh
echo "forbidden tool called: ${0##*/}" >&2
exit 99
EOF
    chmod +x "$work/allowed/$tool"
done
run() { PATH=$work/allowed "$car_shell" "$work/wifi-client.sh" "$1" > "$work/output"; }
assert_state() { grep -Fxq "state=$1" "$work/output" || { cat "$work/output"; exit 1; }; }
assert_line() { grep -Fxq "$1" "$work/output" || { cat "$work/output"; exit 1; }; }

run status; assert_state off
run apply; assert_state pending-restart; assert_line restart=head_unit
[ "$(wc -l < "$work/marks")" -eq 2 ]
cmp -s "$work/original.cfg" "$cfg" && exit 1
grep -Fqx '#-/system/etc/permissions/android.hardware.wifi.xml' "$cfg"
[ -f "$xml" ]
run apply; assert_state pending-restart
[ "$(wc -l < "$work/marks")" -eq 2 ]
printf 'yes\n' > "$work/pm-feature"
run status; assert_state on
: > "$work/sys/class/net/wlan0"
printf 'wpa_state=COMPLETED\n' > "$work/wpa-status"
printf 'wlan0: inet addr:10.7.0.15 Bcast:10.7.0.255 Mask:255.255.255.0\n' > "$work/ifconfig"
run status; assert_line info.client_ip=10.7.0.15
: > "$work/ifconfig"
printf 'wlan0 UP 10.7.0.16/24 0x00001043\n' > "$work/netcfg"
run status; assert_line info.client_ip=10.7.0.16
printf 'wpa_state=DISCONNECTED\n' > "$work/wpa-status"
run status
if grep -q '^info.client_ip=' "$work/output"; then exit 1; fi
run revert; assert_state pending-restart
cmp -s "$work/original.cfg" "$cfg"
[ ! -e "$xml" ]
[ ! -e "$work/state/featurexml.cfg.applied" ]
: > "$work/pm-feature"
run status; assert_state off

printf '%s\n' WRONG.KOR.260902 > "$work/product"
if run apply; then exit 1; fi
assert_state incompatible
printf '%s\n' NEEV.KOR.0000.V039.001.260902 > "$work/product"
rm "$work/sys/bus/sdio/devices/chip"
rm "$work/sys/class/net/wlan0"
if run apply; then exit 1; fi
assert_state incompatible
: > "$work/sys/bus/sdio/devices/chip"
printf 'hmgapi\n' > "$work/owners"
if run apply; then exit 1; fi
assert_state conflict
: > "$work/owners"

printf 'running\n' > "$work/hostapd"
run precheck; assert_line warn.carplay_active=1
: > "$work/hostapd"
run precheck
if grep -q '^warn\.' "$work/output"; then exit 1; fi
: > "$work/projection-on"
run precheck; assert_line warn.carplay_active=1
rm "$work/projection-on"

run apply; assert_state pending-restart
printf 'drift\n' >> "$cfg"
if run revert; then exit 1; fi
assert_state conflict
[ -e "$work/state/featurexml.cfg.applied" ]

# A cleanup failure after exact restore must be retryable.
cp "$work/state/featurexml.cfg.applied" "$cfg"
: > "$work/fail-clear"
if run revert; then exit 1; fi
assert_state conflict
cmp -s "$work/original.cfg" "$cfg"
[ ! -e "$xml" ]
rm "$work/fail-clear"
run revert; assert_state off

echo 'wifi-client module tests passed'
