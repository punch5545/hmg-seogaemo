#!/bin/sh
# Host-only review helper. Baselines are explicit in UPSTREAM.md; never advance automatically.
set -eu
clone=tmp/HMG-API
if [ "$#" -gt 0 ]; then clone=$1; fi
[ -d "$clone/.git" ] || { echo "HMG-API clone missing: $clone" >&2; exit 2; }
report() {
  label=$1 baseline=$2
  shift 2
  git -C "$clone" cat-file -e "$baseline^{commit}" || { echo "Pinned HMG-API commit absent: $baseline" >&2; exit 2; }
  printf '\n=== %s (baseline %s; target HEAD) ===\n' "$label" "$baseline"
  printf 'Commits:\n'
  git -C "$clone" log --oneline "$baseline..HEAD" -- "$@"
  printf 'Changed paths:\n'
  git -C "$clone" diff --name-status "$baseline..HEAD" -- "$@"
  printf 'Diff summary:\n'
  git -C "$clone" diff --stat "$baseline..HEAD" -- "$@"
}

printf 'HMG-API target: '
git -C "$clone" rev-parse HEAD

report 'Mapper and catalogue' 4fab437 \
  core/runtime/java/hmgapi/engine/Dex.java \
  core/runtime/java/hmgapi/engine/Json.java \
  core/runtime/java/hmgapi/engine/CarMap.java \
  core/runtime/java/hmgapi/engine/MapProbe.java \
  core/runtime/java/hmgapi/engine/MapCheck.java \
  tools/map-car.py tools/probe-test.py tools/test-hooks.py core/hookpoints

report 'Ported wireless payload, loader, zygote, native recipes and converter' 4fab437 \
  mods/wireless-carplay-aa core/loader core/zygote \
  core/runtime/java/hmgapi/hook \
  core/runtime/java/hmgapi/engine/MemRecipe.java \
  tools/mkrecipe.py tools/mkmemrecipe.py

report 'Install, uninstall and restore' 4fab437 \
  install core/app/install.sh tools/mkinstallers.sh tools/mknemesismod.sh

report 'hmgapid and runtime engine' 4fab437 \
  core/hmgapid core/runtime/java/hmgapi/engine core/runtime/java/hmgapi/vehicle \
  ':(glob)core/runtime/*test.sh' core/runtime/test

report 'CI and workflows' 4fab437 \
  .github/workflows .github/dependabot.yml Makefile tools/test-all.sh tools/check-scripts.sh
