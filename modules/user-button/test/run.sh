#!/bin/sh
set -eu
module_dir=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
python3 "$module_dir/test/test-module.py"
python3 "$module_dir/test/test-receiver.py"
