#!/bin/sh
set -eu
module_dir=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
python3 "$module_dir/../user-button-shortcut/test/test-module.py" "$module_dir" persist.sys.key.custom com.mobis.intent.action.CONNECTIVITY_SHORTCUT
