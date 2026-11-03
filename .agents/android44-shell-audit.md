Status: current — source audit of local main on 2026-11-03.

# Android 4.4.2 shell compatibility audit

The compatibility authority is `tools/android44-shell-profile.json`, our own
conservative data derived from AOSP tag `android-4.4.2_r1`. The linter never
executes shell input. This establishes compatibility of the reviewed source
forms with that AOSP baseline; it does not establish that the car vendor shipped
identical binaries or that its services return identical output.

## Evidence

Read-only shallow tag clones were fetched into the new audit directory
`/private/tmp/aosp-442-shell/`. None of the fetched sources were built or executed.
For each repository below, the fetch command was
`git clone --depth 1 --branch android-4.4.2_r1 <repository> <directory>`.
The JSON provenance contains the exact repository URLs and immutable source URLs.

| Directory | Repository under `https://android.googlesource.com/` | Commit |
| --- | --- | --- |
| `core` | `platform/system/core` | `e65b7ea8801145626504c724c28aedd0e5038a28` |
| `mksh` | `platform/external/mksh` | `f8c396c4d446a038358106a301b329607a04633d` |
| `bionic` | `platform/bionic` | `5ab8d33aa64a4ca12172dba92df172f70629874d` |
| `native` | `platform/frameworks/native` | `14e8b01a761180250671f3d6494532d8bd04fa8e` |
| `base` | `platform/frameworks/base` | `feef9887e8f8eb6f64fc1b4552c02efb5755cdc1` |
| `wpa_supplicant_8` | `platform/external/wpa_supplicant_8` | `f62167e88c0c1b9621f2984f1a59ae7d41cf4c88` |

Primary evidence is `core/toolbox/Android.mk` (`TOOLS`, `ALL_TOOLS`) and each
tool's option parser. `cp` and `grep` are explicitly appended to `ALL_TOOLS`.
External Android commands were checked in `core/netcfg`, `core/logcat`,
`native/cmds/{service,dumpsys}`, `base/cmds/{pm,am,settings,app_process}`, and
`wpa_supplicant_8/wpa_supplicant/wpa_cli.c`. NEMESIS `remount rw|ro` was checked
against `https://github.com/xwtk/hmg_nemesis` commit
`da77eef6654e1f321ef3b979704681a0abc398f0`, `Sources/native/`.

The pinned shell is mksh R43 2013/02/19 (`MKSH_BUILD_R=431`). Evidence for
builtins and features is `mksh/Android.mk`, `src/funcs.c`, `src/main.c`,
`src/sh_flags.h`, and the parser/expansion sources. Android enables
`MKSH_ASSUME_UTF8`, `MKSH_CONSERVATIVE_FDS`, `MKSH_NOPWNAM`, `MKSH_BUILDSH`,
`HAVE_SELECT=1`, and `HAVE_MKNOD=0`; it does not enable `MKSH_PRINTF_BUILTIN`.

## Findings

| Command or feature | Source result | Fix or retained use |
| --- | --- | --- |
| `mv -f` | Unsupported; `mv.c` treats `-f` as a filename | Removed every production `-f`; plain `mv` uses replacing rename |
| `md5` without a filename | Unsupported; no implicit stdin | Hash path bytes with `md5 /proc/self/fd/0`; validate successful 32-character lowercase digest and propagate failures before constructing backup paths |
| `md5 <file>` output | `<hex>  <path>` | Preserve filename-based checks; corrected fake command to match missing-argument failure |
| `cp`, `cp -Rf` | BSD cp exists, parser `HLNPRfailprv` | Retained; no BusyBox dependency |
| `grep -E/-F/-q/-x/-c/-e`, `-Fxq` | BSD grep supports these and combined short flags | Retained |
| `grep -P`, `-J`, decompression long options | `-P` falls through to usage; Android compiles out compressed reads | Excluded despite spellings appearing in the parser |
| `ls -ln`, `ls -ldn` | Supported; toolbox long output omits host link count | Existing metadata/size parsers already handle toolbox and host layouts |
| `cmp -s` | Unsupported; parser `bln:` | Production already uses plain `cmp` with output redirected; lint rejects `-s` |
| `date +%s`, `+%Y%m%d-%H%M%S` | Toolbox accepts format strings; bionic strftime implements `%s` | Retained reviewed formats |
| `readlink -f` | Supported; parser `nfqs`, canonicalization uses realpath | Corrected old contract prohibition; no production use |
| `timeout`, `which`, `seq`, `xargs`, `basename`, `dirname` | Absent from toolbox and shell builtins | Rejected; none needed by production |
| `printf`, text utilities and archive tools prohibited by contract | Absent from baseline command set | Rejected; host-only helpers excluded from on-car inventory |
| `cat`, `sleep` | mksh builtins shadow toolbox; `HAVE_SELECT=1` enables sleep | Builtin cat only `-u`; project limits sleep to integer seconds. Absolute binaries have separate option specs |
| `print -r/-rn --`, `read -r/-t`, `test`/`[`/`[[` | Supported builtins | Retained and option-checked |
| Arithmetic, `local`/`typeset`, `$'...'`, parameter length/trim/substitution | Supported; `local` aliases `typeset` | Retained; executable names constructed with expansions require review |
| `echo` under invocation as sh | Initial `-n` recognized; otherwise literal POSIX behavior | Exact-byte runtime output uses `print -r --` / `print -rn --` |
| `set -o pipefail` | Absent from R43 flags despite availability in newer local mksh | Reject named options, including combined/attached and `+o` forms |
| Bash process substitution, regex `=~`, case conversion, associative arrays, `&>` | Not supported by R43 | Reject known unsupported forms; no production dependency |
| Binder timeout | Existing `sleep` + `kill -0` polling needs no external timeout | Added KILL before wait so a TERM-ignoring process cannot hang the deadline |
| Payload host build scripts | Previously shipped with on-car payload despite using host tools | Exclude three exact build helper paths from package and OTA; preserve payload source/license files |

