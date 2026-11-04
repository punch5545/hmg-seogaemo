#!/usr/bin/env python3
"""Run isolated root/module suites with bounded sh/mksh parallelism.

Each suite owns temporary fixtures and only reads the completed package. Keep
tests within a suite sequential, especially the runtime lock/deadline tests.
Set HMG_SEOGAEMO_TEST_JOBS=1 (or --jobs 1) for serial diagnosis.
"""
import argparse
from collections import deque
from datetime import datetime
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--jobs", type=int,
                        default=int(os.environ.get("HMG_SEOGAEMO_TEST_JOBS", "2")))
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    shells = ["/bin/sh"]
    mksh = shutil.which("mksh")
    if mksh:
        shells.append(mksh)
    # Long suites first avoid a final worker running alone. All fixtures use
    # private temporary paths, including two invocations of the same module.
    suites = ("test-runtime.py", "test-line-ownership.py", "test-stock.py",
              "test-reconcile-map.py", "test-package.py", "test-entry-dispatch.py",
              "test-map-root.py", "test-diagnostics.py")
    tasks = deque()
    for suite in suites:
        for shell in reversed(shells):
            extra = ["--aapt", args.aapt] if suite == "test-package.py" else []
            tasks.append((shell, [sys.executable, str(ROOT / "tools" / suite), *extra]))
    for suite in sorted((ROOT / "modules").glob("*/test/run.sh")):
        for shell in shells:
            # Fixture setup is host code; its run() selects the car shell.
            tasks.append((shell, ["/bin/sh", str(suite)]))

    logs = ROOT / "out/test-logs"
    logs.mkdir(parents=True, exist_ok=True)
    log_dir = Path(tempfile.mkdtemp(prefix="root-", dir=logs))
    print(f"Root suites: {len(tasks)}, workers: {args.jobs}, shells: {', '.join(shells)}", flush=True)
    print(f"Suite logs: {log_dir.relative_to(ROOT)}", flush=True)
    active = []
    start = time.monotonic()
    serial = 0
    try:
        while tasks or active:
            # Check every completed task before launching another after failure.
            for task in list(active):
                process, output, label, started, path = task
                status = process.poll()
                if status is None:
                    continue
                output.close()
                active.remove(task)
                elapsed = time.monotonic() - started
                stamp = datetime.now().strftime("%H:%M:%S")
                print(f"[{stamp}] {'PASS' if status == 0 else 'FAIL'} {label} ({elapsed:.2f}s)", flush=True)
                if status:
                    print(path.read_text(errors="replace"), file=sys.stderr, flush=True)
                    print(f"Failed suite log: {path}", file=sys.stderr, flush=True)
                    return 1
            while tasks and len(active) < args.jobs:
                shell, command = tasks.popleft()
                serial += 1
                path = log_dir / f"{serial:02d}-{Path(shell).name}-{Path(command[1]).parent.name}-{Path(command[1]).name}.log"
                output = path.open("w")
                label = f"{Path(command[1]).relative_to(ROOT)} [{shell}]"
                try:
                    process = subprocess.Popen(command, cwd=ROOT,
                        env=dict(os.environ, HMG_SEOGAEMO_TEST_SHELL=shell),
                        stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
                except BaseException:
                    output.close()
                    raise
                active.append((process, output, label, time.monotonic(), path))
                print(f"START {label}", flush=True)
            if active:
                time.sleep(0.05)
    finally:
        # Fail fast, including a suite's child shells/Java processes.
        for process, output, *_ in active:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        for process, output, *_ in active:
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                pass
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
            output.close()
    print(f"Root suites passed in {time.monotonic() - start:.2f}s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
