Source: android44-shell-audit.md. Historical: validation run record of the 2026-11-03 audit; not current state.

Passed 2026-11-03: `make clean`, `make test`, `make package` with `tmp/mksh-test-bin` on PATH (mapping, package, runtime and all four module suites under both shells); `make verify-dist`; lint (14 source scripts, 26 package/OTA members, exactly four source waivers repeated in archives); `git diff --check`. The APK used the throwaway debug key (v1 JAR at API 19); no release keys, so no signed OTA manifest, and `verify-dist` reports that debug-build condition.
