#!/system/bin/sh
umask 077
base=${0%/*}
base=${base%/svc}
journal=/data/nemesis/backup/seogaemo/update/journal
if [ "${SEOGAEMO_UPDATE_HEALTH:-}" != 1 ] && [ -f "$journal" ]; then
    IFS= read -r phase < "$journal" || :
    case "$phase" in phase=applying|phase=auto-rollback|phase=rollback)
        sh "$base/core/update.sh" rollback || exit 1 ;;
    esac
fi
current=/data/nemesis/backup/seogaemo/update/current
if [ "$base" != "$current" ] && [ -L "$current" ]; then
    exec sh "$current/svc/reconcile.sh"
fi
echo 'Seogaemo: module reconciliation is not implemented yet'
