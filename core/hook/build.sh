#!/bin/sh
# Builds the core hook host into out/package/core/hook/data and the module API jar into
# out/hook-sdk. The native wrapper/loader and the generic engine classes are the
# wireless-carplay-and-aa sources (AGPL-3.0, derived from HMG-API) built with core paths.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=${ANDROID_BUILD_TOOLS:-$SDK/build-tools/34.0.0}
ANDROID_JAR=${ANDROID_JAR:-$SDK/platforms/android-19/android.jar}
CLANG=${CLANG:-clang}
PAYLOAD=$ROOT/modules/wireless-carplay-and-aa/payload
OUT=$ROOT/out/package/core/hook/data
SDKOUT=$ROOT/out/hook-sdk
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$OUT" "$SDKOUT" "$W/src/hmgapi/hook" "$W/classes" "$W/dex" "$W/syslib"

"$CLANG" --target=armv7a-linux-gnueabi -mthumb -march=armv7-a -mfloat-abi=soft -Os -ffreestanding -fno-builtin \
    -nostdlib -static -fno-stack-protector -fuse-ld=lld -Wl,-e,_start -Wl,--build-id=none -Wall \
    '-DHOOKDIR="/data/nemesis/backup/hmg-seogaemo/modules/core-hook"' \
    '-DHOOKLIB="/system/lib/libhmg_seogaemo_hook.so"' '-DKTAG="hmg-seogaemo-hook"' \
    -o "$OUT/app_process" "$PAYLOAD/native/zygote.c"
CLANG=$CLANG sh "$PAYLOAD/native/syslib-stubs.sh" "$W/syslib"
"$CLANG" --target=armv7a-linux-androideabi -mthumb -march=armv7-a -mfloat-abi=softfp -Os -fPIC -ffreestanding \
    -fno-builtin -nostdlib -shared -fno-stack-protector -fuse-ld=lld -Wl,--hash-style=sysv -Wl,-z,defs \
    -Wl,--build-id=none -Wl,-soname,libhmg_seogaemo_hook.so \
    '-DJAR="/system/framework/hmg-seogaemo-hook.jar"' '-DTAG="HMG-Seogaemo-Hook"' \
    -o "$OUT/libhmg_seogaemo_hook.so" "$PAYLOAD/native/loader.c" "$W/syslib/libc.so" "$W/syslib/liblog.so" "$W/syslib/libdl.so"

for name in Bridge Hooks MethodHook Points LoadedApp ModEntry; do
    cp "$PAYLOAD/src/hmgapi/hook/$name.java.src" "$W/src/hmgapi/hook/$name.java"
done
cp "$ROOT"/core/hook/src/hmgapi/hook/*.java "$W/src/hmgapi/hook/"
javac -nowarn -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" -d "$W/classes" "$W"/src/hmgapi/hook/*.java
"$BT/d8" --release --min-api 19 --lib "$ANDROID_JAR" --output "$W/dex" $(find "$W/classes" -name '*.class' | sort)
touch -t 198001010000 "$W/dex/classes.dex"
rm -f "$OUT/hmg-seogaemo-hook.jar" "$SDKOUT/hmg-seogaemo-hook-api.jar"
(cd "$W/dex" && zip -q -X "$OUT/hmg-seogaemo-hook.jar" classes.dex)
(cd "$W/classes" && zip -q -X -r "$SDKOUT/hmg-seogaemo-hook-api.jar" hmgapi)

SAMPLE=$ROOT/out/hook-sample
rm -rf "$SAMPLE"
mkdir -p "$SAMPLE/classes" "$SAMPLE/dex"
cp -R "$ROOT/core/hook/sample/module" "$SAMPLE/module"
mkdir -p "$SAMPLE/module/files"
javac -nowarn -source 1.7 -target 1.7 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR:$SDKOUT/hmg-seogaemo-hook-api.jar" \
    -d "$SAMPLE/classes" $(find "$ROOT/core/hook/sample/src" -name '*.java' | sort)
"$BT/d8" --release --min-api 19 --lib "$ANDROID_JAR" --classpath "$SDKOUT/hmg-seogaemo-hook-api.jar" \
    --output "$SAMPLE/dex" $(find "$SAMPLE/classes" -name '*.class' | sort)
touch -t 198001010000 "$SAMPLE/dex/classes.dex"
(cd "$SAMPLE/dex" && zip -q -X "$SAMPLE/module/files/hook.jar" classes.dex)
