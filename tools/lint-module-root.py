#!/usr/bin/env python3
"""Reject direct module mutations; only lib/common.sh may write the unit."""
from pathlib import Path
import re
import sys

BAD = re.compile(
    r"(?<![\w])(?:setprop|mount|umount|cp|mv|rm|dd|ln|chmod|chown|"
    r"start|stop|iptables|am|tee|"
    r"sg_prop_baseline|sg_prop_restore|sg_file_baseline|sg_file_mark_applied|"
    r"sg_file_restore|sg_remount|sg_baseline_clear)\b|"
    r"\bsed\s+-i\b|\bnemesis\s+remount\b|"
    r"\bpm\s+(?:install|uninstall|enable|disable)\b|\bsettings\s+put\b")
REDIRECT = re.compile(r"(?<![0-9])(?:>>|>)\s*['\"]?([^\s'\"]+)")
UNAVAILABLE = re.compile(
    r"(?<![\w-])(?:awk|sed|tail|head|cut|tr|wc|sort|uniq|xargs|find|stat|"
    r"sha256sum|sha1sum|tar|unzip|busybox|toybox)(?![\w-])")


def lint(path):
    errors = []
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = re.sub(r"'[^']*'", "''", raw.split("#", 1)[0])
        if UNAVAILABLE.search(line):
            errors.append(f"{path}:{number}: unavailable head-unit tool")
        if "modules/" in str(path) and BAD.search(line):
            errors.append(f"{path}:{number}: direct mutation")
        for target in REDIRECT.findall(line) if "modules/" in str(path) else ():
            target = target.rstrip(";")
            if target in ("&2", "/dev/null") or target.startswith("$HMG_SEOGAEMO_STATE"):
                continue
            errors.append(f"{path}:{number}: direct output redirection")
    return errors


paths = [Path(arg) for arg in sys.argv[1:]]
if not paths:
    paths = sorted(Path("modules").glob("*/root/*.sh"))
    paths += sorted(Path("nemesis/package").rglob("*.sh"))
    paths += [Path("nemesis/package/bin/hmg-seogaemo-root")]
errors = [error for path in paths for error in lint(path)]
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("module mutation lint passed")
