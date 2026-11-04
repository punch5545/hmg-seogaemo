#!/bin/sh
# Module-local fake-root test. No host firmware paths are touched.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
car_shell=${HMG_SEOGAEMO_TEST_SHELL:-$(command -v mksh || command -v sh)}
export HMG_SEOGAEMO_TEST_COMMON="$HERE/../../nemesis/package/lib/common.sh"
. "$HMG_SEOGAEMO_TEST_COMMON"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
export SG_WIRELESS_TEST_ROOT=$T/root HMG_SEOGAEMO_BASE=$T/base HMG_SEOGAEMO_STATE=$T/state HMG_SEOGAEMO_MODULE_DIR=$HERE/payload
export TEST_PROPS=$T/props TEST_STATE=$T/state
mkdir -p "$T/base/lib" "$T/bin" "$T/props" "$T/state" "$T/root/system/bin" "$T/root/system/app" "$T/root/system/framework" "$T/root/system/onebin/neev_kr" "$T/root/system/etc/permissions" "$T/root/proc/sys/kernel/random"
cat > "$T/base/lib/common.sh" <<'SH'
. "$HMG_SEOGAEMO_TEST_COMMON"
sg_log() { sg_print "$*" >&2; }
sg_payload_dirs() {
    case "$1" in /system/*) ;; *) return 0;; esac
    parent=$(sg_path "$1"); parent=${parent%/*}
    while [ "$parent" != "$SG_WIRELESS_TEST_ROOT" ]; do
        mkdir -p "$parent"; chmod 0755 "$parent"; parent=${parent%/*}
    done
}
sg_restart_required() { cat "$SG_WIRELESS_TEST_ROOT/proc/sys/kernel/random/boot_id" > "$HMG_SEOGAEMO_STATE/assets-repaired-boot"; }
sg_restart_pending() {
    [ -f "$HMG_SEOGAEMO_STATE/assets-repaired-boot" ] &&
        [ "$(cat "$HMG_SEOGAEMO_STATE/assets-repaired-boot")" = "$(cat "$SG_WIRELESS_TEST_ROOT/proc/sys/kernel/random/boot_id")" ]
}
sg_public_readable() (
    SG_TARGET_ROOT=$SG_WIRELESS_TEST_ROOT
    target=$1
    [ -f "$target" ] || exit 1
    set -- $(sg_file_meta "$target")
    [ "$((0$1 & 0004))" -ne 0 ] || exit 1
    parent=${target%/*}
    while [ "$parent" != "$SG_TARGET_ROOT" ]; do
        set -- $(sg_file_meta "$parent")
        [ "$((0$1 & 0001))" -ne 0 ] || exit 1
        parent=${parent%/*}
    done
)
sg_path() { case "$1" in "$HMG_SEOGAEMO_STATE"/*) sg_print "$1";; *) sg_print "$SG_WIRELESS_TEST_ROOT$1";; esac; }
sg_key() { sg_print "$HMG_SEOGAEMO_STATE/$2$1"; }
sg_file_baseline() {
    key=$(sg_key "$1" baseline)
    [ ! -e "$key.present" ] || return 0
    mkdir -p "${key%/*}"
    if [ -f "$(sg_path "$1")" ]; then cp -p "$(sg_path "$1")" "$key"; fi
    touch "$key.present"
}
sg_file_mark_applied() {
    key=$(sg_key "$1" applied)
    mkdir -p "${key%/*}"
    if [ -f "$(sg_path "$1")" ]; then cp -p "$(sg_path "$1")" "$key"; else rm -f "$key"; fi
    touch "$key.present"
}
sg_applied() {
    key=$(sg_key "$1" applied); current=$(sg_path "$1")
    [ -f "$key.present" ] || return 1
    if [ -f "$key" ]; then [ -f "$current" ] && cmp -s "$key" "$current"; else [ ! -f "$current" ]; fi
}
sg_file_restore() {
    key=$(sg_key "$1" baseline); current=$(sg_path "$1")
    [ -f "$key.present" ] || return 1
    if [ -f "$key" ]; then mkdir -p "${current%/*}"; cp -p "$key" "$current"; else rm -f "$current"; fi
}
sg_file_put() {
    target=$1 source=$2
    sg_file_baseline "$target"
    dest=$(sg_path "$target"); mkdir -p "${dest%/*}"
    cp "$HMG_SEOGAEMO_MODULE_DIR/$source" "$dest"
    sg_payload_dirs "$target"; chmod "${3:-644}" "$dest"
    sg_file_mark_applied "$target"
    sg_print "file $target" >> "$HMG_SEOGAEMO_STATE/ops.list"
}
sg_file_replace_line() {
    target=$1 old=$2 new=$3
    sg_file_baseline "$target"
    dest=$(sg_path "$target")
    [ "$(grep -Fxc -e "$old" "$dest" || :)" = 1 ] || return 1
    awk -v o="$old" -v n="$new" '{if ($0 == o) print n; else print}' "$dest" > "$dest.next"
    mv "$dest.next" "$dest"
    sg_file_mark_applied "$target"
    sg_print "file $target" >> "$HMG_SEOGAEMO_STATE/ops.list"
}
sg_prop_set() {
    key=$1; val=$2
    if [ ! -f "$HMG_SEOGAEMO_STATE/prop-baseline/$key.present" ]; then
        mkdir -p "$HMG_SEOGAEMO_STATE/prop-baseline"
        getprop "$key" > "$HMG_SEOGAEMO_STATE/prop-baseline/$key"
        touch "$HMG_SEOGAEMO_STATE/prop-baseline/$key.present"
    fi
    setprop "$key" "$val"
    sg_write "$val" > "$HMG_SEOGAEMO_STATE/prop-applied-$key"
    sg_print "prop $key" >> "$HMG_SEOGAEMO_STATE/ops.list"
}
sg_dex_cache_track() { sg_print "$1 $2" > "$HMG_SEOGAEMO_STATE/dex-track"; }
sg_zygote_install() {
    [ "${SG_FAIL_ZYGOTE:-0}" != 1 ] || return 1
    stock=/system/bin/app_process.hmg-seogaemo-stock
    sg_file_baseline "$stock"
    dest=$(sg_path "$stock")
    cp "$(sg_path /system/bin/app_process)" "$dest"
    sg_file_mark_applied "$stock"
    sg_print "file $stock" >> "$HMG_SEOGAEMO_STATE/ops.list"
    sg_file_put /system/bin/app_process "$1" 755 0:0
    sg_file_put /system/lib/libhmg_seogaemo_wireless_hook.so "$2" 644 0:0
    sg_file_put /system/framework/hmg-seogaemo-wireless-hook.jar "$3" 644 0:0
    sg_file_put "$HMG_SEOGAEMO_STATE/enable" data/enable-marker 600 0:0
    sg_file_put "$HMG_SEOGAEMO_STATE/enabled" data/enable-marker 600 0:0
}
sg_revert_all() {
    [ -f "$HMG_SEOGAEMO_STATE/ops.list" ] || return 0
    while read -r kind target; do
        if [ "$kind" = file ]; then sg_applied "$target" || return 1
        else [ "$(getprop "$target")" = "$(cat "$HMG_SEOGAEMO_STATE/prop-applied-$target")" ] || return 1; fi
    done < "$HMG_SEOGAEMO_STATE/ops.list"
    awk '{a[NR]=$0} END {for (i=NR;i>=1;i--) print a[i]}' "$HMG_SEOGAEMO_STATE/ops.list" | while read -r kind target; do
        if [ "$kind" = file ]; then sg_file_restore "$target" || exit 1
        else setprop "$target" "$(cat "$HMG_SEOGAEMO_STATE/prop-baseline/$target")" || exit 1; fi
    done || return 1
    rm -rf "$HMG_SEOGAEMO_STATE/baseline" "$HMG_SEOGAEMO_STATE/applied" "$HMG_SEOGAEMO_STATE/prop-baseline" "$HMG_SEOGAEMO_STATE"/prop-applied-* "$HMG_SEOGAEMO_STATE/ops.list" "$HMG_SEOGAEMO_STATE/dex-track"
}
sg_owner_present() { [ -f "$HMG_SEOGAEMO_STATE/owner-$1" ]; }
sg_status() { sg_print "state=$1" "restart=$2" "detail=${3:-}"; case "$1" in conflict) return 3;; incompatible) return 4;; failed) return 1;; esac; }
SH
cat > "$T/bin/getprop" <<'SH'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
[ -f "$TEST_PROPS/$1" ] && cat "$TEST_PROPS/$1" || :
SH
cat > "$T/bin/setprop" <<'SH'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
sg_write "$2" > "$TEST_PROPS/$1"
SH
mkdir -p "$T/base/bin"
cat > "$T/base/bin/hmg-seogaemo-root" <<'SH'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
[ "$1" = map ] && [ "$2" = status ] || exit 2
[ "$(cat "$TEST_PROPS/ro.build.product")" = NEEV.KOR.0000.V039.001.260902 ] &&
    [ ! -f "$TEST_STATE/map-off" ] || { sg_print "state=incompatible"; exit 4; }
sg_print "state=on"
SH
chmod 644 "$T/base/bin/hmg-seogaemo-root"
chmod +x "$T/bin"/*
export PATH=$T/bin:$PATH
sg_write "NEEV.KOR.0000.V039.001.260902" > "$T/props/ro.build.product"
sg_write "neev_kr" > "$T/props/ro.mobis.onebindir"
sg_write "4.4.2" > "$T/props/ro.build.version.release"
sg_write "stock" > "$T/props/persist.ium.support"
sg_write "old" > "$T/props/persist.sys.iap2.wifi"
sg_write "old" > "$T/props/persist.iap2.iden.wireless"
sg_write "0" > "$T/props/persist.service.adb.tcp.enable"
for name in framework.odex HKMC_DiPO.odex Iap2ServerApp.odex HKMC_GPM.odex; do
    case "$name" in framework.odex) dir=framework;; *) dir=app;; esac
    sg_print "stock-$name" > "$T/root/system/$dir/$name"
done
sg_print "stock-app-process" > "$T/root/system/bin/app_process"
sg_print "boot-one" > "$T/root/proc/sys/kernel/random/boot_id"
sg_print '-/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml' '-/system/etc/permissions/com.hkmc.software.connectivity.androidauto_wireless.xml' '-/system/etc/permissions/com.hkmc.software.wifihotspot.xml' > "$T/root/system/onebin/neev_kr/featurexml.cfg"
SCRIPT=$T/wireless-carplay-and-aa.sh
sed "s|/system/bin/sh|$car_shell|g" "$HERE/root/wireless-carplay-and-aa.sh" > "$SCRIPT"
run() { "$car_shell" "$SCRIPT" "$1" > "$T/output"; }
fail() { echo "FAIL: $1" >&2; cat "$T/output" >&2; exit 1; }
# The zygote starts seconds after boot, before this boot's map verification can finish,
# so the hook gate must not depend on the boot ID (the car-map seal's third line).
if grep -q 'boot_id' "$HERE/payload/src/hmgapi/hook/HmgApi.java.src"; then fail 'zygote hook gate must not require the boot id'; fi
sg_write "OTHER.KOR.0000.V039.001.260902" > "$T/props/ro.build.product"
run precheck && fail wrong-firmware-accepted
grep -q '^state=incompatible$' "$T/output" || fail firmware-state
sg_write "NEEV.KOR.0000.V039.001.260902" > "$T/props/ro.build.product"
sg_write "wrong_onebin" > "$T/props/ro.mobis.onebindir"
run precheck && fail wrong-onebin-accepted
grep -q '^state=incompatible$' "$T/output" || fail onebin-state
sg_write "neev_kr" > "$T/props/ro.mobis.onebindir"
touch "$T/state/map-off"
run precheck && fail unverified-map-accepted
grep -q '^state=incompatible$' "$T/output" || fail unverified-map-state
rm -f "$T/state/map-off"
touch "$T/state/owner-hmgapi"
run precheck && fail owner-accepted
grep -q '^state=conflict$' "$T/output" || fail owner-state
rm -f "$T/state/owner-hmgapi"
run precheck || fail precheck
grep -q '^state=off$' "$T/output" || fail precheck-state
# Upstream treats a removal line that is absent on this firmware as a no-op.
grep -Fv 'androidauto_wireless.xml' "$T/root/system/onebin/neev_kr/featurexml.cfg" > "$T/without-aa-line"
cp "$T/without-aa-line" "$T/root/system/onebin/neev_kr/featurexml.cfg"
run precheck || fail absent-feature-line
sg_print "<permissions/>" > "$T/root/system/etc/permissions/com.hkmc.software.wifihotspot.xml"
run precheck && fail malformed-existing-feature-accepted
grep -q '^state=incompatible$' "$T/output" || fail malformed-feature-state
rm -f "$T/root/system/etc/permissions/com.hkmc.software.wifihotspot.xml"
sg_print '-/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml' >> "$T/root/system/onebin/neev_kr/featurexml.cfg"
run precheck && fail duplicate-feature-line-accepted
grep -q '^state=incompatible$' "$T/output" || fail duplicate-feature-line-state
sed -n '1,2p' "$T/root/system/onebin/neev_kr/featurexml.cfg" > "$T/unique-feature-lines"
cp "$T/unique-feature-lines" "$T/root/system/onebin/neev_kr/featurexml.cfg"
cp "$T/root/system/onebin/neev_kr/featurexml.cfg" "$T/original.cfg"
export SG_FAIL_ZYGOTE=1
run apply && fail injected-zygote-failure-accepted
cmp -s "$T/original.cfg" "$T/root/system/onebin/neev_kr/featurexml.cfg" || fail failed-apply-restoration
[ "$(cat "$T/props/persist.ium.support")" = stock ] || fail failed-apply-property
[ ! -e "$T/root/system/bin/app_process.hmg-seogaemo-stock" ] || fail failed-apply-wrapper
unset SG_FAIL_ZYGOTE
run apply || fail apply
grep -q '^state=pending-restart$' "$T/output" || fail apply-state
[ "$(cat "$T/props/persist.ium.support")" = carplay ] || fail property
[ -f "$T/root/system/bin/app_process.hmg-seogaemo-stock" ] || fail wrapper-backup
[ -f "$T/state/dex-track" ] || fail dex-tracking
[ -f "$T/root/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml" ] || fail feature-file
sg_print "boot-two" > "$T/root/proc/sys/kernel/random/boot_id"
run status || fail status
grep -q '^state=pending-restart$' "$T/output" || fail pending-state
cat > "$T/bin/ps" <<'SH'
#!/bin/sh
. "$HMG_SEOGAEMO_TEST_COMMON"
sg_print 'USER PID PPID VSIZE RSS WCHAN PC NAME' 'root 123 1 10000 2000 ffffffff 00000000 zygote'
SH
chmod +x "$T/bin/ps"
mkdir -p "$T/root/proc/123"
sg_print '/system/lib/libhmg_seogaemo_wireless_hook.so' > "$T/root/proc/123/maps"
run status || fail loaded-status
grep -q '^state=on$' "$T/output" || fail loaded-state
# Existing applied wrapper must not skip repair of old private ancestors.
chmod 700 "$T/root/system/etc/hmg-seogaemo" "$T/root/system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes"
run status && fail unreadable-assets-accepted
grep -q 'assets unreadable' "$T/output" || fail unreadable-detail
run apply || fail already-applied-repair
grep -q '^state=pending-restart$' "$T/output" || fail repaired-restart
case "$(ls -ldn "$T/root/system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes")" in drwxr-xr-x*) ;; *) fail repair-mode;; esac
sg_print "boot-three" > "$T/root/proc/sys/kernel/random/boot_id"
run status || fail after-repair-status
grep -q '^state=on$' "$T/output" || fail after-repair-state
chmod 600 "$T/root/system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes/carplay-audio-formats.recipe"
run status && fail private-recipe-accepted
grep -q 'assets unreadable' "$T/output" || fail private-recipe-detail
chmod 644 "$T/root/system/etc/hmg-seogaemo/wireless-carplay-and-aa/recipes/carplay-audio-formats.recipe"
rm -f "$T/bin/ps" "$T/root/proc/123/maps"
sg_print "external-change" >> "$T/root/system/onebin/neev_kr/featurexml.cfg"
run revert && fail drift-accepted
[ -f "$T/state/enabled" ] || fail state-lost-after-drift
# Restore our exact applied file, then revert.
cp "$T/state/applied/system/onebin/neev_kr/featurexml.cfg" "$T/root/system/onebin/neev_kr/featurexml.cfg"
run revert || fail revert
cmp -s "$T/original.cfg" "$T/root/system/onebin/neev_kr/featurexml.cfg" || fail exact-feature-restore
[ "$(cat "$T/root/system/bin/app_process")" = stock-app-process ] || fail exact-wrapper-restore
[ ! -e "$T/root/system/bin/app_process.hmg-seogaemo-stock" ] || fail stock-copy-present
[ ! -e "$T/root/system/lib/libhmg_seogaemo_wireless_hook.so" ] || fail hook-lib-present
[ ! -e "$T/root/system/framework/hmg-seogaemo-wireless-hook.jar" ] || fail hook-jar-present
[ ! -e "$T/root/system/lib/libleafiaaac.so" ] || fail audio-lib-present
[ ! -e "$T/root/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml" ] || fail feature-file-present
[ "$(cat "$T/props/persist.ium.support")" = stock ] || fail exact-prop-restore
[ ! -f "$T/state/enabled" ] || fail enabled-marker-present
sh "$HERE/test/cpturns/run.sh" > "$T/cpturns.out" 2>&1 || { cat "$T/cpturns.out"; fail cpturns; }
sg_print "ok wireless-carplay-and-aa toolbox parse, guards, rollback, drift refusal, exact revert"
