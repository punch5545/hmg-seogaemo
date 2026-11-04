Status: current — host/Actions measurements and upstream review on 2026-11-04.

# CI timing and upstream review

The requested GitHub Actions logs show tests dominate runtime. CI run
41772317493 failed after 198 seconds in the former automatic-intent-lock race;
it is not a successful timing baseline. CI run 41773103188 passed in 366 seconds,
including 285.88 seconds in the shell matrix, 21 seconds of native/SDK setup,
and a redundant 12.60-second package rebuild after tests. Release 41772317461
passed in 367 seconds, including 291.27 seconds in the shell matrix, 19 seconds
of setup, and roughly 13 seconds rebuilding the already-tested package.

The local serial baseline on this macOS host passed in 465.69 seconds:
7.76 seconds building/packaging, 9.34 seconds of pre-matrix checks,
445.28 seconds in the shell matrix, and 3.31 seconds of remaining checks.
The matrix exercises seven root suites and every discovered module suite
under both /bin/sh and mksh when installed (26 suite invocations currently).

The final parallel make test passed in 243.05 seconds, a 47.8% reduction
(222.64 seconds saved). Its matrix completed in 224.12 seconds, down from
445.28 seconds (49.7%); all 26 invocations passed. Applying that matrix ratio
to the successful GitHub baselines and removing the redundant package build
projects about 211 seconds for CI and 209 seconds for Release (roughly
3.5 minutes each). These are estimates, not remote after measurements; this
branch was not pushed. Runner load and platform differences can affect them.

The runner now uses two workers by default and schedules longer suites first.
Each suite retains sequential tests and isolated temporary fixtures. Completed
package outputs are read-only during the matrix; module tests use unique
temporary directories, including Java compile outputs. Logs are kept under
out/test-logs in a unique run directory. Failure prints the suite log, stops
scheduling work, and terminates active process groups. Set
HMG_SEOGAEMO_TEST_JOBS=1 or pass --jobs 1 for serial diagnosis.

Both workflows run make test once, which already depends on package. Release
then verifies and publishes those exact signed artifacts. Its environment,
certificate check, signature/hash checks, draft asset verification and publish
ordering are retained. CI no longer repeats the separate map validation already
covered by make test. JDK 17, API 19 and preinstalled SDK setup are retained.
Action SHAs match the v4.4.0 checkout and v4.9.1 setup-java tags verified through
GitHub's ref API, preserving the versions resolved in the measured runs.

No job split or cross-workflow artifact reuse was added: runner/artifact transfer
overhead would duplicate setup, and release-signed artifacts must be tested in
the protected release job. No Gradle cache applies to the raw javac/d8/aapt build.
SDK setup is only 10–14 seconds in these logs; caching mutable build outputs or
the whole SDK adds invalidation complexity without addressing the main cost.
No timing assertions or runtime implementations were changed.

The expanded upstream report was run against HMG-API v1.0.1 dd10045. All five
review components keep baseline 4fab437. The wireless mod, native converter and
recipes, loader, zygote, copied hook classes, MemRecipe and recipe tools have no
changes in that range. Broader generic hook loader changes are visible in the
report and explicitly distinguished in UPSTREAM.md. Catalogue translation is
owned by separate work; this change imports no upstream source.

Validation: final make test, make verify-dist, Python compilation, host shell
syntax, expanded upstream review, missing-clone failure, and git diff --check
passed. Synthetic scheduler checks covered two-worker success, failure log and
active-child cancellation, and single-shell serial fallback. Installed Ruby
YAML parsed both workflows, Dependabot and the issue form; actionlint was not
installed, and no new validation tools were installed.
