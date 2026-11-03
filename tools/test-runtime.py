#!/usr/bin/env python3
"""Isolated checks of the shared root runtime."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from testlib import car_env, SHELL

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
    assert stdin.stdout == b"900150983cd24fb0d6963f7d28e17f72  -\n"


def script(path, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/bin/sh\n" + body)
    path.chmod(0o755)


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
    script(fake / "setprop", 'printf %s "$2" > "$FAKE_LIVE/$1"\n')
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
sg_prop_baseline absent
sg_prop_set absent changed
sg_prop_restore absent
[ ! -e "$SG_PROPERTY_ROOT/absent" ]
[ -z "$(getprop absent)" ]
printf '' > "$SG_PROPERTY_ROOT/empty"
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
printf changed > "{target}"
sg_file_mark_applied "{target}"
sg_file_restore "{target}"
cmp -s "{target}" "$HMG_SEOGAEMO_STATE/file.$(sg_file_key "{target}")/bytes"
sg_file_restore "{target}"
sg_file_baseline "{base / "drift-file"}"
printf ours > "{base / "drift-file"}"
sg_file_mark_applied "{base / "drift-file"}"
printf firmware > "{base / "drift-file"}"
if sg_file_restore "{base / "drift-file"}"; then exit 1; fi
[ "$(cat "{base / "drift-file"}")" = firmware ]
sg_file_baseline "{base / "missing"}"
printf changed > "{base / "missing"}"
sg_file_mark_applied "{base / "missing"}"
sg_file_restore "{base / "missing"}"
[ ! -e "{base / "missing"}" ]
sg_file_baseline "{base / "firmware-file"}"
printf ours > "{base / "firmware-file"}"
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
ls() {{ printf '%s\\n' '-rw-r--r-- 1000 1001 3 2024-01-02 03:04 sample'; }}
[ "$(sg_file_meta sample)" = '0644 1000 1001' ]
[ "$(sg_file_size sample)" = 3 ]
''')
    run(toolbox_runner, env=env)


def test_root(temp):
    base = temp / "root"
    (base / "bin").mkdir(parents=True)
    (base / "modules").mkdir()
    (base / "lib").mkdir()
    shutil.copy(ROOT / "nemesis/package/bin/hmg-seogaemo-root", base / "bin/hmg-seogaemo-root")
    shutil.copy(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
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
    (base / "modules/c.properties").write_text("restore=reboot\n")
    state = base / "state"
    env = dict(os.environ, SG_DATA_ROOT=str(state))
    root = base / "bin/hmg-seogaemo-root"
    run(root, "b", "apply", env=env, ok=False)
    run(root, "a", "apply", env=env)
    run(root, "b", "apply", env=env)
    run(root, "a", "watch", "boot", env=env)
    run(root, "a", "watch", "bogus", env=env, ok=False)
    run(root, "b", "watch", "boot", env=env, ok=False)
    run(root, "c", "apply", env=env, ok=False)
    run(root, "a", "revert", env=env, ok=False)
    run(root, "b", "revert", env=env)
    run(root, "a", "revert", env=env)
    run(root, "a", "watch", "boot", env=env, ok=False)
    run(root, "c", "apply", env=env)
    run(root, "c", "revert", env=env)
    assert (state / "desired/c").read_text() == "off"
    assert (state / "desired/a").read_text() == "off"
    assert (state / "desired/b").read_text() == "off"


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
    script(fake / "setprop", 'printf %s "$2" > "$FAKE_LIVE/$1"\n')
    script(fake / "stop", 'printf stopped > "$FAKE_LIVE/init.svc.$1"\n')
    script(fake / "start", 'printf running > "$FAKE_LIVE/init.svc.$1"\n')
    script(fake / "chown", 'exit 0\n')
    script(fake / "nemesis", 'echo "$*" >> "$FAKE_REMOUNTS"\n')
    # Toolbox mount accepts "-o bind source target".
    script(fake / "mount", '''
cp "$4" "$4.under"
cp "$3" "$4"
printf 'bind %s ext4 rw 0 0\n' "$4" > "$SG_MOUNTS_FILE"
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
    shutil.copy(ROOT / "nemesis/package/bin/hmg-seogaemo-root", base / "bin/hmg-seogaemo-root")
    (base / "NEMESIS.data").write_text("package-version=1.2.3\n")
    mounts = base / "mounts"
    mounts.write_text("")
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               FAKE_LIVE=str(live), FAKE_REMOUNTS=str(base / "remounts"),
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
    shutil.copy(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
    env["SG_EXPORT_ROOT"] = str(export_root)
    result = run(base / "bin/hmg-seogaemo-root", "export", "adb", env=env)
    assert "state=on" in result.stdout
    exported = next(export_root.glob("*/ledger/ledger.tsv"))
    assert exported.read_bytes() == ledger.read_bytes()
    assert (exported.parents[1] / "ro.build.product").read_text().strip() == "NEEV.KOR.260902"
    usb = base / "usb0"
    usb.mkdir()
    mounts.write_text(f"/dev/sda1 {usb} vfat rw 0 0\n")
    env["SG_USB_DIRS"] = str(usb)
    result = run(base / "bin/hmg-seogaemo-root", "export", "usb", env=env)
    assert "state=on" in result.stdout
    assert list(usb.glob("HMG-Seogaemo-export/*/ledger/ledger.tsv"))


def test_service_call_api(temp):
    base = temp / "service-call"
    state = base / "data/modules/skip-consent-screen"
    state.mkdir(parents=True)
    (base / "modules").mkdir()
    (base / "modules/skip-consent-screen.properties").write_text(
        "service_calls=com.hkmc.factory:82\n")
    fake = base / "fake"
    fake.mkdir()
    script(fake / "service", 'printf "%s %s\\n" "$2" "$3" >> "$FAKE_CALLS"; echo "Parcel(00000000)"\n')
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
    boot.write_text("boot-b")
    check = base / "check-boot"
    script(check, f'. "{ROOT / "nemesis/package/lib/common.sh"}"\nsg_service_call_this_boot\n')
    run(check, env=env, ok=False)


def test_reconcile_and_uninstall(temp):
    base = temp / "lifecycle"
    for folder in ("bin", "svc", "scripts", "lib"):
        (base / folder).mkdir(parents=True)
    shutil.copy(ROOT / "nemesis/package/svc/reconcile.sh", base / "svc/reconcile.sh")
    shutil.copy(ROOT / "nemesis/package/scripts/preuninstall.sh", base / "scripts/preuninstall.sh")
    shutil.copy(ROOT / "nemesis/package/lib/common.sh", base / "lib/common.sh")
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
    revert) printf off > "$SG_DATA_ROOT/desired/$1"; echo state=off ;;
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
        ready = state / "modules" / name / "prop.test" / "ready"
        ready.parent.mkdir(parents=True)
        ready.write_text("1")
    run(base / "svc/reconcile.sh", env=env)
    assert log.read_text().splitlines() == ["b revert", "a revert"]
    log.write_text("")
    shutil.rmtree(state / "modules")
    backup = state / "modules/b/backup-kept"
    backup.parent.mkdir(parents=True)
    backup.write_text("saved")
    for name in ("a", "b"):
        (state / "desired" / name).write_text("on")
    conflict = state / "modules/a"
    (conflict / "prop.test").mkdir(parents=True)
    (conflict / "prop.test/ready").write_text("1")
    (conflict / "build").write_text("OTHER.BUILD")
    run(base / "scripts/preuninstall.sh", env=env, ok=False)
    assert not log.exists() or log.read_text() == ""
    shutil.rmtree(conflict)
    env["FAKE_FAIL"] = "b:revert"
    run(base / "scripts/preuninstall.sh", env=env, ok=False)
    assert log.read_text().splitlines() == ["b revert"]
    assert backup.exists()
    log.write_text("")
    env["FAKE_FAIL"] = ""
    (state / "desired/b").write_text("on")
    run(base / "scripts/preuninstall.sh", env=env)
    assert log.read_text().splitlines() == ["b revert", "b status", "a revert", "a status"]


def test_watch_dispatch(temp):
    base = temp / "watch-dispatch"
    for folder in ("bin", "svc", "modules"):
        (base / folder).mkdir(parents=True)
    shutil.copy(ROOT / "nemesis/package/svc/reconcile.sh", base / "svc/reconcile.sh")
    (base / "modules.list").write_text("watcher\n")
    (base / "modules/watcher.properties").write_text("watch=boot,screen_on\n")
    state = base / "state"
    (state / "desired").mkdir(parents=True)
    (state / "desired/watcher").write_text("on")
    marker = base / "apk-installed"
    marker.write_text("1")
    fake = base / "fake"
    fake.mkdir()
    script(fake / "getprop", 'echo 1\n')
    script(fake / "pm", 'echo package:fake\n')
    script(fake / "dumpsys", 'cat "$FAKE_DISPLAY"\n')
    script(fake / "sleep", 'echo "mScreenOn=true" > "$FAKE_DISPLAY"\n')
    script(base / "bin/hmg-seogaemo-root", 'echo "$1 $2 $3" >> "$FAKE_CALLS"; echo state=on\n')
    display = base / "display"
    display.write_text("mScreenOn=false")
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               SG_DATA_ROOT=str(state), FAKE_DISPLAY=str(display),
               FAKE_CALLS=str(base / "calls"), FAKE_PM_MARKER=str(marker),
               HMG_SEOGAEMO_WATCH_MAX_POLLS="1")
    run(base / "svc/reconcile.sh", env=env)
    assert (base / "calls").read_text().splitlines() == [
        "watcher apply ", "watcher watch boot", "watcher watch screen_on"]
    (base / "calls").write_text("")
    env["HMG_SEOGAEMO_UPDATE_HEALTH"] = "1"
    run(base / "svc/reconcile.sh", env=env)
    assert (base / "calls").read_text().splitlines() == ["watcher apply "]


def test_lint(temp):
    safe = temp / "modules/safe/root/safe.sh"
    script(safe, 'sg_prop_set demo value\n')
    lint = ROOT / "tools/lint-module-root.py"
    assert subprocess.run(["python3", str(lint), str(safe)], capture_output=True).returncode == 0
    script(safe, 'setprop demo value\n')
    assert subprocess.run(["python3", str(lint), str(safe)], capture_output=True).returncode != 0
    package = temp / "nemesis/package/bin/bad.sh"
    script(package, 'head -c 5 /tmp/file\n')
    assert subprocess.run(["python3", str(lint), str(package)], capture_output=True).returncode != 0


def test_module_build_hook(temp):
    base = temp / "build-hook"
    (base / "app/src").mkdir(parents=True)
    (base / "app/res").mkdir()
    hook = base / "modules/example/build/build.sh"
    script(hook, 'printf built > modules/example/payload-built\n')
    result = subprocess.run(["make", "-f", str(ROOT / "Makefile"), "module-build"],
                            cwd=base, capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert (base / "modules/example/payload-built").read_text() == "built"


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
    manifest.write_text("owns_props=\nowns_files=" + ",".join(map(str, paths + guards)) +
                        "\nservices=\n")
    live = base / "live"
    (live / "ro.build.product").write_text("NEEV.KOR.260902")
    fake = base / "fake"
    script(fake / "getprop", 'cat "$FAKE_LIVE/$1" 2>/dev/null || true\n')
    script(fake / "chown", 'exit 0\n')
    cache = target / "data/dalvik-cache/system@framework@hmg-seogaemo-wireless-hook.jar@classes.dex"
    cache.write_bytes(b"previous-cache")
    app_dir = target / "data/data/com.daudio.app.dipo/app_hmg-seogaemo-wireless-dex"
    env = dict(os.environ, PATH=str(fake) + ":" + os.environ["PATH"],
               SG_DATA_ROOT=str(base / "data/nemesis/backup/hmg-seogaemo"),
               SG_TARGET_ROOT=str(target), HMG_SEOGAEMO_STATE=str(state),
               HMG_SEOGAEMO_BASE=str(base), HMG_SEOGAEMO_MODULE_DIR=str(payload),
               FAKE_LIVE=str(live))
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
printf 3 > "{guards[2]}"
printf generated > "{cache}"
mkdir -p "{app_dir}"
printf generated > "{app_dir / 'classes.dex'}"
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
printf external > "{paths[2]}"
if sg_revert_all; then exit 1; fi
[ "$(cat "{paths[0]}")" = wrapper ]
[ "$(cat "{paths[1]}")" = stock-executable ]
''')
    run(runner, env=env)


with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    test_toolbox_md5(root)
    test_common(root)
    test_root(root)
    test_mutation_api_and_export(root)
    test_service_call_api(root)
    test_reconcile_and_uninstall(root)
    test_watch_dispatch(root)
    test_lint(root)
    test_module_build_hook(root)
    test_zygote_and_dex(root)
print("shared runtime tests passed")
