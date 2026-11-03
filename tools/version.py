#!/usr/bin/env python3
"""Validate the one edited version and render the API 19 manifest."""
import argparse
import pathlib
import re


def read_version(path):
    value = pathlib.Path(path).read_text(encoding="ascii").strip()
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", value):
        raise ValueError("VERSION must be MAJOR.MINOR.PATCH in decimal")
    major, minor, patch = map(int, value.split("."))
    if major > 2146 or minor >= 1000 or patch >= 1000:
        raise ValueError("VERSION component is out of range")
    code = major * 1000000 + minor * 1000 + patch
    if code < 1 or code > 2147483647:
        raise ValueError("versionCode is outside Android's positive 31-bit range")
    return value, code


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-file", default="VERSION")
    parser.add_argument("--manifest", metavar="OUTPUT")
    parser.add_argument("--template", default="app/AndroidManifest.xml")
    parser.add_argument("--code", action="store_true")
    args = parser.parse_args()
    version, code = read_version(args.version_file)
    if args.manifest:
        source = pathlib.Path(args.template).read_text(encoding="utf-8")
        if source.count("@VERSION@") != 1 or source.count("@VERSION_CODE@") != 1:
            raise ValueError("manifest template must contain both version placeholders once")
        output = source.replace("@VERSION_CODE@", str(code)).replace("@VERSION@", version)
        pathlib.Path(args.manifest).write_text(output, encoding="utf-8")
    else:
        print(code if args.code else version)


if __name__ == "__main__":
    main()
