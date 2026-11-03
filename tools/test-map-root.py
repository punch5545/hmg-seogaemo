#!/usr/bin/env python3
"""Exercise fixed map verbs and package-owned publish/export with fake app_process."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parent.parent
SHELL=os.environ.get("MKSH","sh")
with tempfile.TemporaryDirectory(prefix="seogaemo-map-root-") as tmp:
    t=Path(tmp);base=t/"package";state=t/"state";fake=t/"bin"
    for folder in (base/"bin",base/"core",base/"lib",fake,state):folder.mkdir(parents=True,exist_ok=True)
    for source,dest in (("nemesis/package/bin/hmg-seogaemo-root","bin/hmg-seogaemo-root"),
                        ("nemesis/package/core/map.sh","core/map.sh"),
                        ("nemesis/package/lib/common.sh","lib/common.sh")):
        shutil.copyfile(ROOT/source,base/dest)
    (fake/"getprop").write_text("#!/bin/sh\ncase \"$1\" in ro.build.product) echo NEEV.KOR.0000.V039.001.260902;; esac\n")
    (fake/"app_process").write_text('''#!/bin/sh
if [ "$3" = status ]; then printf 'state=off\\nrestart=none\\ndetail=verification needed\\n'; exit 0; fi
[ "$3" = run ] || [ "$3" = verify ] || exit 2
work=$6
for name in map.json car.json; do cp "$SG_FAKE_MAP/$name" "$work/$name" || exit 1; done
printf 'NEEV.KOR.0000.V039.001.260902\\nabc\\nboot\\n' > "$work/verified"
printf '0123456789abcdef0123456789abcdef\\n' > "$work/hash-id"
printf 'abcdef0123456789abcdef0123456789\\n' > "$work/map-id"
printf 'generated\\n' > "$work/source"
printf 'maps/hyundai/ioniq5/kor/260902\\n' > "$work/layout"
printf 'state=on\\nrestart=none\\ndetail=map verified\\n'
''')
    for f in fake.iterdir():f.chmod(0o755)
    env=dict(os.environ,PATH=str(fake)+":"+os.environ["PATH"],SG_DATA_ROOT=str(state),
             SG_EXPORT_ROOT=str(t/"export"),SG_FAKE_MAP=str(ROOT/"maps/hyundai/ioniq5/kor/260902"),
             SG_USB_DIRS=str(t/"no-usb"))
    command=[SHELL,str(base/"bin/hmg-seogaemo-root")]
    for invalid in ("bad","apply","../../etc"):
        result=subprocess.run(command+["map",invalid],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        assert result.returncode==2,(invalid,result.stdout,result.stderr)
    status=subprocess.run(command+["map","status"],env=env,capture_output=True,text=True)
    assert status.returncode==0 and "verification needed" in status.stdout,status
    result=subprocess.run(command+["map","run"],env=env,capture_output=True,text=True)
    assert result.returncode==0,(result.stdout,result.stderr)
    active=(state/"car-maps/active").read_text().strip()
    assert active=="NEEV.KOR.0000.V039.001.260902/0123456789abcdef0123456789abcdef/abcdef0123456789abcdef0123456789"
    assert (state/"car-maps"/active/"map.json").is_file()
    assert "map_publish" in (state/"ledger/ledger.tsv").read_text()
    exported=subprocess.run(command+["map","export"],env=env,capture_output=True,text=True)
    assert exported.returncode==0,(exported.stdout,exported.stderr)
    destination=Path(exported.stdout.split("detail=",1)[1].strip())
    assert (destination/"maps/hyundai/ioniq5/kor/260902/map.json").is_file()
    assert (destination/"maps/hyundai/ioniq5/kor/260902/car.json").is_file()
    ledger_export=subprocess.run(command+["export","adb"],env=env,capture_output=True,text=True)
    assert ledger_export.returncode==0,(ledger_export.stdout,ledger_export.stderr)
    ledger_destination=Path(ledger_export.stdout.split("detail=",1)[1].strip())
    assert (ledger_destination/"maps/hyundai/ioniq5/kor/260902/map.json").is_file()
print("map root verbs, ledger publish, and PR export passed")
