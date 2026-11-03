Status: current — off-car runtime and host-test findings; physical-car behavior remains unverified.

# Shared runtime implementation findings

- Android 4.4 toolbox `ls -ln` omits the link-count field printed by desktop `ls`.
  The runtime detects the format before reading size, uid, and gid. Off-car tests
  include a synthetic toolbox listing.
- The app invokes a non-SUID copy of NEMESIS through `nemesis root -c`, installed
  in the package's `bin/` directory. Launching the `/system/xbin/nemesis` SUID
  binary from an app process can enter the weak-root path on this NEMESIS build.
- Zygote installation is staged with the stock copy first and wrapper last. The
  wrapper's `starts` counter is dynamic; its final decimal value is recorded
  before guarded restore. The two Dalvik cache namespaces are derived from the
  owned system JAR and validated package name, then recorded at revert.
- Off-car tests use fake roots and a fake NEMESIS command. They do not establish
  that the wrapper boots, Dalvik loads the JAR, or wireless projection works on
  the target head unit.
# Host shell test parity (2026-11-03)

Root script tests prepend `tools/testlib/fakebin`, whose `md5` prints the Android
4.4 toolbox digest format (two spaces before the path) and hashes stdin for
`sg_file_key`. The updater checks digest fields independently of spacing.
CI and release install `mksh`; root regression suites run under both `sh` and
`mksh` when installed. The module tests keep restricted command paths; `stat` remains forbidden,
while their tested `cmp` and `grep -E` options work on the host implementations.

The head unit has no `printf` builtin or binary. Upstream evidence is in
`tmp/HMG-Leafia-Plus/packages/wireless-carplay-aa/comp/3_bridge/scripts/common.sh:42`
and the NEMESIS `putline` helper in
`tmp/hmg_nemesis/Sources/packages/adb-tcp/scripts/common.sh:20`. Runtime output
uses `sg_print` (`print -r --`) and exact text writes use `sg_write`
(`print -rn --`). POSIX host shells without `print` use a C-locale byte count
and bounded `dd` here-document, adding only the explicit newline for `sg_print`.
A failed write never retries through a fallback. File and property backing
baselines are copied directly, preserving binary bytes and present-empty versus
absent state. Regression tests compare bytes for empty text, trailing newlines,
backslashes, option-looking arguments, and UTF-8 with no external `printf` on
their restricted PATH; root-script lint rejects `printf` throughout the package
and module scripts.
