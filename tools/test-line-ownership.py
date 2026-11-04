#!/usr/bin/env python3
"""Shared-file composition, guarded restore, and interrupted-write recovery."""
import hashlib
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
from testlib import car_env, SHELL, copy_shell_script

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / 'nemesis/package/lib/common.sh'


class Fixture:
    def __init__(self, root, stock=b'alpha\nbeta\nkeep\n'):
        self.root = root
        self.target = root / 'target'
        root.mkdir()
        self.target.write_bytes(stock)
        self.target.chmod(0o640)
        self.meta = self.target.stat()
        self.stock = stock
        (root / 'modules').mkdir()
        (root / 'fake').mkdir()
        (root / 'data/desired').mkdir(parents=True)
        (root / 'build').write_text('firmware-a')
        self.script('getprop', 'cat "$FAKE_BUILD"\n')
        (root / 'modules.list').write_text('a\nb\n')
        self.claim('a', 'alpha')
        self.claim('b', 'beta')
        self.env = dict(os.environ, PATH=str(root / 'fake') + ':' + os.environ['PATH'],
                        FAKE_BUILD=str(root / 'build'), SG_DATA_ROOT=str(root / 'data'),
                        HMG_SEOGAEMO_BASE=str(root))

    @property
    def shared(self):
        return self.root / 'data/shared-lines' / hashlib.md5(str(self.target).encode()).hexdigest()

    def script(self, name, body):
        path = self.root / 'fake' / name
        path.write_text('#!/bin/sh\n' + body)
        path.chmod(0o755)

    def claim(self, module, stock):
        (self.root / f'modules/{module}.properties').write_text(
            f'owns_lines.1={self.target}:{stock}\n')

    def run(self, module, command, code=0):
        env = car_env(dict(self.env, HMG_SEOGAEMO_STATE=str(self.root / f'data/modules/{module}')))
        result = subprocess.run([SHELL, '-c', f'. {shlex.quote(str(COMMON))}\n' + command],
                                env=env, capture_output=True, text=True)
        assert result.returncode == code, (command, code, result.returncode, result.stdout, result.stderr)
        return result

    def apply(self, module, old, new, code=0):
        self.run(module, 'sg_file_replace_line ' + ' '.join(map(shlex.quote, (str(self.target), old, new))), code)

    def restored(self):
        assert self.target.read_bytes() == self.stock
        meta = self.target.stat()
        assert (meta.st_mode, meta.st_uid, meta.st_gid) == (self.meta.st_mode, self.meta.st_uid, self.meta.st_gid)
        assert not self.shared.exists()


def sharing(root):
    for newline in (b'', b'\n'):
        for order in (('a', 'b'), ('b', 'a')):
            f = Fixture(root / f'sharing-{len(newline)}-{order[0]}', b'alpha\nbeta\nkeep' + newline)
            if newline:
                f.target.chmod(0o4640)
                f.meta = f.target.stat()
            for module in order:
                f.apply(module, 'alpha' if module == 'a' else 'beta', 'A' if module == 'a' else 'B')
            assert f.target.read_bytes() == b'A\nB\nkeep' + newline
            f.apply('a', 'alpha', 'A')  # repeat from stock, not current line
            f.run('a', f'sg_applied {f.target}')
            f.run(order[0], 'sg_revert_all')
            expected = b'alpha\nB\nkeep' if order[0] == 'a' else b'A\nbeta\nkeep'
            assert f.target.read_bytes() == expected + newline
            f.run(order[1], f'sg_applied {f.target}')
            f.run(order[1], 'sg_revert_all')
            f.restored()
            ledger = (f.root / 'data/ledger/ledger.tsv').read_text()
            assert 'line_patch' in ledger and 'line_revert' in ledger and 'line_compose' in ledger
            assert all(len(row.split('\t')) == 7 for row in ledger.splitlines())
            # Line texts and composed hashes are part of the ordinary ledger export tree.
            after = list((f.root / 'data/ledger/after').iterdir())
            assert any(p.read_bytes() == b'A' for p in after)
            before = f.root / 'data/ledger/before'
            assert any(hashlib.md5(b'A\nB\nkeep' + newline).hexdigest() in p.read_text()
                       for p in before.glob('*.meta'))


