#!/bin/sh
# Modified by HMG-Seogaemo: build the single-car projection hook payload.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=${ANDROID_BUILD_TOOLS:-$SDK/build-tools/34.0.0}
ANDROID_JAR=${ANDROID_JAR:-$SDK/platforms/android-19/android.jar}
CLANG=${CLANG:-clang}
OUT=$HERE/data
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$OUT" "$W/classes" "$W/stubs" "$W/syslib" "$W/dex" "$W/source"
"$CLANG" --target=armv7a-linux-gnueabi -mthumb -march=armv7-a -mfloat-abi=soft -Os -ffreestanding -fno-builtin -nostdlib -static -fno-stack-protector -fuse-ld=lld -Wl,-e,_start -Wl,--build-id=none -Wall -o "$OUT/app_process" "$HERE/native/zygote.c"
CLANG=$CLANG sh "$HERE/native/syslib-stubs.sh" "$W/syslib"
"$CLANG" --target=armv7a-linux-androideabi -mthumb -march=armv7-a -mfloat-abi=softfp -Os -fPIC -ffreestanding -fno-builtin -nostdlib -shared -fno-stack-protector -fuse-ld=lld -Wl,--hash-style=sysv -Wl,-z,defs -Wl,--build-id=none -Wl,-soname,libseogaemo_wireless_hook.so -o "$OUT/libseogaemo_wireless_hook.so" "$HERE/native/loader.c" "$W/syslib/libc.so" "$W/syslib/liblog.so" "$W/syslib/libdl.so"
sh "$HERE/native/build.sh" "$OUT/libleafiaaac.so"
find "$HERE/stubs" -name '*.java.src' -print | while IFS= read -r source; do
    rel=${source#"$HERE/stubs/"}; target="$W/source/stubs/${rel%.src}"
    mkdir -p "${target%/*}"; cp "$source" "$target"
done
find "$W/source/stubs" -name '*.java' -print > "$W/stubs.list"
javac -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" -d "$W/stubs" @"$W/stubs.list"
find "$HERE/src" -name '*.java.src' -print | while IFS= read -r source; do
    rel=${source#"$HERE/src/"}; target="$W/source/src/${rel%.src}"
    mkdir -p "${target%/*}"; cp "$source" "$target"
done
find "$W/source/src" -name '*.java' -print > "$W/sources.list"
javac -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR:$W/stubs" -d "$W/classes" @"$W/sources.list"
"$BT/d8" --release --min-api 19 --lib "$ANDROID_JAR" --classpath "$W/stubs" --output "$W/dex" $(find "$W/classes" -name '*.class' | sort)
touch -t 198001010000 "$W/dex/classes.dex"
rm -f "$OUT/seogaemo-wireless-hook.jar"
(cd "$W/dex" && zip -q -X "$OUT/seogaemo-wireless-hook.jar" classes.dex)
file "$OUT/app_process" "$OUT/libseogaemo_wireless_hook.so" "$OUT/libleafiaaac.so"
