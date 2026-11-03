#!/usr/bin/env python3
"""Regression checks for the source-only KitKat compatibility gate."""
import importlib.util
from pathlib import Path
import sys
import unittest

spec = importlib.util.spec_from_file_location('android44_lint', Path(__file__).with_name('lint-android44-shell.py'))
lint = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = lint
spec.loader.exec_module(lint)


class ShellProfileTest(unittest.TestCase):
    def test_rejections(self):
        for source in (
            'timeout 5 service call x 82', 'which cat', 'seq 1 3',
            'basename /x', 'dirname /x', 'xargs cat', 'printf x',
            'mv -f a b', 'mv a -f b', 'cmp -s a b', 'ls -lh',
            'grep -FxQ pattern file', 'grep --unknown pattern', 'grep -e',
            'grep -P pattern file', 'grep -J pattern file',
            '#!/usr/bin/env bash\necho ok',
            'md5', 'sleep 0.5', 'date +%N', 'dd status=none',
            '/system/xbin/busybox cat x', '/usr/bin/grep x',
            'echo "$(unknown_command)"', 'x=${v:-$(bad)}',
            "trap 'bad_command' EXIT", "sh -c 'cmp -s a b'",
            'sh -c "$code"', 'eval "$code"', '$command x',
            'cat <(echo x)', 'echo x &>out', 'echo ${x^^}',
            'echo ${x,}', '[[ x =~ y ]]', 'declare -A a', 'mapfile a',
            'set -o imaginary', 'set -o pipefail', 'grep -Q"$x"', 'kill -BOGUS 1',
            'set -eopipefail', 'set +o pipefail',
            "sh -ec 'unknown_command'", 'sh -ec "$code"',
            '/system/bin/cat -u x', '/system/bin/kill -s TERM 1',
            '/system/bin/sleep -- 1',
            'pm unsupported', 'nemesis unsupported', 'read -z x', 'typeset -A a',
            "case x in a) echo ok ;;& b) echo ok;; esac",
        ):
            with self.subTest(source=source):
                self.assertTrue(lint.Audit(source).run(), source)

    def test_supported_forms(self):
        for source in (
            'cp -Rf a b; mv a b; md5 /proc/self/fd/0',
            "grep -Fxq 'a#b' file; grep -Eq 'a|b'; grep -c -e x file",
            'grep --fixed-strings --quiet x file',
            'ls -ln a; ls -ldn a; cmp a b >/dev/null 2>&1',
            'date +%s; date +%Y%m%d-%H%M%S; readlink -f /x',
            'print -rn -- "$x"; IFS= read -r x',
            'x=$((1 + 2)); echo ${#x} ${x/a/b} ${x##*/}',
            "x=$'hello'; local a=1; typeset -i a=1",
            '[[ -f "$x" ]]; [ -n "$x" ]; test 1 -eq 1',
            "case x in ')'|[.,:_/+=-]) echo ok;; *) echo no;; esac",
            "case x in a) case y in b) cat x;; esac;; esac",
            'f() { cat "$1"; }; f a',
            'for x in a b; do echo "$x"; done',
            'cat <<EOF\nhello # literal data\nEOF\n',
            "cat <<'EOF'\nthis is data\nEOF\n",
            "cat <<'EOF'\n$(unknown_command)\nEOF\n",
            '/system/bin/cat -n x; /system/bin/kill -9 1',
            "sh -ec 'echo ok'; set +o posix; set -eoposix",
            'exec /system/bin/sh -c "echo hi"',
            'set -eu; set -- "$x"; set -o posix',
            'pm install -r /x; am start -n x; service check x',
            'nemesis uninstall hmg-seogaemo',
            'logcat -r -f /x; logcat -r1024 -f /x',
        ):
            with self.subTest(source=source):
                self.assertEqual(lint.Audit(source).run(), [], source)

    def test_waiver_is_inline_and_reasoned(self):
        audit = lint.Audit('$binary x # android44-shell: allow -- fixed host-test seam')
        self.assertEqual(audit.run(), [])
        self.assertTrue(audit.waivers)
        self.assertTrue(lint.Audit('$binary # android44-shell: allow').run())
        self.assertTrue(lint.Audit('# android44-shell: allow -- unrelated\nbad').run())

    def test_source_inventory(self):
        paths = {p.relative_to(lint.ROOT).as_posix() for p in lint.paths()}
        self.assertIn('nemesis/package/bin/hmg-seogaemo-root', paths)
        self.assertIn('nemesis/package/scripts/preuninstall.sh', paths)
        self.assertIn('nemesis/package/core/stock.sh', paths)
        self.assertIn('nemesis/package/core/uninstall-detached.sh', paths)
        self.assertIn('nemesis/package/lib/stock.sh', paths)
        for module in (lint.ROOT / 'modules').glob('*/root/*.sh'):
            self.assertIn(module.relative_to(lint.ROOT).as_posix(), paths)
        for host in lint.PROFILE['host_only_scripts']:
            self.assertNotIn(host, paths)


if __name__ == '__main__':
    unittest.main()