def conflicts(root):
    f = Fixture(root / 'same-line')
    f.claim('b', 'alpha')
    (f.root / 'data/desired/b').write_text('on')
    f.apply('a', 'alpha', 'A', 3)
    assert f.target.read_bytes() == f.stock
    (f.root / 'data/desired/b').write_text('off')
    f.apply('b', 'alpha', 'B')
    f.apply('a', 'alpha', 'A', 3)  # active patches also protect interrupted/off desired state
    assert f.target.read_bytes() == b'B\nbeta\nkeep\n'
    f = Fixture(root / 'drift')
    f.apply('a', 'alpha', 'A')
    f.target.write_bytes(b'external\nbeta\n')
    f.apply('b', 'beta', 'B', 3)
    f.run('a', 'sg_revert_all', 3)
    assert f.target.read_bytes() == b'external\nbeta\n'
    assert (f.shared / 'bytes').read_bytes() == f.stock
    f = Fixture(root / 'firmware')
    f.apply('a', 'alpha', 'A')
    (f.root / 'build').write_text('firmware-b')
    f.apply('b', 'beta', 'B', 3)
    f.run('a', 'sg_revert_all', 3)
    for name, stock in [('missing', b'beta\n'), ('duplicate', b'alpha\nalpha\nbeta\n')]:
        f = Fixture(root / name, stock)
        f.apply('a', 'alpha', 'A', 3)
        assert f.target.read_bytes() == stock
        f.run('a', 'sg_revert_all')
        f.restored()
    f = Fixture(root / 'metadata')
    f.apply('a', 'alpha', 'A')
    f.target.chmod(0o600)
    f.apply('b', 'beta', 'B', 3)
    f.run('a', 'sg_revert_all', 3)
    f = Fixture(root / 'whole')
    (f.root / 'modules/b.properties').write_text(f'owns_files={f.target}\n')
    f.apply('a', 'alpha', 'A', 3)
    (f.root / 'payload').mkdir()
    (f.root / 'payload/new').write_text('whole')
    f.env['HMG_SEOGAEMO_MODULE_DIR'] = str(f.root / 'payload')
    f.run('b', f'sg_file_put {f.target} new', 3)
    f.run('b', f'sg_file_delete {f.target}', 3)
    # Earlier whole-file baselines must never be silently reinterpreted.
    (f.root / 'modules/b.properties').write_text('owns_files=\n')
    saved = f.root / 'data/modules/b' / ('file.' + hashlib.md5(str(f.target).encode()).hexdigest())
    saved.mkdir(parents=True)
    (saved / 'ready').write_text('1')
    f.apply('a', 'alpha', 'A', 3)


def recovery(root):
    for phase in ('copy', 'rename', 'audit'):
        f = Fixture(root / ('failure-' + phase))
        f.apply('a', 'alpha', 'A')
        if phase == 'copy':
            f.script('cp', 'case "$2" in *.hmg-seogaemo.*) echo partial > "$2"; exit 1;; esac\n/bin/cp "$@"\n')
        elif phase == 'rename':
            # Return failure after the actual atomic replacement (power loss window).
            f.script('mv', 'case "$1" in *.hmg-seogaemo.*) /bin/mv "$@"; exit 1;; esac\n/bin/mv "$@"\n')
        else:
            # Failure publishing the ledger after the target write.
            f.script('cp', 'case "$2" in */ledger/before/*) exit 1;; esac\n/bin/cp "$@"\n')
        f.apply('b', 'beta', 'B', 1)
        assert (f.shared / 'pending').exists()
        assert f.target.read_bytes() == (b'A\nbeta\nkeep\n' if phase == 'copy' else b'A\nB\nkeep\n')
        for p in (f.root / 'fake').iterdir():
            if p.name != 'getprop': p.unlink()
        # A new process recovers and retries; revert of either module stays safe.
        f.apply('b', 'beta', 'B')
        f.run('a', 'sg_revert_all')
        assert f.target.read_bytes() == b'alpha\nB\nkeep\n'
        f.run('b', 'sg_revert_all')
        f.restored()
    f = Fixture(root / 'pending-external')
    f.script('cp', 'case "$2" in *.hmg-seogaemo.*) exit 1;; esac\n/bin/cp "$@"\n')
    f.apply('a', 'alpha', 'A', 1)
    f.target.write_bytes(b'external')
    f.run('a', 'sg_revert_all', 3)
    assert (f.shared / 'pending').exists()
    f = Fixture(root / 'cancelled-sibling')
    f.apply('a', 'alpha', 'A')
    f.script('cp', 'case "$2" in *.hmg-seogaemo.*) exit 1;; esac\n/bin/cp "$@"\n')
    f.apply('b', 'beta', 'B', 1)
    (f.root / 'fake/cp').unlink()
    # Reverting another module cancels the pending apply, restores stock, and
    # must not leave an orphan marker that would block the sibling's uninstall.
    f.run('a', 'sg_revert_all')
    f.run('b', 'sg_revert_all')
    f.restored()


