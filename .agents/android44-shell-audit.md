Status: current — source audit against AOSP `android-4.4.2_r1`.

# Android 4.4.2 shell compatibility audit

The compatibility authority is `tools/android44-shell-profile.json`, our own
conservative data derived from AOSP tag `android-4.4.2_r1`. The linter never
executes shell input. This establishes compatibility of the reviewed source
forms with that AOSP baseline; it does not establish that the car vendor shipped
identical binaries or that its services return identical output.

## Evidence

Shallow tag clones into `/private/tmp/aosp-442-shell/` (`git clone --depth 1 --branch android-4.4.2_r1 <repository> <directory>`); nothing was built or executed. The JSON provenance holds the exact repository and immutable source URLs.

| Directory | Repository under `https://android.googlesource.com/` | Commit |
| --- | --- | --- |
| `core` | `platform/system/core` | `e65b7ea8801145626504c724c28aedd0e5038a28` |
| `mksh` | `platform/external/mksh` | `f8c396c4d446a038358106a301b329607a04633d` |
| `bionic` | `platform/bionic` | `5ab8d33aa64a4ca12172dba92df172f70629874d` |
| `native` | `platform/frameworks/native` | `14e8b01a761180250671f3d6494532d8bd04fa8e` |
| `base` | `platform/frameworks/base` | `feef9887e8f8eb6f64fc1b4552c02efb5755cdc1` |
| `wpa_supplicant_8` | `platform/external/wpa_supplicant_8` | `f62167e88c0c1b9621f2984f1a59ae7d41cf4c88` |

Primary evidence: `core/toolbox/Android.mk` (`TOOLS`, `ALL_TOOLS`; `cp` and `grep` appended explicitly) and each tool's option parser. External Android commands were checked in `core/netcfg`, `core/logcat`,
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
| `mv -f` | Unsupported; `mv.c` treats `-f` as a filename | No production `-f`; plain `mv` uses replacing rename |
| `md5` without a filename | Unsupported; no implicit stdin | Hash path bytes with `md5 /proc/self/fd/0`; validate successful 32-character lowercase digest and propagate failures before constructing backup paths |
| `md5 <file>` output | `<hex>  <path>` | Keep filename-based checks; the fake command matches the missing-argument failure |
| `cp`, `cp -Rf` | BSD cp exists, parser `HLNPRfailprv` | Retained; no BusyBox dependency |
| `grep -E/-F/-q/-x/-c/-e`, `-Fxq` | BSD grep supports these and combined short flags | Retained |
| `grep -P`, `-J`, decompression long options | `-P` falls through to usage; Android compiles out compressed reads | Excluded despite spellings appearing in the parser |
| `ls -ln`, `ls -ldn` | Supported; toolbox long output omits host link count | Existing metadata/size parsers already handle toolbox and host layouts |
| `cmp -s` | Unsupported; parser `bln:` | Production already uses plain `cmp` with output redirected; lint rejects `-s` |
| `date +%s`, `+%Y%m%d-%H%M%S` | Toolbox accepts format strings; bionic strftime implements `%s` | Retained reviewed formats |
| `readlink -f` | Supported; parser `nfqs`, canonicalization uses realpath | Allowed by the contract; no production use |
| `timeout`, `which`, `seq`, `xargs`, `basename`, `dirname` | Absent from toolbox and shell builtins | Rejected; none needed by production |
| `printf`, text utilities and archive tools prohibited by contract | Absent from baseline command set | Rejected; host-only helpers excluded from on-car inventory |
| `cat`, `sleep` | mksh builtins shadow toolbox; `HAVE_SELECT=1` enables sleep | Builtin cat only `-u`; project limits sleep to integer seconds. Absolute binaries have separate option specs |
| `print -r/-rn --`, `read -r/-t`, `test`/`[`/`[[` | Supported builtins | Retained and option-checked |
| Arithmetic, `local`/`typeset`, `$'...'`, parameter length/trim/substitution | Supported; `local` aliases `typeset` | Retained; executable names constructed with expansions require review |
| `echo` under invocation as sh | Initial `-n` recognized; otherwise literal POSIX behavior | Exact-byte runtime output uses `print -r --` / `print -rn --` |
| `set -o pipefail` | Absent from R43 flags despite availability in newer local mksh | Reject named options, including combined/attached and `+o` forms |
| Bash process substitution, regex `=~`, case conversion, associative arrays, `&>` | Not supported by R43 | Reject known unsupported forms; no production dependency |
| Binder timeout | Existing `sleep` + `kill -0` polling needs no external timeout | KILL is sent before wait so a TERM-ignoring process cannot hang the deadline |
| Payload host build scripts | Use host tools, so they must not ship on-car | Exclude three exact build helper paths from package and OTA; preserve payload source/license files |

