#!/bin/sh
set -eu
module_dir=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
repo=$(CDPATH= cd -- "$module_dir/../.." && pwd)
car_shell=$(command -v mksh || command -v sh)
tmp=$(mktemp -d "$module_dir/test/.tmp.XXXXXX")
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
mkdir -p "$tmp/base/lib" "$tmp/bin" "$tmp/restricted" "$tmp/state" "$tmp/data/desired" "$tmp/props"
cat > "$tmp/base/lib/common.sh" <<'EOF'
sg_status() {
    printf 'state=%s\nrestart=%s\ndetail=%s\n' "$1" "$2" "$3"
    case "$1" in conflict) return 3;; incompatible) return 4;; failed) return 1;; esac
}
sg_owner_present() { [ -f "$FAKE_ROOT/owner" ]; }
sg_service_call_this_boot() {
    [ -f "$FAKE_ROOT/last-call-boot" ] &&
        [ "$(cat "$FAKE_ROOT/last-call-boot")" = "$(cat "$FAKE_ROOT/boot-id")" ]
}
sg_service_call_ok_this_boot() { sg_service_call_this_boot; }
sg_service_call() {
    [ "$1:$2" = com.hkmc.factory:82 ] || return 2
    printf '%s\n' "$1:$2" >> "$FAKE_ROOT/calls"
    cat "$FAKE_ROOT/boot-id" > "$FAKE_ROOT/last-call-boot"
    case "$(cat "$FAKE_ROOT/response" 2>/dev/null)" in
        exception) SG_SERVICE_CALL_RESPONSE='Cannot broadcast before boot completed' ;;
        *) SG_SERVICE_CALL_RESPONSE='Parcel(00000000)' ;;
    esac
    return 0
}
EOF
cat > "$tmp/bin/getprop" <<'EOF'
#!/bin/sh
cat "$FAKE_ROOT/props/$1" 2>/dev/null || :
EOF
cat > "$tmp/bin/md5" <<'EOF'
#!/bin/sh
if [ -f "$FAKE_ROOT/wrong-hash" ]; then
    echo '00000000000000000000000000000000  file'
else
    echo 'ffe86fa1540c8dea3b5499b1191bb727  file'
fi
EOF
cat > "$tmp/bin/service" <<'EOF'
#!/bin/sh
echo 'Service com.hkmc.factory: found'
EOF
cat > "$tmp/bin/sleep" <<'EOF'
#!/bin/sh
:
EOF
cat > "$tmp/bin/forbidden" <<'EOF'
#!/bin/sh
printf '%s\n' "${0##*/}" >> "$FAKE_ROOT/forbidden"
exit 99
EOF
chmod +x "$tmp/bin/"*
for name in getprop md5 service sleep; do ln -s "$tmp/bin/$name" "$tmp/restricted/$name"; done
for name in cat grep; do ln -s "$(command -v "$name")" "$tmp/restricted/$name"; done
for name in awk sed tail head cut tr wc sort find stat sha256sum tar unzip busybox; do
    ln -s "$tmp/bin/forbidden" "$tmp/restricted/$name"
done
export FAKE_ROOT="$tmp" HMG_SEOGAEMO_BASE="$tmp/base" HMG_SEOGAEMO_STATE="$tmp/state"
export SG_DATA_ROOT="$tmp/data" SG_SKIP_CONSENT_FRAMEWORK="$tmp/framework.odex"
export HMG_SEOGAEMO_SERVICE_BIN="$tmp/bin/service"
script="$module_dir/root/skip-consent-screen.sh"
printf 'stock' > "$tmp/framework.odex"
printf 'NEEV.KOR.0000.V039.001.260902' > "$tmp/props/ro.build.product"
printf 19 > "$tmp/props/ro.build.version.sdk"
printf 1 > "$tmp/props/sys.boot_completed"
printf boot-a > "$tmp/boot-id"
run() { PATH="$tmp/restricted" "$car_shell" "$script" "$@" > "$tmp/output"; }
has() { grep -q -F "$1" "$tmp/output" || { cat "$tmp/output"; exit 1; }; }
run status; has 'state=off'
run precheck; has 'state=off'
: > "$tmp/owner"
if run precheck; then exit 1; fi
has 'state=conflict'; has 'Uninstall local.hkmc.factory82 first'
rm "$tmp/owner"
: > "$tmp/wrong-hash"
if run apply; then exit 1; fi
has 'state=incompatible'
rm "$tmp/wrong-hash"
run apply; has 'state=pending-restart'
printf on > "$tmp/data/desired/skip-consent-screen"
run watch boot; has 'state=on'
run watch boot; [ "$(grep -c '^com.hkmc.factory:82$' "$tmp/calls")" = 1 ]
run watch screen_on; [ "$(grep -c '^com.hkmc.factory:82$' "$tmp/calls")" = 2 ]
run revert; has 'state=pending-restart'; has 'info.desired=off'
printf off > "$tmp/data/desired/skip-consent-screen"
run status; has 'state=pending-restart'; has 'info.desired=off'
printf boot-b > "$tmp/boot-id"
run status; has 'state=off'
[ ! -f "$tmp/forbidden" ]
echo 'skip-consent-screen module tests passed'
