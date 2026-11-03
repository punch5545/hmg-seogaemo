#!/usr/bin/env python3
"""Reject incomplete or unsafe bundled car map changes."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile

root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location("map_validation",root/"tools/validate-maps.py")
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
points,files,apps=module.catalogue();required=module.requirements(points)
with tempfile.TemporaryDirectory(prefix="seogaemo-map-validation-") as tmp:
    base=Path(tmp);module.ROOT=base
    path=base/"maps/hyundai/ioniq5/kor/260902";path.mkdir(parents=True)
    source=root/"maps/hyundai/ioniq5/kor/260902"
    for name in ("map.json","car.json"):shutil.copyfile(source/name,path/name)
    module.validate_bundle(path,points,files,apps,required)
    original=json.loads((path/"car.json").read_text())
    for change in (
        lambda car:car.update({"vin":"private"}),
        lambda car:car["files"].pop(next(iter(car["files"]))),
    ):
        altered=json.loads(json.dumps(original));change(altered)
        (path/"car.json").write_text(json.dumps(altered))
        try:module.validate_bundle(path,points,files,apps,required)
        except ValueError:pass
        else:raise AssertionError("unsafe map accepted")
    (path/"car.json").write_text(json.dumps(original))
    mapping=json.loads((path/"map.json").read_text())
    mapping["points"].pop(required["wireless-carplay-and-aa"][0])
    (path/"map.json").write_text(json.dumps(mapping))
    try:module.validate_bundle(path,points,files,apps,required)
    except ValueError:pass
    else:raise AssertionError("missing required point accepted")
print("map validation rejects forbidden fields and missing points/hashes")