## Enforcement and boundaries

`make test` runs lint regression cases (`tools/lint-android44-shell.py`), then the profile lint, then the separate module mutation lint. The profile lint checks all 14 source scripts (four module roots, dispatcher, install/open, mapping, update, library, install/uninstall hooks, two services; includes export/recovery logic) plus shell members of the built package and OTA ZIPs (generated hooks, accidentally shipped host scripts). Scripts are recognized by `.sh` suffix or shell shebang, including extensionless entry points. The USB wrapper embeds the inspected package.

The JSON `host_only_scripts` entries exclude host generator/upstream tools and three payload build helpers (need host tools; excluded from package and OTA, payload source/license files kept). Build/test dirs outside payload are host-only; payload subdirectories are still inspected. Package tests assert helpers are absent and all other payload bytes retained.

The lexer checks command positions, absolute binary paths, combined short flags, flag arity, reviewed subcommands, `dd` operand names, date formats and selected unsupported shell features. It recurses into command substitutions (including defaults), literal `sh -c` / `sh -ec` and trap strings. Quoted here-documents are data; expansions in unquoted bodies are checked. Unknown or dynamic executable positions fail closed; `eval` and opaque backticks fail.

Limits: a practical source gate for this repo's style, not a full parser or dataflow proof. Dynamic arguments, sourced paths, function-call arguments, variable values and command output are not resolved, so a dynamic argument could become an option at runtime: keep option spelling literal and validate variable operands in runtime code. The profile is a reviewed subset; accepting a flag does not prove all argument values, operand counts or combinations. Check unfamiliar syntax against the pinned sources before extending it.

## Explicit waivers

Only these four production lines carry `# android44-shell: allow -- <specific reason>`; it suppresses diagnostics on that physical line and the lint prints it (archive copies repeat it).

| Location | Reason |
| --- | --- |
| `nemesis/package/lib/common.sh`, `sg_service_call` | Dynamic service binary defaults to fixed `/system/bin/service`; environment override is the host-test seam |
| `modules/skip-consent-screen/root/skip-consent-screen.sh`, factory check | Same service binary seam, fixed `check` invocation |
| `modules/wireless-carplay-and-aa/module.sh`, mapping check | Same package dispatcher with fixed `map status` |

## Validation and remaining device checks

Regression tests cover unsupported commands/options, combined flags, nested substitutions, shell features, waivers, source inventory, MD5 error/malformed/empty output, exact-byte restore and a Binder child that ignores TERM. Runtime runs under `/bin/sh` and local mksh R59c (supplemental only; production is R43). The APK from local builds uses the throwaway debug key (v1 JAR at API 19), so `verify-dist` reports the debug-build condition without a signed OTA manifest.

Still requires on-car confirmation: vendor binary presence/version and PATH, `/proc/self/fd/0` readability in the root shell pipeline, service/dumpsys/netcfg/ifconfig/wpa_cli output formats and service names, mount/remount and ownership behavior, and a physical install/apply/revert with byte-exact original restore. Static AOSP evidence and host fakes do not establish these.
