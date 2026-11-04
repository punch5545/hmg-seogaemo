#!/bin/sh
# Host JVM tests of payload diagnostics and per-part point enforcement.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/hmgapi/hook" "$T/src/hmgapi/engine" "$T/out"
for name in RuntimeResult Observations RequiredPoints Points NativePatch MethodHook; do
    cp "$HERE/../../payload/src/hmgapi/hook/$name.java.src" "$T/src/hmgapi/hook/$name.java"
done
cp "$HERE/../../payload/src/hmgapi/engine/MemRecipe.java.src" "$T/src/hmgapi/engine/MemRecipe.java"
javac -source 7 -target 7 -Xlint:-options -d "$T/out" $(find "$T/src" "$HERE/stubs" -name '*.java') "$HERE/RuntimeTest.java"
java -Drecipes="$HERE/../../payload/recipes" -cp "$T/out" hmgapi.hook.RuntimeTest "$T"
