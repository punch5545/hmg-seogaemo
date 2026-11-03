# hmg-seogaemo

UI for Hyundai/Kia/Genesis Gen5W head units (Android 4.4.2, 1920x720, hdpi).

## Before starting work

- Read everything in `.agents/`: analysis notes on the upstream projects and UI
  guidelines (e.g. `.agents/display-size-support.md`, `.agents/ui-guidelines.md`).
  Add new findings there.
- If `AGENTS.local.md` exists, read and follow it. It holds local, per-user notes
  and is gitignored.

## Layout

- Upstream clones (NEMESIS, HMG-API, HMG-Leafia-Plus) live in `tmp/` (gitignored).
- Design system: `DESIGN.md` (Vercel-inspired, adapted to a dark car theme).

## Build and test

- `VERSION` is the single edited version. `make` builds the API 19 APK at `out/seogaemo.apk` and generates the built-in module registry.
- `make test` builds and checks the APK, module registry, NEMESIS zip layout, descriptor, and root script syntax/dispatch. `make clean` removes generated output.
- `make package` writes `hmg-seogaemo-<version>-nemesis-package.zip`, `hmg-seogaemo-<version>-nemesis-usb.zip`, the APK, and `SHA256SUMS` to `out/dist/`. With `OTA_MANIFEST_PRIVATE_KEY` set to a PEM path, it also writes the signed manifest and signature. `make verify-dist` verifies hashes and the signature with `keys/ota-manifest-public.pem` (or `OTA_MANIFEST_PUBLIC_KEY` for a local test).
- Release APK signing uses `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, and `RELEASE_KEY_ALIAS` together. Otherwise the build uses a throwaway debug key. Never release a debug APK or commit key material.
- To cut a release: bump `VERSION` to `X.Y.Z`, review and commit the change, then push tag `vX.Y.Z`. The release workflow builds and verifies assets with the `release` environment secrets, uploads them to a draft GitHub Release, verifies the asset list, then publishes it.
