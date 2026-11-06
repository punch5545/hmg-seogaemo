#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp "$HERE/../../payload/src/hmgapi/mods/wireless/dipo/ProjectionSwitch.java.src" "$T/ProjectionSwitch.java"
javac --release 7 -Xlint:-options -d "$T/classes" "$T/ProjectionSwitch.java" "$HERE/ProjectionSwitchTest.java"
java -cp "$T/classes" hmgapi.mods.wireless.dipo.ProjectionSwitchTest
