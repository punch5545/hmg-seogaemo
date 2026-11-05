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
            try:
                os.kill(grandchild, 9)
            except ProcessLookupError:
                pass

    # A killed export must never leave raw (unredacted) bytes in the export
    # folder: raw content is staged in a private directory under the state tree.
    import signal
    (base / "bin/hmg-seogaemo-root").write_text(host_shell_script('''#!/system/bin/sh
case "$1 $2" in
    "map status")
        echo "ssid=secret-in-flight"
        if [ -f "$SG_DATA_ROOT/hang" ]; then
            sleep 100 &
            echo $! > "$SG_DATA_ROOT/grandchild-hang"
            : > "$SG_DATA_ROOT/inflight"
            wait
        fi
        echo state=on;;
    *) echo state=off;;
esac
'''))

    caller_text = caller.read_text()

    def tree_text(root):
        return b"".join(p.read_bytes() for p in root.rglob("*") if p.is_file())

    def start_hung_export(target):
        (data / "hang").touch()
        (data / "inflight").unlink(missing_ok=True)
        caller.write_text(caller_text.replace(str(out), str(target)))
        process = subprocess.Popen([SHELL, str(caller)], env=env, start_new_session=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        deadline = time.monotonic() + 60
        while not (data / "inflight").exists():
            assert process.poll() is None and time.monotonic() < deadline
            time.sleep(0.05)
        return process

    def assert_only_redacted(target, expect_staged):
        assert b"secret-in-flight" not in tree_text(target), "raw bytes reached the export folder"
        names = [p.name for p in target.rglob("*")]
        assert not [n for n in names if n.startswith(".") or n in ("raw", "probe", "stage")], names
        staged = [d for d in data.glob("diagnostics-tmp.*")]
        assert bool(staged) == expect_staged, staged
        for d in staged:
            assert (d.stat().st_mode & 0o777) == 0o700, oct(d.stat().st_mode)
            assert b"secret-in-flight" in tree_text(d)

    for sig, cleaned in ((signal.SIGTERM, True), (signal.SIGKILL, False)):
        target = temp / f"out-{sig.name}"
        target.mkdir()
        process = start_hung_export(target)
        assert_only_redacted(target, True)   # in flight: raw bytes only in the private stage
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            pass
        process.communicate(timeout=60)
        for pid_file in data.glob("grandchild-hang"):
            pid = int(pid_file.read_text())
            if alive(pid):
                try:
                    os.kill(pid, 9)
                except ProcessLookupError:
                    pass
            pid_file.unlink()
        assert_only_redacted(target, not cleaned)  # TERM trap removes it; KILL cannot
    # The next run sweeps the stale staging directory left by SIGKILL, but
    # leaves a live exporter's directory and unrelated names alone.
    assert len(list(data.glob("diagnostics-tmp.*"))) == 1
    foreign = data / "diagnostics-tmp.notapid"
    foreign.mkdir()
    live = data / f"diagnostics-tmp.{os.getpid()}"
    live.mkdir()
    (data / "hang").unlink()
    final = temp / "out-final"
    final.mkdir()
    caller.write_text(caller_text.replace(str(out), str(final)))
    result = subprocess.run([SHELL, str(caller)], env=env, capture_output=True, text=True, timeout=150)
    assert result.returncode == 0, result
    assert sorted(p.name for p in data.glob("diagnostics-tmp.*")) == sorted([foreign.name, live.name]), \
        list(data.glob("diagnostics-tmp.*"))
    assert b"secret-in-flight" not in tree_text(final)
print("diagnostics bounded reads and probe guard passed")
