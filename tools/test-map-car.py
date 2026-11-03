#!/usr/bin/env python3
# Follows HMG-API tools/test-hooks.py@4fab437 (host catalogue testing).
"""Synthetic smali shapes for the independent host mapper."""
import importlib.util
from pathlib import Path
import tempfile

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location("host_mapper",ROOT/"tools/map-car.py")
host=importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name]=host
spec.loader.exec_module(host)
with tempfile.TemporaryDirectory(prefix="hmg-seogaemo-smali-") as tmp:
    root=Path(tmp);stock=root/"stock";(stock/"app").mkdir(parents=True);(stock/"framework").mkdir()
    (stock/"app/Example.odex").write_bytes(b"synthetic")
    smali=root/"smali";smali.mkdir()
    (smali/"Example.smali").write_text('''.class public Lcom/example/Example;
.super Ljava/lang/Object;
.method public unique()V
    const-string v0, "needle"
    return-void
.end method
.method public other()V
    return-void
.end method
''')
    classes=host.read_smalis(smali)
    assert set(classes)=={"com.example.Example"}
    assert classes["com.example.Example"].literals=={"needle"}
    mapper=host.HostMapper(stock,root/"work",root/"unused.jar")
    mapper.disassemble=lambda _:classes
    group={"group":"example","namespace":"com.example","files":["/system/app/Example.odex"],
           "captures":{"special":{"within":"com.example.Example","expression":r"\.method public (?P<selected>unique)\(\)V\n"}},
           "points":{
             "example.target":{"type":"class","match":{"name":"com.example.Example"}},
             "example.target.unique":{"type":"method","owner":"example.target","match":{"name":"unique","parameters":[],"returns":"void"}},
             "example.target.missing":{"type":"method","owner":"example.target","match":{"name":"absent"}},
             "example.target.ambiguous":{"type":"method","owner":"example.target","match":{"parameters":[],"returns":"void"}},
             "example.target.pattern":{"type":"method","owner":"example.target","match":{"smali_pattern":"special","group":"selected","parameters":[],"returns":"void"}},
           }}
    points,missing,hashes,apps=mapper.scan([group])
    assert points["example.target.unique"]["method"]=="unique"
    assert points["example.target.pattern"]["method"]=="unique"
    assert "0 methods" in missing["example.target.missing"]
    assert "2 methods" in missing["example.target.ambiguous"]
    assert hashes["/system/app/Example.odex"]
print("host mapper synthetic smali: unique, missing, ambiguous, capture OK")
