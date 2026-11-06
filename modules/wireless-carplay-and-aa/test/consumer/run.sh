#!/bin/sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
W=$(mktemp -d)
W=$(cd "$W" && pwd -P)
trap 'rm -rf "$W"' EXIT
cp "$HERE/payload/runtime/HmgSeogaemoProjectionRuntime.java.src" "$W/HmgSeogaemoProjectionRuntime.java"
javac -source 7 -target 7 -d "$W/classes" "$W/HmgSeogaemoProjectionRuntime.java" "$HERE/test/consumer/RuntimeConsumerTest.java"
java -cp "$W/classes" com.seogaemo.hmg.runtime.RuntimeConsumerTest "$W/root"
