#!/bin/sh
# Modified by HMG-Seogaemo: module-local entry for the packaged source build.
set -eu
HERE=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
exec sh "$HERE/payload/build.sh" "$@"
