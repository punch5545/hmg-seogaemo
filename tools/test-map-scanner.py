#!/usr/bin/env python3
"""Build a synthetic Dalvik fixture and exercise the structural mapper."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parent.parent
SDK=Path(os.environ.get("ANDROID_HOME",str(Path.home()/"Library/Android/sdk")))
BT=SDK/"build-tools/34.0.0"
ANDROID=SDK/"platforms/android-19/android.jar"
with tempfile.TemporaryDirectory(prefix="seogaemo-map-test-") as tmp:
    temp=Path(tmp)
    classes=temp/"classes";classes.mkdir()
    subprocess.run(["javac","-source","7","-target","7","-bootclasspath",str(ANDROID),"-d",str(classes),
                    str(ROOT/"tools/fixtures/dex/Example.java")],check=True,stdout=subprocess.DEVNULL)
    dex=temp/"dex";dex.mkdir()
    subprocess.run([str(BT/"d8"),"--min-api","19","--lib",str(ANDROID),"--output",str(dex),
                    str(classes/"com/example/Example.class")],check=True,stdout=subprocess.DEVNULL)
    system=temp/"system"; (system/"app").mkdir(parents=True)
    shutil.copyfile(dex/"classes.dex",system/"app/Example.odex")
    catalogue=temp/"catalogue";catalogue.mkdir()
    points={
      "example.target":{"type":"class","match":{"name":"com.example.Example"}},
      "example.target.unique":{"type":"method","owner":"example.target","match":{"name":"unique","parameters":[],"returns":"void"}},
      "example.target.missing":{"type":"method","owner":"example.target","match":{"name":"absent"}},
      "example.target.ambiguous":{"type":"method","owner":"example.target","match":{"parameters":[],"returns":"void"}},
      "example.target.reference":{"type":"method","owner":"example.target","match":{"parameters":[],"returns":"void","smali_pattern":"synthetic"}},
    }
    (catalogue/"example.json").write_text(json.dumps({"schema":1,"group":"example","namespace":"com.example",
      "files":["/system/app/Example.odex"],"points":points}))
    maps=temp/"maps/x/y/z/w";maps.mkdir(parents=True)
    (maps/"map.json").write_text(json.dumps({"schema":1,"points":{"example.target.reference":
        {"method":"unique","params":[],"ret":"void","static":False}}}))
    output=temp/"compiled";output.mkdir()
    source=list((ROOT/"core/map/src").rglob("*.java"))+[ROOT/"tools/MapScannerTest.java"]
    subprocess.run(["javac","-source","7","-target","7","-d",str(output),*map(str,source)],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(["java","-cp",str(output),"com.seogaemo.hmg.core.map.MapScannerTest",str(system),str(catalogue),str(temp/"maps")],check=True)
