#!/usr/bin/env python3
"""Reject direct module mutations; only lib/common.sh may write the unit."""
from pathlib import Path
import re
import shlex
import sys

BAD = re.compile(
    r"(?<![\w])(?:setprop|mount|umount|cp|mv|rm|dd|ln|chmod|chown|"
    r"start|stop|iptables|am|tee|"
    r"sg_prop_baseline|sg_prop_restore|sg_file_baseline|sg_file_mark_applied|"
    r"sg_file_restore|sg_remount|sg_remount_ro|sg_mkdir_target|sg_baseline_clear|"
    r"sg_dir_ensure|sg_dirs_revert|sg_export_public)\b|"
    r"\bsed\s+-i\b|\bnemesis\s+remount\b|"
    r"\bpm\s+(?:install|uninstall|enable|disable)\b|\bsettings\s+put\b")
# Module shells keep globbing on: the runtime's state globs must never silently match nothing.
# A word split may turn it off for one statement: `set -f; set -- $x; set +f`.
NOGLOB = re.compile(r"(?<![\w-])set\s+(?:-[a-zA-Z]*f[a-zA-Z]*\b|(?:-[a-zA-Z]*o|\+o)\s+noglob\b)")
SCOPED_NOGLOB = re.compile(r"^\s*set -f; set -- [^;]+; set \+f\s*$")
REDIRECT = re.compile(r"(?<![0-9])(?:>>|>)\s*['\"]?([^\s'\"]+)")


def lint(path):
    errors = []
    values = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        assignment = re.match(r'^\s*([A-Za-z_][A-Za-z0-9_]*)=[\"\']?(/system[^\s\"\']*)[\"\']?\s*$', raw)
        if assignment:
            values[assignment[1]] = assignment[2]
        try:
            tokens = shlex.split(raw, comments=True)
        except ValueError:
            tokens = []  # Shell syntax is validated separately.
        for i, token in enumerate(tokens):
            if token not in ("sg_file_put", "sg_commit_file") or len(tokens) <= i + 3:
                continue
            target, mode = tokens[i + 1], tokens[i + 3]
            for name, value in values.items():
                target = target.replace("${" + name + "}", value)
                target = re.sub(r"\$" + re.escape(name) + r"(?![A-Za-z0-9_])", lambda _: value, target)
            if target.startswith("/system/") and re.fullmatch(r"0?[0-7]{3,4}", mode):
                if not int(mode, 8) & 0o004 and not re.search(r"# payload-mode: allow -- \S", raw):
                    errors.append(f"{path}:{number}: private system payload mode (use inline payload-mode justification)")
        line = re.sub(r"'[^']*'", "''", raw.split("#", 1)[0])
        if "modules/" in str(path) and NOGLOB.search(line) and not SCOPED_NOGLOB.search(line):
            errors.append(f"{path}:{number}: module shells must keep globbing on (set -f only as 'set -f; set -- ...; set +f')")
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
    paths = sorted(Path("modules").glob("*/root/*.sh")) + sorted(Path("modules").glob("*/module.sh"))
    paths += sorted(Path("nemesis/package").rglob("*.sh"))
    paths += [Path("nemesis/package/bin/hmg-seogaemo-root")]
errors = [error for path in paths for error in lint(path)]
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("module mutation lint passed")
