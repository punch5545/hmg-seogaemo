#!/bin/sh
# Follows HMG-API core/hookpoints/* and mapper paths@4fab437 (change tracking only).
set -eu
clone=${1:-tmp/HMG-API}
[ -d "$clone/.git" ] || { echo "HMG-API clone missing: $clone" >&2; exit 2; }
git -C "$clone" cat-file -e '4fab437^{commit}' || { echo 'Pinned HMG-API commit absent' >&2; exit 2; }
set -- \
  core/runtime/java/hmgapi/engine/Dex.java \
  core/runtime/java/hmgapi/engine/Json.java \
  core/runtime/java/hmgapi/engine/CarMap.java \
  core/runtime/java/hmgapi/engine/MapProbe.java \
  core/runtime/java/hmgapi/engine/MapCheck.java \
  tools/map-car.py tools/probe-test.py tools/test-hooks.py \
  core/hookpoints/btsettings.json core/hookpoints/buttonsettings.json \
  core/hookpoints/dipo.json core/hookpoints/framework.json \
  core/hookpoints/gpm.json core/hookpoints/homesetting.json \
  core/hookpoints/iap2.json core/hookpoints/launcher.json \
  core/hookpoints/panel.json core/hookpoints/settings.json \
  core/hookpoints/themesettings.json
printf 'Tracked HMG-API commits since 4fab437:\n'
git -C "$clone" log --oneline 4fab437..HEAD -- "$@"
printf '\nTracked path diff summary:\n'
git -C "$clone" diff --stat 4fab437..HEAD -- "$@"
