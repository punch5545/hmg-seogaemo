#!/usr/bin/env python3
"""Host fixture for numeric inode owners without privileged chown."""
import json
import os
from pathlib import Path
import subprocess
import sys

store = Path(os.environ['FAKE_OWNERS'])
owners = json.loads(store.read_text()) if store.exists() else {}
if sys.argv[1] == 'chown':
    owner = sys.argv[2].replace('.', ':').split(':')
    for path in sys.argv[3:]:
        inode = os.stat(path).st_ino
        owners[str(inode)] = owner
    store.write_text(json.dumps(owners))
else:
    result = subprocess.run(['/bin/ls', *sys.argv[2:]], capture_output=True, text=True)
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) >= 9 and fields[0][0] in '-dl':
            # Host inode owners default to simulated root.
            inode = os.stat(fields[-1], follow_symlinks=False).st_ino
            fields[2:4] = owners.get(str(inode), ['0', '0'])
            line = ' '.join(fields)
        print(line)
    sys.stderr.write(result.stderr)
    sys.exit(result.returncode)
