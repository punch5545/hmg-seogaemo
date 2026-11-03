#!/system/bin/sh
# Fixed updater; this file in the NEMESIS package is never overwritten by OTA.
set -eu
umask 077
base=${0%/*}
base=${base%/core}
state=/data/nemesis/backup/seogaemo
root=$state/update
input=/data/data/com.seogaemo.app/files/update
descriptor=$base/NEMESIS.data
verb=${1:-}
[ "$#" -eq 1 ] || exit 2
case "$verb" in stage|apply|rollback|status) ;; *) exit 2 ;; esac
mkdir -p "$state" "$root" "$root/versions"
current_version() {
    if [ -f "$root/current/VERSION" ]; then
        sed -n '1p' "$root/current/VERSION"
    else
        sed -n 's/^package-version=//p' "$descriptor"
    fi
}
installed=$(current_version)
case "$installed" in *[!0-9.]*|'') echo 'invalid installed version' >&2; exit 1 ;; esac
if [ "$verb" = status ]; then
    echo "version=$installed"
    if [ -f "$root/journal" ]; then sed -n '1p' "$root/journal"; else echo 'phase=idle'; fi
    if [ -L "$root/previous" ]; then echo 'rollback=available'; else echo 'rollback=unavailable'; fi
    exit 0
fi
lock=$state/.lock
mkdir "$lock" 2>/dev/null || { echo 'another root operation is active' >&2; exit 1; }
trap 'rmdir "$lock"' 0
trap 'exit 1' 1 2 3 15
switch_to() {
    rm -f "$root/next"
    ln -s "$1" "$root/next"
    CLASSPATH="$base/app/Seogaemo.apk" app_process /system/bin com.seogaemo.app.core.update.UpdateVerifier switch "$root/next" "$root/current"
}
if [ "$verb" = stage ]; then
    [ -d "$input" ] || { echo 'download missing' >&2; exit 1; }
    [ ! -L "$input" ] || exit 1
    rm -rf "$root/incoming"
    mkdir "$root/incoming"
    count=0
    for file in "$input"/*; do
        [ -f "$file" ] && [ ! -L "$file" ] || exit 1
        name=${file##*/}
        case "$name" in
            hmg-seogaemo-*-manifest.json|hmg-seogaemo-*-manifest.json.sig|hmg-seogaemo-*-ota.zip) ;;
            *) exit 1 ;;
        esac
        bytes=$(wc -c < "$file")
        [ "$bytes" -le 209715200 ] || exit 1
        cp "$file" "$root/incoming/$name"
        count=$((count + 1))
    done
    [ "$count" -eq 3 ] || exit 1
    trusted=$base/app/Seogaemo.apk
    [ -f "$root/current/app/Seogaemo.apk" ] && trusted=$root/current/app/Seogaemo.apk
    version=$(CLASSPATH="$trusted" app_process /system/bin com.seogaemo.app.core.update.UpdateVerifier verify "$root/incoming" "$installed") || exit 1
    case "$version" in *[!0-9.]*|'') exit 1 ;; esac
    dest=$root/versions/$version
    [ ! -e "$dest" ] || { echo 'version already staged' >&2; exit 1; }
    CLASSPATH="$trusted" app_process /system/bin com.seogaemo.app.core.update.UpdateVerifier extract "$root/incoming" "$installed" "$dest" || { rm -rf "$dest"; exit 1; }
    [ -s "$dest/modules.list" ] && [ -s "$dest/modules.graph" ] && [ -f "$dest/bin/install-apk.sh" ] && [ -f "$dest/bin/seogaemo-root" ] && [ -f "$dest/svc/reconcile.sh" ] || exit 1
    echo "$version" > "$root/staged"
    echo 'phase=staged' > "$root/journal"
    echo "version=$version"
    exit 0
fi
if [ "$verb" = rollback ]; then
    [ -L "$root/previous" ] || { echo 'no previous build' >&2; exit 1; }
    old=$(ls -ld "$root/previous" | sed 's/.* -> //')
    [ -f "$old/app/Seogaemo.apk" ] || exit 1
    echo 'phase=rollback' > "$root/journal"
    switch_to "$old"
    sh "$root/current/bin/install-apk.sh" || exit 1
    [ -f "$root/previous-version" ] || exit 1
    previous_version=$(cat "$root/previous-version")
    sed "s/^package-version=.*/package-version=$previous_version/" "$descriptor" > "$descriptor.ota-new"
    mv -f "$descriptor.ota-new" "$descriptor"
    echo 'phase=rolled-back' > "$root/journal"
    exit 0
fi
[ -f "$root/staged" ] || { echo 'no staged build' >&2; exit 1; }
version=$(cat "$root/staged")
case "$version" in *[!0-9.]*|'') exit 1 ;; esac
verified=$(CLASSPATH="$base/app/Seogaemo.apk" app_process /system/bin com.seogaemo.app.core.update.UpdateVerifier verify "$root/incoming" "$installed") || exit 1
[ "$verified" = "$version" ] || exit 1
new=$root/versions/$version
[ -f "$new/app/Seogaemo.apk" ] || exit 1
old=$base
[ -L "$root/current" ] && old=$(ls -ld "$root/current" | sed 's/.* -> //')
echo "$installed" > "$root/previous-version"
rm -f "$root/previous"
ln -s "$old" "$root/previous"
echo 'phase=applying' > "$root/journal"
switch_to "$new"
if ! sh "$root/current/bin/install-apk.sh" || ! grep -Fxq "$version" "$root/current/VERSION" || ! [ -s "$root/current/modules.graph" ] || ! dumpsys package com.seogaemo.app | grep -Fq "versionName=$version" || ! SEOGAEMO_UPDATE_HEALTH=1 sh "$root/current/svc/reconcile.sh"; then
    echo 'phase=auto-rollback' > "$root/journal"
    switch_to "$old"
    sh "$root/current/bin/install-apk.sh" || true
    exit 1
fi
# Keep every other descriptor field byte-for-byte identical.
if ! sed "s/^package-version=.*/package-version=$version/" "$descriptor" > "$descriptor.ota-new" || ! grep -Fxq "package-version=$version" "$descriptor.ota-new" || ! mv -f "$descriptor.ota-new" "$descriptor"; then
    echo 'phase=auto-rollback' > "$root/journal"
    switch_to "$old"
    sh "$root/current/bin/install-apk.sh" || true
    exit 1
fi
rm -f "$root/staged"
echo 'phase=healthy' > "$root/journal"
echo "version=$version"
