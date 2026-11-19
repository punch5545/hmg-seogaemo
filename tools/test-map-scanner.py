#!/usr/bin/env python3
# Follows HMG-API tools/probe-test.py@4fab437 (host/on-car parity testing).
"""Build a synthetic Dalvik fixture and exercise the structural mapper."""
import hashlib
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
with tempfile.TemporaryDirectory(prefix="hmg-seogaemo-map-test-") as tmp:
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
      "example.target.handle":{"type":"field","owner":"example.target","match":{"name":"handle","type":"long","class_member":True}},
      "example.target.lock":{"type":"field","owner":"example.target","match":{"name":"lock","type":"java.lang.Object","class_member":True}},
      "example.target.as_interface":{"type":"method","owner":"example.target","match":{"name":"asInterface","parameters":["java.lang.Object"],"returns":"@example.target","class_member":True}},
      "example.target.add_listener":{"type":"method","owner":"example.target","match":{"name":"addListener","parameters":["@example.target"],"returns":"void","class_member":False}},
      "example.target.wrong_static":{"type":"field","owner":"example.target","match":{"name":"handle","type":"long","class_member":False}},
      "example.target.wrong_signature":{"type":"method","owner":"example.target","match":{"name":"asInterface","parameters":["@example.target"],"returns":"@example.target","class_member":True}},
    }
    (catalogue/"example.json").write_text(json.dumps({"schema":1,"group":"example","namespace":"com.example",
      "files":["/system/app/Example.odex"],"points":points}))
    maps=temp/"maps/x/y/z/w";maps.mkdir(parents=True)
    (maps/"map.json").write_text(json.dumps({"schema":1,"points":{"example.target.reference":
        {"method":"unique","params":[],"ret":"void","static":False}}}))
    output=temp/"compiled";output.mkdir()
    source=list((ROOT/"core/map/src").rglob("*.java"))+[ROOT/"tools/HmgSeogaemoMapScannerTest.java"]
    subprocess.run(["javac","-source","7","-target","7","-d",str(output),*map(str,source)],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(["java","-cp",str(output),"com.seogaemo.hmg.core.map.HmgSeogaemoMapScannerTest",str(system),str(catalogue),str(temp/"maps")],check=True)

    # Reference names come from the nearest bundled firmware first: identical stock files, then the
    # same build (product without the model token), then any bundle. unique() and other() both fit
    # the reference point's shape, so the names alone decide.
    unit="UNIT.KOR.0000.V039.001.260902"
    same,far="OTHER.KOR.0000.V039.001.260902","OTHER.KOR.0000.V037.001.260319"
    stock={"/system/app/Example.odex":hashlib.md5((system/"app/Example.odex").read_bytes()).hexdigest()}
    wrong={"/system/app/Example.odex":"0"*32}
    scenarios=[
      ("same build beats another build","unique",[("unique",same,wrong),("other",far,wrong)]),
      ("same build beats another build, swapped","other",[("unique",far,wrong),("other",same,wrong)]),
      ("same build disagrees","-",[("unique",same,wrong),("other",same,wrong),("other",far,wrong)]),
      ("identical stock files beat the same build","other",[("unique",same,wrong),("other",far,stock)]),
      ("other builds agree","other",[("other",far,wrong),("other",far,wrong)]),
      ("other builds disagree","-",[("unique",far,wrong),("other",far,wrong)]),
      ("nearer name absent on the unit falls through","unique",[("gone",same,wrong),("unique",far,wrong)]),
      ("a map without car.json ranks last","other",[("unique",None,None),("other",same,wrong)]),
    ]
    for number,(label,expected,references) in enumerate(scenarios):
        root=temp/f"ranked-{number}"
        for index,(method,product,files) in enumerate(references):
            folder=root/f"make/model{index}/kor/000000";folder.mkdir(parents=True)
            (folder/"map.json").write_text(json.dumps({"schema":1,"points":{"example.target.reference":
                {"method":method,"params":[],"ret":"void","static":False}}}))
            if product:(folder/"car.json").write_text(json.dumps({"schema":1,"product":product,"files":files}))
        subprocess.run(["java","-cp",str(output),"com.seogaemo.hmg.core.map.HmgSeogaemoMapScannerTest",str(system),str(catalogue),
                        str(root),unit,expected,label],check=True)
    print(f"map scanner reference ranking: {len(scenarios)} scenarios OK")
