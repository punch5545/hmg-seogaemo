#!/bin/sh
set -eu
. "$SEOGAEMO_BASE/lib/common.sh"

if [ "$#" -ne 1 ]; then
    echo 'usage: wifi-client.sh apply|revert|status|precheck' >&2
    exit 2
fi
case "$1" in
    apply|revert|status|precheck)
        sg_status not-implemented none 'not implemented'
        ;;
    *)
        echo 'usage: wifi-client.sh apply|revert|status|precheck' >&2
        exit 2
        ;;
esac
