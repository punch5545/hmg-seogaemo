#!/usr/bin/env python3
"""Export diagnostics: bounded block reads, redaction, and probes that cannot hang."""
import os
from pathlib import Path
import subprocess
import tempfile
import time

from testlib import SHELL, car_env, copy_shell_script, host_shell_script

ROOT = Path(__file__).resolve().parents[1]


def alive(pid):
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    return True


with tempfile.TemporaryDirectory(prefix="hmg-seogaemo-diagnostics-") as directory:
    temp = Path(directory).resolve()
    base, data, out = temp / "package", temp / "data", temp / "out"
    for path in (base / "lib", base / "bin", data, out):
        path.mkdir(parents=True)
    copy_shell_script(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
    copy_shell_script(ROOT / "nemesis/package/lib/diagnostics.sh", base / "lib/diagnostics.sh")
    (base / "modules.list").write_text("demo\n")
    # "map status" leaves a grandchild holding the probe output and never exits.
    (base / "bin/hmg-seogaemo-root").write_text(host_shell_script('''#!/system/bin/sh
case "$1 $2" in
    "map status")
        echo state=on
        sleep 100 &
        echo $! > "$SG_DATA_ROOT/grandchild"
        wait;;
    *) echo state=off; echo "ssid=secret";;
esac
'''))
    big = "".join(f"line {n:06d} filler text\n" for n in range(4000))
    (data / "reconcile.log").write_text(big + "password=hunter2\nTAIL-MARKER\n")
    (data / "watcher.log").write_text("short log\npsk=hunter2\nkept\n")
    # Every exported line is redacted; a log of nothing but short secrets grows
    # when redacted and must still honor the 64 KiB cap.
    (data / "update").mkdir()
    (data / "update/log").write_text("pass\n" * 4000)

    caller = temp / "caller.sh"
    caller.write_text(host_shell_script(f'''#!/system/bin/sh
base={base}
SG_DATA_ROOT={data}
HMG_SEOGAEMO_BASE=$base
HMG_SEOGAEMO_STATE={data}/core
export base SG_DATA_ROOT HMG_SEOGAEMO_BASE HMG_SEOGAEMO_STATE
. "$base/lib/common.sh"
. "$base/lib/diagnostics.sh"
hmg_seogaemo_diagnostics {out}
'''))
    env = car_env()
    started = time.monotonic()
    # Capture through a pipe: a grandchild holding it would block until it exits.
    result = subprocess.run([SHELL, str(caller)], env=env, capture_output=True, text=True, timeout=150)
    elapsed = time.monotonic() - started
    grandchild = int((data / "grandchild").read_text())
    try:
        assert result.returncode == 0, result
        assert elapsed < 80, f"probe guard did not bound the export: {elapsed:.1f}s"
        diagnostics = out / "diagnostics"
        manifest = (diagnostics / "manifest.txt").read_text()
        assert "probe exit=" in manifest and "map-status.txt" in manifest, manifest
        assert "state=on" in (diagnostics / "map-status.txt").read_text()
        assert "secret" not in (diagnostics / "module-demo-status.txt").read_text()
        assert not list(diagnostics.glob(".*")), "temporary staging files leaked"

        tail = (diagnostics / "logs/reconcile.log").read_text()
        assert 0 < len(tail.encode()) <= 65536
        assert tail.endswith("[redacted sensitive line]\nTAIL-MARKER\n"), tail[-80:]
        assert "hunter2" not in tail and "line 003999 filler text" in tail
        assert "truncated to last 65536 bytes: logs/reconcile.log" in manifest
        # A torn first line (a tail may start mid-credential) is discarded.
        assert tail.startswith("line ")
        assert (diagnostics / "logs/watcher.log").read_text() == \
            "short log\n[redacted sensitive line]\nkept\n"
        assert (diagnostics / "update/log").stat().st_size <= 65536
        # On a system with /proc the guard also stops the grandchild.
        if Path("/proc/self/stat").exists():
            deadline = time.monotonic() + 5
            while alive(grandchild) and time.monotonic() < deadline:
                time.sleep(0.1)
            assert not alive(grandchild), "probe grandchild survived the guard"
    finally:
        if alive(grandchild):
            os.kill(grandchild, 9)
print("diagnostics bounded reads and probe guard passed")
