#!/system/bin/sh
# NEMESIS confirms type=execute before running this script by its absolute path on the stick.
# --update also accepts a fresh installation; never uninstall first.
exec /system/xbin/nemesis install --update "${0%/*}/packages/hmg-seogaemo.zip" # android44-shell: allow -- pinned NEMESIS main.c cmd_install supports --update for fresh and replacement packages
