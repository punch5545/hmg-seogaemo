#!/usr/bin/env python3
# Follows HMG-API core/hookpoints/*.json@4fab437 (stock file inventory).
"""Print the exact stock paths named by the hookpoint catalogue."""
import argparse
import json
from pathlib import Path
import shlex

ROOT=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb-pull",action="store_true",help="print copy-paste shell commands")
parser.add_argument("--destination",default="stock/system",help="local system tree for --adb-pull")
args=parser.parse_args()
files=sorted({file for path in (ROOT/"core/hookpoints").glob("*.json")
              for file in json.loads(path.read_text())["files"]})
if args.adb_pull:
    for file in files:
        target=Path(args.destination)/file[len("/system/"):]
        print("mkdir -p",shlex.quote(str(target.parent)))
        print("adb pull",shlex.quote(file),shlex.quote(str(target)))
else:print("\n".join(files))
