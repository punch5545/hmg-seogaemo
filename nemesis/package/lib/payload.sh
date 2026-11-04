# Read-only selection guard, sourced only from the installed recovery runtime.
hmg_seogaemo_payload_valid() (
    payload=$1
    for member in bin/hmg-seogaemo-root bin/install-apk.sh lib/common.sh lib/stock.sh \
            core/stock.sh core/uninstall-detached.sh core/hmg-seogaemo-map.sh \
            svc/reconcile.sh svc/watcher.sh; do
        [ -f "$payload/$member" ] && [ -r "$payload/$member" ] && [ -s "$payload/$member" ] || exit 1
        /system/bin/sh -n "$payload/$member" >/dev/null 2>&1 || exit 1
    done
    for member in modules.list modules.graph core/map/hmg-seogaemo-mapper.jar app/hmg-seogaemo.apk VERSION; do
        [ -f "$payload/$member" ] && [ -r "$payload/$member" ] && [ -s "$payload/$member" ] || exit 1
    done
    while IFS= read -r id; do
        case "$id" in ''|*[!a-z0-9-]*|-*|*--*|*-) exit 1;; esac
        for member in "modules/$id.sh" "modules/$id.properties"; do
            [ -f "$payload/$member" ] && [ -r "$payload/$member" ] && [ -s "$payload/$member" ] || exit 1
        done
        /system/bin/sh -n "$payload/modules/$id.sh" >/dev/null 2>&1 || exit 1
    done < "$payload/modules.list"
)
