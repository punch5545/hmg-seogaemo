#!/system/bin/sh
# Fixed relative runtime directories; CarPlay may publish runtime/<module>/ logs.
HMG_SEOGAEMO_DIAGNOSTIC_RUNTIME_DIRS=runtime

hmg_seogaemo_diagnostics() (
    diag=$1/diagnostics
    mkdir -p "$diag" || exit 1
    count=0
    : > "$diag/manifest.txt"
    redact() {
        while IFS= read -r line || [ -n "$line" ]; do
            case "$line" in
                *[Ss][Ss][Ii][Dd]*|*[Pp][Ss][Kk]*|*[Pp][Aa][Ss][Ss]*|*[Ss][Ee][Cc][Rr][Ee][Tt]*|*[Tt][Oo][Kk][Ee][Nn]*|*[Cc][Rr][Ee][Dd]*) sg_print '[redacted sensitive line]';;
                *) sg_print "$line";;
            esac
        done
    }
    reserve() {
        [ "$count" -lt 32 ] || { sg_print "omitted budget: $1" >> "$diag/manifest.txt"; return 1; }
        count=$((count + 1))
    }
    copy_log() {
        [ -f "$1" ] && sg_path_safe "$1" || return 0
        reserve "$2" || return 0
        mkdir -p "$diag/${2%/*}" || return 1
        bytes=$(sg_file_size "$1") || return 1
        skip=0
        if [ "$bytes" -gt 65536 ]; then
            skip=$((bytes - 65536))
            sg_print "truncated to last 65536 bytes: $2" >> "$diag/manifest.txt"
        fi
        dd if="$1" bs=1 skip="$skip" count=65536 2>/dev/null | {
            # A tail can begin inside a credential line: discard that fragment.
            if [ "$skip" -gt 0 ]; then IFS= read -r fragment || :; fi
            redact
        } | dd bs=1 count=65536 2>/dev/null > "$diag/$2"
    }
    probe() {
        name=$1; shift
        # Bound duration and pipe output. Status alone is read-only.
        (
            /system/bin/sh "$base/bin/hmg-seogaemo-root" "$@" 2>&1 &
            pid=$!
            (sleep 10; kill "$pid" 2>/dev/null) >/dev/null 2>&1 &
            guard=$!
            wait "$pid"
            code=$?
            kill "$guard" 2>/dev/null || :
            wait "$guard" 2>/dev/null || :
            sg_print "probe exit=$code: $name" >> "$diag/manifest.txt"
        ) | dd bs=1 count=65536 2>/dev/null | redact | dd bs=1 count=65536 2>/dev/null > "$diag/$name"
    }
    for source in "$SG_DATA_ROOT"/reconcile.log* "$SG_DATA_ROOT"/watcher.log*; do
        copy_log "$source" "logs/${source##*/}" || exit 1
    done
    for area in stock uninstall update; do
        for leaf in result module-output journal log uninstall.log runner.log; do
            copy_log "$SG_DATA_ROOT/$area/$leaf" "$area/$leaf" || exit 1
        done
    done
    probe map-status.txt map status
    props='ro.build.product ro.build.version.release ro.build.version.sdk ro.product.device ro.product.model ro.product.board ro.build.fingerprint sys.boot_completed service.adb.tcp.port init.svc.adbd init.svc.hostapd'
    : > "$diag/owned-paths.txt"
    while IFS= read -r id; do
        case "$id" in ''|*[!a-z0-9-]*|-*|*--*|*-) continue;; esac
        probe "module-$id-status.txt" "$id" status
        copy_log "$SG_DATA_ROOT/modules/$id/module.log" "modules/$id/module.log" || exit 1
        paths=$base/modules/$id.d
        if [ -f "$base/modules/$id.properties" ]; then
            while IFS='=' read -r key value; do
                case "$key" in
                    owns_props) props="$props $value";;
                    owns_files) paths="$paths $value";;
                    owns_lines.*) paths="$paths ${value%%:*}";;
                esac
            done < "$base/modules/$id.properties"
        fi
        (
            IFS=' ,'
            for path in $paths; do
                case "$path" in /*) ;; *) continue;; esac
                sg_print "module=$id path=$path"
                ls -ldn "$path" 2>&1
                while [ "$path" != / ]; do
                    path=${path%/*}
                    [ -n "$path" ] || path=/
                    ls -ldn "$path" 2>&1
                done
            done
        ) | dd bs=1 count=8192 2>/dev/null >> "$diag/owned-paths.txt"
    done < "$base/modules.list"
    (
        IFS=' ,'
        for prop in $props; do
            case "$prop" in ''|*[!a-zA-Z0-9_.-]*) continue;; esac
            case "$prop" in
                *[Ss][Ss][Ii][Dd]*|*[Pp][Ss][Kk]*|*[Pp][Aa][Ss][Ss]*|*[Ss][Ee][Cc][Rr][Ee][Tt]*|*[Tt][Oo][Kk][Ee][Nn]*|*[Kk][Ee][Yy]*|*[Cc][Rr][Ee][Dd]*) sg_print "$prop=[redacted]";;
                *) sg_print "$prop=$(getprop "$prop")" | redact;;
            esac
        done
    ) | dd bs=1 count=65536 2>/dev/null > "$diag/properties.txt"
    # Fixed area, bounded depth, text logs/receipts only; never copy configs.
    for area in $HMG_SEOGAEMO_DIAGNOSTIC_RUNTIME_DIRS; do
        for source in "$SG_DATA_ROOT/$area"/* "$SG_DATA_ROOT/$area"/*/* "$SG_DATA_ROOT/$area"/*/*/* "$SG_DATA_ROOT/$area"/wireless-carplay-and-aa/apps/*/*; do
            case "${source##*/}" in
                *.log|*.log.1|*.txt|result|module-output) copy_log "$source" "${source#"$SG_DATA_ROOT/"}" || exit 1;;
            esac
        done
    done
    sg_print 'limits: 32 logs, 64 KiB each; status 64 KiB/10 seconds each; metadata 8 KiB/module; properties 64 KiB' >> "$diag/manifest.txt"
)
