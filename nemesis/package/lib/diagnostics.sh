#!/system/bin/sh
# Fixed relative runtime directories; CarPlay may publish runtime/<module>/ logs.
HMG_SEOGAEMO_DIAGNOSTIC_RUNTIME_DIRS=runtime

# Best effort (toolbox has no pkill): print up to three levels of descendants of
# $1 by scanning /proc. Used to stop a probe's grandchildren, not only its shell.
hmg_seogaemo_diag_descendants() {
    diag_frontier=$1; diag_found=; diag_level=0
    while [ "$diag_level" -lt 3 ] && [ -n "$diag_frontier" ]; do
        diag_next=
        for diag_stat in /proc/[0-9]*/stat; do
            diag_line=
            { read -r diag_line < "$diag_stat"; } 2>/dev/null || continue
            set -- ${diag_line#*) }
            for diag_parent in $diag_frontier; do
                [ "$2" = "$diag_parent" ] || continue
                diag_child=${diag_stat#/proc/}
                diag_next="$diag_next ${diag_child%/stat}"
            done
        done
        diag_found="$diag_found$diag_next"; diag_frontier=$diag_next; diag_level=$((diag_level + 1))
    done
    echo $diag_found
}

hmg_seogaemo_diagnostics() (
    diag=$1/diagnostics
    # Raw (unredacted) bytes are staged only in a private 0700 directory under
    # the state tree, never in the export folder that lives on removable media.
    # Only redacted, size-capped files are written into "$diag". A killed run
    # (SIGKILL cannot be trapped) leaves its staging directory here; the next
    # run sweeps exactly diagnostics-tmp.<digits> directories of dead processes.
    umask 077
    for stale in "$SG_DATA_ROOT"/diagnostics-tmp.*; do
        case "${stale##*/diagnostics-tmp.}" in ''|*[!0-9]*) continue;; esac
        [ -d "$stale" ] && [ ! -L "$stale" ] || continue
        ! kill -0 "${stale##*.}" 2>/dev/null || continue
        rm -rf "$stale"
    done
    stage=$SG_DATA_ROOT/diagnostics-tmp.$$
    rm -rf "$stage"
    mkdir -p "$SG_DATA_ROOT" && mkdir "$stage" && chmod 0700 "$stage" || exit 1
    trap 'diag_code=$?; rm -rf "$stage"; exit "$diag_code"' 0
    trap 'exit 1' 1 2 3 15
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
        # Block-sized reads from the regular file (a byte-at-a-time dd on a
        # head unit is far too slow); keep the last 16 blocks = 64 KiB.
        blocks=$(((bytes + 4095) / 4096))
        skip=0
        if [ "$blocks" -gt 16 ]; then
            skip=$((blocks - 16))
            sg_print "truncated to last 65536 bytes: $2" >> "$diag/manifest.txt"
        fi
        # Stage the block read in a file so the line loop reads a seekable
        # descriptor (buffered) instead of a pipe (one syscall per byte).
        dd if="$1" bs=4096 skip="$skip" count=16 2>/dev/null > "$stage/raw"
        {
            # A tail can begin inside a credential line: discard that fragment.
            if [ "$skip" -gt 0 ]; then IFS= read -r fragment || :; fi
            redact
        } < "$stage/raw" > "$stage/stage"
        rm -f "$stage/raw"
        # Redaction can lengthen short lines; cap from a file, never a pipe
        # (a short pipe read would make a block-counted dd stop early).
        dd if="$stage/stage" bs=4096 count=16 2>/dev/null > "$diag/$2"
        rm -f "$stage/stage"
    }
    probe() {
        name=$1; shift
        # Bound duration. Output goes to a file, never a pipe, so a lingering
        # grandchild cannot block this function; status alone is read-only.
        : > "$stage/probe"
        (
            /system/bin/sh "$base/bin/hmg-seogaemo-root" "$@" > "$stage/probe" 2>&1 < /dev/null &
            pid=$!
            (
                n=0
                while [ "$n" -lt 10 ]; do
                    sleep 1
                    kill -0 "$pid" 2>/dev/null || exit 0
                    n=$((n + 1))
                done
                kill $(hmg_seogaemo_diag_descendants "$pid") "$pid" 2>/dev/null
            ) > /dev/null 2>&1 < /dev/null &
            guard=$!
            wait "$pid"
            code=$?
            kill "$guard" 2>/dev/null || :
            wait "$guard" 2>/dev/null || :
            kill $(hmg_seogaemo_diag_descendants "$pid") 2>/dev/null || :
            sg_print "probe exit=$code: $name" >> "$diag/manifest.txt"
        )
        dd if="$stage/probe" bs=4096 count=16 2>/dev/null > "$stage/raw"
        redact < "$stage/raw" > "$stage/stage"
        rm -f "$stage/raw"
        dd if="$stage/stage" bs=4096 count=16 2>/dev/null > "$diag/$name"
        rm -f "$stage/probe" "$stage/stage"
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
    for id in $(sg_module_list "$base"); do
        case "$id" in ''|*[!a-z0-9-]*|-*|*--*|*-) continue;; esac
        probe "module-$id-status.txt" "$id" status
        copy_log "$SG_DATA_ROOT/modules/$id/module.log" "modules/$id/module.log" || exit 1
        paths=$(sg_module_dir "$base" "$id")
        manifest=$(sg_module_props "$base" "$id")
        if [ -f "$manifest" ]; then
            while IFS='=' read -r key value; do
                case "$key" in
                    owns_props) props="$props $value";;
                    owns_files) paths="$paths $value";;
                    owns_lines.*) paths="$paths ${value%%:*}";;
                esac
            done < "$manifest"
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
        ) > "$stage/stage"
        dd if="$stage/stage" bs=4096 count=2 2>/dev/null >> "$diag/owned-paths.txt"
        rm -f "$stage/stage"
    done
    (
        IFS=' ,'
        for prop in $props; do
            case "$prop" in ''|*[!a-zA-Z0-9_.-]*) continue;; esac
            case "$prop" in
                *[Ss][Ss][Ii][Dd]*|*[Pp][Ss][Kk]*|*[Pp][Aa][Ss][Ss]*|*[Ss][Ee][Cc][Rr][Ee][Tt]*|*[Tt][Oo][Kk][Ee][Nn]*|*[Kk][Ee][Yy]*|*[Cc][Rr][Ee][Dd]*) sg_print "$prop=[redacted]";;
                *) sg_print "$prop=$(getprop "$prop")" | redact;;
            esac
        done
    ) > "$stage/stage"
    dd if="$stage/stage" bs=4096 count=16 2>/dev/null > "$diag/properties.txt"
    rm -f "$stage/stage"
    # Fixed area, bounded depth, text logs/receipts only; never copy configs.
    for area in $HMG_SEOGAEMO_DIAGNOSTIC_RUNTIME_DIRS; do
        for source in "$SG_DATA_ROOT/$area"/* "$SG_DATA_ROOT/$area"/*/* "$SG_DATA_ROOT/$area"/*/*/* "$SG_DATA_ROOT/$area"/wireless-carplay-and-aa/apps/*/*; do
            case "${source##*/}" in
                *.log|*.log.1|*.txt|result|module-output) copy_log "$source" "${source#"$SG_DATA_ROOT/"}" || exit 1;;
            esac
        done
    done
    # CarPlay turn-by-turn records (DiPO's generated maneuver/lane summaries and
    # journals, no credentials): fixed names, copied whole outside the log budget,
    # at most 1 MiB + 64 KiB per file (12 files, about 13 MiB).
    tbt=${HMG_SEOGAEMO_DIPO_RUNTIME:-/data/data/com.daudio.app.dipo/app_hmg-seogaemo-wireless-runtime}
    for name in tbt-icons.txt tbt-lanes.txt tbt-icons.journal tbt-icons.journal.1 tbt-icons.journal.2 \
        tbt-icons.journal.3 tbt-icons.journal.4 tbt-icons.journal.5 tbt-lanes.journal tbt-lanes.journal.1 \
        tbt-lanes.journal.2 tbt-lanes.journal.3; do
        [ -f "$tbt/$name" ] && sg_path_safe "$tbt/$name" || continue
        mkdir -p "$diag/tbt" || exit 1
        dd if="$tbt/$name" bs=4096 count=272 2>/dev/null > "$diag/tbt/$name"
    done
    sg_print 'limits: 32 logs, 64 KiB each; status 64 KiB/10 seconds each; metadata 8 KiB/module; properties 64 KiB; turn-by-turn records 1 MiB + 64 KiB each' >> "$diag/manifest.txt"
)
