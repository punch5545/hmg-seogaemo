#!/usr/bin/env python3
"""Installed module packages through the real dispatcher, runtime and reconcile."""
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile
from testlib import car_env, host_shell_tree, SHELL

ROOT = Path(__file__).resolve().parents[1]
PRODUCT = "NEEV.KOR.0000.V039.001.260902"

SAMPLE = {
    "module.properties": "id=sample\nversion=1.0.0\napi=1\ncars=NEEV.KOR.*.260902\nrerun_on=boot\nicon=wifi\n"
                         "uses=wifi.radio:ap\nsetting.1=audio:toggle:off\nsetting.2=mode:choice(fast|safe):safe\n",
    "apply.sh": "set_prop --reassert persist.test.mode {setting:mode}\n"
                "if setting_is audio on; then\n    set_prop persist.test.extra on\nfi\n",
    "status.sh": "info mode setting mode\n",
    "ui.txt": "status\ntoggle setting=audio label=@audio\n",
    "strings/en.txt": "name=Sample\nsummary=Test\naudio=Audio\n",
    "strings/ko.txt": "name=샘플\n",
}


def script(path, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/bin/sh\n" + body)
    path.chmod(0o755)


class Car:
    def __init__(self, temp):
        self.temp = temp
        self.base = temp / "package"
        shutil.copytree(ROOT / "nemesis/package", self.base)
        host_shell_tree(self.base)
        for path in self.base.rglob("*"):
            if path.is_file() and path.suffix in (".sh", "") and b"random/boot_id" in path.read_bytes():
                path.write_text(path.read_text().replace("/proc/sys/kernel/random/boot_id", str(temp / "boot_id")))
        (self.base / "modules").mkdir(exist_ok=True)
        (self.base / "modules.list").write_text("builtin-a\n")
        (self.base / "modules.graph").write_text("builtin-a\tdepends=\texcludes=\n")
        (self.base / "modules.lines").write_text("")
        (self.base / "modules/builtin-a.properties").write_text(
            "id=builtin-a\nversion=1.0.0\nowns_props=persist.builtin\n"
            "owns_files=/system/etc/builtin.conf\nservices=\ndepends=\nexcludes=\n")
        script(self.base / "modules/builtin-a.sh", "echo state=off; echo restart=none; echo detail=off\n")
        self.data = temp / "data"
        self.live = temp / "live"
        self.inbox = temp / "inbox"
        for path in (self.data, self.live, self.inbox, temp / "property"):
            path.mkdir()
        classes = temp / "classes"
        subprocess.run(["javac", "-nowarn", "-d", str(classes),
                        str(ROOT / "core/hsm/src/com/seogaemo/hmg/core/hsm/HmgSeogaemoHsmExtract.java")],
                       check=True, capture_output=True)
        fake = temp / "fake"
        script(fake / "getprop", 'cat "$FAKE_LIVE/$1" 2>/dev/null || true\n')
        script(fake / "setprop", 'printf %s "$2" > "$FAKE_LIVE/$1"\n')
        script(fake / "app_process", f'shift; cls=$1; shift; exec java -cp "{classes}" "$cls" "$@"\n')
        script(fake / "pm", "echo package:fixture\n")
        self.prop("ro.build.product", PRODUCT)
        self.prop("ro.build.version.sdk", "19")
        self.prop("sys.boot_completed", "1")
        self.prop("persist.test.mode", "stock-mode")
        self.prop("persist.test.extra", "stock-extra")
        self.env = car_env(dict(os.environ, PATH=f"{fake}:{os.environ['PATH']}", FAKE_LIVE=str(self.live),
                                SG_DATA_ROOT=str(self.data), SG_PROPERTY_ROOT=str(temp / "property"),
                                SG_HSM_INBOX=str(self.inbox), SG_BOOT_ID_FILE=str(temp / "boot_id")))
        (temp / "boot_id").write_text("boot-1\n")

    def prop(self, key, value=None):
        if value is not None:
            (self.live / key).write_text(value)
        path = self.live / key
        return path.read_text() if path.exists() else ""

    def root(self, *args, code=0):
        result = subprocess.run([SHELL, str(self.base / "bin/hmg-seogaemo-root"), *args], env=self.env,
                                capture_output=True, text=True, timeout=120)
        assert result.returncode == code, (args, result.returncode, result.stdout, result.stderr)
        return result.stdout

    def offer(self, files, digest_name=None):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            for name, text in files.items():
                archive.writestr(name, text)
        data = buffer.getvalue()
        digest = hashlib.sha256(data).hexdigest()
        (self.inbox / f"{digest_name or digest}.hsm").write_bytes(data)
        return digest_name or digest


def variant(**changes):
    files = dict(SAMPLE)
    files.update(changes)
    return {name: text for name, text in files.items() if text is not None}


def test_install_validation(car):
    assert "state=failed" in car.root("module", "install", "0" * 64, code=1)
    wrong = car.offer(SAMPLE, digest_name="1" * 64)
    assert "digest mismatch" in car.root("module", "install", wrong, code=1)
    bad_key = variant(**{"module.properties": SAMPLE["module.properties"] + "owns_props=persist.x\n"})
    assert "HSM-E203" in car.root("module", "install", car.offer(bad_key), code=1)
    bad_icon = variant(**{"module.properties": SAMPLE["module.properties"].replace("icon=wifi", "icon=../x.png")})
    assert "HSM-E204 module.properties: bad value for icon" in car.root("module", "install", car.offer(bad_icon), code=1)
    builtin = variant(**{"module.properties": SAMPLE["module.properties"].replace("id=sample", "id=builtin-a")})
    assert "built-in" in car.root("module", "install", car.offer(builtin), code=3)
    collide = variant(**{"apply.sh": "put_file /system/etc/builtin.conf files/x.conf\n", "files/x.conf": "x"})
    assert "builtin-a" in car.root("module", "install", car.offer(collide), code=3)
    syntax = variant(**{"apply.sh": "if car_is X; then\n"})
    result = subprocess.run([SHELL, str(car.base / "bin/hmg-seogaemo-root"), "module", "install", car.offer(syntax)],
                            env=car.env, capture_output=True, text=True)
    assert result.returncode == 2 and "HSM-E105 apply.sh:1:" in result.stderr, result
    big = variant(**{"strings/en.txt": SAMPLE["strings/en.txt"] + "".join(f"k{i}={'x' * 60}\n" for i in range(60))})
    assert "HSM-E208" in car.root("module", "install", car.offer(big), code=1)
    bad_key = variant(**{"strings/en.txt": SAMPLE["strings/en.txt"] + "Bad-Key=x\n"})
    assert "HSM-E209" in car.root("module", "install", car.offer(bad_key), code=1)
    other_car = variant(**{"module.properties": SAMPLE["module.properties"].replace("NEEV.KOR.*.260902", "OTHER.*")})
    assert "state=incompatible" in car.root("module", "install", car.offer(other_car), code=4)
    assert not (car.data / "installed/sample").exists()


def test_lifecycle(car):
    out = car.root("module", "install", car.offer(SAMPLE))
    assert "Installed sample 1.0.0" in out, out
    generated = (car.data / "installed/sample.properties").read_text().splitlines()
    for line in ("owns_props=persist.test.mode,persist.test.extra", "reassert_props=persist.test.mode",
                 "watch=boot", "uses=wifi.radio:ap", "source=direct"):
        assert line in generated, generated
    listing = car.root("module", "list")
    assert "info.module_builtin_a=builtin-a," in listing and "info.module_sample=sample,1.0.0,direct" in listing
    described = car.root("module", "describe", "sample", "ko").splitlines()
    for line in ("info.version=1.0.0", "info.source=direct", "info.restore=exact", "info.uses=wifi.radio:ap", "info.icon=wifi",
                 "info.setting_1=audio:toggle:off", "info.ui_1=status", "info.ui_2=toggle setting=audio label=@audio",
                 "info.str_name=Sample", "info.str_name=샘플", "info.str_audio=Audio", "state=on"):
        assert line in described, described
    assert described.index("info.str_name=Sample") < described.index("info.str_name=샘플")
    assert car.root("module", "describe", "sample", "en").count("info.str_name=") == 1
    car.root("module", "describe", "sample", "KO", code=2)
    car.root("module", "describe", "builtin-a", "en", code=1)
    assert "state=off" in car.root("sample", "status")
    assert "state=on" in car.root("sample", "apply")
    assert car.prop("persist.test.mode") == "safe" and car.prop("persist.test.extra") == "stock-extra"
    assert "info.mode=safe" in car.root("sample", "status")
    car.root("sample", "set", "audio", "on")
    assert car.prop("persist.test.extra") == "on"
    car.root("sample", "set", "mode", "fast")
    assert car.prop("persist.test.mode") == "fast" and car.prop("persist.test.extra") == "on"
    car.root("sample", "set", "audio", "off")
    assert car.prop("persist.test.extra") == "stock-extra", "branch target survived a setting change"
    car.root("sample", "set", "mode", "turbo", code=2)
    car.root("builtin-a", "set", "mode", "fast", code=2)
    car.root("sample", "set", "MODE", "fast", code=2)

    clash = variant(**{"module.properties": SAMPLE["module.properties"].replace("id=sample", "id=clash")
                       .replace("wifi.radio:ap", "wifi.radio:sta"),
                       "apply.sh": "set_prop persist.test.clash on\n"})
    car.root("module", "install", car.offer(clash))
    assert "Conflicts with module on: sample" in car.root("clash", "apply", code=3)
    car.root("module", "remove", "clash")

    (car.data / "desired/sample").write_text("on")
    car.prop("persist.test.mode", "fast")
    assert "state=on" in car.root("sample", "watch", "boot")

    car.root("module", "install", car.offer(SAMPLE), code=3)
    out = car.root("module", "remove", "sample")
    assert "Removed sample" in out, out
    assert car.prop("persist.test.mode") == "stock-mode" and car.prop("persist.test.extra") == "stock-extra"
    assert "sample" not in (car.data / "installed.list").read_text()
    assert not (car.data / "installed/sample").exists() and not (car.data / "desired/sample").exists()
    car.root("sample", "status", code=2)


def test_reconcile_reapplies(car):
    car.root("module", "install", car.offer(SAMPLE))
    car.root("sample", "apply")
    car.prop("persist.test.mode", "lost-on-reboot")
    (car.temp / "boot_id").write_text("boot-2\n")
    result = subprocess.run([SHELL, str(car.base / "svc/reconcile.sh")], env=car.env,
                            capture_output=True, text=True, timeout=120)
    assert result.returncode == 0, (result.stdout, result.stderr)
    assert car.prop("persist.test.mode") == "safe", (car.data / "reconcile.log").read_text() \
        if (car.data / "reconcile.log").exists() else result
    out = car.root("stock", "restore")
    assert "state=off" in out and "info.module_sample=reverted" in out, out
    assert car.prop("persist.test.mode") == "stock-mode"


def hooker(module_id, process):
    return {
        "module.properties": f"id={module_id}\nversion=1.0.0\napi=1\ncars=NEEV.KOR.*.260902\n",
        "apply.sh": f"hook_apps files/hook.jar demo.Entry {process} com.daudio.app.dipo:remote\n",
        "files/hook.jar": f"jar-{module_id}",
        "strings/en.txt": "name=Hooker\nsummary=Test\n",
    }


def test_hook_host(car):
    target = car.temp / "target"
    for sub in ("system/bin", "system/lib", "system/framework", "data/dalvik-cache"):
        (target / sub).mkdir(parents=True)
    process = target / "system/bin/app_process"
    process.write_bytes(b"stock-executable")
    process.chmod(0o755)
    data = car.base / "core/hook/data"
    data.mkdir(parents=True)
    for name, body in (("app_process", b"wrapper"), ("libhmg_seogaemo_hook.so", b"loader"), ("hmg-seogaemo-hook.jar", b"engine")):
        (data / name).write_bytes(body)
    fake = Path(car.env["PATH"].split(":")[1])
    for name in ("ls", "chown"):
        script(fake / name, f'exec {sys.executable} "{ROOT / "tools/testlib/fake-ownership.py"}" {name} "$@"\n')
    script(fake / "nemesis", "exit 0\n")
    car.env.update(SG_TARGET_ROOT=str(target), FAKE_OWNERS=str(car.temp / "owners.json"))
    extras = [target / "system/bin/app_process.hmg-seogaemo-stock", target / "system/lib/libhmg_seogaemo_hook.so",
              target / "system/framework/hmg-seogaemo-hook.jar"]
    mods = car.data / "core/hook/mods"

    car.root("module", "install", car.offer(hooker("hook-one", "com.android.settings")))
    generated = (car.data / "installed/hook-one.properties").read_text().splitlines()
    assert "hooks=1" in generated and "restart=head_unit" in generated, generated
    assert "excludes=wireless-carplay-and-aa" in generated, generated
    assert any(line.startswith("owns_files=") and f"{mods}/hook-one/entry" in line for line in generated), generated
    out = car.root("hook-one", "apply")
    assert "state=pending-restart" in out and "restart=head_unit" in out, out
    assert process.read_bytes() == b"wrapper" and extras[0].read_bytes() == b"stock-executable", out
    assert extras[1].read_bytes() == b"loader" and extras[2].read_bytes() == b"engine"
    assert (car.data / "modules/core-hook/enable").exists() and (car.data / "modules/core-hook/enabled").exists()
    assert (mods / "hook-one/entry").read_text() == "demo.Entry\n"
    assert (mods / "hook-one/processes").read_text() == "com.android.settings\ncom.daudio.app.dipo:remote\n"
    assert (mods / "hook-one/hook.jar").read_bytes() == b"jar-hook-one"
    (car.data / "core/hook/status").write_text("pid=1\ngate=ready 1\nmod.hook-one=loaded 2 process(es)\n")
    status = car.root("hook-one", "status")
    for line in ("info.hook_host=on", "info.hook_gate=ready 1", "info.hook_load=loaded 2 process(es)"):
        assert line in status, status
    # After the restart, the boot-time re-apply of an applied module must not ask for another one.
    (car.temp / "boot_id").write_text("boot-hook\n")
    out = car.root("hook-one", "apply")
    assert "state=on" in out, out
    assert "state=on" in car.root("hook-one", "status")

    car.root("module", "install", car.offer(hooker("hook-two", "com.mnsoft.navi")))
    car.root("hook-two", "apply")
    out = car.root("hook-one", "revert")
    assert "state=pending-restart" in out, out
    assert process.read_bytes() == b"wrapper", "wrapper removed while another hook module is on"
    assert not (mods / "hook-one/entry").exists() and (mods / "hook-two/entry").exists()
    car.root("hook-two", "revert")
    assert process.read_bytes() == b"stock-executable", "stock app_process not restored"
    assert not any(path.exists() for path in extras), extras
    assert not list(mods.glob("*/entry")) and not (car.data / "core/hook/dex").exists()
    assert not list((car.data / "modules/core-hook").glob("file.*/ready"))

    car.root("hook-one", "apply")
    assert process.read_bytes() == b"wrapper"
    out = car.root("stock", "restore")
    assert "info.module_hook_one=reverted" in out, out
    assert process.read_bytes() == b"stock-executable" and not any(path.exists() for path in extras), out
    car.root("module", "remove", "hook-one")
    car.root("module", "remove", "hook-two")


def test_button_action(car):
    pkg = variant(**{"module.properties": SAMPLE["module.properties"].replace("id=sample", "id=buttons")
                     .replace("rerun_on=boot\n", ""),
                     "apply.sh": "set_prop persist.test.mode on\nif action_is rescan; then\n"
                                 "    set_prop persist.test.extra rescanned\nfi\n",
                     "ui.txt": "button name=rescan label=@audio confirm=@audio\n"})
    car.root("module", "install", car.offer(pkg))
    assert "Turn the module on first" in car.root("buttons", "action", "rescan", code=3)
    car.root("buttons", "apply")
    (car.data / "desired/buttons").write_text("on")
    assert car.prop("persist.test.extra") == "stock-extra"
    assert "Unknown button" in car.root("buttons", "action", "other", code=1)
    car.root("buttons", "action", "Bad", code=2)
    car.root("builtin-a", "action", "rescan", code=2)
    out = car.root("buttons", "action", "rescan")
    assert "state=on" in out, out
    assert car.prop("persist.test.extra") == "rescanned"
    assert "state=off" in car.root("buttons", "revert")
    assert car.prop("persist.test.extra") == "stock-extra" and car.prop("persist.test.mode") == "stock-mode"


def test_usb_import(car):
    usb, unmounted, app = car.base.parent / "usb0", car.base.parent / "usb1", car.base.parent / "app-files"
    dest = app / "hsm-usb"
    for path in (usb, unmounted):
        path.mkdir()
    mounts = car.base.parent / "mounts"
    mounts.write_text(f"/dev/block/sda1 {usb} vfat rw 0 0\n")
    car.env.update(SG_USB_DIRS=f"{usb} {unmounted}", SG_MOUNTS_FILE=str(mounts), SG_HSM_USB_INBOX=str(dest))
    assert "App storage not ready" in car.root("module", "usb-import", code=1)
    app.mkdir()
    dest.mkdir()
    (dest / "stale.hsm").write_text("old")
    (usb / "theme-1.0.hsm").write_bytes(b"PK-theme")
    (usb / "UPPER.HSM").write_bytes(b"PK-upper")
    (usb / "bad name.hsm").write_bytes(b"PK")
    (usb / ".hidden.hsm").write_bytes(b"PK")
    (usb / "empty.hsm").write_bytes(b"")
    (usb / "notes.txt").write_text("x")
    (usb / "link.hsm").symlink_to(usb / "theme-1.0.hsm")
    (unmounted / "other.hsm").write_bytes(b"PK-other")
    out = car.root("module", "usb-import")
    assert "state=on" in out and "info.count=2" in out, out
    assert sorted(path.name for path in dest.iterdir()) == ["UPPER.hsm", "theme-1.0.hsm"]
    assert (dest / "theme-1.0.hsm").read_bytes() == b"PK-theme"
    assert oct(dest.stat().st_mode & 0o777) == "0o755"
    assert oct((dest / "theme-1.0.hsm").stat().st_mode & 0o777) == "0o644"
    (usb / "theme-1.0.hsm").unlink()
    (usb / "UPPER.HSM").unlink()
    assert "info.count=0" in car.root("module", "usb-import")
    assert list(dest.iterdir()) == []
    car.root("module", "usb-import", "x", code=2)


def main():
    for test in (test_install_validation, test_lifecycle, test_reconcile_reapplies, test_hook_host, test_usb_import,
                 test_button_action):
        with tempfile.TemporaryDirectory() as directory:
            test(Car(Path(directory).resolve()))
    print("installed module tests passed")


if __name__ == "__main__":
    sys.exit(main())
