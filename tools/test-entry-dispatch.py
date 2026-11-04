#!/usr/bin/env python3
"""Stable entry points execute selected logic; invalid commands never reach it."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from testlib import car_env, SHELL, host_shell_tree

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    stable = root / 'stable'
    selected = root / 'selected'
    shutil.copytree(ROOT / 'nemesis/package', stable)
    host_shell_tree(stable)
    current = root / 'state/update/current'
    current.parent.mkdir(parents=True)
    selected.mkdir()
    current.symlink_to(selected, target_is_directory=True)
    env = car_env(dict(os.environ, SG_DATA_ROOT=str(root / 'state')))
    def run(path, *args):
        return subprocess.run([SHELL, str(path), *args], env=env,
                              capture_output=True, text=True, timeout=5)
    entries = ['bin/hmg-seogaemo-root', 'bin/open.sh', 'svc/reconcile.sh',
               'svc/watcher.sh', 'scripts/preuninstall.sh']
    for entry in entries:
        target = selected / entry
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text('echo selected\nfor arg do echo "arg=$arg"; done\n')
        args = ('wifi-client', 'revert') if entry.endswith('hmg-seogaemo-root') else ()
        result = run(stable / entry, *args)
        assert result.returncode == 0 and result.stdout.startswith('selected\n'), (entry, result)
        if args: assert result.stdout == 'selected\narg=wifi-client\narg=revert\n'
    dispatcher = stable / entries[0]
    for args in [(), ('device', 'bad'), ('stock', 'restore', 'extra'),
                 ('map', 'shell'), ('bad/path', 'status'), ('wifi-client', 'watch', 'bad')]:
        result = run(dispatcher, *args)
        assert result.returncode == 2 and 'selected' not in result.stdout
    # Missing selected dispatcher falls back rather than execing a broken target.
    (selected / entries[0]).unlink()
    (stable / 'modules').mkdir()
    (stable / 'modules.list').write_text('wifi-client\n')
    (stable / 'modules/wifi-client.sh').write_text('echo state=off; echo restart=none\n')
    assert 'state=off' in run(dispatcher, 'wifi-client', 'status').stdout
    # A selection back to the installed tree must not recurse, including aliases.
    current.unlink()
    current.symlink_to(stable, target_is_directory=True)
    assert 'state=off' in run(dispatcher, 'wifi-client', 'status').stdout
    # A real selected dispatcher also stops delegating when invoked via its real path.
    shutil.copytree(stable, selected, dirs_exist_ok=True)
    current.unlink()
    current.symlink_to(selected, target_is_directory=True)
    assert 'state=off' in run(dispatcher, 'wifi-client', 'status').stdout
    assert 'state=off' in run(selected / entries[0], 'wifi-client', 'status').stdout
    current.unlink()
    current.symlink_to(root / 'missing')
    assert 'state=off' in run(dispatcher, 'wifi-client', 'status').stdout
    # USB repair must execute the fresh tree even with a stale OTA selection.
    current.unlink()
    current.symlink_to(selected, target_is_directory=True)
    (selected / 'scripts/postinstall.sh').write_text('echo stale; exit 90\n')
    hook = stable / 'scripts/postinstall.sh'
    hook.write_text(hook.read_text().replace('/system/xbin/nemesis', str(root / 'nemesis')))
    (root / 'nemesis').write_text('client')
    fake = root / 'fake'
    fake.mkdir()
    (fake / 'getprop').write_text('#!/bin/sh\necho NEEV.KOR.0000.V039.001.260902\n')
    (fake / 'getprop').chmod(0o755)
    env['PATH'] = str(fake) + ':' + env['PATH']
    for pointer in ('current-path', 'previous-path', 'previous-version', 'journal'):
        (current.parent / pointer).write_text('old')
    result = run(hook)
    assert result.returncode == 0 and 'stale' not in result.stdout, result
    assert not current.is_symlink()
    assert selected.is_dir()
    assert not (current.parent / 'previous-path').exists()
print('stable and OTA entry dispatch passed')
