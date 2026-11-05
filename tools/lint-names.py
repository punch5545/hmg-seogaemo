#!/usr/bin/env python3
"""Reject unprefixed project names in tracked source and documentation."""

from pathlib import Path
import re
import subprocess
import sys


STEM = "seoga" + "emo"
ROOT = Path(__file__).resolve().parents[1]


def allowed(text, match):
    before = text[:match.start()].lower()
    after = text[match.end():].lower()
    if before.endswith(("hmg-", "hmg_")):
        return True
    if before.endswith(("hmg", "hmg ")) and match.group() == STEM.title():
        return True
    if before.endswith(("com.", "com/", "com\\.")) and after.startswith((".hmg", "/hmg", "\\.hmg")):
        return True
    if after.startswith("/hmg-" + STEM):
        return True
    return False


def bad_names(text):
    return [match.group() for match in re.finditer(STEM, text, re.I) if not allowed(text, match)]


def main():
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT
    ).decode().split("\0")
    failures = []
    for name in names:
        if not name or name.startswith(("tmp/", "out/")):
            continue
        path = ROOT / name
        if not path.is_file():
            continue
        if bad_names(name):
            failures.append(name + ": unprefixed filename")
        try:
            lines = path.read_text().splitlines()
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(lines, 1):
            if bad_names(line):
                failures.append("%s:%d: unprefixed project name" % (name, number))
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Name lint passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
