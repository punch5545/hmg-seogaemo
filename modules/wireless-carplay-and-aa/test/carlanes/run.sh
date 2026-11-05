#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp "$HERE/../../payload/src/hmgapi/mods/wireless/CarLaneCapture.java.src" "$T/CarLaneCapture.java"
javac --release 7 -Xlint:-options -d "$T/classes" "$T/CarLaneCapture.java" "$HERE/CarLaneCaptureTest.java" "$HERE"/stubs/*.java
java -cp "$T/classes" hmgapi.mods.wireless.CarLaneCaptureTest
