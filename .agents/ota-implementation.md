Status: current — implemented OTA behavior, decisions, research evidence and on-car validation gaps.

# OTA update

Target: NEMESIS 1.16.4+ and HMG-Seogaemo on Android 4.4.2; HMG-API absent. Citations under `tmp/` are
`tmp/hmg_nemesis/...` unless noted. "Direct OTA" still uses the NEMESIS root command channel for privilege; it bypasses
only the NEMESIS package installer.

## Owner decisions

- Telematics modem must **not** be used for updates: only the `wifi` module as a client (or USB stick fallback).
- CarPlay interruption and extra reboots during an update are accepted.
- Avoid NEMESIS as much as possible: APK, module scripts, root payload, module graph and CA bundle are updated by
  HMG-Seogaemo itself. NEMESIS hook/service entry scripts stay thin, stable wrappers that only dispatch into the versioned
  payload; `nemesis install --update` is a rare last resort for wrapper/descriptor schema changes.
- Signed manifest is the security boundary; the app uses TLS 1.2 with system or bundled-root trust and hostname checks.
- Signing keys live in GitHub: environment `release` on seogaemo/hmg-seogaemo, deployments limited to tags `v*`. Secrets:
  `RELEASE_KEYSTORE_B64` (PKCS12, base64), `RELEASE_KEYSTORE_PASSWORD` (store = key password), `RELEASE_KEY_ALIAS`
  (`hmg-seogaemo`), `OTA_MANIFEST_PRIVATE_KEY_PEM` (RSA 3072, SHA256withRSA). Release APK cert SHA-256:
  `3A:71:C3:82:C9:75:98:E1:7F:BE:68:8E:9F:11:69:A9:57:50:03:74:9D:D6:B1:9C:E8:74:60:CC:5D:E1:E8:53`. Public manifest key:
  `keys/ota-manifest-public.pem`. The release workflow uses `environment: release`. APK and manifest keys are separate;
  never commit either private key or copy it to the device. A hash supplied only by the GitHub API is insufficient against
  a compromised release account.

## Release pipeline

- `VERSION` (`MAJOR.MINOR.PATCH`) is the only edited version; tag must equal `v$(cat VERSION)`; versions are monotonic.
  Android `versionName`, 31-bit `versionCode`, NEMESIS `package-version`, module versions and the root
  `installed-version` file derive from it. `NEMESIS.data` descriptor `version=1.1` is separate. About reads
  `PackageInfo.versionName` at runtime.
- Release assets (`hmg-seogaemo-<v>` prefix except `SHA256SUMS`): NEMESIS package zip, USB zip, OTA zip, APK, `SHA256SUMS`,
  signed JSON manifest + detached signature. Manifest lists APK and three zips with sizes and SHA-256; `SHA256SUMS` also
  covers manifest and signature. The job checks tag vs `VERSION`, verifies the distribution and expected APK certificate,
  uploads to a draft, confirms all seven asset names, then publishes (so `/releases/latest` never points at a partial one).

## Client and verifier

- Signed manifest = deterministic compact JSON, schema 1; `UpdateVerifier` accepts only the byte format emitted by
  `tools/package.py` (serializer/schema change needs an updater change). Checks RSA signature with embedded
  `keys/ota-manifest-public.pem`, target, monotonically greater dotted version, exact four asset names, OTA zip size/hash
  before extraction. Rejects duplicate/unsafe ZIP names; bounds compressed and expanded bytes.
- Network: only HTTPS `api.github.com`, `github.com`, `release-assets.githubusercontent.com`,
  `objects.githubusercontent.com`; forces TLS 1.2; validates hostnames; accepts a chain trusted by the Android system store
  or the bundled `app/res/raw/update_roots.pem`; HTTPS redirects only to vetted GitHub hosts; SNI via DNS hostname. A chain
  trusted by neither fails closed (signed USB package remains the fallback). Check the head-unit clock before TLS.
  Java fetcher in `app/src/com/seogaemo/hmg/core/update/`; root updater `nemesis/package/core/update.sh`; fixed verbs
  `hmg-seogaemo-root update stage|apply|rollback|status`, never caller paths.
