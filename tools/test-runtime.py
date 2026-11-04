#!/usr/bin/env python3
"""Isolated checks of the shared root runtime."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
from testlib import car_env, SHELL, host_shell_script, copy_shell_script

ROOT = Path(__file__).resolve().parents[1]


def test_toolbox_md5(temp):
    sample = temp / "md5-sample"
    sample.write_bytes(b"abc")
    result = subprocess.run(["md5", str(sample)], env=car_env(), capture_output=True, text=True)
    assert result.returncode == 0
    assert result.stdout == f"900150983cd24fb0d6963f7d28e17f72  {sample}\n"
    missing = subprocess.run(["md5", str(temp / "missing")], env=car_env(), capture_output=True)
    assert missing.returncode != 0
    stdin = subprocess.run(["md5"], input=b"abc", env=car_env(), capture_output=True)
    assert stdin.returncode != 0 and not stdin.stdout
    piped = subprocess.run(["md5", "/proc/self/fd/0"], input=b"abc", env=car_env(), capture_output=True)
    assert piped.returncode == 0
    assert piped.stdout == b"900150983cd24fb0d6963f7d28e17f72  /proc/self/fd/0\n"


def script(path, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('#!/bin/sh\n. "' + str(ROOT / 'nemesis/package/lib/common.sh') + '"\n' + host_shell_script(body))
    path.chmod(0o755)


def test_file_key_failures(temp):
    runner = temp / 'file-key-runner'
    script(runner, '''
set -eu
key=$(sg_file_key abc)
[ "$key" = 900150983cd24fb0d6963f7d28e17f72 ]
md5() { echo malformed; }
if sg_file_key abc; then exit 1; fi
md5() { :; }
if sg_file_key abc; then exit 1; fi
md5() { return 1; }
if sg_file_key abc; then exit 1; fi
sg_check_build() { :; }
if sg_file_mark_applied /owned; then exit 1; fi
''')
    run(runner, env=dict(os.environ, HMG_SEOGAEMO_STATE=str(temp)))


def fake_ownership(fake):
    for name in ("ls", "chown"):
        script(fake / name, f'exec {sys.executable} "{ROOT / "tools/testlib/fake-ownership.py"}" {name} "$@"\n')
    script(fake / "nemesis", "exit 0\n")


def run(path, *args, env, ok=True):
    result = subprocess.run([SHELL, str(path), *args], env=car_env(env), text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if ok:
        assert result.returncode == 0, (path, args, result.stdout, result.stderr)
    else:
        assert result.returncode != 0, (path, args, result.stdout, result.stderr)
    return result


def test_common(temp):
    base = temp / "common"
    state = base / "state"
    live = base / "live"
    backing = base / "backing"
    fake = base / "fake"
    for path in (state, live, backing, fake):
        path.mkdir(parents=True)
    (base / "modules").mkdir()
    (base / "modules/state.properties").write_text(
        f"owns_props=absent,empty,drift,ro.build.product\n"
        f"owns_files={base / 'owned'},{base / 'drift-file'},{base / 'missing'},{base / 'firmware-file'}\n"
        "services=adbd\n")
    script(fake / "getprop", 'cat "$FAKE_LIVE/$1" 2>/dev/null || true\n')
    script(fake / "setprop", 'sg_write "$2" > "$FAKE_LIVE/$1"\n')
    (live / "ro.build.product").write_text("NEEV.KOR.260902")
    target = base / "owned"
    target.write_bytes(b"original\x00bytes")
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               FAKE_LIVE=str(live), SG_PROPERTY_ROOT=str(backing),
               HMG_SEOGAEMO_STATE=str(state), HMG_SEOGAEMO_BASE=str(base),
               HMG_SEOGAEMO_MODULE_DIR=str(base / "payload"), SG_DATA_ROOT=str(base / "data"))
    common = ROOT / "nemesis/package/lib/common.sh"
    runner = base / "runner"
    script(runner, f'''
set -eu
. "{common}"
# Fresh status probes must not create state or report a firmware conflict.
if sg_applied absent; then exit 1; fi
[ ! -e "$HMG_SEOGAEMO_STATE/module.log" ]
sg_prop_baseline absent
sg_prop_set absent changed
sg_prop_restore absent
[ ! -e "$SG_PROPERTY_ROOT/absent" ]
[ -z "$(getprop absent)" ]
sg_write '' > "$SG_PROPERTY_ROOT/empty"
sg_prop_baseline empty
sg_prop_set empty changed
sg_prop_restore empty
[ -f "$SG_PROPERTY_ROOT/empty" ] && [ ! -s "$SG_PROPERTY_ROOT/empty" ]
sg_prop_baseline drift
sg_prop_set drift ours
setprop drift theirs
if sg_prop_restore drift; then exit 1; fi
[ "$(getprop drift)" = theirs ]
sg_file_baseline "{target}"
sg_write changed > "{target}"
sg_file_mark_applied "{target}"
sg_file_restore "{target}"
cmp -s "{target}" "$HMG_SEOGAEMO_STATE/file.$(sg_file_key "{target}")/bytes"
sg_file_restore "{target}"
sg_file_baseline "{base / "drift-file"}"
sg_write ours > "{base / "drift-file"}"
sg_file_mark_applied "{base / "drift-file"}"
sg_write firmware > "{base / "drift-file"}"
if sg_file_restore "{base / "drift-file"}"; then exit 1; fi
[ "$(cat "{base / "drift-file"}")" = firmware ]
sg_file_baseline "{base / "missing"}"
sg_write changed > "{base / "missing"}"
sg_file_mark_applied "{base / "missing"}"
sg_file_restore "{base / "missing"}"
[ ! -e "{base / "missing"}" ]
sg_file_baseline "{base / "firmware-file"}"
sg_write ours > "{base / "firmware-file"}"
sg_file_mark_applied "{base / "firmware-file"}"
setprop ro.build.product OTHER.BUILD
if sg_file_restore "{base / "firmware-file"}"; then exit 1; fi
[ "$(cat "{base / "firmware-file"}")" = ours ]
setprop ro.build.product NEEV.KOR.260902
sg_log test
sg_baseline_clear
''')
    run(runner, env=env)
    assert target.read_bytes() == b"original\x00bytes"
    toolbox_runner = base / "toolbox-ls"
    script(toolbox_runner, f'''
set -eu
. "{common}"
ls() {{ sg_print '-rw-r--r-- 1000 1001 3 2024-01-02 03:04 sample'; }}
[ "$(sg_file_meta sample)" = '0644 1000 1001' ]
[ "$(sg_file_size sample)" = 3 ]
''')
    run(toolbox_runner, env=env)


def test_output_bytes(temp):
    punctuation = temp / 'status-punctuation'
    script(punctuation, "sg_status on adbd 'TCP ADB on port 5555; stock switch off'\n")
    assert run(punctuation, env=os.environ).stdout == (
        'state=on\nrestart=adbd\ndetail=TCP ADB on port 5555; stock switch off\n')
    base = temp / "output-bytes"
    restricted = base / "bin"
    restricted.mkdir(parents=True)
    # No external output formatter is available, even on hosts that ship one.
    for name in ("cat", "cp", "mv", "rm", "mkdir", "chmod", "chown", "ls", "dd", "cmp", "python3"):
        (restricted / name).symlink_to(shutil.which(name))
    (restricted / "md5").symlink_to(ROOT / "tools/testlib/fakebin/md5")
    common = ROOT / "nemesis/package/lib/common.sh"
    state = base / "state"
    runner = base / "runner"
    backing = base / "backing"
    backing.mkdir()
    cases = [b"", b"value", b"value\n", b"value\n\n", b"-n", b"-e",
             b"a\\nb\\c\\\\%s\t$()", "한국어\n".encode()]
    for index, value in enumerate(cases):
        env = dict(os.environ, PATH=str(restricted), SG_DATA_ROOT=str(base / "data"),
                   HMG_SEOGAEMO_STATE=str(state), SG_PROPERTY_ROOT=str(backing),
                   TEST_VALUE=value.decode(), TEST_DEST=str(base / "output"))
        script(runner, f'''
set -eu
. "{common}"
sg_write "$TEST_VALUE" > "$TEST_DEST"
sg_print "$TEST_VALUE" '' '-n' > "$TEST_DEST.lines"
''')
        run(runner, env=env)
        assert (base / "output").read_bytes() == value
        assert (base / "output.lines").read_bytes() == value + b"\n\n-n\n"
        # Baseline/restore files and property backing bytes independently of text output.
        target = base / f"target-{index}"
        prop = backing / "demo"
        target.write_bytes(value)
        prop.write_bytes(value)
        script(runner, f'''
set -eu
. "{common}"
getprop() {{ echo 'stock'; }}
setprop() {{ :; }}
sg_file_baseline "{target}"
sg_write changed > "{target}"
sg_file_mark_applied "{target}"
sg_file_restore "{target}"
sg_prop_baseline demo
sg_write changed > "$SG_PROPERTY_ROOT/demo"
sg_prop_restore demo
''')
        run(runner, env=env)
        assert target.read_bytes() == value
        assert prop.read_bytes() == value
        assert (state / "prop.demo/backing").read_bytes() == value
        shutil.rmtree(state)
    # Absence must remain distinct from a present zero-byte file.
    prop.unlink()
    target.unlink()
    run(runner, env=env)
    assert not target.exists() and not prop.exists()
    # A failed print write must propagate; it must not retry via the fallback.
    for helper in ("sg_write", "sg_print"):
        script(runner, f'''
. "{common}"
print() {{ echo partial; return 7; }}
{helper} test
''')
        assert run(runner, env=env, ok=False).stdout == "partial\n"


def test_root(temp):
    base = temp / "root"
    (base / "bin").mkdir(parents=True)
    (base / "modules").mkdir()
    (base / "lib").mkdir()
    copy_shell_script(ROOT / "nemesis/package/bin/hmg-seogaemo-root", base / "bin/hmg-seogaemo-root")
    copy_shell_script(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
    (base / "modules.list").write_text("a\nb\nc\n")
    (base / "modules.graph").write_text(
        "a\tdepends=\texcludes=c\n"
        "b\tdepends=a\texcludes=\n"
        "c\tdepends=\texcludes=a\n")
    for name in "abc":
        script(base / "modules" / (name + ".sh"),
               'case "$1" in apply) echo state=on;; revert) echo state=off;; '
               'status) echo state=off;; watch) echo state=on;; esac\necho restart=none\necho detail=\n')
    script(base / "modules/c.sh", '''
case "$1" in
    apply) echo state=on; echo restart=none ;;
    revert) echo state=pending-restart; echo restart=head_unit; echo info.desired=off ;;
    status) echo state=off; echo restart=none ;;
esac
echo detail=
''')
    (base / "modules/a.properties").write_text("watch=boot,screen_on\n")
    (base / "modules/c.properties").write_text("restore=exact\n")
    # All module entry points and the dispatcher are non-executable after extraction.
    for path in base.rglob("*"):
        if path.is_file():
            path.chmod(0o644)
    state = base / "state"
    env = dict(os.environ, SG_DATA_ROOT=str(state))
    root = base / "bin/hmg-seogaemo-root"
    fake = base / "fake"
    fake.mkdir()
    for command in ("sync", "reboot"):
        script(fake / command, 'echo ' + command + ' >> "$FAKE_DEVICE_CALLS"\n' +
               ('[ "${FAKE_SYNC_FAIL:-0}" = 0 ]\n' if command == "sync" else 'exit 0\n'))
    calls = base / "device-calls"
    device_env = dict(env, PATH=str(fake) + ":" + os.environ["PATH"], FAKE_DEVICE_CALLS=str(calls))
    for args in [("device", "bad"), ("device", "reboot", "extra")]:
        run(root, *args, env=device_env, ok=False)
    assert not calls.exists()
    run(root, "device", "reboot", env=device_env)
    assert calls.read_text() == "sync\nreboot\n"
    calls.write_text("")
    run(root, "device", "reboot", env=dict(device_env, FAKE_SYNC_FAIL="1"), ok=False)
    assert calls.read_text() == "sync\n"
    def refused(args, detail, code=3):
        response = run(root, *args, env=env, ok=False)
        assert response.returncode == code, response
        assert ('state=conflict' if code == 3 else 'state=failed') in response.stdout
        assert 'restart=none' in response.stdout
        assert 'detail=' + detail in response.stdout, response.stdout
    refused(("b", "apply"), "Dependency off: a")
    refused(("a", "bogus"), "Invalid command", 2)
    refused(("a", "status", "extra"), "Invalid command", 2)
    refused(("unknown", "status"), "Invalid command", 2)
    run(root, "a", "apply", env=env)
    status = run(root, "a", "status", env=env).stdout
    assert 'info.desired=on' in status
    run(root, "b", "apply", env=env)
    run(root, "a", "watch", "boot", env=env)
    run(root, "a", "watch", "bogus", env=env, ok=False)
    run(root, "b", "watch", "boot", env=env, ok=False)
    refused(("c", "apply"), "Excluded module on: a")
    refused(("a", "revert"), "Dependent on: b")
    run(root, "b", "revert", env=env)
    run(root, "a", "revert", env=env)
    assert 'info.desired=off' in run(root, "a", "status", env=env).stdout
    run(root, "a", "watch", "boot", env=env, ok=False)
    (state / "uninstall").mkdir()
    (state / "uninstall/pending").touch()
    refused(("c", "apply"), "Uninstall pending")
    (state / "uninstall/pending").unlink()
    run(root, "c", "apply", env=env)
    run(root, "c", "revert", env=env)
    assert (state / "desired/c").read_text() == "off"
    assert (state / "desired/a").read_text() == "off"
    assert (state / "desired/b").read_text() == "off"


def test_automatic_intent_lock(temp):
    for automatic, explicit, before, after in (
            ('apply', 'revert', 'on', 'off'), ('revert', 'apply', 'off', 'on')):
        base = temp / ('intent-' + automatic)
        for part in ('bin', 'lib', 'modules', 'state/desired', 'fake'):
            (base / part).mkdir(parents=True, exist_ok=True)
        dispatcher = base / 'bin/hmg-seogaemo-root'
        copy_shell_script(ROOT / 'nemesis/package/bin/hmg-seogaemo-root', dispatcher)
        # A private sleep wrapper makes lock contention observable even with
        # mksh's builtin sleep, without timing the race against the host clock.
        dispatcher.write_text(dispatcher.read_text().replace('umask 077', '''umask 077
sleep() { : > "$SG_DATA_ROOT/queued"; /bin/sleep 0.1; }''', 1))
        copy_shell_script(ROOT / 'nemesis/package/lib/common.sh', base / 'lib/common.sh')
        (base / 'modules.list').write_text('example\n')
        (base / 'modules.graph').write_text('example\tdepends=\texcludes=\n')
        (base / 'modules/example.properties').write_text('id=example\n')
        (base / 'modules/example.sh').write_text('''
echo "$1" >> "$SG_DATA_ROOT/calls"
: > "$SG_DATA_ROOT/entered"
while [ ! -f "$SG_DATA_ROOT/release" ]; do /bin/sleep 0.01; done
case "$1" in apply) echo state=on;; revert) echo state=off;; esac
echo restart=none
''')
        script(base / 'fake/pm', 'echo package:fixture\n')
        state = base / 'state'
        desired = state / 'desired/example'
        desired.write_text(before)
        env = car_env(dict(os.environ, SG_DATA_ROOT=str(state)))
        env['PATH'] = str(base / 'fake') + ':' + env['PATH']
        def wait_for(path):
            deadline = time.monotonic() + 5
            while not path.exists():
                assert time.monotonic() < deadline, str(path)
                time.sleep(0.005)
        user = subprocess.Popen([SHELL, str(dispatcher), 'example', explicit],
                                env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        queued = None
        try:
            wait_for(state / 'entered')
            queued = subprocess.Popen([SHELL, str(dispatcher), 'example', automatic],
                env=dict(env, HMG_SEOGAEMO_RECONCILE='1'),
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            wait_for(state / 'queued')
            (state / 'release').touch()
            assert user.communicate(timeout=5)[0].startswith('state=')
            assert user.returncode == 0
            output, error = queued.communicate(timeout=5)
            assert queued.returncode == 0, (output, error)
            assert 'state=not-implemented' in output and 'restart=none' in output, output
            assert 'info.skipped=true' in output and 'info.desired=' + after in output, output
            assert 'detail=Automatic module intent changed' in output, output
            assert desired.read_text() == after
            assert (state / 'calls').read_text().splitlines() == [explicit]
            assert (state / 'journal').read_text().split()[-1] == explicit
        finally:
            (state / 'release').touch()
            for child in (user, queued):
                if child is not None and child.poll() is None:
                    child.kill()
                    child.communicate()


def test_mutation_api_and_export(temp):
    base = temp / "api"
    state = base / "data/modules/test"
    state.mkdir(parents=True)
    for folder in ("modules", "payload", "fake", "bin"):
        (base / folder).mkdir()
    fake = base / "fake"
    live = base / "live"
    live.mkdir()
    (live / "ro.build.product").write_text("NEEV.KOR.260902")
    (live / "init.svc.adbd").write_text("running")
    script(fake / "getprop", 'cat "$FAKE_LIVE/$1" 2>/dev/null || true\n')
    script(fake / "setprop", 'sg_write "$2" > "$FAKE_LIVE/$1"\n')
    script(fake / "stop", 'sg_write stopped > "$FAKE_LIVE/init.svc.$1"\n')
    script(fake / "start", 'sg_write running > "$FAKE_LIVE/init.svc.$1"\n')
    fake_ownership(fake)
    script(fake / "nemesis", 'echo "$*" >> "$FAKE_REMOUNTS"\n')
    # Toolbox mount accepts "-o bind source target".
    script(fake / "mount", '''
cp "$4" "$4.under"
cp "$3" "$4"
sg_print "bind $4 ext4 rw 0 0" > "$SG_MOUNTS_FILE"
''')
    script(fake / "umount", 'cp "$1.under" "$1"; rm -f "$1.under"; : > "$SG_MOUNTS_FILE"\n')
    put = base / "put"
    replace = base / "replace"
    deleted = base / "deleted"
    bound = base / "bound"
    put.write_text("old")
    replace.write_text("before\nkeep\n")
    deleted.write_text("delete me")
    bound.write_text("under")
    (base / "payload/new").write_text("new")
    (base / "payload/bind").write_text("overlay")
    (base / "modules/test.properties").write_text(
        f"owns_props=demo\nowns_files={put},{replace},{deleted},{bound}\nservices=adbd\n")
    copy_shell_script(ROOT / "nemesis/package/bin/hmg-seogaemo-root", base / "bin/hmg-seogaemo-root")
    (base / "NEMESIS.data").write_text("package-version=1.2.3\n")
    mounts = base / "mounts"
    mounts.write_text("")
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               FAKE_OWNERS=str(base / "owners.json"), FAKE_LIVE=str(live), FAKE_REMOUNTS=str(base / "remounts"),
               SG_DATA_ROOT=str(base / "data"), HMG_SEOGAEMO_STATE=str(state),
               HMG_SEOGAEMO_BASE=str(base), HMG_SEOGAEMO_MODULE_DIR=str(base / "payload"),
               SG_PROPERTY_ROOT=str(base / "property"), SG_MOUNTS_FILE=str(mounts))
    (base / "property").mkdir()
    runner = base / "run-api"
    script(runner, f'''
set -eu
. "{ROOT / 'nemesis/package/lib/common.sh'}"
if sg_prop_set unowned evil; then exit 1; fi
if sg_file_put "{base / 'unowned'}" new; then exit 1; fi
sg_remount rw
sg_remount ro
sg_prop_set demo ours
sg_file_put "{put}" new
sg_file_replace_line "{replace}" before after
sg_file_delete "{deleted}"
chmod 0600 "$HMG_SEOGAEMO_MODULE_DIR/bind"
if sg_bind_mount "{bound}" bind; then exit 1; fi
chmod 0644 "$HMG_SEOGAEMO_MODULE_DIR/bind"
chown 1000:1000 "$HMG_SEOGAEMO_MODULE_DIR/bind"
if sg_bind_mount "{bound}" bind; then exit 1; fi
chown 0:0 "$HMG_SEOGAEMO_MODULE_DIR/bind"
sg_bind_mount "{bound}" bind
cp "{bound}.under" "{bound}"
: > "$SG_MOUNTS_FILE"
sg_reapply_binds
sg_service_restart adbd
sg_applied demo
sg_applied "{put}"
sg_applied "{bound}"
sg_revert_all
''')
    run(runner, env=env)

    assert put.read_text() == "old"
    assert replace.read_text() == "before\nkeep\n"
    assert deleted.read_text() == "delete me"
    assert bound.read_text() == "under"
    assert (live / "demo").read_text() == ""
    ledger = base / "data/ledger/ledger.tsv"
    rows = ledger.read_text().splitlines()
    assert len(rows) >= 8, rows
    assert all(len(row.split("\t")) == 7 for row in rows)
    assert (base / "data/ledger/before").is_dir()
    assert (base / "data/ledger/after").is_dir()
    assert (base / "remounts").read_text().splitlines() == ["remount rw", "remount ro"]
    export_root = base / "hmg-seogaemo-export"
    (base / "lib").mkdir(exist_ok=True)
    copy_shell_script(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
    export_root.mkdir(mode=0o700)
    env["SG_EXPORT_ROOT"] = str(export_root)
    result = run(base / "bin/hmg-seogaemo-root", "export", "adb", env=env)
    assert "state=on" in result.stdout
    exported = next(export_root.glob("*/ledger/ledger.tsv"))
    assert exported.read_bytes() == ledger.read_bytes()
    assert export_root.stat().st_mode & 0o777 == 0o755
    # Every exported ancestor is traversable and every file readable by shell.
    for entry in export_root.rglob("*"):
        assert entry.stat().st_mode & 0o777 == (0o755 if entry.is_dir() else 0o644)
    assert (exported.parents[1] / "ro.build.product").read_text().strip() == "NEEV.KOR.260902"
    usb = base / "usb0"
    usb.mkdir(mode=0o711)
    (usb / "HMG-Seogaemo-export").mkdir(mode=0o700)
    private_mode = state.stat().st_mode & 0o777
    mounts.write_text(f"/dev/sda1 {usb} vfat rw 0 0\n")
    env["SG_USB_DIRS"] = str(usb)
    result = run(base / "bin/hmg-seogaemo-root", "export", "usb", env=env)
    assert "state=on" in result.stdout
    assert list(usb.glob("HMG-Seogaemo-export/*/ledger/ledger.tsv"))
    assert usb.stat().st_mode & 0o777 == 0o711
    assert state.stat().st_mode & 0o777 == private_mode
    for entry in [usb / "HMG-Seogaemo-export", *(usb / "HMG-Seogaemo-export").rglob("*")]:
        assert entry.stat().st_mode & 0o777 == (0o755 if entry.is_dir() else 0o644)


def test_service_call_api(temp):
    base = temp / "service-call"
    state = base / "data/modules/skip-consent-screen"
    state.mkdir(parents=True)
    (base / "modules").mkdir()
    (base / "modules/skip-consent-screen.properties").write_text(
        "service_calls=com.hkmc.factory:82\n")
    fake = base / "fake"
    fake.mkdir()
    script(fake / "service", 'sg_print "$2 $3" >> "$FAKE_CALLS"; echo "Parcel(00000000)"\n')
    boot = base / "boot-id"
    boot.write_text("boot-a")
    runner = base / "runner"
    script(runner, f'''
set -eu
. "{ROOT / 'nemesis/package/lib/common.sh'}"
if sg_service_call wrong.name 82; then exit 1; fi
if sg_service_call com.hkmc.factory 83; then exit 1; fi
if sg_service_call 'com.hkmc.factory;bad' 82; then exit 1; fi
if sg_service_call com.hkmc.factory 00082; then exit 1; fi
sg_service_call com.hkmc.factory 82
[ "$SG_SERVICE_CALL_RESPONSE" = 'Parcel(00000000)' ]
sg_service_call_this_boot
''')
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               SG_DATA_ROOT=str(base / "data"), HMG_SEOGAEMO_STATE=str(state),
               HMG_SEOGAEMO_BASE=str(base), SG_BOOT_ID_FILE=str(boot),
               HMG_SEOGAEMO_SERVICE_BIN=str(fake / "service"),
               FAKE_CALLS=str(base / "calls"))
    run(runner, env=env)
    assert (base / "calls").read_text() == "com.hkmc.factory 82\n"
    ledger = (base / "data/ledger/ledger.tsv").read_text()
    assert "service_call\tcom.hkmc.factory:82" in ledger
    assert "Parcel(00000000)" in "".join(
        path.read_text() for path in (base / "data/ledger/after").iterdir())
    # Exercise the full timeout path without spending fifteen real seconds.
    # This service ignores TERM; the runtime must KILL it before wait().
    (fake / 'service').write_text(
        '#!' + sys.executable + chr(10) +
        'import signal, time, os' + chr(10) +
        'signal.signal(signal.SIGTERM, signal.SIG_IGN)' + chr(10) +
        'open(os.environ["FAKE_CALLS"] + ".ready", "w").close()' + chr(10) +
        'time.sleep(60)' + chr(10))
    (fake / 'sleep').write_text(
        '#!' + sys.executable + chr(10) +
        'import os, time' + chr(10) +
        'deadline = time.monotonic() + 2' + chr(10) +
        'while not os.path.exists(os.environ["FAKE_CALLS"] + ".ready") and time.monotonic() < deadline: time.sleep(.01)' + chr(10) +
        'time.sleep(.01)' + chr(10))
    (fake / 'sleep').chmod(0o755)
    script(runner, '''
set -u
sleep() { "$FAKE_SLEEP" "$@"; }
if sg_service_call com.hkmc.factory 82; then exit 1; fi
[ "$(cat "$HMG_SEOGAEMO_STATE/last-call-result")" = timeout ]
''')
    timed = subprocess.run([SHELL, str(runner)], env=car_env(dict(env, FAKE_SLEEP=str(fake / 'sleep'))),
                           capture_output=True, timeout=6)
    assert timed.returncode == 0, timed.stderr
    assert b'timeout' in (base / 'data/ledger/ledger.tsv').read_bytes()
    boot.write_text("boot-b")
    check = base / "check-boot"
    script(check, f'. "{ROOT / "nemesis/package/lib/common.sh"}"\nsg_service_call_this_boot\n')
    run(check, env=env, ok=False)


def test_reconcile(temp):
    base = temp / "lifecycle"
    for folder in ("bin", "svc", "scripts", "lib"):
        (base / folder).mkdir(parents=True)
    copy_shell_script(ROOT / "nemesis/package/svc/reconcile.sh", base / "svc/reconcile.sh")
    copy_shell_script(ROOT / "nemesis/package/scripts/preuninstall.sh", base / "scripts/preuninstall.sh")
    copy_shell_script(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
    (base / "modules.list").write_text("a\nb\n")
    state = base / "state"
    (state / "desired").mkdir(parents=True)
    fake = base / "fake"
    fake.mkdir()
    script(fake / "getprop", 'echo 1\n')
    script(fake / "pm", 'case "$1" in path) [ -f "$FAKE_PM_MARKER" ] && echo package:fake;; uninstall) rm -f "$FAKE_PM_MARKER"; echo Success;; esac\n')
    log = base / "calls"
    marker = base / "apk-installed"
    marker.write_text("1")
    script(base / "bin/hmg-seogaemo-root", '''
echo "$1 $2" >> "$FAKE_CALLS"
case "$2" in
    revert) sg_write off > "$SG_DATA_ROOT/desired/$1"; echo state=off ;;
    apply) echo state=on ;;
    status) echo state=off ;;
esac
echo restart=none
echo detail=
[ "$FAKE_FAIL" != "$1:$2" ]
''')
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               SG_DATA_ROOT=str(state), FAKE_CALLS=str(log), FAKE_FAIL="",
               FAKE_PM_MARKER=str(marker))
    (state / "desired/a").write_text("on")
    (state / "desired/b").write_text("on")
    run(base / "svc/reconcile.sh", env=env)
    assert log.read_text().splitlines() == ["a apply", "b apply"]
    log.write_text("")
    for name in ("a", "b"):
        (state / "desired" / name).write_text("off")
        # Interrupted directory-only applies must be found in reverse discovery.
        ready = state / "modules" / name / ("dir.key" if name == "a" else "dir-order")
        ready.parent.mkdir(parents=True)
        ready.write_text("1")
    run(base / "svc/reconcile.sh", env=env)
    assert log.read_text().splitlines() == ["b revert", "a revert"]
    log.write_text("")


def test_watch_dispatch(temp):
    base = temp / "watch-dispatch"
    for folder in ("bin", "svc", "modules", "lib"):
        (base / folder).mkdir(parents=True)
    copy_shell_script(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
    # Newer mksh has a sleep builtin, so PATH alone cannot intercept it.
    script(base / "svc/watcher.sh",
           f'sleep() {{ "{base / "fake/sleep"}" "$@"; }}\n' +
           (ROOT / "nemesis/package/svc/watcher.sh").read_text())
    (base / "modules.list").write_text("watcher\nplain\n")
    (base / "modules/watcher.properties").write_text("watch=boot,screen_on\n")
    (base / "modules/plain.properties").write_text("id=plain\n")
    state = base / "state"
    (state / "desired").mkdir(parents=True)
    fake = base / "fake"
    fake.mkdir()
    script(fake / "getprop", 'echo 1\n')
    script(fake / "dumpsys", 'cat "$FAKE_DISPLAY"\n')
    # Each sleep flips the display on, so the second poll sees off -> on.
    script(fake / "sleep", 'echo "$1" >> "$FAKE_SLEEPS"; echo "mWakefulness=Awake" > "$FAKE_DISPLAY"\n')
    script(base / "bin/hmg-seogaemo-root", 'echo "$1 $2 $3" >> "$FAKE_CALLS"; echo state=on\n')
    display = base / "display"
    boot_id = base / "boot-id"
    boot_id.write_text("boot-a")
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               SG_DATA_ROOT=str(state), FAKE_DISPLAY=str(display),
               FAKE_CALLS=str(base / "calls"), FAKE_SLEEPS=str(base / "sleeps"),
               SG_BOOT_ID_FILE=str(boot_id), HMG_SEOGAEMO_WATCH_MAX_POLLS="3")
    calls = base / "calls"
    # Nothing enabled: idle with the long interval, never dispatch, never exit early.
    display.write_text("mWakefulness=Asleep")
    run(base / "svc/watcher.sh", env=env)
    assert not calls.exists()
    assert (base / "sleeps").read_text().splitlines() == ["300", "300"]
    (base / "sleeps").unlink()
    # Enabled: boot dispatched once, screen_on on the off -> on transition only.
    (state / "desired/watcher").write_text("on")
    (state / "desired/plain").write_text("on")
    display.write_text("mWakefulness=Asleep")
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text().splitlines() == ["watcher watch boot", "watcher watch screen_on"]
    assert (base / "sleeps").read_text().splitlines() == ["5", "5"]
    # Watcher restart in the same boot does not repeat the boot event; a new boot does.
    calls.write_text("")
    display.write_text("mWakefulness=Awake")
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text() == ""
    boot_id.write_text("boot-b")
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text().splitlines() == ["watcher watch boot"]
    # The watcher never applies or reverts.
    assert "apply" not in calls.read_text() and "revert" not in calls.read_text()
    # Unreadable display state never triggers a call.
    calls.write_text("")
    display.write_text("garbage")
    script(fake / "sleep", 'echo "$1" >> "$FAKE_SLEEPS"\n')
    boot_id.write_text("boot-c")
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text().splitlines() == ["watcher watch boot"]
    # Screen off-> on recognises both Android 4.4 policy and wakefulness keys.
    calls.write_text("")
    script(fake / "dumpsys", 'if [ -f "$FAKE_DISPLAY.n" ]; then echo mDisplayPowerRequest.policy=BRIGHT; else : > "$FAKE_DISPLAY.n"; echo mDisplayPowerRequest.policy=OFF; fi\n')
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text().splitlines() == ["watcher watch screen_on"]


    # Quick boot: same boot ID, always Awake, property drift alone dispatches.
    (base / "modules/watcher.properties").write_text(
        "watch=boot,screen_on\nowns_props=demo\nreassert_props=demo\n")
    prop_dir = state / "modules/watcher/prop.demo"
    prop_dir.mkdir(parents=True)
    (prop_dir / "ready").write_text("1")
    (prop_dir / "applied").write_text("our.action")
    actual = base / "actual-property"
    env["FAKE_PROPERTY"] = str(actual)
    script(fake / "getprop", 'if [ "$1" = sys.boot_completed ]; then echo 1; else cat "$FAKE_PROPERTY"; fi\n')
    script(fake / "dumpsys", 'echo mWakefulness=Awake\n')
    script(base / "bin/hmg-seogaemo-root", 'echo "$1 $2 $3" >> "$FAKE_CALLS"; echo our.action > "$FAKE_PROPERTY"; echo state=on\n')
    for drift in ("stock.reset", "user.choice"):
        actual.write_text(drift)
        calls.write_text("")
        run(base / "svc/watcher.sh", env=env)
        assert calls.read_text().splitlines() == ["watcher watch screen_on"]
        assert actual.read_text().strip() == "our.action"
    calls.write_text("")
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text() == ""  # matching value never dispatches
    (state / "desired/watcher").write_text("off")
    actual.write_text("user.choice")
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text() == "" and actual.read_text() == "user.choice"
    (state / "desired/watcher").write_text("on")
    (state / "stock").mkdir()
    (state / "stock/paused").touch()
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text() == ""
    (state / "stock/paused").unlink()
    (prop_dir / "applied").unlink()
    run(base / "svc/watcher.sh", env=env)
    assert calls.read_text() == ""  # never invent an assignment without a snapshot


def test_reconcile_does_not_watch(temp):
    text = (ROOT / "nemesis/package/svc/reconcile.sh").read_text()
    assert " watch " not in text and "dumpsys" not in text


def test_lint(temp):
    safe = temp / "modules/safe/root/safe.sh"
    script(safe, 'sg_prop_set demo value\n')
    lint = ROOT / "tools/lint-module-root.py"
    assert subprocess.run(["python3", str(lint), str(safe)], capture_output=True).returncode == 0
    script(safe, 'setprop demo value\n')
    assert subprocess.run(["python3", str(lint), str(safe)], capture_output=True).returncode != 0
    for body in ('sg_file_put /system/etc/private payload 0600 0:0\n',
                 'ASSET=/system/etc/hmg-seogaemo/test\nsg_file_put "$ASSET/private" payload 600 0:0\n'):
        script(safe, body)
        assert subprocess.run(["python3", str(lint), str(safe)], capture_output=True).returncode != 0
    script(safe, 'sg_file_put /system/etc/private payload 0600 0:0 # payload-mode: allow -- root-only boot secret\n')
    assert subprocess.run(["python3", str(lint), str(safe)], capture_output=True).returncode == 0
    package = temp / "nemesis/package/bin/bad.sh"
    lint = ROOT / "tools/lint-android44-shell.py"
    script(package, 'head -c 5 /tmp/file\n')
    assert subprocess.run(["python3", str(lint), str(package)], capture_output=True).returncode != 0
    for path in (safe, package):
        script(path, 'printf "%s\\n" value\n')
        assert subprocess.run(["python3", str(lint), str(path)], capture_output=True).returncode != 0


def test_module_build_hook(temp):
    base = temp / "build-hook"
    (base / "app/src").mkdir(parents=True)
    (base / "app/res").mkdir()
    hook = base / "modules/example/build/build.sh"
    script(hook, 'sg_write built > modules/example/payload-built\n')
    result = subprocess.run(["make", "-f", str(ROOT / "Makefile"), "module-build"],
                            cwd=base, capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert (base / "modules/example/payload-built").read_text() == "built"


def test_remount_ro_busy(temp):
    base = temp / "remount-busy"
    state = base / "state"
    fake = base / "fake"
    state.mkdir(parents=True)
    script(fake / "nemesis", '''
echo "$*" >> "$FAKE_REMOUNTS"
[ "$1 $2" != "remount ro" ] || exit 16
''')
    runner = base / "runner"
    script(runner, '''
set -eu
# A busy /system must not fail a write that was already verified.
sg_remount_ro
# The raw call still reports the failure to callers that need it.
if sg_remount ro; then exit 1; fi
grep -Fq 'warning: /system stays read-write until restart' "$HMG_SEOGAEMO_STATE/module.log"
''')
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"], FAKE_REMOUNTS=str(base / "remounts"),
               HMG_SEOGAEMO_STATE=str(state))
    run(runner, env=env)
    assert (base / "remounts").read_text().splitlines() == ["remount ro", "remount ro"]


def test_system_directory_mode(temp):
    base = temp / "dir-mode"
    base.mkdir()
    runner = base / "runner"
    fake = base / "fake"
    fake_ownership(fake)
    script(fake / "getprop", "echo firmware\n")
    system = base / "system"
    namespace = system / "etc/hmg-seogaemo"
    (system / "etc").mkdir(parents=True)
    for module in ("a", "b"):
        manifest = base / "modules" / f"{module}.properties"
        manifest.parent.mkdir(exist_ok=True)
        manifest.write_text(f"owns_files={namespace}/{module}/recipes/audio.recipe\n")
    payload = base / "payload"
    payload.mkdir()
    (payload / "recipe").write_text("patch")
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               FAKE_OWNERS=str(base / "owners.json"), SG_TARGET_ROOT=str(base),
               SG_DATA_ROOT=str(base / "data"), HMG_SEOGAEMO_BASE=str(base),
               HMG_SEOGAEMO_STATE=str(base / "data/modules/a"), HMG_SEOGAEMO_MODULE_DIR=str(payload))
    def check(body, ok=True):
        script(runner, "set -eu\numask 077\n" + body)
        return run(runner, env=env, ok=ok)
    apply = f'sg_file_put "{namespace}/a/recipes/audio.recipe" recipe 0644 0:0\n'
    check(apply + f'sg_public_readable "{namespace}/a/recipes/audio.recipe"\n')
    for target in (namespace, namespace / "a", namespace / "a/recipes"):
        assert target.stat().st_mode & 0o777 == 0o755
    check(f'HMG_SEOGAEMO_STATE="{base}/data/modules/b"\nsg_file_put "{namespace}/b/recipes/audio.recipe" recipe 0644 0:0\n')
    check("sg_revert_all\n")
    assert namespace.is_dir() and not (namespace / "a").exists()
    check(f'HMG_SEOGAEMO_STATE="{base}/data/modules/b"\nsg_revert_all\n')
    assert not namespace.exists()
    # Legacy 0700 ancestors: preserve their real baseline and repair traversal.
    (namespace / "a/recipes").mkdir(parents=True)
    for target in (namespace, namespace / "a", namespace / "a/recipes"):
        target.chmod(0o700)
    check(f'chown 1000:1001 "{namespace}" "{namespace}/a" "{namespace}/a/recipes"\n')
    # Interrupted chown/chmod sequence recognizes only the journaled intermediate.
    check(f'chmod() {{ [ "$2" != "{namespace}" ] || return 1; command chmod "$@"; }}\nsg_payload_dirs "{namespace}/a/recipes/audio.recipe"\n', ok=False)
    check("sg_revert_all\n")
    check(f'[ "$(sg_file_meta "{namespace}")" = "0700 1000 1001" ]\n')
    check(apply)
    # Directory drift refuses before any file restores.
    (namespace / "a/recipes").chmod(0o711)
    check("sg_revert_all\n", ok=False)
    assert (namespace / "a/recipes/audio.recipe").read_text() == "patch"
    (namespace / "a/recipes").chmod(0o755)
    # Simulate loss after metadata restoration, before the restored marker.
    check("sg_remount_ro() { exit 77; }\nsg_dirs_revert\n", ok=False)
    assert (namespace / "a/recipes").stat().st_mode & 0o777 == 0o700
    check("sg_dirs_preflight\n")
    (namespace / "a/recipes").chmod(0o711)
    check("sg_dirs_preflight\n", ok=False)
    (namespace / "a/recipes").chmod(0o700)
    # A failed restored-marker write also remains resumable.
    check('sg_write() { case "$1" in 1) return 1;; *) printf "%s" "$1";; esac; }\nsg_dirs_revert\n', ok=False)
    check("sg_revert_all\n")
    for target in (namespace, namespace / "a", namespace / "a/recipes"):
        assert target.stat().st_mode & 0o777 == 0o700
        check(f'[ "$(sg_file_meta "{target}")" = "0700 1000 1001" ]\n')
    shutil.rmtree(namespace)
    # A failed file copy still leaves directory-only material for rollback.
    script(fake / "cp", '[ "$1" != "$HMG_SEOGAEMO_MODULE_DIR/recipe" ] || exit 1\n/bin/cp "$@"\n')
    check(apply, ok=False)
    check("sg_restore_material\n")
    check("sg_revert_all\n")
    check("sg_restore_material\n", ok=False)
    check("sg_revert_all\n")  # Empty restore remains idempotent.
    assert not namespace.exists()
    (fake / "cp").unlink()
    check(apply)
    (namespace / "a/recipes/unrelated").write_text("external")
    check("sg_revert_all\n", ok=False)
    assert list((base / "data/payload-dirs").glob("*/ready"))
    (namespace / "a/recipes/unrelated").unlink()
    check("sg_revert_all\n")
    assert not namespace.exists()
    # Ancestor symlink substitution never follows into another tree.
    outside = base / "outside"
    outside.mkdir()
    namespace.symlink_to(outside, target_is_directory=True)
    check(apply, ok=False)
    assert not list(outside.iterdir())


def test_public_readable(temp):
    base = temp / "public-bits"
    directory = base / "parent"
    directory.mkdir(parents=True)
    target = directory / "recipe"
    target.write_text("patch")
    runner = base / "runner"
    env = dict(os.environ, SG_TARGET_ROOT=str(base))
    # All low permission digits: owner/group bits must never imply public access.
    for digit in range(8):
        target.chmod(0o660 + digit)
        script(runner, f'sg_public_readable "{target}"\n')
        run(runner, env=env, ok=digit >= 4)
    target.chmod(0o664)
    for digit in range(8):
        directory.chmod(0o770 + digit)
        run(runner, env=env, ok=bool(digit & 1))
    directory.chmod(0o755)


def test_file_metadata_guards(temp):
    base = temp / "file-metadata"
    fake = base / "fake"
    fake_ownership(fake)
    script(fake / "getprop", "echo firmware\n")
    (base / "modules").mkdir()
    target = base / "target"
    target.write_text("stock")
    payload = base / "payload"
    payload.mkdir()
    (payload / "new").write_text("ours")
    (base / "modules/a.properties").write_text(f"owns_props=demo\nowns_files={target}\n")
    backing = base / "property"
    backing.mkdir()
    (backing / "demo").write_bytes(b"stock backing\x00")
    (backing / "demo").chmod(0o600)
    script(fake / "getprop", 'if [ "$1" = demo ]; then cat "$SG_PROPERTY_ROOT/live" 2>/dev/null || :; else echo firmware; fi\n')
    script(fake / "setprop", 'sg_write "$2" > "$SG_PROPERTY_ROOT/live"\n')
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               FAKE_OWNERS=str(base / "owners.json"), SG_DATA_ROOT=str(base / "data"),
               SG_PROPERTY_ROOT=str(backing),
               HMG_SEOGAEMO_BASE=str(base), HMG_SEOGAEMO_STATE=str(base / "data/modules/a"),
               HMG_SEOGAEMO_MODULE_DIR=str(payload))
    runner = base / "runner"
    script(runner, f'''
set -eu
chown 1000:1001 "$SG_PROPERTY_ROOT/demo"
sg_prop_set demo ours
chmod 0644 "$SG_PROPERTY_ROOT/demo"
chown 0:0 "$SG_PROPERTY_ROOT/demo"
sg_prop_restore demo
[ "$(sg_file_meta "$SG_PROPERTY_ROOT/demo")" = "0600 1000 1001" ]
sg_file_put "{target}" new 0644 0:0
chmod 0600 "{target}"
if sg_applied "{target}"; then exit 1; fi
if sg_restore_preflight; then exit 1; fi
if sg_file_restore "{target}"; then exit 1; fi
chmod 0644 "{target}"
chown 1000:1000 "{target}"
if sg_applied "{target}"; then exit 1; fi
if sg_revert_all; then exit 1; fi
chown 0:0 "{target}"
rm "$HMG_SEOGAEMO_STATE/file.$(sg_file_key "{target}")/applied-meta"
sg_applied "{target}"
[ -f "$HMG_SEOGAEMO_STATE/file.$(sg_file_key "{target}")/applied-meta" ]
sg_revert_all
# A chown that lies must fail before replacing the live file.
chown() {{ :; }}
if sg_file_put "{target}" new 0644 1000:1000; then exit 1; fi
[ "$(cat "{target}")" = stock ]
chown() {{ return 1; }}
if sg_file_put "{target}" new 0644 0:0; then exit 1; fi
[ "$(cat "{target}")" = stock ]
''')
    run(runner, env=env)


def test_zygote_and_dex(temp):
    base = temp / "zygote"
    state = base / "data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa"
    target = base / "target"
    payload = base / "payload"
    manifest = base / "modules/wireless-carplay-and-aa.properties"
    for folder in (state, target / "system/bin", target / "system/lib",
                   target / "system/framework", target / "data/dalvik-cache",
                   payload, manifest.parent, base / "fake", base / "live"):
        folder.mkdir(parents=True, exist_ok=True)
    paths = [target / "system/bin/app_process",
             target / "system/bin/app_process.hmg-seogaemo-stock",
             target / "system/lib/libhmg_seogaemo_wireless_hook.so",
             target / "system/framework/hmg-seogaemo-wireless-hook.jar"]
    guards = [state / name for name in ("enable", "enabled", "starts", "disable")]
    paths[0].write_bytes(b"stock-executable")
    paths[0].chmod(0o755)
    (payload / "wrapper").write_bytes(b"wrapper")
    (payload / "loader").write_bytes(b"loader")
    (payload / "jar").write_bytes(b"jar")
    for source in payload.iterdir():
        source.chmod(0o644)
    manifest.write_text("owns_props=\nowns_files=" + ",".join(map(str, paths + guards)) +
                        "\nservices=\n")
    live = base / "live"
    (live / "ro.build.product").write_text("NEEV.KOR.260902")
    fake = base / "fake"
    script(fake / "getprop", 'cat "$FAKE_LIVE/$1" 2>/dev/null || true\n')
    fake_ownership(fake)
    cache = target / "data/dalvik-cache/system@framework@hmg-seogaemo-wireless-hook.jar@classes.dex"
    cache.write_bytes(b"previous-cache")
    app_dir = target / "data/data/com.daudio.app.dipo/app_hmg-seogaemo-wireless-dex"
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               SG_DATA_ROOT=str(base / "data/nemesis/backup/hmg-seogaemo"),
               SG_TARGET_ROOT=str(target), HMG_SEOGAEMO_STATE=str(state),
               HMG_SEOGAEMO_BASE=str(base), HMG_SEOGAEMO_MODULE_DIR=str(payload),
               FAKE_OWNERS=str(base / "owners.json"), FAKE_LIVE=str(live))
    common = ROOT / "nemesis/package/lib/common.sh"
    runner = base / "run-zygote"
    script(runner, f'''
set -eu
. "{common}"
sg_dex_cache_track /system/framework/hmg-seogaemo-wireless-hook.jar com.daudio.app.dipo
sg_zygote_install wrapper loader jar
[ "$(cat "{paths[1]}")" = stock-executable ]
[ -x "{paths[1]}" ]
[ "$(cat "{paths[0]}")" = wrapper ]
[ -x "{paths[0]}" ]
sg_write 3 > "{guards[2]}"
sg_write generated > "{cache}"
mkdir -p "{app_dir}"
sg_write generated > "{app_dir / 'classes.dex'}"
sg_revert_all
[ "$(cat "{paths[0]}")" = stock-executable ]
[ ! -e "{paths[1]}" ] && [ ! -e "{paths[2]}" ] && [ ! -e "{paths[3]}" ]
[ ! -e "{guards[0]}" ] && [ ! -e "{guards[1]}" ] && [ ! -e "{guards[2]}" ] && [ ! -e "{guards[3]}" ]
[ "$(cat "{cache}")" = previous-cache ]
[ ! -e "{app_dir}" ]
''')
    run(runner, env=env)
    ledger = base / "data/nemesis/backup/hmg-seogaemo/ledger/ledger.tsv"
    assert "zygote_starts" in ledger.read_text()
    assert "dex_generated" in ledger.read_text()
    # A failure at the final wrapper copy must leave stock executable intact.
    script(fake / "cp", '''
if [ "$1" = "$FAIL_SOURCE" ]; then exit 1; fi
/bin/cp "$@"
''')
    rollback_env = dict(env, FAIL_SOURCE=str(payload / "wrapper"))
    script(runner, f'''
set -eu
. "{common}"
if sg_zygote_install wrapper loader jar; then exit 1; fi
[ "$(cat "{paths[0]}")" = stock-executable ]
[ ! -e "{paths[1]}" ] && [ ! -e "{paths[2]}" ] && [ ! -e "{paths[3]}" ]
''')
    run(runner, env=rollback_env)
    (fake / "cp").unlink()
    # A drifted runtime must block the entire restore before app_process changes.
    script(runner, f'''
set -eu
. "{common}"
sg_zygote_install wrapper loader jar
sg_write external > "{paths[2]}"
if sg_revert_all; then exit 1; fi
[ "$(cat "{paths[0]}")" = wrapper ]
[ -x "{paths[0]}" ]
[ "$(cat "{paths[1]}")" = stock-executable ]
''')
    run(runner, env=env)


with tempfile.TemporaryDirectory() as directory:
    root = Path(directory).resolve()
    test_toolbox_md5(root)
    test_file_key_failures(root)
    test_common(root)
    test_output_bytes(root)
    test_root(root)
    test_automatic_intent_lock(root)
    test_mutation_api_and_export(root)
    test_service_call_api(root)
    test_reconcile(root)
    test_watch_dispatch(root)
    test_reconcile_does_not_watch(root)
    test_lint(root)
    test_module_build_hook(root)
    test_zygote_and_dex(root)
    test_remount_ro_busy(root)
    test_system_directory_mode(root)
    test_public_readable(root)
    test_file_metadata_guards(root)
print("shared runtime tests passed")
