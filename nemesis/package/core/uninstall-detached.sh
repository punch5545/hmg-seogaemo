#!/system/bin/sh
# Copied outside the package before launch; must survive package/APK removal.
umask 077
trap '' HUP
job=${0%/*}
. "$job/common.sh"
# The caller publishes our PID only after its receipt and lock release. A dead
# caller or failed publication must never trigger removal on a timer.
tries=0
code=1
while [ "$tries" -lt 30 ]; do
    if [ "$(cat "$job/ready" 2>/dev/null)" = "$$" ]; then
        nemesis uninstall hmg-seogaemo
        code=$?
        break
    fi
    tries=$((tries + 1))
    sleep 1
done
if [ "$code" -eq 0 ]; then value=removed; else value=failed; fi
sg_write "$value" > "$job/result.tmp.$$" && mv "$job/result.tmp.$$" "$job/result"
sg_write "$code" > "$job/exit-code"
HMG_SEOGAEMO_STATE=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}/stock
sg_ledger package_uninstall stock /dev/null "$job/result" '' "$value"
rm -f "$job/pending" "$job/ready"
exit "$code"
