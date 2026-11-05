Source: research-nemesis-packaging.md. Historical: NEMESIS 1.16.1/1.16.2 USB reinstall behavior (2026-11-04 car session), the earlier packaging proposal framing, and the validation note of the extraction-mode fix. Not current state.

- NEMESIS 1.16.1/1.16.2 `usb.c:do_install` ran a standalone uninstall then install for USB reinstalls while Android was up; the hook could not tell that from an intentional removal and no replacement was staged. 1.16.3 (`dc08358`) and 1.16.4 use `install --update`.
- Extraction-mode fix validation: `make test` passed with POSIX sh; mksh was not installed on the host. Android 4.4 profile lint also passed on source and packaged scripts.
