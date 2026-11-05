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


def run_tests(tests, done):
    """Run test functions, each in its own temp dir.

    Without arguments run all and print `done`. With names run only those
    (so tools/test-root-shells.py can shard one suite across workers);
    `--list` prints the names.
    """
    import sys
    import tempfile
    names = sys.argv[1:]
    if names == ["--list"]:
        print("\n".join(test.__name__ for test in tests))
        return
    unknown = set(names) - {test.__name__ for test in tests}
    assert not unknown, f"unknown tests: {sorted(unknown)}"
    with tempfile.TemporaryDirectory() as directory:
        for test in tests:
            if not names or test.__name__ in names:
                folder = Path(directory).resolve() / test.__name__
                folder.mkdir()
                test(folder)
    if not names:
        print(done)
