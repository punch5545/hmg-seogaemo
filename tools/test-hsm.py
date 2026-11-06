#!/usr/bin/env python3
"""Restricted module script interpreter (nemesis/package/lib/hsm.sh)."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from testlib import car_env, SHELL

ROOT = Path(__file__).resolve().parents[1]
LIB = ROOT / "nemesis/package/lib"

RUNNER = r'''
. "$LIB/common.sh"
. "$LIB/hsm.sh"
getprop() {
    case "$1" in
        ro.build.product) sg_print NEEV.KOR.0000.V039.001.260902 ;;
        persist.test.live) sg_print live-value ;;
        *) sg_print '' ;;
    esac
}
sg_prop_set() { sg_print "CALL prop_set $1 $2"; [ "$1" != persist.test.fail ]; }
sg_payload_dirs() { sg_print "CALL payload_dirs $1"; }
sg_file_put() { sg_print "CALL file_put $1 $2 $3 $4"; }
sg_file_replace_line() { sg_print "CALL replace_line $1|$2|$3"; }
sg_service_restart() { sg_print "CALL service_restart $1"; }
sg_service_call() { sg_print "CALL service_call $1 $2"; }
sg_log() { sg_print "CALL log $*"; }
hsm_walk "$@"
'''

PROPERTIES = """id=sample
version=1.0.0
api=1
cars=NEEV.KOR.*.260902
setting.1=mode:choice(fast|safe):safe
setting.2=audio:toggle:off
setting.3=level:number(1..5):3
"""


def module(temp, scripts):
    pkg = temp / "pkg"
    shutil.rmtree(pkg, ignore_errors=True)
    (pkg / "strings").mkdir(parents=True)
    (pkg / "files/recipes").mkdir(parents=True)
    (pkg / "module.properties").write_text(PROPERTIES)
    (pkg / "strings/en.txt").write_text("name=Sample\nsummary=Test\nbusy=Busy\nwifi=Needs Wi-Fi\n")
    (pkg / "files/lib.so").write_bytes(b"x")
    (pkg / "files/hook.jar").write_bytes(b"jar")
    (pkg / "files/recipes/a.recipe").write_text("a")
    (pkg / "files/recipes/b.recipe").write_text("b")
    for name, text in scripts.items():
        (pkg / name).write_text(text)
    return pkg


def walk(temp, pkg, script, kind, op, settings=None, extra=None):
    data = temp / "data"
    (data / "desired").mkdir(parents=True, exist_ok=True)
    runner = temp / "runner.sh"
    runner.write_text(RUNNER)
    env = {"PATH": os.environ["PATH"], "LIB": str(LIB), "HMG_SEOGAEMO_MODULE_DIR": str(pkg), "SG_DATA_ROOT": str(data),
           "HMG_SEOGAEMO_BASE": str(temp / "base"), "HMG_SEOGAEMO_STATE": str(data / "modules/sample")}
    if settings is not None:
        sdir = temp / "settings"
        sdir.mkdir(exist_ok=True)
        for key, value in settings.items():
            (sdir / key).write_text(value)
        env["HSM_SETTINGS_DIR"] = str(sdir)
    env.update(extra or {})
    return subprocess.run([SHELL, str(runner), str(pkg / script), kind, op],
                          env=car_env(env), capture_output=True, text=True)


def calls(result):
    return [line[5:] for line in result.stdout.splitlines() if line.startswith("CALL ")]


def expect_error(temp, text, code, line, kind="apply"):
    pkg = module(temp, {"apply.sh": text})
    result = walk(temp, pkg, "apply.sh", kind, "validate")
    assert result.returncode == 2, (text, result)
    assert f"HSM-E{code} apply.sh:{line}:" in result.stderr, (text, result.stderr)


APPLY = '''# sample
set_prop persist.test.mode "{setting:mode}"
if setting_is audio on; then
    put_files /system/etc/sample/ files/recipes/
elif not file_missing /system/etc/sample.conf
then
    put_file /system/lib/libsample.so files/lib.so 0644
else
    replace_line /system/onebin/neev_kr/featurexml.cfg "-/system/etc/x.xml" "#-/system/etc/x.xml"
fi
if prop_is persist.test.live live-value; then
    call_service factory 82
fi
restart_service adbd
log done "quoted \\"text\\""
'''


def test_errors(temp):
    cases = [
        ("bogus x\n", 110, 1),
        ('set_prop a "b\n', 102, 1),
        ("if module_on wifi; then\n", 105, 1),
        ("if car_is X; then\nelse\nelse\nfi\n", 105, 3),
        ("if car_is X; then\nelse\nelif car_is Y; then\nfi\n", 105, 3),
        ("block @busy\n", 113, 1),
        ("put_file /system/lib/x.so files/missing.so\n", 112, 1),
        ("set_prop a {setting:nope}\n", 112, 1),
        ("set_prop ../x y\n", 112, 1),
        ("app_data_dir runtime com.daudio.app.dipo\n", 120, 1),
        ("hook_apps files/lib.so demo.Entry com.android.settings\n", 112, 1),
        ("hook_apps files/hook.jar Entry com.android.settings\n", 112, 1),
        ("hook_apps files/hook.jar demo.Entry\n", 111, 1),
        ('replace_line /system/x "{setting:mode}" new\n', 112, 1),
        ("if car_is X\nset_prop a b\nfi\n", 104, 2),
        ("set_prop a b; then\n", 104, 1),
        ("restart_service\n", 111, 1),
        ("call_service factory 70000\n", 112, 1),
        ("if setting_is mode turbo; then\nfi\n", 112, 1),
        ("set_prop a\tb\n", 103, 0),
        ("\n" * 512 + "log x\n", 101, 513),
    ]
    for text, code, line in cases:
        expect_error(temp, text, code, line)
    pkg = module(temp, {"check.sh": "warn @nope\n"})
    result = walk(temp, pkg, "check.sh", "check", "validate")
    assert result.returncode == 2 and "HSM-E112 check.sh:1:" in result.stderr, result


def test_touched(temp):
    pkg = module(temp, {"apply.sh": APPLY.replace("set_prop persist", "set_prop --reassert persist", 1)})
    result = walk(temp, pkg, "apply.sh", "apply", "touched")
    assert result.returncode == 0, result
    assert result.stdout.splitlines() == [
        "prop\tpersist.test.mode",
        "reassert\tpersist.test.mode",
        "file\t/system/etc/sample/a.recipe",
        "file\t/system/etc/sample/b.recipe",
        "file\t/system/lib/libsample.so",
        "line\t/system/onebin/neev_kr/featurexml.cfg:-/system/etc/x.xml",
        "call\tfactory:82",
        "service\tadbd",
    ], result.stdout


def test_run_branches(temp):
    pkg = module(temp, {"apply.sh": APPLY})
    on = walk(temp, pkg, "apply.sh", "apply", "run", settings={"audio": "on", "mode": "fast"})
    assert on.returncode == 0, on
    assert calls(on) == [
        "prop_set persist.test.mode fast",
        "payload_dirs /system/etc/sample/a.recipe",
        "file_put /system/etc/sample/a.recipe files/recipes/a.recipe 644 0:0",
        "payload_dirs /system/etc/sample/b.recipe",
        "file_put /system/etc/sample/b.recipe files/recipes/b.recipe 644 0:0",
        "service_call factory 82",
        "service_restart adbd",
        'log done quoted "text"',
    ], on.stdout
    off = walk(temp, pkg, "apply.sh", "apply", "run", settings={"audio": "off", "mode": "turbo"})
    assert off.returncode == 0, off
    assert calls(off)[0] == "prop_set persist.test.mode safe", off.stdout
    assert "replace_line /system/onebin/neev_kr/featurexml.cfg|-/system/etc/x.xml|#-/system/etc/x.xml" in calls(off)
    assert not any(call.startswith("file_put") for call in calls(off)), off.stdout


def test_run_stops_on_failure(temp):
    pkg = module(temp, {"apply.sh": "set_prop persist.test.fail x\nrestart_service adbd\n"})
    result = walk(temp, pkg, "apply.sh", "apply", "run")
    assert result.returncode == 1, result
    assert calls(result) == ["prop_set persist.test.fail x"], result.stdout


def test_rerun_needs_baseline(temp):
    pkg = module(temp, {"apply.sh": "set_prop persist.test.mode x\n"})
    result = walk(temp, pkg, "apply.sh", "apply", "run", extra={"HSM_RERUN": "1"})
    assert result.returncode == 1 and "prop_set" not in result.stdout, result
    ready = temp / "data/modules/sample/prop.persist.test.mode"
    ready.mkdir(parents=True)
    (ready / "ready").write_text("")
    result = walk(temp, pkg, "apply.sh", "apply", "run", extra={"HSM_RERUN": "1"})
    assert result.returncode == 0 and calls(result) == ["prop_set persist.test.mode x"], result


def test_check_and_status(temp):
    check = "warn @busy\nif uses_on wifi.radio:sta; then\n    block @wifi\nfi\nwarn @busy\n"
    status = "info mode setting mode\ninfo live prop persist.test.live\nif action_is reconnect; then\n    fail @busy\nfi\n"
    pkg = module(temp, {"check.sh": check, "status.sh": status})
    free = walk(temp, pkg, "check.sh", "check", "run")
    assert free.returncode == 0 and free.stdout.splitlines() == ["warn=@busy", "warn=@busy"], free
    other = temp / "base/modules/wifi.properties"
    other.parent.mkdir(parents=True)
    other.write_text("id=wifi\nuses=wifi.radio:sta,system.feature\n")
    (temp / "data/desired").mkdir(parents=True, exist_ok=True)
    (temp / "data/desired/wifi").write_text("on")
    blocked = walk(temp, pkg, "check.sh", "check", "run")
    assert blocked.returncode == 3 and blocked.stdout.splitlines() == ["warn=@busy", "block=@wifi"], blocked
    (temp / "data/desired/wifi").write_text("off")
    assert walk(temp, pkg, "check.sh", "check", "run").returncode == 0
    shown = walk(temp, pkg, "status.sh", "status", "run", settings={"mode": "fast"}, extra={"HSM_ACTION": "reconnect"})
    assert shown.returncode == 0 and shown.stdout.splitlines() == ["info.mode=fast", "info.live=live-value", "fail=@busy"], shown


def main():
    for test in (test_errors, test_touched, test_run_branches, test_run_stops_on_failure,
                 test_rerun_needs_baseline, test_check_and_status):
        with tempfile.TemporaryDirectory() as directory:
            test(Path(directory))
    print("hsm interpreter tests passed")


if __name__ == "__main__":
    sys.exit(main())
