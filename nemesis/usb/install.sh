#!/system/bin/sh
# NEMESIS confirms type=execute before running this script with the USB root cwd.
# --update also accepts a fresh installation; never uninstall first.
exec /system/xbin/nemesis install --update "$PWD/packages/hmg-seogaemo.zip" # android44-shell: allow -- pinned NEMESIS main.c cmd_install supports --update for fresh and replacement packages
