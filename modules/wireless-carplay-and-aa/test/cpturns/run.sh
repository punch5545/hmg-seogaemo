#!/bin/sh
# Host test of the CarPlay turn-by-turn (dipo/CpTurns) with stand-ins for Android, Cluster/Car and Dipo.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
PAY=$HERE/../../payload
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/hmgapi/mods/wireless/dipo" "$T/src/com/tcc/iap2" "$T/out"
cp "$PAY/src/hmgapi/mods/wireless/dipo/CpTurns.java.src" "$T/src/hmgapi/mods/wireless/dipo/CpTurns.java"
cp "$PAY/stubs/com/tcc/iap2/Iap2NotiMsg.java.src" "$T/src/com/tcc/iap2/Iap2NotiMsg.java"
javac --release 8 -Xlint:-options -nowarn -d "$T/out" \
    $(find "$HERE/stubs" "$HERE/test" "$HERE/aaturns/stubs" -name '*.java') $(find "$T/src" -name '*.java')
java -cp "$T/out" hmgapi.mods.wireless.dipo.CpTurnsTest
