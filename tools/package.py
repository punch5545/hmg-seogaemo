#!/usr/bin/env python3
"""Build deterministic NEMESIS archives and a signed release manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

from version import read_version

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "out/dist"
TARGET = {"os": "NEEV.KOR.*.260902", "models": "neev_kr", "androidApi": 19}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def add_zip(path, files):
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, source in sorted(files.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            mode = 0o755 if name.endswith("/") or name.endswith(".sh") or name.endswith("/seogaemo-root") else 0o644
            info.external_attr = (mode << 16) | (0x10 if name.endswith("/") else 0)
            archive.writestr(info, source if isinstance(source, bytes) else source.read_bytes())


def apk_version(apk, aapt):
    output = subprocess.check_output([aapt, "dump", "badging", str(apk)], text=True)
    match = re.search(r"^package: name='com\.seogaemo\.app' versionCode='([0-9]+)' versionName='([^']+)'", output, re.M)
    if not match:
        raise ValueError("APK package/version not found by aapt")
    return match.group(2), int(match.group(1))


def build(aapt):
    version, code = read_version(ROOT / "VERSION")
    apk = ROOT / "out/seogaemo.apk"
    if apk_version(apk, aapt) != (version, code):
        raise ValueError("APK version differs from VERSION")
    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir(parents=True)
    prefix = f"hmg-seogaemo-{version}"
    package_zip = DIST / f"{prefix}-nemesis-package.zip"
    usb_zip = DIST / f"{prefix}-nemesis-usb.zip"
    apk_asset = DIST / f"{prefix}.apk"
    shutil.copyfile(apk, apk_asset)
    files = {"app/Seogaemo.apk": apk}
    for source in (ROOT / "nemesis/package").rglob("*"):
        if source.is_file() and not source.name.startswith("."):
            name = source.relative_to(ROOT / "nemesis/package").as_posix()
            data = source.read_bytes()
            if name == "NEMESIS.data":
                if data.count(b"@VERSION@") != 1:
                    raise ValueError("descriptor version placeholder missing")
                data = data.replace(b"@VERSION@", version.encode())
            files[name] = data
    for source in (ROOT / "out/package").rglob("*"):
        if source.is_file():
            files[source.relative_to(ROOT / "out/package").as_posix()] = source
    for module in sorted((ROOT / "modules").iterdir()):
        payload = module / "payload"
        if payload.is_dir():
            files[f"modules/{module.name}.d/"] = b""
            for source in payload.rglob("*"):
                if source.is_file():
                    name = f"modules/{module.name}.d/{source.relative_to(payload).as_posix()}"
                    files[name] = source
    add_zip(package_zip, files)
    add_zip(usb_zip, {
        "NEMESIS.data": ROOT / "nemesis/usb/NEMESIS.data",
        "packages/seogaemo.zip": package_zip,
    })
    assets = [apk_asset, package_zip, usb_zip]
    private = os.environ.get("OTA_MANIFEST_PRIVATE_KEY")
    if private:
        if not Path(private).is_file():
            raise ValueError("OTA_MANIFEST_PRIVATE_KEY does not name a file")
        manifest = DIST / f"{prefix}-manifest.json"
        signature = DIST / f"{prefix}-manifest.json.sig"
        payload = {
            "schema": 1,
            "version": version,
            "versionCode": code,
            "target": TARGET,
            "assets": [{"name": p.name, "size": p.stat().st_size, "sha256": digest(p)} for p in sorted(assets)],
        }
        manifest.write_bytes((json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n").encode())
        with signature.open("wb") as output:
            subprocess.run(["openssl", "dgst", "-sha256", "-sign", private, str(manifest)], stdout=output, check=True)
        assets.extend([manifest, signature])
    else:
        print("OTA_MANIFEST_PRIVATE_KEY absent: skipping manifest and signature")
    (DIST / "SHA256SUMS").write_text("".join(f"{digest(p)}  {p.name}\n" for p in sorted(assets)), encoding="ascii")
    print(f"Built {len(assets) + 1} assets in {DIST}")


def verify(aapt):
    version, code = read_version(ROOT / "VERSION")
    prefix = f"hmg-seogaemo-{version}"
    expected = {f"{prefix}.apk", f"{prefix}-nemesis-package.zip", f"{prefix}-nemesis-usb.zip", "SHA256SUMS"}
    manifest = DIST / f"{prefix}-manifest.json"
    signature = DIST / f"{prefix}-manifest.json.sig"
    if manifest.exists() != signature.exists():
        raise ValueError("manifest and signature must occur together")
    if manifest.exists():
        expected.update((manifest.name, signature.name))
        key = os.environ.get("OTA_MANIFEST_PUBLIC_KEY", str(ROOT / "keys/ota-manifest-public.pem"))
        subprocess.run(["openssl", "dgst", "-sha256", "-verify", key, "-signature", str(signature), str(manifest)], check=True)
        data = json.loads(manifest.read_text(encoding="utf-8"))
        if data["schema"] != 1 or data["version"] != version or data["versionCode"] != code or data["target"] != TARGET:
            raise ValueError("manifest metadata mismatch")
        names = {x["name"] for x in data["assets"]}
        if len(names) != 3 or names != expected - {"SHA256SUMS", manifest.name, signature.name}:
            raise ValueError("manifest asset names mismatch")
        for asset in data["assets"]:
            path = DIST / asset["name"]
            if path.stat().st_size != asset["size"] or digest(path) != asset["sha256"]:
                raise ValueError(f"manifest hash mismatch: {path.name}")
    else:
        print("No signed manifest to verify (debug distribution)")
    actual = {p.name for p in DIST.iterdir() if p.is_file()}
    if actual != expected:
        raise ValueError(f"distribution assets mismatch: {actual ^ expected}")
    lines = (DIST / "SHA256SUMS").read_text(encoding="ascii").splitlines()
    checks = {}
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9_.-]+)", line)
        if not match or match.group(2) in checks:
            raise ValueError("invalid SHA256SUMS")
        checks[match.group(2)] = match.group(1)
    if set(checks) != expected - {"SHA256SUMS"}:
        raise ValueError("SHA256SUMS asset list mismatch")
    for name, value in checks.items():
        if digest(DIST / name) != value:
            raise ValueError(f"SHA256SUMS mismatch: {name}")
    if apk_version(DIST / f"{prefix}.apk", aapt) != (version, code):
        raise ValueError("distribution APK version mismatch")
    print("Distribution hashes and version verified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("build", "verify"))
    parser.add_argument("--aapt", required=True)
    args = parser.parse_args()
    try:
        (build if args.command == "build" else verify)(args.aapt)
    except (ValueError, FileNotFoundError, subprocess.CalledProcessError) as exc:
        print(f"package: {exc}", file=sys.stderr)
        sys.exit(1)
