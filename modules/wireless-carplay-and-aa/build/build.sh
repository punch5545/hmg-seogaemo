#!/bin/sh
# Modified by HMG-Seogaemo: module-local entry for the packaged source build.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
sh "$HERE/payload/build.sh" "$@"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=${ANDROID_BUILD_TOOLS:-$SDK/build-tools/34.0.0}
ANDROID_JAR=${ANDROID_JAR:-$SDK/platforms/android-19/android.jar}
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
OUT=$HERE/files
mkdir -p "$W/classes" "$W/dex" "$OUT"
cp "$HERE/payload/runtime/HmgSeogaemoProjectionRuntime.java.src" "$W/HmgSeogaemoProjectionRuntime.java"
javac -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" -d "$W/classes" "$W/HmgSeogaemoProjectionRuntime.java"
"$BT/d8" --release --min-api 19 --lib "$ANDROID_JAR" --output "$W/dex" $(find "$W/classes" -name '*.class' | sort)
touch -t 198001010000 "$W/dex/classes.dex"
rm -f "$OUT/hmg-seogaemo-projection-runtime.jar"
(cd "$W/dex" && zip -q -X "$OUT/hmg-seogaemo-projection-runtime.jar" classes.dex)
