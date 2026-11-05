#!/usr/bin/env python3
"""Exercise fixed map verbs and package-owned publish/export with fake app_process."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
from testlib import car_env, SHELL, copy_shell_script

ROOT=Path(__file__).resolve().parent.parent
os.environ.update(car_env())
with tempfile.TemporaryDirectory(prefix="hmg-seogaemo-map-root-") as tmp:
    t=Path(tmp).resolve();base=t/"package";state=t/"state";fake=t/"bin"
    for folder in (base/"bin",base/"core",base/"lib",fake,state):folder.mkdir(parents=True,exist_ok=True)
    for source,dest in (("nemesis/package/bin/hmg-seogaemo-root","bin/hmg-seogaemo-root"),
                        ("nemesis/package/core/hmg-seogaemo-map.sh","core/hmg-seogaemo-map.sh"),
                        ("nemesis/package/lib/common.sh","lib/common.sh"),
                        ("nemesis/package/lib/diagnostics.sh","lib/diagnostics.sh")):
        copy_shell_script(ROOT/source,base/dest)
    (base/"modules.list").write_text("")
    (fake/"getprop").write_text("#!/bin/sh\ncase \"$1\" in ro.build.product) echo NEEV.KOR.0000.V039.001.260902;; esac\n")
    (fake/"app_process").write_text('''#!/bin/sh
. "$HMG_SEOGAEMO_BASE/lib/common.sh"
[ -d "$ANDROID_DATA/dalvik-cache" ] || exit 9
# status never creates core/vm: it uses it when it exists, else a scratch cache.
case "$ANDROID_DATA" in
    "$SG_DATA_ROOT/core/vm.status."*) [ "$3" = status ] && [ ! -e "$SG_DATA_ROOT/core/vm" ] || exit 9 ;;
    "$SG_DATA_ROOT/core/vm") ;;
    *) exit 9 ;;
esac
[ "$BOOTCLASSPATH" = /system/framework/core.jar ] || exit 8
if [ "$3" = status ]; then sg_print state=off restart=none "detail=verification needed"; exit 0; fi
[ "$3" = run ] || [ "$3" = verify ] || exit 2
work=$6
for name in map.json car.json; do cp "$SG_FAKE_MAP/$name" "$work/$name" || exit 1; done
sg_print NEEV.KOR.0000.V039.001.260902 abc boot > "$work/verified"
sg_print 0123456789abcdef0123456789abcdef > "$work/hash-id"
sg_print abcdef0123456789abcdef0123456789 > "$work/map-id"
sg_print generated > "$work/source"
sg_print maps/hyundai/ioniq5/kor/260902 > "$work/layout"
sg_print state=on restart=none "detail=map verified"
''')
    for f in fake.iterdir():f.chmod(0o755)
    env=dict(os.environ,PATH=str(fake)+":"+os.environ["PATH"],SG_DATA_ROOT=str(state),
             SG_EXPORT_ROOT=str(t/"export"),SG_FAKE_MAP=str(ROOT/"maps/hyundai/ioniq5/kor/260902"),
             SG_USB_DIRS=str(t/"no-usb"),BOOTCLASSPATH="/system/framework/core.jar")
    (t/"export").mkdir(mode=0o700)
    state.chmod(0o700)
    def public_tree(container):
        for entry in [container, *container.rglob("*")]:
            assert entry.stat().st_mode & 0o777 == (0o755 if entry.is_dir() else 0o644), entry
        assert state.stat().st_mode & 0o777 == 0o700
    command=[SHELL,str(base/"bin/hmg-seogaemo-root")]
    for invalid in ("bad","apply","../../etc"):
        result=subprocess.run(command+["map",invalid],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        assert result.returncode==2,(invalid,result.stdout,result.stderr)
    status=subprocess.run(command+["map","status"],env=env,capture_output=True,text=True)
    assert status.returncode==0 and "verification needed" in status.stdout,status
    assert not (state/"core").exists(), list(state.rglob("*"))
    product=state/"car-maps/NEEV.KOR.0000.V039.001.260902"
    stale=[product/"0123456789abcdef0123456789abcdef/11111111111111111111111111111111",
           product/"fedcba9876543210fedcba9876543210/22222222222222222222222222222222"]
    for old in stale:
        old.mkdir(parents=True)
        (old/"map.json").write_text("{}")
    (product/"0123456789abcdef0123456789abcdef/not-a-map").mkdir()
    result=subprocess.run(command+["map","run"],env=env,capture_output=True,text=True)
    assert result.returncode==0,(result.stdout,result.stderr)
    assert not any(old.exists() for old in stale) and not stale[1].parent.exists(), list(product.rglob("*"))
    assert (product/"0123456789abcdef0123456789abcdef/not-a-map").is_dir()
    assert not list((state/"car-maps").glob(".before.*")), list((state/"car-maps").iterdir())
    assert (state/"core/vm/dalvik-cache").is_dir()
    status=subprocess.run(command+["map","status"],env=env,capture_output=True,text=True)
    assert status.returncode==0 and not list((state/"core").glob("vm.status.*")),status
    active=(state/"car-maps/active").read_text().strip()
    assert active=="NEEV.KOR.0000.V039.001.260902/0123456789abcdef0123456789abcdef/abcdef0123456789abcdef0123456789"
    assert (state/"car-maps"/active/"map.json").is_file()
    assert "map_publish" in (state/"ledger/ledger.tsv").read_text()
    exported=subprocess.run(command+["map","export"],env=env,capture_output=True,text=True)
    assert exported.returncode==0,(exported.stdout,exported.stderr)
    destination=Path(exported.stdout.split("detail=",1)[1].strip())
    assert (destination/"maps/hyundai/ioniq5/kor/260902/map.json").is_file()
    assert (destination/"maps/hyundai/ioniq5/kor/260902/car.json").is_file()
    public_tree(t/"export")
    ledger_export=subprocess.run(command+["export","adb"],env=env,capture_output=True,text=True)
    assert ledger_export.returncode==0,(ledger_export.stdout,ledger_export.stderr)
    ledger_destination=Path(ledger_export.stdout.split("detail=",1)[1].strip())
    assert (ledger_destination/"maps/hyundai/ioniq5/kor/260902/map.json").is_file()
    public_tree(t/"export")
    usb=t/"usb"
    usb.mkdir(mode=0o711)
    (usb/"HMG-Seogaemo-export").mkdir(mode=0o700)
    mounts=t/"mounts"
    mounts.write_text(f"/dev/sda1 {usb} vfat rw 0 0\n")
    env.update(SG_USB_DIRS=str(usb), SG_MOUNTS_FILE=str(mounts))
    exported=subprocess.run(command+["map","export"],env=env,capture_output=True,text=True)
    assert exported.returncode==0,(exported.stdout,exported.stderr)
    public_tree(usb/"HMG-Seogaemo-export")
    assert usb.stat().st_mode & 0o777 == 0o711
print("map root verbs, ledger publish, and PR export passed")
