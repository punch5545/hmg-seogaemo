#!/usr/bin/env python3
"""Off-car checks for the archives and shell interface."""
import argparse
import configparser
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

from package import DIST, ROOT, apk_version, verify
from version import read_version


def descriptor(data):
    parser = configparser.ConfigParser(interpolation=None)
    parser.read_string(data.decode())
    return parser


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("--aapt", required=True)
    args = parser.parse_args()
    version, code = read_version(ROOT / "VERSION")
    prefix = f"hmg-seogaemo-{version}"
    verify(args.aapt)
    package_path = DIST / f"{prefix}-nemesis-package.zip"
    usb_path = DIST / f"{prefix}-nemesis-usb.zip"
    ota_path = DIST / f"{prefix}-ota.zip"
    with zipfile.ZipFile(package_path) as package, zipfile.ZipFile(usb_path) as usb, zipfile.ZipFile(ota_path) as ota:
        entries = set(package.namelist())
        required = {"NEMESIS.data", "app/Seogaemo.apk", "bin/seogaemo-root", "bin/install-apk.sh",
                    "bin/open.sh", "scripts/postinstall.sh", "scripts/preuninstall.sh",
                    "svc/reconcile.sh", "lib/common.sh", "modules.list", "modules.graph"}
        assert required <= entries, required - entries
        assert set(usb.namelist()) == {"NEMESIS.data", "packages/seogaemo.zip"}
        assert usb.read("packages/seogaemo.zip") == package_path.read_bytes()
        assert ota.read("VERSION") == (version + "\n").encode()
        assert ota.read("app/Seogaemo.apk") == package.read("app/Seogaemo.apk")
        for name in ota.namelist():
            assert name == "VERSION" or name == "app/Seogaemo.apk" or name.startswith(("bin/", "lib/", "modules/", "core/", "svc/")) or name in ("modules.list", "modules.graph")
            if name in entries:
                assert ota.read(name) == package.read(name)
        usb_meta = descriptor(usb.read("NEMESIS.data"))
        assert dict(usb_meta["nemesis"]) == {"version": "1.0", "type": "install", "path": "packages/seogaemo.zip"}
        meta = descriptor(package.read("NEMESIS.data"))
        assert dict(meta["nemesis"]) == {
            "version": "1.1", "id": "seogaemo", "name": "HMG-Seogaemo", "type": "application,service",
            "package-version": version, "short-desc": '"Built-in controls for the Gen5W head unit."',
            "os": "NEEV.KOR.*.260902", "models": "neev_kr", "postinstall": "scripts/postinstall.sh",
            "preuninstall": "scripts/preuninstall.sh"}
        assert dict(meta["app.0"]) == {"kind": "sh", "path": "bin/open.sh", "uid": "root"}
        assert dict(meta["service.0"]) == {"name": "reconcile", "kind": "sh", "path": "svc/reconcile.sh", "uid": "root", "mode": "once"}
        ids = package.read("modules.list").decode().splitlines()
        assert ids and len(ids) == len(set(ids))
        assert len(package.read("modules.graph").decode().splitlines()) == len(ids)
        for name in ids:
            props = package.read(f"modules/{name}.properties").decode()
            assert f"\nid={name}\n" in "\n" + props
            assert f"\nversion={version}\n" in "\n" + props
            assert f"modules/{name}.sh" in entries
            payload = ROOT / "modules" / name / "payload"
            if payload.is_dir():
                assert f"modules/{name}.d/" in entries
                for source in payload.rglob("*"):
                    if source.is_file():
                        entry = f"modules/{name}.d/{source.relative_to(payload).as_posix()}"
                        assert package.read(entry) == source.read_bytes()
        for name in ids:
            with tempfile.TemporaryDirectory() as module_temp:
                module_root = Path(module_temp)
                (module_root / "lib").mkdir()
                (module_root / "modules").mkdir()
                (module_root / "lib/common.sh").write_bytes(package.read("lib/common.sh"))
                script = module_root / f"modules/{name}.sh"
                script.write_bytes(package.read(f"modules/{name}.sh"))
                for verb in ("status", "precheck"):
                    result = subprocess.run(["sh", str(script), verb],
                                            env=dict(os.environ, SEOGAEMO_BASE=str(module_root)),
                                            capture_output=True, text=True)
                    assert result.returncode == 0
                    assert result.stdout == "state=not-implemented\nrestart=none\ndetail=not implemented\n"
        with tempfile.TemporaryDirectory() as temp:
            temp = Path(temp)
            for name in entries:
                if name.endswith(".sh") or name == "bin/seogaemo-root":
                    path = temp / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(package.read(name))
                    subprocess.run(["sh", "-n", str(path)], check=True)
            # Run a copied dispatcher against an isolated fake module tree.
            (temp / "modules.list").write_text("good\n")
            (temp / "modules/good.sh").write_text("#!/bin/sh\nprintf 'action=%s\\nbase=%s\\nstate=%s\\nmodule_dir=%s\\n' \"$1\" \"$SEOGAEMO_BASE\" \"$SEOGAEMO_STATE\" \"$SEOGAEMO_MODULE_DIR\"\n")
            command = ["sh", str(temp / "bin/seogaemo-root")]
            result = subprocess.run(command + ["good", "status"], capture_output=True, text=True)
            expected = f"action=status\nbase={temp}\nstate=/data/nemesis/backup/seogaemo/modules/good\nmodule_dir={temp}/modules/good.d\n"
            assert result.returncode == 0 and result.stdout == expected
            result = subprocess.run(command + ["good", "precheck"], capture_output=True, text=True)
            assert result.returncode == 0 and result.stdout == expected.replace("status", "precheck")
            for bad in ([], ["bad", "status"], ["../good", "status"], ["good", "delete"], ["good", "status", "extra"]):
                assert subprocess.run(command + bad, capture_output=True).returncode != 0
            # A PackageManager text failure can exit zero. The boot property
            # still has to be restored in both outcomes.
            (temp / "app").mkdir()
            (temp / "app/Seogaemo.apk").write_bytes(b"test")
            fake = temp / "fake-bin"
            fake.mkdir()
            state = temp / "boot-property"
            for name, script in {
                "getprop": '#!/bin/sh\ncat "$FAKE_STATE"\n',
                "setprop": '#!/bin/sh\nprintf %s "$2" > "$FAKE_STATE"\n',
                "pm": '#!/bin/sh\ncase "$1" in install) echo "$FAKE_PM_RESULT";; path) echo package:/data/app/seogaemo.apk;; esac\n',
            }.items():
                path = fake / name
                path.write_text(script)
                path.chmod(0o755)
            env = dict(os.environ, PATH=f"{fake}:{os.environ['PATH']}", FAKE_STATE=str(state))
            for output, succeeds in (("Success", True), ("Failure [INSTALL_FAILED]", False)):
                state.write_text("1")
                env["FAKE_PM_RESULT"] = output
                result = subprocess.run(["sh", str(temp / "bin/install-apk.sh")], env=env, capture_output=True)
                assert (result.returncode == 0) == succeeds
                assert state.read_text() == "1"
        with tempfile.NamedTemporaryFile(suffix=".apk") as temp:
            temp.write(package.read("app/Seogaemo.apk"))
            temp.flush()
            assert apk_version(Path(temp.name), args.aapt) == (version, code)
        # Exercise the fixed updater transaction against a fake root tree.
        with tempfile.TemporaryDirectory() as fake_temp:
            fake_root = Path(fake_temp)
            package_root = fake_root / "package"
            (package_root / "core").mkdir(parents=True)
            (package_root / "bin").mkdir()
            (package_root / "app").mkdir()
            (package_root / "app/Seogaemo.apk").write_bytes(b"old apk")
            (package_root / "NEMESIS.data").write_bytes(package.read("NEMESIS.data"))
            (package_root / "bin/install-apk.sh").write_text("#!/bin/sh\nexit 0\n")
            updater = package.read("core/update.sh").decode()
            state = fake_root / "state"
            incoming = fake_root / "download"
            updater = updater.replace("state=/data/nemesis/backup/seogaemo", "state=" + str(state))
            updater = updater.replace("input=/data/data/com.seogaemo.app/files/update", "input=" + str(incoming))
            (package_root / "core/update.sh").write_text(updater)
            incoming.mkdir()
            for name in ("hmg-seogaemo-0.1.1-manifest.json", "hmg-seogaemo-0.1.1-manifest.json.sig", "hmg-seogaemo-0.1.1-ota.zip"):
                (incoming / name).write_bytes(b"test")
            fake_bin = fake_root / "bin"
            fake_bin.mkdir()
            (fake_bin / "app_process").write_text("#!/bin/sh\ncase \"$3\" in verify) echo 0.1.1;; extract) mkdir -p \"$6/app\" \"$6/bin\" \"$6/svc\"; echo 0.1.1 > \"$6/VERSION\"; echo graph > \"$6/modules.graph\"; echo list > \"$6/modules.list\"; echo apk > \"$6/app/Seogaemo.apk\"; echo '#!/bin/sh' > \"$6/bin/install-apk.sh\"; echo 'exit 0' >> \"$6/bin/install-apk.sh\"; echo '#!/bin/sh' > \"$6/bin/seogaemo-root\"; echo 'exit 0' >> \"$6/bin/seogaemo-root\"; echo '#!/bin/sh' > \"$6/svc/reconcile.sh\"; echo 'exit 0' >> \"$6/svc/reconcile.sh\";; switch) python3 -c 'import os,sys; os.replace(sys.argv[1],sys.argv[2])' \"$4\" \"$5\";; esac\n")
            (fake_bin / "dumpsys").write_text("#!/bin/sh\necho versionName=0.1.1\n")
            for name in ("app_process", "dumpsys"):
                (fake_bin / name).chmod(0o755)
            env = dict(os.environ, PATH=f"{fake_bin}:{os.environ['PATH']}")
            updater_cmd = ["sh", str(package_root / "core/update.sh")]
            for verb in ("stage", "apply", "rollback"):
                result = subprocess.run(updater_cmd + [verb], env=env, capture_output=True, text=True)
                assert result.returncode == 0, (verb, result.stdout, result.stderr)
            assert "package-version=" + version in (package_root / "NEMESIS.data").read_text(), (package_root / "NEMESIS.data").read_text()
            assert (state / "update/journal").read_text().strip() == "phase=rolled-back"
            (state / "update/staged").write_text("0.1.1\n")
            (fake_bin / "dumpsys").write_text("#!/bin/sh\necho versionName=wrong\n")
            result = subprocess.run(updater_cmd + ["apply"], env=env, capture_output=True, text=True)
            assert result.returncode != 0
            assert (state / "update/current").resolve() == package_root.resolve(), (result.stdout, result.stderr, (state / "update/current").resolve(), package_root.resolve(), (state / "update/journal").read_text())
            assert (state / "update/journal").read_text().strip() == "phase=auto-rollback"
    print("NEMESIS package tests passed")


if __name__ == "__main__":
    run()
