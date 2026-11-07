#!/usr/bin/env python3
"""Writes aa-lane-routes.s into the segment of the aa-tbt-* recipes (run from the module root).

The recipes must still hold the upstream lane packing (the four sites below with their upstream
bytes); the segment grows by the tail (4-aligned end of the upstream segment). Needs clang and
llvm-objcopy for thumbv7. test/aalanes/test.py checks the result.
"""
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, "..", ".."))
RECIPES = ("aa-tbt-48ee793a", "aa-tbt-a0bea1c4")
# segment offset, upstream bytes as stored (little-endian halfwords)
SITES = (
    (0xF7E, "4ff0010a"),       # mov.w r10, #1
    (0xFF2, "18bf4ff0020a"),   # it ne; movne.w r10, #2
    (0x101C, "83f86c10"),      # strb.w r1, [r3, #0x6c]
    (0x1598, "00"),            # arrow table entry of shape 9
)
HEADER = ("# Modified by HMG-Seogaemo: the lane byte of event 34 is the mask of recommended arrow slots, "
          "U-turn right gets an arrow (source payload/recipes/aa-lane-routes.s, test test/aalanes); "
          "the segment grows by the tail after the upstream code\n")


def assemble(tail):
    clang = os.environ.get("CLANG", "clang")
    objcopy = os.environ.get("OBJCOPY", "llvm-objcopy")
    with tempfile.TemporaryDirectory() as d:
        obj, out = os.path.join(d, "x.o"), os.path.join(d, "x.bin")
        subprocess.check_call([clang, "--target=thumbv7-linux-androideabi", "-x", "assembler-with-cpp",
                               "-DTAIL=%d" % tail, "-c", os.path.join(HERE, "aa-lane-routes.s"), "-o", obj])
        subprocess.check_call([objcopy, "-O", "binary", "--only-section=.text", obj, out])
        return open(out, "rb").read()


def main():
    for name in RECIPES:
        path = os.path.join(MOD, "files", "recipes", name + ".recipe")
        text = open(path).read()
        m = re.search(r"^segment (\S+) ([0-9a-f]+)$", text, re.M)
        seg = bytearray.fromhex(m.group(2))
        for off, want in SITES:
            if seg[off:off + len(want) // 2].hex() != want:
                sys.exit("%s: segment +0x%x is not the upstream lane code (%s), nothing changed" % (name, off, seg[off:off + len(want) // 2].hex()))
        tail = (len(seg) + 3) & ~3
        blob = assemble(tail)
        new = bytearray(seg) + bytearray(tail - len(seg))
        for off, want in SITES:
            n = len(want) // 2
            new[off:off + n] = blob[off:off + n]
        new += blob[tail:]
        text = text.replace(m.group(0), "segment %s %s" % (m.group(1), new.hex()))
        lines = text.split("\n")
        at = max(i for i, l in enumerate(lines) if l.startswith("#")) + 1
        lines.insert(at, HEADER.rstrip("\n"))
        open(path, "w").write("\n".join(lines))
        print("%s: segment %d -> %d bytes" % (name, len(seg), len(new)))


if __name__ == "__main__":
    main()
