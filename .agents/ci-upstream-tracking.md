Status: current — host/Actions measurements and upstream review on 2026-11-04.

# CI timing and upstream review

## Measurements

Tests dominate runtime.

| Run | Result | Notes |
| --- | --- | --- |
| CI 41772317493 | failed at 198 s | former automatic-intent-lock race; not a timing baseline |
| CI 41773103188 | passed 366 s | shell matrix 285.88 s, native/SDK setup 21 s, redundant package rebuild 12.60 s |
| Release 41772317461 | passed 367 s | shell matrix 291.27 s, setup 19 s, ~13 s rebuilding the tested package |
| Local serial (macOS) | 465.69 s | build/package 7.76, pre-matrix checks 9.34, matrix 445.28, rest 3.31 |
| Local parallel `make test` | 243.05 s (-47.8%, 222.64 s saved) | matrix 224.12 s (-49.7%); all 26 invocations passed |

The matrix runs seven root suites plus every discovered module suite under /bin/sh and mksh when installed (26 invocations). Applying the matrix ratio to the GitHub baselines and dropping the redundant package build projects ~211 s (CI) and ~209 s (Release) saved; estimates only, not pushed or measured remotely, and runner load varies.

## Runner and workflows

- Test runner: two workers by default, longer suites first; tests within a suite stay sequential with isolated temp fixtures. Package outputs are read-only during the matrix; module tests use unique temp dirs (including Java outputs). Logs: unique run dir under `out/test-logs`. Failure prints the suite log, stops scheduling and kills active process groups. Serial diagnosis: `HMG_SEOGAEMO_TEST_JOBS=1` or `--jobs 1`.
- Both workflows run `make test` once (it depends on `package`); Release verifies and publishes those exact signed artifacts, keeping its environment, certificate check, signature/hash checks, draft asset verification and publish ordering. CI no longer repeats the map validation covered by `make test`. JDK 17, API 19 and preinstalled SDK setup retained.
- Action SHAs match the verified GitHub ref-API tags checkout v4.4.0 and setup-java v4.9.1 (versions resolved in the measured runs).
- Not added: job split or cross-workflow artifact reuse (transfer overhead duplicates setup; release-signed artifacts must be tested in the protected release job); Gradle cache (raw javac/d8/aapt build); SDK/build-output caching (SDK setup is only 10–14 s; invalidation complexity without addressing the main cost).

## Upstream review

Expanded report run against HMG-API v1.0.1 `dd10045`. All five review components keep baseline `4fab437`. The wireless mod, native converter and recipes, loader, zygote, copied hook classes, MemRecipe and recipe tools have no changes in that range. Broader generic hook loader changes appear in the report and are distinguished in `UPSTREAM.md`. Catalogue translation is separate work; no upstream source imported.

## Validation done

Final `make test`, `make verify-dist`, Python compilation, host shell syntax, expanded upstream review, missing-clone failure, `git diff --check`. Synthetic scheduler checks: two-worker success, failure log and active-child cancellation, single-shell serial fallback. Ruby YAML parsed both workflows, Dependabot and the issue form; actionlint not installed.
