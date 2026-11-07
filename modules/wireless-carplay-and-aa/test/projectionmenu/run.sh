#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp "$HERE/../../payload/src/hmgapi/mods/wireless/ProjectionMenu.java.src" "$T/ProjectionMenu.java"
javac --release 7 -Xlint:-options -d "$T/classes" "$T/ProjectionMenu.java" "$HERE/ProjectionMenuTest.java"
java -cp "$T/classes" hmgapi.mods.wireless.ProjectionMenuTest
