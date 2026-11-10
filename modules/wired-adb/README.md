# Wired ADB

Bundled module of HMG-Seogaemo. A Mac plugged into a data USB port of the car reaches ADB over
the cable (`adb connect 192.168.2.249:5556`), without Wi-Fi and without enabling stock ADB. Only
computers whose `adbkey.pub` the owner imported from a USB drive (`HSM/adbkey.pub`) can connect.

`files/cable-adbd` is built from the Android Open Source Project (Apache-2.0, see `files/NOTICE.txt`);
`files/cable-link` and the build recipe are in the HMG-Seogaemo repository
(https://github.com/seogaemo/hmg-seogaemo, `modules/wired-adb/payload`).