- Staging: app extracts the authenticated OTA ZIP into private `files/update/payload` with a per-file MD5 list. `core/update.sh`
  checks it with toolbox `md5`, calls the **installed old APK** via `app_process` to copy and verify manifest/OTA SHA-256,
  extracts into a root-owned versioned directory with the trusted old APK, and checks the new directory's Java-generated
  MD5 list before activation. It never runs a verifier from the downloaded payload and assumes no `openssl`, `sha256sum`,
  `unzip`, `sed`, `wc`. The old APK's public key is part of the trust root; key rotation needs an explicit plan.
  Toolbox `md5` prints `<hex> <path>` ([source](https://android.googlesource.com/platform/system/core.git/+/android-4.4.2_r1/toolbox/md5.c));
  MD5 is a change detector, authentication is RSA/SHA-256.
- Root state: `/data/nemesis/backup/hmg-seogaemo/update`; a symlink selects the current payload; initial previous build is the
  stable NEMESIS package tree. Uses the shared root lock, journals phases, keeps the previous build, changes the installed
  descriptor's `package-version` only after APK install and basic health checks. Java `File.renameTo()` swaps the symlink
  atomically (shell `mv` followed directory symlinks in host tests; rejected). Boot reconcile wrapper rolls back a
  transaction interrupted in `applying`, `auto-rollback`, or `rollback`, then dispatches to the selected payload's reconcile.
  Rollback target is the immediately previous verified build; root-side highest-accepted-version blocks other downgrades.
- APK install: root `pm install -r` needs a temporary `sys.boot_completed=0` workaround (restore even on failure; check `pm`
  output plus `pm path`); same signer keeps app data, a mismatch forces uninstall and loses it (`pkg.c:460-549`). Unit may
  lack `tail` and working toolbox remount (`tmp/HMG-API/CLAUDE.md:52,58`).
- Module continuity: app persists pre-transition enabled module IDs and `update-check` resume intent in private prefs; enables
  Wi-Fi client via `ModuleManager.requestEnable`; after update/cancel/failure requests the previous set, keeps the snapshot if
  restoration fails. Restoring projection may need a second reboot and drops Wi-Fi; do not claim completion before observed
  status. An accepted check continues after the new boot, Wi-Fi readiness and usable network; the app must be opened (no
  Android boot receiver).
- Entry scripts: do not rewrite `preuninstall`/`postinstall` in place (a half-written restore hook can block safe
  uninstall). Payload-only OTA replaces only the descriptor's `package-version` after health succeeds; hook/descriptor
  schema changes use the signed NEMESIS zip with `nemesis install --update` (`main.c:5`, `pkg.c:759-776,851-889`: stops
  services, runs the **old** preuninstall, removes the old tree; failure after removal can leave no package).
  A process cannot replace the script it is executing; the wrapper launches a separate immutable updater/rollback helper.

## NEMESIS facts

- `packages.db` stores ID, name, lock flag, types, not version; version is parsed from `/data/nemesis/apps/<id>/NEMESIS.data`
  (`pkg.c:18-47,287-307,928-952,1010-1027`). Manager compares against Store/USB catalogs (`PkgUpdates.java:28-40,65-75`);
  its own updater is separate (`UpdateService.java:49-85`). A stale descriptor makes My Packages stale. The Store needs a
  separately submitted package (`07-Submit-a-Package.md:11-56`).
- NEMESIS "Use built-in modem for network services" is gated in Manager's `Portal.java:101-133`; it grants our app no route.
  HMG-API's `updates` option affects the OEM OTAClient, not our releases (`tmp/HMG-API/CLAUDE.md:68`).
- GitHub [latest-release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release) returns the newest
  non-draft/non-prerelease by `created_at`, not highest version; public reads need no token; asset API gives `200` or `302`;
  unauthenticated limit 60 requests/hour per IP.
- TLS: TLS 1.2 supported from API 16 but enabled by default only from API 20 ([SSLSocket](https://developer.android.com/reference/javax/net/ssl/SSLSocket)).
  Manager uses `HttpsURLConnection`, a TLS 1.2 socket factory, hostname verification and a bundled-root fallback
  (`Portal.java:151-161,237-319`, `res/raw/nemesis_roots.pem`). Stock 4.4 cannot be assumed to trust ISRG Root X1
  ([Let's Encrypt](https://letsencrypt.org/ta/docs/certificate-compatibility/): Android 7.1.1+). Do not pin leaves or
  disable hostname checks. API 19 supports `SHA256withRSA`/`SHA256withECDSA`, not Ed25519.

## Live TLS chains and bundled roots (2026-11-03)

`openssl s_client -connect <host>:443 -servername <host> -showcerts`: `api.github.com`, `github.com` served Sectigo DV E36 →
Sectigo Root E46 (cross-signed by USERTrust ECC); `objects.githubusercontent.com`, `release-assets.githubusercontent.com`
served Let's Encrypt YR1 → ISRG Root YR (cross-signed by ISRG Root X1). Saved API and objects chains: `tools/fixtures/tls/`.
Observations, not pins. `app/res/raw/update_roots.pem` (also in the signed OTA ZIP) holds these self-signed anchors:

| Subject | Expiry (UTC) | SHA-256 fingerprint |
| --- | --- | --- |
| C=GB, O=Sectigo Limited, CN=Sectigo Public Server Authentication Root E46 | Mar 21 23:59:59 2046 GMT | `C9:0F:26:F0:FB:1B:40:18:B2:22:27:51:9B:5C:A2:B5:3E:2C:A5:B3:BE:5C:F1:8E:FE:1B:EF:47:38:0C:53:83` |
| C=GB, O=Sectigo Limited, CN=Sectigo Public Server Authentication Root R46 | Mar 21 23:59:59 2046 GMT | `7B:B6:47:A6:2A:EE:AC:88:BF:25:7A:A5:22:D0:1F:FE:A3:95:E0:AB:45:C7:3F:93:F6:56:54:EC:38:F2:5A:06` |
| C=US, ST=New Jersey, L=Jersey City, O=The USERTRUST Network, CN=USERTrust ECC Certification Authority | Jan 18 23:59:59 2038 GMT | `4F:F4:60:D5:4B:9C:86:DA:BF:BC:FC:57:12:E0:40:0D:2B:ED:3F:BC:4D:4F:BD:AA:86:E0:6A:DC:D2:A9:AD:7A` |
| C=US, ST=New Jersey, L=Jersey City, O=The USERTRUST Network, CN=USERTrust RSA Certification Authority | Jan 18 23:59:59 2038 GMT | `E7:93:C9:B0:2F:D8:AA:13:E2:1C:31:22:8A:CC:B0:81:19:64:3B:74:9C:89:89:64:B1:74:6D:46:C3:D4:CB:D2` |
| C=US, O=Internet Security Research Group, CN=ISRG Root X1 | Jun  4 11:04:38 2035 GMT | `96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6` |
| C=US, O=DigiCert Inc, OU=www.digicert.com, CN=DigiCert Global Root G2 | Jan 15 12:00:00 2038 GMT | `CB:3C:CB:B7:60:31:E5:E0:13:8F:8D:D3:9A:23:F9:DE:47:FF:C3:5E:43:C1:14:4C:EA:27:D4:6A:5A:B1:CB:5F` |
| C=US, O=DigiCert Inc, OU=www.digicert.com, CN=DigiCert Global Root CA | Nov 10 00:00:00 2031 GMT | `43:48:A0:E9:44:4C:78:CB:26:5E:05:8D:5E:89:44:B4:D8:4F:96:62:BD:26:DB:25:7F:89:34:A4:43:C7:01:61` |
| OU=GlobalSign Root CA - R6, O=GlobalSign, CN=GlobalSign | Dec 10 00:00:00 2034 GMT | `2C:AB:EA:FE:37:D0:6C:A2:2A:BA:73:91:C0:03:3D:25:98:29:52:C4:53:64:73:49:76:3A:3A:B5:AD:6C:CF:69` |
| OU=GlobalSign Root CA - R3, O=GlobalSign, CN=GlobalSign | Mar 18 10:00:00 2029 GMT | `CB:B5:22:D7:B7:F1:27:AD:6A:01:13:86:5B:DF:1C:D4:10:2E:7D:07:59:AF:63:5A:7C:F4:72:0D:C9:63:C5:3B` |

## Entry dispatch and damaged-payload recovery

- The installed dispatcher validates exact argument counts, fixed core verb pairs, module ID syntax and watch events before
  selecting an OTA payload. If `current` is a symlink to a readable dispatcher it execs that dispatcher with the same argv;
  canonical directories are compared to prevent self/alias loops; both copies validate. Broken/missing selection falls back to the
  installed package. Service and preuninstall/launch entry points use the same canonical guard; USB postinstall never delegates.
  Reconcile keeps its pre-delegation interrupted-update rollback. The authenticated updater stays the fixed NEMESIS-tree
  implementation and trust anchor; selected dispatchers route update verbs to it, never to downloaded updater copies.
  `bin/install-apk.sh` installs its own payload (update/rollback callers choose the APK) and must not redirect to current.
  An already-installed old wrapper needs one NEMESIS package refresh to gain delegation.
- After a successful APK install (or Android down), USB postinstall retires `current`/`current-path`, previous rollback pointers
  and the update journal, keeping version directories, baselines, ledger; failed APK install preserves selection
  (see [boot-lifecycle.md](boot-lifecycle.md), "Deferred APK install").
- Dispatcher refusals emit state/restart/detail. Status adds saved `info.desired=on|off` (live fallback for legacy intent); plans
  revert exclusions if observed active OR saved on; successful pending-restart reverts need `info.desired=off` and permit the next
  step; the app keeps step outcomes and the strongest restart even on later failure and shows one final result. OTA resume stores its
  originating kernel boot ID: after a collected head-unit restart it needs a different boot, target readiness and a usable network;
  map-refresh completion retries the continuation so an already-connected launch doesn't lose its only connectivity event.
- Damaged selected payload: the installed dispatcher routes `update stage|apply|rollback|status` and package
  `stock restore|status|uninstall` through the stable installation before OTA selection; device reboot and ledger export stay
  there too. Stock transactions may use the selected runtime only after checking required nonempty runtime files, generated
  module manifests and `sh -n`; otherwise the installed guarded restore is used. The NEMESIS uninstall hook always enters the installed
  stock controller. Ordinary dispatch delegates only after the same completeness/syntax check; an invalid payload falls back to the
  installed runtime for module `status`/`precheck` and `map status`; other module/map mutations return a structured failed/none
  receipt directing the owner to rollback or USB reinstall without running either payload's mutation. A failed delegated
  mutation is never replayed. These checks detect missing/empty files and parse damage; they don't replace signed OTA
  verification or prove runtime behavior.
- Projection rollback guard: OTA rollback (explicit, automatic, interrupted-update recovery) refuses to select another package/APK
  while projection restore material remains; disable projection or restore stock first. Failed update health with active projection
  keeps the selected package and reports blocked rollback. The fixed updater sets `HMG_SEOGAEMO_PROJECTION_ROLLBACK_GUARD=1`; with
  `HMG_SEOGAEMO_UPDATE_HEALTH=1` and no capability `sg_zygote_install` keeps the applied generation and returns `info.hook_upgrade=
  deferred until update is finalized`. The new APK checks `info.restore_material=absent` before dispatching rollback. USB replaces the
  updater and uses ordinary postinstall reconcile (no deferral). Direct root rollback through a pre-upgrade fixed updater can't use
  the guard: disable projection first or reinstall via USB.

## Unverified on the car

TLS/redirect/CA/SNI and wrong clock; `app_process` launch; `dumpsys package` version output; root atomic rename/durability
(no directory fsync via the API 19 implementation; power-loss durability unknown); module reconciliation; APK signer
continuity; whether Manager reloads the edited descriptor and a later NEMESIS uninstall uses the stable hook; Wi-Fi
transition with CarPlay active; signer mismatch, bad signature/hash, power loss at each journal phase, failed APK
install/reconcile, automatic rollback, later NEMESIS Store/USB update and uninstall, restored baselines. Keep an offline USB
recovery image. Open: retained rollback depth and free-space threshold.
