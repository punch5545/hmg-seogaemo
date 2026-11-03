"""Shared host tools for tests that execute head-unit scripts."""
import os
from pathlib import Path
import shutil

FAKEBIN = Path(__file__).resolve().parent / "fakebin"
SHELL = os.environ.get("HMG_SEOGAEMO_TEST_SHELL") or shutil.which("mksh") or "/bin/sh"


def car_env(values=None):
    result = dict(os.environ if values is None else values)
    result["PATH"] = f"{FAKEBIN}:{result['PATH']}"
    return result


def host_shell_script(text):
    """Translate only the device interpreter in private host fixture copies."""
    return text.replace("/system/bin/sh", SHELL)


def copy_shell_script(source, destination):
    Path(destination).write_text(host_shell_script(Path(source).read_text()))


def host_shell_tree(root):
    for path in Path(root).rglob("*"):
        if path.is_file() and (path.suffix == ".sh" or path.name == "hmg-seogaemo-root"):
            path.write_text(host_shell_script(path.read_text()))