## Enforcement and boundaries

`make test` runs regression cases for `tools/lint-android44-shell.py`, then the
profile lint, then the separate module mutation lint. The profile lint checks all
14 current source scripts (four module roots, dispatcher, install/open, mapping,
update, library, install/uninstall hooks and two services), plus shell members
of the built package and OTA ZIPs. This includes export/recovery logic in the
dispatcher. Scripts are recognized by `.sh` suffix or shell shebang, including
extensionless entry points. Package/OTA inspection catches generated hooks and
accidentally shipped host scripts. The USB wrapper embeds the inspected package.

The JSON's exact `host_only_scripts` entries exclude host generator/upstream
tools and three payload build helpers. Build/test directories outside payload
are host-only; payload subdirectories are still inspected. Package tests assert
that host helpers are absent while all other payload bytes are retained.

The lexer checks command positions, absolute binary paths, combined short flags,
flag argument arity, reviewed subcommands, `dd` operand names, date formats, and
selected unsupported shell features. It recursively checks command substitutions
(including defaults), literal `sh -c` / `sh -ec` and trap strings. Quoted
here-documents are data; expansions in unquoted bodies are checked. Unknown or
dynamic executable positions fail closed. `eval` and opaque backticks fail.

This is a practical source gate for this repository's style, not a full shell
parser or dataflow proof. It recognizes project function names across the source
inventory; dynamic arguments, sourced paths, function call arguments, variable
values and command-specific output are not resolved. A dynamic argument could
become an option at runtime. Keep option spelling literal and validate variable
operands in runtime code. The profile is a reviewed subset: accepting a flag does
not prove all possible argument values, operand counts or semantic combinations.
Review unfamiliar shell syntax against the pinned sources before extending it.

## Explicit waivers

Only these four production lines carry
`# android44-shell: allow -- <specific reason>`. A waiver suppresses diagnostics
on that physical line and is printed by the lint; archive copies repeat it.

| Location | Reason |
| --- | --- |
| `nemesis/package/lib/common.sh`, `sg_service_call` | Dynamic service binary defaults to fixed `/system/bin/service`; environment override is the host-test seam |
| `modules/skip-consent-screen/root/skip-consent-screen.sh`, factory check | Same service binary seam, fixed `check` invocation |
| `modules/wifi-client/root/wifi-client.sh`, projection check | Dynamic package root resolves to the fixed `bin/hmg-seogaemo-root` dispatcher with `wireless-carplay-and-aa status` |
| `modules/wireless-carplay-and-aa/root/wireless-carplay-and-aa.sh`, mapping check | Same package dispatcher with fixed `map status` |

## Validation and remaining device checks

Regression tests cover unsupported commands/options, combined flags, nested
substitutions, shell features, waivers and source inventory. Runtime regression
tests cover MD5 error/malformed/empty output, exact-byte restore and a Binder
child that ignores TERM. Both `/bin/sh` and local mksh R59c exercise the runtime;
R59c is supplemental evidence only, because the production baseline is R43.
Completed successfully on 2026-11-03:

- With `tmp/mksh-test-bin` prepended to PATH: `make clean`, `make test`, then
  `make package`. The runner confirmed `/bin/sh` and mksh R59c runs of mapping,
  package, runtime and all four module suites.
- `make verify-dist`: distribution asset list, hashes and version passed.
- Final `python3 tools/test-android44-shell.py` and
  `python3 tools/lint-android44-shell.py`: passed, 14 source scripts and 26
  package/OTA members; exactly four source waivers, repeated in the archives.
- `git diff --check`: passed.

The APK uses a throwaway debug key, verified with v1 JAR signing at API 19.
Release signing keys were not supplied, so no signed OTA manifest was generated;
distribution verification explicitly reports this debug-build condition.

Still requires on-car confirmation: actual vendor binary presence/version and
PATH, `/proc/self/fd/0` readability in the root shell pipeline, service/dumpsys/
netcfg/ifconfig/wpa_cli output formats and service names, mount/remount and
ownership behavior, and a physical install/apply/revert with byte-exact original
restoration. Static AOSP evidence and host fakes do not establish those outcomes.
