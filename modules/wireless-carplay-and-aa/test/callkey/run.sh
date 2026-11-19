#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp "$HERE/../../payload/src/hmgapi/mods/wireless/dipo/CallKey.java.src" "$T/CallKey.java"
javac --release 7 -Xlint:-options -d "$T/classes" "$T/CallKey.java" "$HERE/CallKeyTest.java"
java -cp "$T/classes" hmgapi.mods.wireless.dipo.CallKeyTest
