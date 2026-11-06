Status: current — host/Actions measurements, test runner, workflows and upstream review.

# CI timing and upstream review

## Measurements

Before the 2026-11-07 speedup (CI 42283754098, Release 42283736341, ubuntu-latest, 4 CPUs):
CI 8.5 min (make test 458 s: root suites 433 s on 3 workers, 1,123 task-seconds of which
mksh 749 s and /bin/sh 374 s; the serial host chain 154 s ran beside it, module install 97 s;
setup 42 s, of which apt 30 s and API 19 download 12 s). Release 10.5 min: the same make test
again on the tagged commit. Local make test (macOS, 10 CPUs, from clean): 251 s.

Causes found and fixed:
- mksh R59 takes one to two seconds to redefine every common.sh function when one process
  sources it twice (bash/dash: milliseconds). Test fixtures did that per call; the shipped
  scripts do not (the dispatcher fix landed separately). Fixtures now load it once per process.
- The md5 and ls/chown fixtures were Python: one interpreter start per call (one runtime test
  made 1,328 ownership calls). They are now shell + host md5sum/ls/awk.
- Fixture sleeps of one to four real seconds (wifi daemon polls and scan wait) are 0.25 s steps
  with the same retry counts.
- /bin/sh repeated every root suite. It now runs a smoke set (below).
- test-module-install ran ten tests serially and compiled its extractor ten times; it is
  sharded and compiles once.

After (local, same host, from clean): make test 98-104 s over two runs (CPU about 520 s vs
1,291 s); the full two-shell matrix (`HMG_SEOGAEMO_TEST_SHELLS=all make test-suites`) 225 s.
Remaining long tasks are real production waits/work: the export path's per-file size reads
(test_mutation_api_and_export, a stateful sequence of seven exports, about 50-60 s), zygote/dex,
the diagnostics 10 s probe guard. Expected CI: about 3-4 min (setup about 15 s with caches,
package about 25 s, suites about 2-2.5 min with the longest task as the floor). Expected
Release: about 2-3 min when CI passed the tagged commit, about 4-5 min otherwise. These are
estimates until the first runs on main.

## Runner and workflows

- `tools/run-tests.py` runs every suite in one pool sized to the CPU count (known long tasks
  first): root suites (sharded per test function where built on `testlib.run_tests`), every
  `modules/*/test/run.sh`, the host checks (module install sharded, maps, lints, HSM, OTA,
  confirm routing, generator) and `make test-java`. Each task has private temp fixtures and only
  reads the completed package. Logs: unique run dir under `out/test-logs`. Failure prints the
  task log, stops scheduling and kills active process groups. Serial diagnosis:
  `HMG_SEOGAEMO_TEST_JOBS=1` or `--jobs 1`; subset: `make test TESTS_ONLY=word,...`;
  `make test-suites` runs the pool on the package already in `out/` without rebuilding.
- Shells: root and module suites run on mksh (the head-unit shell). /bin/sh runs only
  `test_common`, `test_output_bytes`, `test_file_key_failures` (the byte-exact sg_write/sg_print
  fallback for shells without `print`), `test-entry-dispatch.py` and the skip-consent-screen module.
  Android 4.4 syntax is covered by the shell lint. `HMG_SEOGAEMO_TEST_SHELLS=all` restores the
  full matrix; without mksh everything runs on /bin/sh.
- `.github/actions/setup` is shared by both workflows: JDK 17, cached API 19 platform and mksh
  .deb, preinstalled LLVM 18 on PATH (apt `clang lld` fallback). CI runs `make test` once.
- Release builds and signs the package, runs `verify-dist` and the APK certificate check, then
  waits for CI on the same SHA (the build overlaps it). CI success: `make test-dist` (package
  and dispatcher suites) on the release-signed build; anything else: `make test-suites` (full
  suite on that same build, never rebuilt). Draft, asset list check and publish unchanged. CI
  runs on main pushes, so a tag on a commit CI never tested always takes the full path.
- Action SHAs match the verified GitHub ref-API tags checkout v4.4.0, setup-java v4.9.1 and
  cache v4.3.0.
- Not added: job split (setup and package rebuild per job cost more than the parallelism wins on
  a 4-CPU runner); Gradle cache (raw javac/d8/aapt build).

## Upstream review

Reviewed against HMG-API v1.0.1 `dd10045`. All five review components keep baseline `4fab437`. The wireless mod, native converter and recipes, loader, zygote, copied hook classes, MemRecipe and recipe tools have no changes in that range. Broader generic hook loader changes appear in the report and are distinguished in `UPSTREAM.md`. Catalogue translation is separate work; no upstream source imported.
