#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
cp "$HERE/../../payload/src/hmgapi/mods/wireless/UsbHotspot.java.src" "$T/UsbHotspot.java"
javac --release 7 -Xlint:-options -d "$T/classes" "$T/UsbHotspot.java" "$HERE/UsbHotspotTest.java" "$HERE"/stubs/*.java
java -cp "$T/classes" hmgapi.mods.wireless.UsbHotspotTest
