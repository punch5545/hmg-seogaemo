#!/bin/sh
set -eu

repo=$(CDPATH= cd "$(dirname "$0")/../../.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/wifi-client-test.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM
mkdir -p "$work/base/lib" "$work/bin" "$work/system/onebin/neev_kr" \
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
sg_file_baseline() {
    name=${1##*/}
    if [ ! -e "$SEOGAEMO_STATE/$name.saved" ] && [ ! -e "$SEOGAEMO_STATE/$name.absent" ]; then
        if [ -e "$1" ]; then cp -p "$1" "$SEOGAEMO_STATE/$name.saved";
        else : > "$SEOGAEMO_STATE/$name.absent"; fi
    fi
}
sg_file_mark_applied() {
    name=${1##*/}
    cp -p "$1" "$SEOGAEMO_STATE/$name.applied"
    printf '%s\n' "$name" >> "$TEST_WORK/marks"
}
sg_file_restore() {
    name=${1##*/}
    if [ -e "$SEOGAEMO_STATE/$name.saved" ] && [ -e "$1" ] &&
       cmp -s "$1" "$SEOGAEMO_STATE/$name.saved"; then return 0; fi
    if [ -e "$SEOGAEMO_STATE/$name.absent" ] && [ ! -e "$1" ]; then return 0; fi
    if [ -e "$1" ] && [ -e "$SEOGAEMO_STATE/$name.applied" ]; then
        cmp -s "$1" "$SEOGAEMO_STATE/$name.applied" || return 1
    fi
    if [ -e "$SEOGAEMO_STATE/$name.absent" ]; then
        rm -f "$1"
        [ ! -e "$1" ]
    else
        cp -p "$SEOGAEMO_STATE/$name.saved" "$1"
        cmp -s "$SEOGAEMO_STATE/$name.saved" "$1"
    fi
}
sg_remount() { printf '%s\n' "$1" >> "$TEST_WORK/remounts"; }
sg_baseline_clear() {
    [ ! -e "$TEST_WORK/fail-clear" ] || return 1
    rm -f "$SEOGAEMO_STATE"/*.saved "$SEOGAEMO_STATE"/*.absent "$SEOGAEMO_STATE"/*.applied
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
cat > "$work/bin/wpa_cli" <<'EOF'
#!/bin/sh
cat "$TEST_WORK/wpa-status" 2>/dev/null || :
EOF
chmod +x "$work/bin/"*

# Rewrite only fixed filesystem roots in a private copy; featurexml entries
# remain the actual absolute paths used on the car.
sed -e "s|^cfg=/system/|cfg=$work/system/|" \
    -e "s|^xml=/system/|xml=$work/system/|" \
    -e "s|/sys/bus/sdio/devices/\*|$work/sys/bus/sdio/devices/*|" \
    -e "s|/sys/class/net/wlan\*|$work/sys/class/net/wlan*|" \
    -e "s|/sys/class/net/wlan0|$work/sys/class/net/wlan0|" \
    "$repo/modules/wifi-client/root/wifi-client.sh" > "$work/wifi-client.sh"

export TEST_WORK=$work SEOGAEMO_BASE=$work/base SEOGAEMO_STATE=$work/state
PATH=$work/bin:$PATH
export PATH
run() { sh "$work/wifi-client.sh" "$1" > "$work/output"; }
assert_state() { grep -Fxq "state=$1" "$work/output" || { cat "$work/output"; exit 1; }; }
assert_line() { grep -Fxq "$1" "$work/output" || { cat "$work/output"; exit 1; }; }

run status; assert_state off
run apply; assert_state pending-restart; assert_line restart=head_unit
[ "$(wc -l < "$work/marks")" -eq 2 ]
cmp -s "$work/original.cfg" "$cfg" && exit 1
grep -Fqx '#-/system/etc/permissions/android.hardware.wifi.xml' "$cfg"
[ -f "$xml" ]
run apply; assert_state pending-restart
[ "$(grep -c '^rw$' "$work/remounts")" -eq 1 ]
printf 'yes\n' > "$work/pm-feature"
run status; assert_state on
: > "$work/sys/class/net/wlan0"
printf 'wpa_state=COMPLETED\n' > "$work/wpa-status"
printf 'wlan0: inet addr:10.7.0.15 Bcast:10.7.0.255 Mask:255.255.255.0\n' > "$work/ifconfig"
run status; assert_line info.client_ip=10.7.0.15
printf 'wpa_state=DISCONNECTED\n' > "$work/wpa-status"
run status
if grep -q '^info.client_ip=' "$work/output"; then exit 1; fi
run revert; assert_state pending-restart
cmp -s "$work/original.cfg" "$cfg"
[ ! -e "$xml" ]
[ ! -e "$work/state/applied" ]
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

run apply; assert_state pending-restart
printf 'drift\n' >> "$cfg"
if run revert; then exit 1; fi
assert_state conflict
[ -e "$work/state/applied" ]

# A cleanup failure after exact restore must be retryable.
cp "$work/state/applied.cfg" "$cfg"
: > "$work/fail-clear"
if run revert; then exit 1; fi
assert_state failed
cmp -s "$work/original.cfg" "$cfg"
[ ! -e "$xml" ]
rm "$work/fail-clear"
run revert; assert_state off

echo 'wifi-client module tests passed'