def literals(root):
    text = r'  a,b:c=d\z  '
    f = Fixture(root / 'literal', (text + '\nbeta').encode())
    f.claim('a', text)
    f.apply('a', text, r'-x,y:z=\new  ')
    assert f.target.read_bytes() == b'-x,y:z=\\new  \nbeta'
    f.run('a', 'sg_revert_all')
    f.restored()
    f = Fixture(root / 'empty-line', b'alpha\n\nbeta')
    f.claim('a', '')
    f.apply('a', '', 'blank')
    assert f.target.read_bytes() == b'alpha\nblank\nbeta'
    f.run('a', 'sg_revert_all')
    f.restored()
    # A patch output equal to another stock line must not cascade.
    f = Fixture(root / 'no-cascade')
    f.apply('a', 'alpha', 'beta')
    f.apply('b', 'beta', 'B')
    assert f.target.read_bytes() == b'beta\nB\nkeep\n'
    f.run('a', 'sg_revert_all')
    f.run('b', 'sg_revert_all')
    f.restored()


def audit_records(root):
    f = Fixture(root / 'audit-records')
    f.script('date', 'echo 12345\n')
    target = shlex.quote(str(f.target))
    f.run('a', f'sg_file_replace_line {target} alpha A && '
          f'sg_file_replace_line {target} alpha Z && sg_revert_all')
    rows = [row.split('\t') for row in (f.root / 'data/ledger/ledger.tsv').read_text().splitlines()[1:]]
    assert len({row[4] for row in rows}) == len(rows), rows
    assert [(f.root / 'data/ledger' / row[5]).read_bytes()
            for row in rows if row[2] == 'line_patch'] == [b'A', b'Z']
    f.restored()


def lifecycle(root):
    f = Fixture(root / 'lifecycle')
    for name in ('bin', 'svc', 'lib'):
        (f.root / name).mkdir()
    shutil.copy(COMMON, f.root / 'lib/common.sh')
    for name in ('svc/reconcile.sh',):
        copy_shell_script(ROOT / 'nemesis/package' / name, f.root / name)
    f.script('getprop', 'case "$1" in sys.boot_completed) echo 1;; *) cat "$FAKE_BUILD";; esac\n')
    f.script('pm', '[ "$1:$2" != path:android ] || echo package:framework\n')
    (f.root / 'bin/hmg-seogaemo-root').write_text(
        '. "$HMG_SEOGAEMO_BASE/lib/common.sh"\n'
        'HMG_SEOGAEMO_STATE=$SG_DATA_ROOT/modules/$1\n'
        'case "$2" in revert) sg_revert_all || exit $?; '
        'sg_write off > "$SG_DATA_ROOT/desired/$1";; status) sg_print state=off;; esac\n')
    f.apply('a', 'alpha', 'A')
    f.apply('b', 'beta', 'B')
    f.run('a', f'{shlex.quote(SHELL)} {shlex.quote(str(f.root / "svc/reconcile.sh"))}')
    f.restored()
    # preuninstall delegates to core/stock.sh; see tools/test-stock.py.


with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    sharing(root)
    conflicts(root)
    recovery(root)
    literals(root)
    audit_records(root)
    lifecycle(root)
print('line ownership tests passed')
