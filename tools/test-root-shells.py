#!/usr/bin/env python3
"""Run the on-car regression suites with POSIX sh and mksh when installed."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
shells = ["/bin/sh"]
mksh = shutil.which("mksh")
if mksh:
    shells.append(mksh)
for shell in shells:
    print(f"Testing root scripts with {shell}", flush=True)
    env = dict(os.environ, HMG_SEOGAEMO_TEST_SHELL=shell)
    for suite in ("test-map-root.py", "test-package.py", "test-runtime.py", "test-stock.py"):
        args = sys.argv[1:] if suite == "test-package.py" else []
        subprocess.run([sys.executable, str(root / "tools" / suite), *args],
                       cwd=root, env=env, check=True)
    for suite in sorted((root / "modules").glob("*/test/run.sh")):
        # Fixture setup is host code. Its run() uses the explicit car shell.
        subprocess.run(["/bin/sh", str(suite)], cwd=root, env=env, check=True)
