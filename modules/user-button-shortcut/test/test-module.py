"""Exercise module lifecycle with the real baseline/restore API and fake properties."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

MODULE = Path(__file__).resolve().parents[1]
ROOT = MODULE.parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from testlib import SHELL, car_env
KEY = 'persist.sys.key.swrccustom'
ACTION = 'com.seogaemo.hmg.USER_BUTTON_SHORTCUT'
with tempfile.TemporaryDirectory() as directory:
    base = Path(directory)
    state = base / 'state/user-button-shortcut'
    for name in ('lib', 'modules', 'props', 'backing', 'bin'):
        (base / name).mkdir()
    state.mkdir(parents=True)
    shutil.copy(ROOT / 'nemesis/package/lib/common.sh', base / 'lib/common.sh')
    shutil.copy(MODULE / 'module.properties', base / 'modules/user-button-shortcut.properties')
    def fake(name, body):
        p = base / 'bin' / name
        p.write_text('#!/bin/sh\n. "$HMG_SEOGAEMO_BASE/lib/common.sh"\n' + body)
        p.chmod(0o755)
    fake('getprop', 'cat "$FAKE_PROPS/$1" 2>/dev/null || :\n')
    fake('setprop', '[ ! -f "$FAKE_PROPS/fail" ] || exit 1\nsg_write "$2" > "$FAKE_PROPS/$1"\nsg_write "$2" > "$SG_PROPERTY_ROOT/$1"\n')
    env = car_env(dict(os.environ, PATH=str(base / 'bin') + ':' + os.environ['PATH'],
        FAKE_PROPS=str(base / 'props'), HMG_SEOGAEMO_BASE=str(base),
        HMG_SEOGAEMO_STATE=str(state), SG_DATA_ROOT=str(base / 'data'),
        SG_PROPERTY_ROOT=str(base / 'backing')))
    def prop(key, value): (base / 'props' / key).write_text(value)
    def run(verb, expected, code=0):
        p = subprocess.run([SHELL, str(MODULE / 'root/user-button-shortcut.sh'), verb], env=env, capture_output=True, text=True)
        assert p.returncode == code, (verb, p.stdout, p.stderr)
        assert 'state=' + expected in p.stdout, p.stdout
        return p.stdout
    product = 'NEEV.KOR.0000.V039.001.260902'
    prop('ro.build.product', product)
    prop('ro.build.version.sdk', '19')
    prop(KEY, 'gohome')
    original = b'gohome\x00'
    (base / 'backing' / KEY).write_bytes(original)
    run('status', 'off')
    assert not list(state.iterdir())
    assert 'warn.assignment=1' in run('precheck', 'off')
    run('apply', 'on')
    run('apply', 'on')
    assert (state / ('prop.' + KEY) / 'live').read_text() == 'gohome'
    prop(KEY, 'com.mobis.intent.action.CONNECTIVITY_SHORTCUT')
    run('status', 'conflict', 3)
    run('apply', 'conflict', 3)  # reconcile must not fight stock settings
    run('revert', 'conflict', 3)
    assert (state / ('prop.' + KEY) / 'ready').exists()
    prop(KEY, ACTION)
    run('revert', 'off')
    assert (base / 'props' / KEY).read_text() == 'gohome'
    assert (base / 'backing' / KEY).read_bytes() == original
    # Absent backing file is restored as absent, including an empty live value.
    (base / 'backing' / KEY).unlink()
    prop(KEY, '')
    run('apply', 'on')
    prop('ro.build.product', 'different')
    run('revert', 'conflict', 3)
    prop('ro.build.product', product)
    run('revert', 'off')
    assert not (base / 'backing' / KEY).exists()
    prop('ro.build.version.sdk', '20')
    run('apply', 'incompatible', 4)
    prop('ro.build.version.sdk', '19')
    prop(KEY, ACTION)
    run('apply', 'conflict', 3)
    prop(KEY, 'gohome')
    (base / 'props/fail').touch()
    run('apply', 'failed', 1)
print('user-button-shortcut module tests passed')
