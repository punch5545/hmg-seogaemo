#!/system/bin/sh
# This NEMESIS-tree updater is fixed; downloaded copies are never executed.
set -eu
umask 077
base=${0%/*}
base=${base%/core}
. "$base/lib/common.sh"
state=/data/nemesis/backup/hmg-seogaemo
root=$state/update
input=/data/data/com.seogaemo.hmg/files/update
descriptor=$base/NEMESIS.data
trusted=$base/app/hmg-seogaemo.apk
verb=${1:-}
[ "$#" -eq 1 ] || exit 2
case "$verb" in stage|apply|rollback|status) ;; *) exit 2 ;; esac
mkdir -p "$state" "$root" "$root/versions"

descriptor_version() {
    while IFS= read -r line; do
        case "$line" in package-version=*) sg_print "${line#package-version=}"; return ;; esac
    done < "$descriptor"
    return 1
}
current_version() {
    if [ -f "$root/current/VERSION" ]; then
        IFS= read -r value < "$root/current/VERSION" || :
        sg_print "$value"
    else
        descriptor_version
    fi
}
installed=$(current_version)
case "$installed" in *[!0-9.]*|'') echo 'invalid installed version' >&2; exit 1 ;; esac
if [ "$verb" = status ]; then
    sg_print "version=$installed"
    if [ -f "$root/journal" ]; then
        IFS= read -r phase < "$root/journal" || :
        sg_print "$phase"
    else
        echo 'phase=idle'
    fi
    if [ -f "$root/previous-path" ]; then echo 'rollback=available'; else echo 'rollback=unavailable'; fi
    exit 0
fi

lock=$state/.lock
mkdir "$lock" 2>/dev/null || { echo 'another root operation is active' >&2; exit 1; }
update_pid=
update_out=
# Leaving for any reason (including a signal, which exits through here) ends the
# verifier VM and everything it spawned before the lock is released.
update_cleanup() {
    update_status=$?
    if [ -n "$update_pid" ]; then sg_kill_tree "$update_pid" 3 || :; update_pid=; fi
    [ -z "$update_out" ] || rm -f "$update_out"
    rmdir "$lock" || update_status=1
    exit "$update_status"
}
trap update_cleanup 0
trap 'exit 1' 1 2 3 15
# Runs the verifier and leaves its output in $update_value. The VM must be a child of
# this shell rather than of a command substitution, or the signal trap could not reach it.
java_value() {
    update_out=$root/java-out.$$
    update_value=
    java_update "$@" > "$update_out" || { update_code=$?; rm -f "$update_out"; update_out=; return "$update_code"; }
    update_value=$(cat "$update_out")
    rm -f "$update_out"; update_out=
}
java_update() {
    # Private Dalvik cache and a bounded wait: a hung VM must not hold the
    # update lock, and our APK must not leave entries in /data/dalvik-cache.
    sg_vm_prepare "$state/core/vm" || return 1
    if update_boot=$(sg_bootclasspath); then BOOTCLASSPATH=$update_boot; export BOOTCLASSPATH; fi
    ANDROID_DATA="$state/core/vm" CLASSPATH="$trusted" app_process /system/bin com.seogaemo.hmg.core.update.UpdateVerifier "$@" &
    update_pid=$!
    # First run may dexopt the whole APK; copy/extract handle the downloaded payload.
    sg_bounded_wait "$update_pid" 240 && update_code=0 || update_code=$?
    update_pid=
    if [ "$update_code" -eq 124 ]; then echo 'update verifier timed out' >&2; fi
    return "$update_code"
}
switch_to() {
    rm -f "$root/next"
    ln -s "$1" "$root/next"
    java_update switch "$root/next" "$root/current"
    sg_print "$1" > "$root/current-path.next"
    mv "$root/current-path.next" "$root/current-path"
    sync
}
rewrite_descriptor() {
    found=0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            package-version=*) sg_print "package-version=$1"; found=1 ;;
            *) sg_print "$line" ;;
        esac
    done < "$descriptor" > "$descriptor.ota-new"
    [ "$found" -eq 1 ] || return 1
    mv "$descriptor.ota-new" "$descriptor"
    sync
}
check_md5() {
    dir=$1
    [ -f "$dir/files.md5" ] || return 1
    count=0
    while IFS='|' read -r expected relative; do
        case "$expected" in ''|*[!0-9a-f]*) return 1 ;; esac
        case "$relative" in ''|/*|*..*|*'|'*) return 1 ;; esac
        [ -f "$dir/$relative" ] || return 1
        actual=$(md5 "$dir/$relative") || return 1
        set -- $actual
        [ "$1" = "$expected" ] && [ "$2" = "$dir/$relative" ] || return 1
        count=$((count + 1))
    done < "$dir/files.md5"
    [ "$count" -gt 3 ]
}

if [ "$verb" = stage ]; then
    [ -d "$input/payload" ] || { echo 'download missing' >&2; exit 1; }
    check_md5 "$input/payload" || { echo 'download payload changed' >&2; exit 1; }
    rm -rf "$root/incoming"
    java_update copy "$input" "$root/incoming" || exit 1
    java_value verify "$root/incoming" "$installed" || exit 1
    version=$update_value
    case "$version" in *[!0-9.]*|'') exit 1 ;; esac
    dest=$root/versions/$version
    [ ! -e "$dest" ] || { echo 'version already staged' >&2; exit 1; }
    java_update extract "$root/incoming" "$installed" "$dest" || { rm -rf "$dest"; exit 1; }
    check_md5 "$dest" || { rm -rf "$dest"; exit 1; }
    [ -s "$dest/modules.list" ] && [ -s "$dest/modules.graph" ] && [ -f "$dest/bin/install-apk.sh" ] && [ -f "$dest/bin/hmg-seogaemo-root" ] && [ -f "$dest/svc/reconcile.sh" ] || exit 1
    sync
    sg_print "$version" > "$root/staged"
    echo 'phase=staged' > "$root/journal"
    sg_print "version=$version"
    exit 0
fi

rollback_projection_guard() (
    HMG_SEOGAEMO_STATE=$state/modules/wireless-carplay-and-aa
    if sg_restore_material; then
        echo 'rollback blocked: disable wireless-carplay-and-aa or restore stock first; selected package retained' >&2
        return 1
    fi
)
auto_rollback() {
    echo 'phase=auto-rollback' > "$root/journal"
    rollback_projection_guard || return 1
    switch_to "$old"
    HMG_SEOGAEMO_PM_DOWNGRADE=1 /system/bin/sh "$root/current/bin/install-apk.sh" || return 1
}

if [ "$verb" = rollback ]; then
    rollback_projection_guard || exit 1
    [ -f "$root/previous-path" ] && [ -f "$root/previous-version" ] || { echo 'no previous build' >&2; exit 1; }
    IFS= read -r old < "$root/previous-path" || :
    IFS= read -r previous_version < "$root/previous-version" || :
    case "$old" in "$base"|"$root"/versions/*) ;; *) exit 1 ;; esac
    [ -f "$old/app/hmg-seogaemo.apk" ] || exit 1
    echo 'phase=rollback' > "$root/journal"
    switch_to "$old"
    HMG_SEOGAEMO_PM_DOWNGRADE=1 /system/bin/sh "$root/current/bin/install-apk.sh" || exit 1
    rewrite_descriptor "$previous_version" || exit 1
    echo 'phase=rolled-back' > "$root/journal"
    exit 0
fi

[ -f "$root/staged" ] || { echo 'no staged build' >&2; exit 1; }
IFS= read -r version < "$root/staged" || :
case "$version" in *[!0-9.]*|'') exit 1 ;; esac
java_value verify "$root/incoming" "$installed" || exit 1
[ "$update_value" = "$version" ] || exit 1
new=$root/versions/$version
check_md5 "$new" || exit 1
[ -f "$new/app/hmg-seogaemo.apk" ] || exit 1
old=$base
if [ -f "$root/current-path" ]; then IFS= read -r old < "$root/current-path" || :; fi
case "$old" in "$base"|"$root"/versions/*) ;; *) exit 1 ;; esac
sg_print "$old" > "$root/previous-path"
sg_print "$installed" > "$root/previous-version"
echo 'phase=applying' > "$root/journal"
sync
switch_to "$new"
if ! /system/bin/sh "$root/current/bin/install-apk.sh" || ! grep -Fxq "$version" "$root/current/VERSION" || ! [ -s "$root/current/modules.graph" ] || ! dumpsys package com.seogaemo.hmg | grep -Fq "versionName=$version" || ! HMG_SEOGAEMO_UPDATE_HEALTH=1 HMG_SEOGAEMO_PROJECTION_ROLLBACK_GUARD=1 /system/bin/sh "$root/current/svc/reconcile.sh"; then
    auto_rollback || true
    exit 1
fi
if ! rewrite_descriptor "$version"; then
    auto_rollback || true
    exit 1
fi
rm -f "$root/staged"
echo 'phase=healthy' > "$root/journal"
sg_print "version=$version"
