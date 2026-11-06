#!/usr/bin/env python3
"""Decode the packed app log ("HSR1." + base64url raw deflate) that the app's issue QR code puts in
a GitHub issue. Reads a file or stdin; with --github-comment it reads the issue from
GITHUB_EVENT_PATH and posts (or updates) one comment with the decoded text."""
import argparse
import base64
import json
import os
import re
import sys
import urllib.request
import zlib

PATTERN = re.compile(r"HSR1\.([A-Za-z0-9_-]{4,})")
MAX_TEXT = 65536
MARKER = "<!-- hmg-seogaemo-report-log -->"


def decode(packed):
    data = base64.urlsafe_b64decode(packed + "=" * (-len(packed) % 4))
    inflater = zlib.decompressobj(-15)
    text = inflater.decompress(data, MAX_TEXT)
    if inflater.unconsumed_tail:
        raise ValueError("decoded log is larger than 64 KiB")
    return text.decode("utf-8", "replace")


def decode_all(body):
    logs = []
    for match in PATTERN.finditer(body or ""):
        try:
            logs.append(decode(match.group(1)))
        except (ValueError, zlib.error) as error:
            logs.append(f"[could not decode: {error}]")
    return logs


def printable(text):
    text = "".join(c for c in text if c in "\n\t" or (ord(c) >= 0x20 and ord(c) != 0x7F))
    return text.replace("````", "` ` ` `")


def api(method, url, token, payload=None):
    request = urllib.request.Request(url, method=method, data=None if payload is None else json.dumps(payload).encode())
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Accept", "application/vnd.github+json")
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read() or b"null")


def github_comment():
    with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as handle:
        issue = json.load(handle)["issue"]
    logs = decode_all(issue.get("body"))
    if not logs:
        return
    body = MARKER + "\n**Decoded app log** from the QR code. Review it for personal data.\n"
    for log in logs:
        body += "\n````text\n" + printable(log) + "\n````\n"
    token = os.environ["GITHUB_TOKEN"]
    comments_url = issue["comments_url"]
    for comment in api("GET", comments_url + "?per_page=100", token):
        if comment.get("user", {}).get("login") == "github-actions[bot]" and MARKER in comment.get("body", ""):
            api("PATCH", comment["url"], token, {"body": body})
            return
    api("POST", comments_url, token, {"body": body})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", nargs="?", help="issue body or packed text (default: stdin)")
    parser.add_argument("--github-comment", action="store_true")
    args = parser.parse_args()
    if args.github_comment:
        github_comment()
        return 0
    body = open(args.file, encoding="utf-8").read() if args.file else sys.stdin.read()
    logs = decode_all(body)
    if not logs:
        print("no HSR1 log found", file=sys.stderr)
        return 1
    print("\n\n".join(logs))
    return 0


if __name__ == "__main__":
    sys.exit(main())
