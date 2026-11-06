#!/bin/sh
# Modified by HMG-Seogaemo: build the wireless projection mod jar for the core hook host and
# the AAC converter. The engine (hmgapi.hook, hmgapi.engine) ships in the core hook host.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$HERE/../../.." && pwd)
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=${ANDROID_BUILD_TOOLS:-$SDK/build-tools/34.0.0}
ANDROID_JAR=${ANDROID_JAR:-$SDK/platforms/android-19/android.jar}
CLANG=${CLANG:-clang}
OUT=$HERE/../files
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$OUT" "$W/classes" "$W/stubs" "$W/dex" "$W/source"
API=$ROOT/out/hook-sdk/hmg-seogaemo-hook-api.jar
# make runs hook-build first; a direct run builds the SDK when it is missing.
[ -f "$API" ] || CLANG=$CLANG sh "$ROOT/core/hook/build.sh"
sh "$HERE/native/build.sh" "$OUT/libleafiaaac.so"
find "$HERE/stubs" -name '*.java.src' -print | while IFS= read -r source; do
    rel=${source#"$HERE/stubs/"}; target="$W/source/stubs/${rel%.src}"
    mkdir -p "${target%/*}"; cp "$source" "$target"
done
find "$W/source/stubs" -name '*.java' -print > "$W/stubs.list"
javac -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" -d "$W/stubs" @"$W/stubs.list"
find "$HERE/src/hmgapi/mods" -name '*.java.src' -print | while IFS= read -r source; do
    rel=${source#"$HERE/src/"}; target="$W/source/src/${rel%.src}"
    mkdir -p "${target%/*}"; cp "$source" "$target"
done
find "$W/source/src" -name '*.java' -print > "$W/sources.list"
javac -nowarn -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR:$W/stubs:$API" -d "$W/classes" @"$W/sources.list"
"$BT/d8" --release --min-api 19 --lib "$ANDROID_JAR" --classpath "$W/stubs" --classpath "$API" --output "$W/dex" $(find "$W/classes" -name '*.class' | sort)
touch -t 198001010000 "$W/dex/classes.dex"
rm -f "$OUT/wireless-mod.jar"
(cd "$W/dex" && zip -q -X "$OUT/wireless-mod.jar" classes.dex)
file "$OUT/libleafiaaac.so"
