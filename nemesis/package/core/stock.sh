#!/system/bin/sh
# Package lifecycle transaction; never invoked with caller-supplied paths.
umask 077
[ "$#" -eq 1 ] || exit 2
case "$1" in restore|status|uninstall) ;; *) exit 2;; esac
base=${0%/*}
base=${base%/core}
SG_DATA_ROOT=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
[ ! -L "$SG_DATA_ROOT/update/current" ] || base=$SG_DATA_ROOT/update/current
HMG_SEOGAEMO_BASE=$base
export SG_DATA_ROOT HMG_SEOGAEMO_BASE
. "$base/lib/common.sh"
. "$base/lib/stock.sh"
stock=$SG_DATA_ROOT/stock
if [ "$1" = status ]; then hmg_seogaemo_stock_status; exit $?; fi
mkdir -p "$stock" "$SG_DATA_ROOT/desired" || exit 1
chmod 700 "$SG_DATA_ROOT" "$stock" "$SG_DATA_ROOT/desired" || exit 1
lock=$SG_DATA_ROOT/operation.lock
tries=0
while ! mkdir "$lock" 2>/dev/null; do
    holder=$(cat "$lock/pid" 2>/dev/null)
    case "$holder" in ''|*[!0-9]*) ;; *)
        if ! kill -0 "$holder" 2>/dev/null; then rm -rf "$lock"; continue; fi ;;
    esac
    tries=$((tries + 1))
    [ "$tries" -lt 10 ] || { sg_status failed none 'Module operation busy'; exit 1; }
    sleep 1
done
sg_write "$$" > "$lock/pid" || exit 1
stock_queue_ready=0
hmg_seogaemo_stock_exit() {
    stock_exit_code=$?
    rm -rf "$lock"
    # Publish only after stdout is complete and the hook can acquire the lock.
    if [ "$stock_exit_code" -eq 0 ] && [ "$stock_queue_ready" -eq 1 ]; then
        sg_write "$stock_pid" > "$stock_uninstall/ready"
    fi
}
trap 'hmg_seogaemo_stock_exit' EXIT
trap 'exit 1' HUP INT TERM
if [ "$1" = uninstall ] && hmg_seogaemo_stock_uninstall_pending; then
    sg_status failed none 'Uninstall already queued'
    exit 1
fi
hmg_seogaemo_stock_restore
code=$?
if [ "$code" -eq 0 ] && [ "$1" = uninstall ]; then
    hmg_seogaemo_stock_uninstall
    code=$?
    if [ "$code" -ne 0 ]; then
        stock_state=failed; stock_detail='Uninstall could not be queued; HMG-Seogaemo remains installed'
        hmg_seogaemo_stock_receipt
    fi
fi
hmg_seogaemo_stock_status
[ "$code" -ne 0 ] || [ "$1" != uninstall ] || stock_queue_ready=1
exit "$code"
