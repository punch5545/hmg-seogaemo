#!/usr/bin/env python3
"""Validate independently authored hook rules, required points, and bundled maps."""
import argparse
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
PRODUCT = re.compile(r"^[A-Za-z0-9._-]+$")
MD5 = re.compile(r"^[0-9a-f]{32}$")
POINT = re.compile(r"^[a-z0-9_]+(?:\.[a-z0-9_]+)+$")
MEMBER_KEYS = ({"class"}, {"field", "type", "static"},
               {"method", "params", "ret", "static"}, {"ctor"})


def fail(message):
    raise ValueError(message)


def catalogue():
    points = {}
    files = set()
    apps = {}
    for path in sorted((ROOT / "core/hookpoints").glob("*.json")):
        data = json.loads(path.read_text())
        if data.get("schema") != 1 or data.get("group") != path.stem:
            fail(f"bad catalogue header: {path}")
        if not isinstance(data.get("points"), dict) or not data["points"]:
            fail(f"empty point group: {path}")
        if not isinstance(data.get("files"), list) or not data["files"]:
            fail(f"empty stock file list: {path}")
        for file in data["files"]:
            if not re.fullmatch(r"/system/(?:app|framework)/[A-Za-z0-9_.-]+", file):
                fail(f"unsafe stock file: {file}")
            files.add(file)
        apps[path.stem] = data
        for key, rule in data["points"].items():
            if not POINT.fullmatch(key) or not key.startswith(path.stem + ".") or key in points:
                fail(f"invalid point id: {key}")
            if rule.get("type") not in {"class", "field", "method", "ctor"} or not isinstance(rule.get("match"), dict):
                fail(f"invalid point rule: {key}")
            points[key] = rule
    if not points:
        fail("catalogue absent")
    for key, rule in points.items():
        if rule["type"] != "class" and rule.get("owner") not in points:
            fail(f"missing owner for {key}")
        for reference in re.findall(r"@([a-z0-9_.]+)", json.dumps(rule["match"])):
            if reference not in points or points[reference]["type"] != "class":
                fail(f"invalid class reference {reference} in {key}")
    return points, files, apps


def requirements(points):
    found = {}
    for module in sorted((ROOT / "modules").glob("*/module.properties")):
        path = module.parent / "hookpoints.required"
        if not path.exists():
            continue
        ids = path.read_text().splitlines()
        if not ids or ids != sorted(set(ids)):
            fail(f"required points must be nonempty, unique and sorted: {path}")
        for key in ids:
            if key not in points:
                fail(f"unknown required point {key} in {path}")
            owner = points[key].get("owner")
            if owner and owner not in ids:
                fail(f"required owner {owner} omitted by {path}")
        found[module.parent.name] = ids
    return found


def validate_bundle(directory, points, stock_files, apps, required):
    relative = directory.relative_to(ROOT / "maps")
    if len(relative.parts) != 4 or not all(re.fullmatch(r"[a-z0-9_-]+", part) for part in relative.parts):
        fail(f"invalid map layout: {directory}")
    car = json.loads((directory / "car.json").read_text())
    mapping = json.loads((directory / "map.json").read_text())
    if set(mapping) != {"schema", "points"} or mapping["schema"] != 1:
        fail(f"invalid map schema: {directory}")
    if set(car) != {"schema", "product", "onebin", "android", "files", "app_files", "missing", "by_reference"} or car["schema"] != 1:
        fail(f"invalid car schema or forbidden field: {directory}")
    if not PRODUCT.fullmatch(car["product"]) or not PRODUCT.fullmatch(car["onebin"]) or not re.fullmatch(r"[0-9.]+", car["android"]):
        fail(f"invalid car identity: {directory}")
    if set(car["files"]) != stock_files or not all(MD5.fullmatch(v) for v in car["files"].values()):
        fail(f"stock hash coverage mismatch: {directory}")
    if set(car["app_files"]) != set(apps):
        fail(f"app file coverage mismatch: {directory}")
    for group, spec in apps.items():
        part = car["app_files"][group]
        if set(part) != {"package", "files"} or part["package"] != spec["namespace"] or part["files"] != spec["files"]:
            fail(f"app file declaration mismatch: {group}")
    mapped = mapping["points"]
    if not isinstance(mapped, dict) or not mapped or not set(mapped) <= set(points):
        fail(f"unknown or empty mapped points: {directory}")
    if set(mapped) & set(car["missing"]) or set(mapped) | set(car["missing"]) != set(points):
        fail(f"catalogue coverage mismatch: {directory}")
    if not set(car["by_reference"]) <= set(mapped):
        fail(f"invalid by_reference: {directory}")
    for key, value in mapped.items():
        if set(value) not in MEMBER_KEYS:
            fail(f"invalid mapped member: {key}")
        if points[key]["type"] != next(k for k in ("class", "field", "method", "ctor") if k in value):
            fail(f"mapped kind mismatch: {key}")
        for field in ("class", "field", "method"):
            if field in value and not re.fullmatch(r"[A-Za-z0-9_.$]+", value[field]):
                fail(f"invalid member name: {key}")
    for module, ids in required.items():
        absent = set(ids) - set(mapped)
        if absent:
            fail(f"{module} requires missing points in {directory}: {sorted(absent)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scope", choices=["catalogue", "all"])
    args = parser.parse_args()
    points, stock_files, apps = catalogue()
    required = requirements(points)
    if args.scope == "all":
        bundles = list((ROOT / "maps").glob("*/*/*/*/car.json"))
        if not bundles:
            fail("no bundled maps")
        for path in bundles:
            validate_bundle(path.parent, points, stock_files, apps, required)
    print(f"validated {len(points)} catalogue points, {len(required)} module requirements" +
          (f", {len(bundles)} bundled maps" if args.scope == "all" else ""))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"validate-maps: {error}", file=sys.stderr)
        sys.exit(1)
