#!/usr/bin/env python3
"""The issue-log decoder reads what the app's IssueLink packs (written by QrCodeTest) and refuses bombs."""
import base64
import importlib.util
from pathlib import Path
import sys
import zlib

spec = importlib.util.spec_from_file_location("decoder", Path(__file__).with_name("decode-issue-log.py"))
decoder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(decoder)

cut, whole = Path(sys.argv[1]).read_text().split()
text = decoder.decode_all("issue body\n" + cut + "\n")[0]
lines = text.split("\n")
assert lines[0] == "# app 1.0 한글", lines[0]
assert lines[1].startswith("line 0 state=failed") and len(lines) > 20, len(lines)
assert lines[-1].endswith(" more lines in the USB export]"), lines[-1]
assert decoder.decode_all(whole) == ["a\nb"]

bomb = zlib.compressobj(9, zlib.DEFLATED, -15)
packed = base64.urlsafe_b64encode(bomb.compress(b"x" * 200000) + bomb.flush()).decode().rstrip("=")
assert decoder.decode_all("HSR1." + packed)[0].startswith("[could not decode")
assert decoder.printable("a````b\x00\x1b[31m") == "a` ` ` `b[31m"
print("issue log decoder checks passed")
