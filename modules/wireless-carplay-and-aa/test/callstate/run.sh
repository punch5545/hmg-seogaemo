#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp "$HERE/../../payload/src/hmgapi/mods/wireless/dipo/CallState.java.src" "$T/CallState.java"
javac --release 7 -Xlint:-options -d "$T/classes" "$T/CallState.java" "$HERE/CallStateTest.java"
java -cp "$T/classes" hmgapi.mods.wireless.dipo.CallStateTest
