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
CI and release install `mksh`; test runners use it when present and otherwise
use `sh`. The module tests keep restricted command paths; `stat` remains forbidden,
while their tested `cmp` and `grep -E` options work on the host implementations.
