#!/system/bin/sh
# Root-only helpers; hmg-seogaemo-root holds the operation lock.
# Never fall back after a failed write: a partial write must stay a failure.
sg_write() {
    if command -v print >/dev/null 2>&1; then
        print -rn -- "$1"
    else
        (
            LC_ALL=C
            export LC_ALL
            dd bs=1 count="${#1}" 2>/dev/null <<EOF
$1
EOF
        )
    fi
}
sg_print() {
    for sg_print_line do
        if command -v print >/dev/null 2>&1; then
            print -r -- "$sg_print_line" || return 1
        else
            sg_write "$sg_print_line" && echo || return 1
        fi
    done
}
SG_DATA_ROOT=${SG_DATA_ROOT:-/data/nemesis/backup/hmg-seogaemo}
SG_PROPERTY_ROOT=${SG_PROPERTY_ROOT:-/data/property}
sg_ready() { mkdir -p "$HMG_SEOGAEMO_STATE" && chmod 700 "$HMG_SEOGAEMO_STATE"; }
sg_copy() { cp "$1" "$2.tmp.$$" && mv -f "$2.tmp.$$" "$2"; }
sg_equal() {
    cmp "$1" "$2" >/dev/null 2>&1
}
sg_clean() {
    sg_chars=$1
    sg_output=
    while [ -n "$sg_chars" ] && [ "${#sg_output}" -lt 200 ]; do
        sg_char=${sg_chars%"${sg_chars#?}"}
        sg_chars=${sg_chars#?}
        case "$sg_char" in
            [a-zA-Z0-9]|' '|'('|')'|[.,:_/+=-]) sg_output=$sg_output$sg_char ;;
            *) sg_output=$sg_output? ;;
        esac
    done
    sg_write "$sg_output"
}
sg_bind_build() {
    sg_ready || return 1
    sg_build=$(getprop ro.build.product) || return 1
    if [ -f "$HMG_SEOGAEMO_STATE/build" ]; then
        [ "$(cat "$HMG_SEOGAEMO_STATE/build")" = "$sg_build" ]
    else
        sg_write "$sg_build" > "$HMG_SEOGAEMO_STATE/build.tmp.$$" &&
            mv -f "$HMG_SEOGAEMO_STATE/build.tmp.$$" "$HMG_SEOGAEMO_STATE/build"
    fi
}
sg_check_build() {
    [ -f "$HMG_SEOGAEMO_STATE/build" ] &&
        [ "$(cat "$HMG_SEOGAEMO_STATE/build")" = "$(getprop ro.build.product)" ] ||
        { sg_log 'conflict: firmware build changed'; return 3; }
}
sg_log() {
    sg_ready || return 1
    sg_print "$(date +%s) $(sg_clean "$*")" >> "$HMG_SEOGAEMO_STATE/module.log" || return 1
    sg_bytes=$(sg_file_size "$HMG_SEOGAEMO_STATE/module.log") || return 1
    if [ "$sg_bytes" -gt 32768 ]; then
        dd if="$HMG_SEOGAEMO_STATE/module.log" of="$HMG_SEOGAEMO_STATE/module.log.tmp.$$" bs=1 skip="$((sg_bytes - 24576))" 2>/dev/null || return 1
        mv -f "$HMG_SEOGAEMO_STATE/module.log.tmp.$$" "$HMG_SEOGAEMO_STATE/module.log"
    fi
}
sg_file_size() {
    set -- $(ls -ln "$1") || return 1
    # Android toolbox omits the link count; desktop ls includes it.
    case "$5" in *[!0-9]*|'') sg_print "$4";; *) sg_print "$5";; esac
}
sg_prop_name() { case "$1" in ''|*[!a-zA-Z0-9_.-]*) return 2;; esac; }
sg_prop_baseline() {
    sg_prop_name "$1" && sg_bind_build || return 1
    sg_dir=$HMG_SEOGAEMO_STATE/prop.$1
    [ ! -f "$sg_dir/ready" ] || return 0
    mkdir -p "$sg_dir" || return 1
    sg_live=$(getprop "$1") || return 1
    sg_write "$sg_live" > "$sg_dir/live.tmp.$$" && mv -f "$sg_dir/live.tmp.$$" "$sg_dir/live" || return 1
    if [ -e "$SG_PROPERTY_ROOT/$1" ]; then
        sg_copy "$SG_PROPERTY_ROOT/$1" "$sg_dir/backing" || return 1
        sg_exists=present
    else sg_exists=absent; fi
    sg_write "$sg_exists" > "$sg_dir/existence.tmp.$$" &&
        mv -f "$sg_dir/existence.tmp.$$" "$sg_dir/existence" &&
        sg_write "1" > "$sg_dir/ready.tmp.$$" && mv -f "$sg_dir/ready.tmp.$$" "$sg_dir/ready"
}
sg_prop_write() {
    sg_prop_name "$1" || return 2
    setprop "$1" "$2" && [ "$(getprop "$1")" = "$2" ] || return 1
    sg_dir=$HMG_SEOGAEMO_STATE/prop.$1
    if [ -f "$sg_dir/ready" ]; then
        sg_write "$2" > "$sg_dir/applied.tmp.$$" &&
            mv -f "$sg_dir/applied.tmp.$$" "$sg_dir/applied" || return 1
    fi
}
sg_prop_restore() {
    sg_prop_name "$1" || return 2
    sg_check_build || return 3
    sg_dir=$HMG_SEOGAEMO_STATE/prop.$1
    [ -f "$sg_dir/ready" ] || return 1
    sg_live=$(cat "$sg_dir/live") || return 1
    sg_current=$(getprop "$1") || return 1
    if [ "$sg_current" != "$sg_live" ]; then
        [ -f "$sg_dir/applied" ] &&
            [ "$sg_current" = "$(cat "$sg_dir/applied")" ] ||
            { sg_log "conflict: property $1 changed"; return 3; }
    fi
    sg_prop_write "$1" "$sg_live" || return 1
    case "$(cat "$sg_dir/existence")" in
        present) sg_copy "$sg_dir/backing" "$SG_PROPERTY_ROOT/$1" &&
            sg_equal "$sg_dir/backing" "$SG_PROPERTY_ROOT/$1" || return 1 ;;
        absent) rm -f "$SG_PROPERTY_ROOT/$1" && [ ! -e "$SG_PROPERTY_ROOT/$1" ] || return 1 ;;
        *) return 1 ;;
    esac
    [ "$(getprop "$1")" = "$sg_live" ]
}
sg_file_key() {
    set -- $(sg_write "$1" | md5)
    sg_write "$1"
}
sg_file_meta() {
    # Android 4.4 toolbox has ls -n but no stat. Parse only the fixed mode,
    # owner and group fields; module-owned paths never contain whitespace.
    set -- $(ls -ldn "$1") || return 1
    sg_chars=${1#?}
    case "$5" in
        *[!0-9]*|'') sg_uid=$2; sg_gid=$3 ;;
        *) sg_uid=$3; sg_gid=$4 ;;
    esac
    sg_user=0; sg_group=0; sg_other=0; sg_special=0
    for sg_pos in 1 2 3 4 5 6 7 8 9; do
        sg_char=${sg_chars%"${sg_chars#?}"}
        sg_chars=${sg_chars#?}
        case "$sg_pos:$sg_char" in
            1:r) sg_user=$((sg_user + 4));; 2:w) sg_user=$((sg_user + 2));;
            3:x) sg_user=$((sg_user + 1));; 3:s) sg_user=$((sg_user + 1)); sg_special=$((sg_special + 4));;
            3:S) sg_special=$((sg_special + 4));;
            4:r) sg_group=$((sg_group + 4));; 5:w) sg_group=$((sg_group + 2));;
            6:x) sg_group=$((sg_group + 1));; 6:s) sg_group=$((sg_group + 1)); sg_special=$((sg_special + 2));;
            6:S) sg_special=$((sg_special + 2));;
            7:r) sg_other=$((sg_other + 4));; 8:w) sg_other=$((sg_other + 2));;
            9:x) sg_other=$((sg_other + 1));; 9:t) sg_other=$((sg_other + 1)); sg_special=$((sg_special + 1));;
            9:T) sg_special=$((sg_special + 1));;
            *:-) ;; *) return 1;;
        esac
    done
    sg_print "$sg_special$sg_user$sg_group$sg_other $sg_uid $sg_gid"
}
sg_file_baseline() {
    case "$1" in /*) ;; *) return 2;; esac
    sg_bind_build || return 1
    sg_dir=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
    [ ! -f "$sg_dir/ready" ] || { [ "$(cat "$sg_dir/path")" = "$1" ]; return; }
    mkdir -p "$sg_dir" || return 1
    sg_write "$1" > "$sg_dir/path.tmp.$$" && mv -f "$sg_dir/path.tmp.$$" "$sg_dir/path" || return 1
    if [ -e "$1" ]; then
        [ -f "$1" ] || return 1
        sg_copy "$1" "$sg_dir/bytes" || return 1
        sg_file_meta "$1" > "$sg_dir/meta.tmp.$$" &&
            mv -f "$sg_dir/meta.tmp.$$" "$sg_dir/meta" || return 1
        sg_exists=present
    else sg_exists=absent; fi
    sg_write "$sg_exists" > "$sg_dir/existence.tmp.$$" &&
        mv -f "$sg_dir/existence.tmp.$$" "$sg_dir/existence" &&
        sg_write "1" > "$sg_dir/ready.tmp.$$" && mv -f "$sg_dir/ready.tmp.$$" "$sg_dir/ready"
}
sg_file_mark_applied() {
    case "$1" in /*) ;; *) return 2;; esac
    sg_check_build || return 3
    sg_dir=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
    [ -f "$sg_dir/ready" ] && [ "$(cat "$sg_dir/path")" = "$1" ] || return 1
    if [ -e "$1" ]; then
        [ -f "$1" ] || return 1
        sg_copy "$1" "$sg_dir/applied" || return 1
        sg_exists=present
    else rm -f "$sg_dir/applied"; sg_exists=absent; fi
    sg_write "$sg_exists" > "$sg_dir/applied-existence.tmp.$$" &&
        mv -f "$sg_dir/applied-existence.tmp.$$" "$sg_dir/applied-existence"
}
sg_file_restore() {
    case "$1" in /*) ;; *) return 2;; esac
    sg_check_build || return 3
    sg_dir=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
    [ -f "$sg_dir/ready" ] && [ "$(cat "$sg_dir/path")" = "$1" ] || return 1
    sg_original=$(cat "$sg_dir/existence") || return 1
    if [ "$sg_original" = absent ] && [ ! -e "$1" ]; then return 0; fi
    if [ "$sg_original" = present ] && [ -f "$1" ] &&
            sg_equal "$sg_dir/bytes" "$1"; then
        read sg_mode sg_uid sg_gid < "$sg_dir/meta" || return 1
        [ "$(sg_file_meta "$1")" = "$sg_mode $sg_uid $sg_gid" ] && return 0
    fi
    sg_applied=$(cat "$sg_dir/applied-existence" 2>/dev/null) || { sg_log "conflict: no applied marker for $1"; return 3; }
    case "$sg_applied" in
        absent) [ ! -e "$1" ] || { sg_log "conflict: file $1 changed"; return 3; } ;;
        present) [ -f "$1" ] && sg_equal "$sg_dir/applied" "$1" ||
            { sg_log "conflict: file $1 changed"; return 3; } ;;
        *) return 3 ;;
    esac
    case "$(cat "$sg_dir/existence")" in
        absent) rm -f "$1" && [ ! -e "$1" ] ;;
        present)
            read sg_mode sg_uid sg_gid < "$sg_dir/meta" || return 1
            sg_copy "$sg_dir/bytes" "$1" &&
                chmod "$sg_mode" "$1" && chown "$sg_uid:$sg_gid" "$1" &&
                sg_equal "$sg_dir/bytes" "$1" &&
                [ "$(sg_file_meta "$1")" = "$sg_mode $sg_uid $sg_gid" ] ;;
        *) return 1 ;;
    esac
}
sg_restore_preflight() {
    sg_line_preflight || return 3
    sg_check_build || return 3
    for sg_check in "$HMG_SEOGAEMO_STATE"/prop.*/ready; do
        [ -f "$sg_check" ] || continue
        sg_dir=${sg_check%/ready}
        sg_key=${sg_dir##*/prop.}
        sg_live=$(cat "$sg_dir/live") || return 1
        sg_current=$(getprop "$sg_key") || return 1
        [ "$sg_current" = "$sg_live" ] || {
            [ -f "$sg_dir/applied" ] &&
                [ "$sg_current" = "$(cat "$sg_dir/applied")" ] || return 3
        }
    done
    for sg_check in "$HMG_SEOGAEMO_STATE"/file.*/ready; do
        [ -f "$sg_check" ] || continue
        sg_dir=${sg_check%/ready}
        sg_path=$(cat "$sg_dir/path") || return 1
        sg_original=$(cat "$sg_dir/existence") || return 1
        if [ "$sg_original" = absent ] && [ ! -e "$sg_path" ]; then continue; fi
        if [ "$sg_original" = present ] && [ -f "$sg_path" ] &&
                sg_equal "$sg_dir/bytes" "$sg_path"; then continue; fi
        sg_applied=$(cat "$sg_dir/applied-existence" 2>/dev/null) || return 3
        case "$sg_applied" in
            absent) [ ! -e "$sg_path" ] || return 3 ;;
            present) [ -f "$sg_path" ] && sg_equal "$sg_dir/applied" "$sg_path" || return 3 ;;
            *) return 3 ;;
        esac
    done
}
sg_remount() { case "$1" in rw|ro) nemesis remount "$1";; *) return 2;; esac; }
sg_baseline_clear() {
    [ -n "$HMG_SEOGAEMO_STATE" ] || return 2
    rm -rf "$HMG_SEOGAEMO_STATE"/prop.* "$HMG_SEOGAEMO_STATE"/file.* "$HMG_SEOGAEMO_STATE"/bind.*
    rm -f "$HMG_SEOGAEMO_STATE/build" "$HMG_SEOGAEMO_STATE/changes"
}
# Package-only mutation API. Metadata comes from the packaged, generated
# module.properties; callers cannot nominate an arbitrary target.
sg_validate() {
    [ -n "$HMG_SEOGAEMO_BASE" ] && [ -n "$HMG_SEOGAEMO_STATE" ] || return 2
    sg_manifest=$HMG_SEOGAEMO_BASE/modules/${HMG_SEOGAEMO_STATE##*/}.properties
    [ -f "$sg_manifest" ] || return 2
    sg_found=0
    while IFS='=' read -r sg_field sg_values; do
        [ "$sg_field" = "$1" ] || continue
        case ",$sg_values," in *,"$2",*) sg_found=1;; esac
    done < "$sg_manifest"
    [ "$sg_found" -eq 1 ]
}
sg_payload() {
    case "$1" in ''|/*|*..*|*[!a-zA-Z0-9_./-]*) return 2;; esac
    sg_source=$HMG_SEOGAEMO_MODULE_DIR/$1
    [ -f "$sg_source" ] && [ ! -L "$sg_source" ]
}
sg_ledger() {
    sg_ledger_dir=$SG_DATA_ROOT/ledger
    mkdir -p "$sg_ledger_dir/before" "$sg_ledger_dir/after" || return 1
    [ -f "$sg_ledger_dir/ledger.tsv" ] ||
        sg_print "time	module	op	target	before-ref	after-ref	result" > "$sg_ledger_dir/ledger.tsv" || return 1
    sg_stamp=$(date +%s).$$.${SG_LEDGER_SEQ:-0}
    # Subshell transactions do not propagate their counters to the caller.
    # Preserve every referenced record even within the same second and PID.
    while [ -e "$sg_ledger_dir/before/$sg_stamp" ] || [ -e "$sg_ledger_dir/after/$sg_stamp" ]; do
        SG_LEDGER_SEQ=$((${SG_LEDGER_SEQ:-0} + 1))
        sg_stamp=$(date +%s).$$.$SG_LEDGER_SEQ
    done
    SG_LEDGER_SEQ=$((${SG_LEDGER_SEQ:-0} + 1))
    sg_before=before/$sg_stamp
    sg_after=after/$sg_stamp
    if [ -f "$3" ]; then sg_copy "$3" "$sg_ledger_dir/$sg_before" || return 1
    else sg_write "absent" > "$sg_ledger_dir/$sg_before" || return 1; fi
    if [ -f "$4" ]; then sg_copy "$4" "$sg_ledger_dir/$sg_after" || return 1
    else sg_write "absent" > "$sg_ledger_dir/$sg_after" || return 1; fi
    if [ -n "$5" ] && [ -f "$5" ]; then sg_copy "$5" "$sg_ledger_dir/$sg_before.meta" || return 1; fi
    sg_print "$(date +%s)	${HMG_SEOGAEMO_STATE##*/}	$1	$2	$sg_before	$sg_after	${6:-ok}" >> "$sg_ledger_dir/ledger.tsv"
}
sg_changed() { sg_print "$1	$2" >> "$HMG_SEOGAEMO_STATE/changes"; }
sg_prop_set() {
    sg_validate owns_props "$1" || return 2
    sg_prop_baseline "$1" || return 1
    sg_prop_write "$1" "$2" || return 1
    sg_ledger prop_set "$1" "$HMG_SEOGAEMO_STATE/prop.$1/live" "$HMG_SEOGAEMO_STATE/prop.$1/applied" "" || return 1
    sg_changed prop "$1"
}
sg_commit_file() {
    sg_target=$1; sg_input=$2; sg_mode=$3; sg_owner=$4
    sg_validate owns_files "$sg_target" || return 2
    sg_whole_exclusive "$sg_target" || return 3
    sg_file_baseline "$sg_target" || return 1
    sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$sg_target")
    if [ -z "$sg_mode" ] || [ -z "$sg_owner" ]; then
        if [ -f "$sg_saved/meta" ]; then
            read sg_old_mode sg_old_uid sg_old_gid < "$sg_saved/meta" || return 1
        else sg_old_mode=0644; sg_old_uid=0; sg_old_gid=0; fi
        [ -n "$sg_mode" ] || sg_mode=$sg_old_mode
        [ -n "$sg_owner" ] || sg_owner=$sg_old_uid:$sg_old_gid
    fi
    sg_write "$sg_mode" | grep -Eq '^0?[0-7]{3,4}$' || return 2
    sg_write "$sg_owner" | grep -Eq '^[a-zA-Z0-9_]+:[a-zA-Z0-9_]+$' || return 2
    case "$sg_target" in /system/*) sg_remount rw || return 1; sg_system=1;; *) sg_system=0;; esac
    sg_tmp=$sg_target.hmg-seogaemo.$$
    sg_rc=0
    mkdir -p "${sg_target%/*}" &&
        cp "$sg_input" "$sg_tmp" &&
        chmod "$sg_mode" "$sg_tmp" &&
        { chown "${sg_owner%:*}.${sg_owner#*:}" "$sg_tmp" 2>/dev/null ||
            chown "$sg_owner" "$sg_tmp"; } &&
        mv -f "$sg_tmp" "$sg_target" &&
        sg_equal "$sg_input" "$sg_target" || sg_rc=1
    sg_expected_mode=$sg_mode
    [ "${#sg_expected_mode}" -eq 3 ] && sg_expected_mode=0$sg_expected_mode
    if [ "$sg_rc" -eq 0 ]; then
        set -- $(sg_file_meta "$sg_target")
        [ "$1" = "$sg_expected_mode" ] || sg_rc=1
    fi
    rm -f "$sg_tmp"
    if [ "$sg_system" -eq 1 ]; then sg_remount ro || sg_rc=1; fi
    [ "$sg_rc" -eq 0 ] || return 1
    sg_file_mark_applied "$sg_target" || return 1
    sg_ledger file_put "$sg_target" "$sg_saved/bytes" "$sg_saved/applied" "$sg_saved/meta" || return 1
    sg_changed file "$sg_target"
}
sg_file_put() {
    sg_validate owns_files "$1" && sg_payload "$2" || return 2
    sg_commit_file "$1" "$sg_source" "${3:-}" "${4:-}"
}
# Shared text transactions. Subshells keep their scratch variables out of the
# existing whole-file runtime. The dispatcher holds the global operation lock.
sg_line_text() {
    [ "$#" -eq 1 ] || return 2
    [ "$(sg_write "$1" | LC_ALL=C grep -c '[[:cntrl:]]')" = 0 ] || return 2
    case "$1" in *'
'*) return 2;; esac
}
sg_line_claim() (
    sg_line_text "$2" || exit 2
    while IFS='=' read -r key value; do
        case "$key" in owns_lines.*)
            [ "$value" != "$1:$2" ] || exit 0 ;;
        esac
    done < "$HMG_SEOGAEMO_BASE/modules/${HMG_SEOGAEMO_STATE##*/}.properties"
    exit 2
)
sg_whole_exclusive() (
    for manifest in "$HMG_SEOGAEMO_BASE"/modules/*.properties; do
        [ "$manifest" != "$HMG_SEOGAEMO_BASE/modules/${HMG_SEOGAEMO_STATE##*/}.properties" ] || continue
        while IFS='=' read -r key value; do
            case "$key" in
                owns_files) case ",$value," in *,"$1",*) exit 3;; esac ;;
                owns_lines.*) [ "${value%%:*}" != "$1" ] || exit 3 ;;
            esac
        done < "$manifest"
    done
)
sg_line_authorize() (
    sg_line_claim "$1" "$2" || exit 2
    module=${HMG_SEOGAEMO_STATE##*/}
    for manifest in "$HMG_SEOGAEMO_BASE"/modules/*.properties; do
        other=${manifest##*/}; other=${other%.properties}
        while IFS='=' read -r key value; do
            case "$key" in
                owns_files) case ",$value," in *,"$1",*) exit 3;; esac ;;
                owns_lines.*)
                    [ "$other" != "$module" ] || continue
                    if [ "$value" = "$1:$2" ] &&
                        [ "$(cat "$SG_DATA_ROOT/desired/$other" 2>/dev/null)" = on ]; then exit 3; fi ;;
            esac
        done < "$manifest"
    done
    # Do not reinterpret an earlier runtime's whole-file baseline as stock.
    for saved in "$SG_DATA_ROOT"/modules/*/file."$(sg_file_key "$1")"/ready; do
        [ ! -f "$saved" ] || exit 3
    done
)
sg_line_guard() (
    shared=$SG_DATA_ROOT/shared-lines/$(sg_file_key "$1")
    [ -f "$shared/ready" ] || exit 1
    [ "$(cat "$shared/path")" = "$1" ] &&
        [ "$(cat "$shared/build")" = "$(getprop ro.build.product)" ] &&
        [ -f "$1" ] && [ ! -L "$1" ] &&
        [ "$(sg_file_meta "$1")" = "$(cat "$shared/meta")" ] || exit 3
    expected=$shared/bytes
    if [ -f "$shared/active" ]; then
        version=$(cat "$shared/active") || exit 1
        case "$version" in v[0-9]* ) ;; *) exit 3;; esac
        expected=$shared/versions/$version/bytes
    fi
    sg_equal "$expected" "$1" && exit 0
    if [ -f "$shared/pending" ]; then
        version=$(cat "$shared/pending") || exit 1
        sg_equal "$shared/versions/$version/bytes" "$1" && exit 0
    fi
    exit 3
)
sg_line_audit() (
    transaction=$1
    module=$(cat "$transaction/module") || exit 1
    HMG_SEOGAEMO_STATE=$SG_DATA_ROOT/modules/$module
    for event in "$transaction"/events/*; do
        [ -d "$event" ] || continue
        op=$(cat "$event/op") || exit 1
        sg_ledger "$op" "$(cat "$transaction/path")" "$event/old" "$event/new" "$transaction/hashes" || exit 1
    done
    sg_ledger line_compose "$(cat "$transaction/path")" "$transaction/before" "$transaction/bytes" "$transaction/hashes"
)
sg_line_recover() (
    target=$1
    shared=$SG_DATA_ROOT/shared-lines/$(sg_file_key "$target")
    sg_line_guard "$target" || exit $?
    [ -f "$shared/pending" ] || exit 0
    version=$(cat "$shared/pending") || exit 1
    transaction=$shared/versions/$version
    if sg_equal "$transaction/bytes" "$target"; then
        sg_line_audit "$transaction" || exit 1
        sg_copy "$shared/pending" "$shared/active" || exit 1
        sync || exit 1
    fi
    rm -f "$shared/pending"
)
sg_line_baseline() (
    target=$1
    shared=$SG_DATA_ROOT/shared-lines/$(sg_file_key "$target")
    [ ! -f "$shared/ready" ] || { sg_line_recover "$target"; exit $?; }
    [ -f "$target" ] && [ ! -L "$target" ] || exit 3
    stage=$shared.init.$$
    mkdir -p "$stage/versions" || exit 1
    chmod 700 "$SG_DATA_ROOT/shared-lines" "$stage" || exit 1
    sg_copy "$target" "$stage/bytes" &&
        sg_file_meta "$target" > "$stage/meta" &&
        sg_write "$target" > "$stage/path" &&
        getprop ro.build.product > "$stage/build" &&
        sg_write present > "$stage/existence" &&
        sg_write 1 > "$stage/ready" &&
        mv "$stage" "$shared" || exit 1
)
# Compose each output line from the original input. Patch output is never fed
# into another patch, even when its text equals a different stock line.
sg_line_compose() (
    shared=$1; next=$2
    for patch in "$next"/patches/*/*; do
        [ -d "$patch" ] || continue
        owner=${patch%/*}; owner=${owner##*/}
        grep -Fxq "$owner" "$HMG_SEOGAEMO_BASE/modules.list" || exit 3
        old=$(cat "$patch/old") || exit 1
        count=0
        while IFS= read -r line || [ -n "$line" ]; do
            [ "$line" != "$old" ] || count=$((count + 1))
        done < "$shared/bytes"
        [ "$count" -eq 1 ] || exit 3
    done
    : > "$next/bytes" || exit 1
    while :; do
        line=; newline=1
        IFS= read -r line || newline=0
        [ "$newline" -eq 1 ] || [ -n "$line" ] || break
        output=$line; matches=0
        while IFS= read -r module; do
            for patch in "$next/patches/$module"/*; do
                [ -d "$patch" ] || continue
                if [ "$(cat "$patch/old")" = "$line" ]; then
                    matches=$((matches + 1))
                    output=$(cat "$patch/new") || exit 1
                fi
            done
        done < "$HMG_SEOGAEMO_BASE/modules.list"
        [ "$matches" -le 1 ] || exit 3
        if [ "$newline" -eq 1 ]; then sg_print "$output"; else sg_write "$output"; fi >> "$next/bytes" || exit 1
        [ "$newline" -eq 1 ] || break
    done < "$shared/bytes"
)
sg_line_transaction() (
    target=$1; action=$2; old=${3:-}; new=${4:-}
    module=${HMG_SEOGAEMO_STATE##*/}
    sg_line_baseline "$target" || exit $?
    shared=$SG_DATA_ROOT/shared-lines/$(sg_file_key "$target")
    sg_line_guard "$target" || exit $?
    version=v$(date +%s).$$; serial=0
    while [ -e "$shared/versions/$version" ]; do serial=$((serial + 1)); version=v$(date +%s).$$.$serial; done
    next=$shared/versions/$version
    mkdir -p "$next/patches" "$next/events" || exit 1
    if [ -f "$shared/active" ]; then
        previous=$(cat "$shared/active") || exit 1
        cp -R "$shared/versions/$previous/patches/." "$next/patches/" || exit 1
    fi
    mkdir -p "$HMG_SEOGAEMO_STATE/line.$(sg_file_key "$target")" || exit 1
    marker=$HMG_SEOGAEMO_STATE/line.$(sg_file_key "$target")
    sg_write "$target" > "$marker/path.tmp.$$" &&
        mv -f "$marker/path.tmp.$$" "$marker/path" &&
        sg_write 1 > "$marker/ready.tmp.$$" &&
        mv -f "$marker/ready.tmp.$$" "$marker/ready" || exit 1
    sg_bind_build || exit 3
    if [ "$action" = apply ]; then
        # Refuse an already applied same-line patch, even if desired=off.
        for patch in "$next"/patches/*/*; do
            [ -d "$patch" ] || continue
            owner=${patch%/*}; owner=${owner##*/}
            if [ "$(cat "$patch/old")" = "$old" ] && [ "$owner" != "$module" ]; then exit 3; fi
        done
        number=
        while IFS='=' read -r key value; do
            case "$key" in owns_lines.*) [ "$value" != "$target:$old" ] || number=${key#owns_lines.};; esac
        done < "$HMG_SEOGAEMO_BASE/modules/$module.properties"
        case "$number" in ''|*[!0-9]*) exit 2;; esac
        patch=$next/patches/$module/$number
        mkdir -p "$patch" "$next/events/$number" || exit 1
        sg_write "$old" > "$patch/old" && sg_write "$new" > "$patch/new" &&
            cp "$patch/old" "$next/events/$number/old" &&
            cp "$patch/new" "$next/events/$number/new" &&
            sg_write line_patch > "$next/events/$number/op" || exit 1
    else
        for patch in "$next/patches/$module"/*; do
            [ -d "$patch" ] || continue
            number=${patch##*/}
            mkdir -p "$next/events/$number" &&
                cp "$patch/new" "$next/events/$number/old" &&
                cp "$patch/old" "$next/events/$number/new" &&
                sg_write line_revert > "$next/events/$number/op" || exit 1
        done
        rm -rf "$next/patches/$module" || exit 1
    fi
    sg_line_compose "$shared" "$next" || exit $?
    sg_copy "$target" "$next/before" && sg_write "$module" > "$next/module" &&
        sg_write "$target" > "$next/path" || exit 1
    { md5 "$next/before"; md5 "$next/bytes"; cat "$shared/build"; sg_print ''; cat "$shared/meta"; } > "$next/hashes" || exit 1
    sg_print "$version" > "$shared/pending.tmp.$$" &&
        mv -f "$shared/pending.tmp.$$" "$shared/pending" || exit 1
    sync || exit 1
    sg_line_guard "$target" || exit $?
    system=0
    case "$target" in /system/*) sg_remount rw || exit 1; system=1;; esac
    temporary=$target.hmg-seogaemo.$$
    read mode uid gid < "$shared/meta" || exit 1
    rc=0
    cp "$next/bytes" "$temporary" && chown "$uid:$gid" "$temporary" &&
        chmod "$mode" "$temporary" &&
        mv -f "$temporary" "$target" &&
        sg_equal "$next/bytes" "$target" &&
        [ "$(sg_file_meta "$target")" = "$(cat "$shared/meta")" ] || rc=1
    rm -f "$temporary"
    if [ "$system" -eq 1 ]; then sg_remount ro || rc=1; fi
    [ "$rc" -eq 0 ] || exit 1
    sync || exit 1
    sg_line_recover "$target" || exit $?
    if [ "$action" = revert ]; then
        rm -rf "$marker" || exit 1
        remaining=0
        for patch in "$next"/patches/*/*; do [ ! -d "$patch" ] || remaining=1; done
        if [ "$remaining" -eq 0 ]; then
            sg_equal "$shared/bytes" "$target" &&
                [ "$(sg_file_meta "$target")" = "$(cat "$shared/meta")" ] || exit 3
            # A cancelled sibling apply can leave a marker without a patch.
            # Stock is verified and no patches remain, so retire those too.
            for stale in "$SG_DATA_ROOT"/modules/*/line."$(sg_file_key "$target")"; do
                [ ! -d "$stale" ] || rm -rf "$stale" || exit 1
            done
            rm -rf "$shared"
        fi
    fi
)
sg_line_preflight() (
    for marker in "$HMG_SEOGAEMO_STATE"/line.*/ready; do
        [ -f "$marker" ] || continue
        target=$(cat "${marker%/ready}/path") || exit 1
        sg_line_guard "$target" || exit $?
    done
)
sg_line_revert_all() (
    for marker in "$HMG_SEOGAEMO_STATE"/line.*/ready; do
        [ -f "$marker" ] || continue
        target=$(cat "${marker%/ready}/path") || exit 1
        shared=$SG_DATA_ROOT/shared-lines/$(sg_file_key "$target")
        sg_line_transaction "$target" revert || exit $?
    done
)
sg_line_applied() (
    shared=$SG_DATA_ROOT/shared-lines/$(sg_file_key "$1")
    [ -f "$shared/active" ] && [ ! -f "$shared/pending" ] || exit 1
    sg_line_guard "$1" || exit $?
    version=$(cat "$shared/active") || exit 1
    for patch in "$shared/versions/$version/patches/${HMG_SEOGAEMO_STATE##*/}"/*; do
        [ ! -d "$patch" ] || exit 0
    done
    exit 1
)

sg_file_replace_line() {
    if sg_line_claim "$1" "$2"; then
        sg_line_text "$3" || return 2
        sg_line_authorize "$1" "$2" || return $?
        sg_line_transaction "$1" apply "$2" "$3"
        return $?
    fi
    sg_validate owns_files "$1" || return 2
    sg_whole_exclusive "$1" || return 3
    [ -f "$1" ] || return 1
    sg_replacement=$HMG_SEOGAEMO_STATE/replace.$$
    sg_ready || return 1
    sg_old=$2; sg_new=$3; sg_matches=0
    : > "$sg_replacement" || return 1
    while IFS= read -r sg_line || [ -n "$sg_line" ]; do
        if [ "$sg_line" = "$sg_old" ]; then
            sg_matches=$((sg_matches + 1))
            sg_line=$sg_new
        fi
        sg_print "$sg_line" >> "$sg_replacement" || return 1
    done < "$1"
    [ "$sg_matches" -eq 1 ] || { rm -f "$sg_replacement"; return 1; }
    sg_commit_file "$1" "$sg_replacement" "" ""
    sg_rc=$?
    rm -f "$sg_replacement"
    return "$sg_rc"
}
sg_file_delete() {
    sg_validate owns_files "$1" || return 2
    sg_whole_exclusive "$1" || return 3
    sg_file_baseline "$1" || return 1
    sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
    case "$1" in /system/*) sg_remount rw || return 1; sg_system=1;; *) sg_system=0;; esac
    rm -f "$1"; sg_rc=$?
    [ ! -e "$1" ] || sg_rc=1
    if [ "$sg_system" -eq 1 ]; then sg_remount ro || sg_rc=1; fi
    [ "$sg_rc" -eq 0 ] || return 1
    sg_file_mark_applied "$1" || return 1
    sg_ledger file_delete "$1" "$sg_saved/bytes" "$sg_saved/applied" "$sg_saved/meta" || return 1
    sg_changed file "$1"
}
sg_bind_mount() {
    sg_validate owns_files "$1" && sg_payload "$2" || return 2
    sg_whole_exclusive "$1" || return 3
    [ -f "$1" ] || return 1
    sg_file_baseline "$1" || return 1
    sg_bind=$HMG_SEOGAEMO_STATE/bind.$(sg_file_key "$1")
    if sg_mount_present "$1"; then
        sg_applied "$1" || return 3
        return 0
    fi
    mount -o bind "$sg_source" "$1" || return 1
    sg_equal "$sg_source" "$1" || return 1
    sg_file_mark_applied "$1" || return 1
    sg_print "$1	$2" > "$sg_bind.tmp.$$" &&
        mv -f "$sg_bind.tmp.$$" "$sg_bind" || return 1
    sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
    sg_ledger bind_mount "$1" "$sg_saved/bytes" "$sg_saved/applied" "$sg_saved/meta" || return 1
    sg_changed file "$1"
}
sg_mount_present() { grep -q " $1 " "${SG_MOUNTS_FILE:-/proc/mounts}" 2>/dev/null; }
sg_reapply_binds() {
    for sg_spec in "$HMG_SEOGAEMO_STATE"/bind.*; do
        [ -f "$sg_spec" ] || continue
        IFS='	' read -r sg_target sg_source_rel < "$sg_spec" || return 1
        sg_bind_mount "$sg_target" "$sg_source_rel" || return 1
    done
}
sg_service_restart() {
    sg_validate services "$1" || return 2
    sg_before_service=$HMG_SEOGAEMO_STATE/service.$1.before.$$
    sg_after_service=$HMG_SEOGAEMO_STATE/service.$1.after.$$
    sg_ready || return 1
    getprop "init.svc.$1" > "$sg_before_service" || return 1
    stop "$1" && start "$1" || return 1
    getprop "init.svc.$1" > "$sg_after_service" || return 1
    [ "$(cat "$sg_after_service")" = running ] || return 1
    sg_ledger service_restart "$1" "$sg_before_service" "$sg_after_service" "" || return 1
    rm -f "$sg_before_service" "$sg_after_service"
}
sg_service_call() {
    [ "$#" -eq 2 ] || return 2
    case "$1" in ''|*[!a-zA-Z0-9_.-]*|.*|*.|*..*|*-*) return 2;; esac
    case "$2" in ''|0*|*[!0-9]*) return 2;; esac
    [ "$2" -le 65535 ] || return 2
    sg_validate service_calls "$1:$2" || return 2
    sg_call_boot=$(cat "${SG_BOOT_ID_FILE:-/proc/sys/kernel/random/boot_id}" 2>/dev/null) || return 1
    case "$sg_call_boot" in ''|*[!a-zA-Z0-9-]*) return 1;; esac
    [ "${#sg_call_boot}" -le 64 ] || return 1
    sg_ready || return 1
    sg_call_output=$HMG_SEOGAEMO_STATE/service-call.$$.out
    sg_call_before=$HMG_SEOGAEMO_STATE/service-call.$$.before
    sg_call_after=$HMG_SEOGAEMO_STATE/service-call.$$.after
    sg_print "no call" > "$sg_call_before" || return 1
    sg_write "$sg_call_boot" > "$HMG_SEOGAEMO_STATE/last-call-boot.tmp.$$" &&
        mv -f "$HMG_SEOGAEMO_STATE/last-call-boot.tmp.$$" "$HMG_SEOGAEMO_STATE/last-call-boot" &&
        sg_write "pending" > "$HMG_SEOGAEMO_STATE/last-call-result.tmp.$$" &&
        mv -f "$HMG_SEOGAEMO_STATE/last-call-result.tmp.$$" "$HMG_SEOGAEMO_STATE/last-call-result" || return 1
    "${HMG_SEOGAEMO_SERVICE_BIN:-/system/bin/service}" call "$1" "$2" > "$sg_call_output" 2>&1 &
    sg_call_pid=$!
    sg_call_wait=0
    while kill -0 "$sg_call_pid" 2>/dev/null && [ "$sg_call_wait" -lt 15 ]; do
        sleep 1
        sg_call_wait=$((sg_call_wait + 1))
    done
    if kill -0 "$sg_call_pid" 2>/dev/null; then
        kill "$sg_call_pid" 2>/dev/null || :
        wait "$sg_call_pid" 2>/dev/null || :
        sg_call_result=timeout
        sg_call_rc=1
    else
        wait "$sg_call_pid"; sg_call_rc=$?
        if [ "$sg_call_rc" -eq 0 ]; then sg_call_result=ok; else sg_call_result=failed; fi
    fi
    SG_SERVICE_CALL_RESPONSE=$(dd if="$sg_call_output" bs=1 count=512 2>/dev/null) || SG_SERVICE_CALL_RESPONSE=
    case "$SG_SERVICE_CALL_RESPONSE" in
        *'Cannot broadcast before boot completed'*|*'fffffffb 00000026 00610043'*)
            sg_call_result=boot-incomplete; sg_call_rc=1 ;;
    esac
    sg_print "$(sg_clean "$SG_SERVICE_CALL_RESPONSE")" > "$sg_call_after" || return 1
    sg_ledger service_call "$1:$2" "$sg_call_before" "$sg_call_after" "" "$sg_call_result" || return 1
    sg_write "$sg_call_result" > "$HMG_SEOGAEMO_STATE/last-call-result.tmp.$$" &&
        mv -f "$HMG_SEOGAEMO_STATE/last-call-result.tmp.$$" "$HMG_SEOGAEMO_STATE/last-call-result" || return 1
    rm -f "$sg_call_output" "$sg_call_before" "$sg_call_after"
    [ "$sg_call_rc" -eq 0 ]
}
sg_service_call_this_boot() {
    [ -f "$HMG_SEOGAEMO_STATE/last-call-boot" ] || return 1
    sg_seen_boot=$(cat "$HMG_SEOGAEMO_STATE/last-call-boot") || return 1
    sg_now_boot=$(cat "${SG_BOOT_ID_FILE:-/proc/sys/kernel/random/boot_id}" 2>/dev/null) || return 1
    [ -n "$sg_now_boot" ] && [ "$sg_seen_boot" = "$sg_now_boot" ]
}
sg_service_call_ok_this_boot() {
    sg_service_call_this_boot && [ "$(cat "$HMG_SEOGAEMO_STATE/last-call-result" 2>/dev/null)" = ok ]
}
sg_applied() {
    sg_check_build || return 3
    case "$1" in
        /*) if [ -f "$HMG_SEOGAEMO_STATE/line.$(sg_file_key "$1")/ready" ]; then
                sg_line_applied "$1"; return $?
            fi
            sg_dir=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
            case "$(cat "$sg_dir/applied-existence" 2>/dev/null)" in
                absent) [ ! -e "$1" ] ;;
                present) [ -f "$1" ] && sg_equal "$sg_dir/applied" "$1" ;;
                *) return 1 ;;
            esac ;;
        *) sg_dir=$HMG_SEOGAEMO_STATE/prop.$1
            [ -f "$sg_dir/applied" ] && [ "$(getprop "$1")" = "$(cat "$sg_dir/applied")" ] ;;
    esac
}
# The zygote wrapper may advance this counter before the shell runs again.
# Only decimal counters are accepted as wrapper-owned state.
sg_zygote_finalize_guard() {
    sg_guard=$HMG_SEOGAEMO_STATE/starts
    sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$sg_guard")
    [ -f "$sg_saved/applied" ] || return 0
    [ -f "$sg_guard" ] || return 3
    sg_count=$(cat "$sg_guard") || return 1
    case "$sg_count" in ''|*[!0-9]*) return 3;; esac
    if ! sg_equal "$sg_saved/applied" "$sg_guard"; then
        sg_old_applied=$HMG_SEOGAEMO_STATE/starts.old.$$
        sg_copy "$sg_saved/applied" "$sg_old_applied" || return 1
        sg_file_mark_applied "$sg_guard" || return 1
        sg_ledger zygote_starts "$sg_guard" "$sg_old_applied" "$sg_saved/applied" "$sg_saved/meta" || return 1
        rm -f "$sg_old_applied"
    fi
}
sg_file_baseline_absent() {
    [ ! -e "$1" ] || return 3
    sg_bind_build || return 1
    sg_absent_dir=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$1")
    [ ! -e "$sg_absent_dir/ready" ] || return 0
    mkdir -p "$sg_absent_dir" || return 1
    sg_write "$1" > "$sg_absent_dir/path.tmp.$$" &&
        mv -f "$sg_absent_dir/path.tmp.$$" "$sg_absent_dir/path" &&
        sg_write "absent" > "$sg_absent_dir/existence.tmp.$$" &&
        mv -f "$sg_absent_dir/existence.tmp.$$" "$sg_absent_dir/existence" &&
        sg_write "1" > "$sg_absent_dir/ready.tmp.$$" &&
        mv -f "$sg_absent_dir/ready.tmp.$$" "$sg_absent_dir/ready"
}
# Only the JAR named in owns_files can nominate a cache namespace. The two
# derived cache locations are fixed by Android's Dalvik naming convention.
sg_dex_cache_track() {
    sg_validate owns_files "${SG_TARGET_ROOT:-}$1" || return 2
    case "$1" in /system/framework/*.jar) ;; *) return 2;; esac
    case "$2" in ''|*[!a-zA-Z0-9_.]*) return 2;; esac
    sg_jar=${1##*/}
    sg_cache=${SG_TARGET_ROOT:-}/data/dalvik-cache/system@framework@$sg_jar@classes.dex
    sg_app_dir=${SG_TARGET_ROOT:-}/data/data/$2/app_hmg-seogaemo-wireless-dex
    sg_ready || return 1
    [ ! -f "$HMG_SEOGAEMO_STATE/dex.cache" ] || {
        [ "$(cat "$HMG_SEOGAEMO_STATE/dex.cache")" = "$sg_cache" ] &&
            [ "$(cat "$HMG_SEOGAEMO_STATE/dex.app-dir")" = "$sg_app_dir" ]; return; }
    if [ -d "$sg_app_dir" ]; then sg_dir_existed=present; else sg_dir_existed=absent; fi
    for sg_dex in "$sg_cache" "$sg_app_dir"/* "$sg_app_dir"/.[!.]* "$sg_app_dir"/..?*; do
        [ -e "$sg_dex" ] || {
            if [ "$sg_dex" = "$sg_cache" ]; then
                sg_file_baseline_absent "$sg_dex" || return 1
                sg_changed file "$sg_dex" || return 1
                sg_ledger dex_track "$sg_dex" "$sg_dex" "$sg_dex" "" || return 1
            fi
            continue
        }
        case "${sg_dex##*/}" in *[!a-zA-Z0-9_.@-]*) return 3;; esac
        [ -f "$sg_dex" ] && [ ! -L "$sg_dex" ] || return 3
        sg_file_baseline "$sg_dex" || return 1
        sg_changed file "$sg_dex" || return 1
        sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$sg_dex")
        sg_ledger dex_track "$sg_dex" "$sg_saved/bytes" "$sg_dex" "$sg_saved/meta" || return 1
    done
    sg_write "$sg_cache" > "$HMG_SEOGAEMO_STATE/dex.cache.tmp.$$" &&
        mv -f "$HMG_SEOGAEMO_STATE/dex.cache.tmp.$$" "$HMG_SEOGAEMO_STATE/dex.cache" &&
        sg_write "$sg_app_dir" > "$HMG_SEOGAEMO_STATE/dex.app-dir.tmp.$$" &&
        mv -f "$HMG_SEOGAEMO_STATE/dex.app-dir.tmp.$$" "$HMG_SEOGAEMO_STATE/dex.app-dir" &&
        sg_write "$sg_dir_existed" > "$HMG_SEOGAEMO_STATE/dex.dir-existed.tmp.$$" &&
        mv -f "$HMG_SEOGAEMO_STATE/dex.dir-existed.tmp.$$" "$HMG_SEOGAEMO_STATE/dex.dir-existed"
}
sg_dex_finalize() {
    [ -f "$HMG_SEOGAEMO_STATE/dex.cache" ] || return 0
    sg_cache=$(cat "$HMG_SEOGAEMO_STATE/dex.cache") || return 1
    sg_app_dir=$(cat "$HMG_SEOGAEMO_STATE/dex.app-dir") || return 1
    for sg_dex in "$sg_cache" "$sg_app_dir"/* "$sg_app_dir"/.[!.]* "$sg_app_dir"/..?*; do
        [ -e "$sg_dex" ] || continue
        case "${sg_dex##*/}" in *[!a-zA-Z0-9_.@-]*) return 3;; esac
        [ -f "$sg_dex" ] && [ ! -L "$sg_dex" ] || return 3
        sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$sg_dex")
        if [ ! -f "$sg_saved/ready" ]; then
            # This path was absent at activation; record that baseline now.
            mkdir -p "$sg_saved" || return 1
            sg_write "$sg_dex" > "$sg_saved/path.tmp.$$" && mv -f "$sg_saved/path.tmp.$$" "$sg_saved/path" || return 1
            sg_write "absent" > "$sg_saved/existence.tmp.$$" && mv -f "$sg_saved/existence.tmp.$$" "$sg_saved/existence" || return 1
            sg_write "1" > "$sg_saved/ready.tmp.$$" && mv -f "$sg_saved/ready.tmp.$$" "$sg_saved/ready" || return 1
            sg_changed file "$sg_dex" || return 1
        fi
        if [ -f "$sg_saved/applied" ]; then
            sg_dex_before=$sg_saved/applied
        elif [ -f "$sg_saved/bytes" ]; then sg_dex_before=$sg_saved/bytes
        else sg_dex_before=/dev/null; fi
        sg_dex_old=$HMG_SEOGAEMO_STATE/dex.old.$$
        sg_copy "$sg_dex_before" "$sg_dex_old" || return 1
        sg_file_mark_applied "$sg_dex" || return 1
        sg_ledger dex_generated "$sg_dex" "$sg_dex_old" "$sg_saved/applied" "$sg_saved/meta" || return 1
        rm -f "$sg_dex_old"
    done
}
sg_zygote_install() {
    sg_payload "$1" || return 2; sg_wrapper=$sg_source
    sg_payload "$2" || return 2; sg_loader=$sg_source
    sg_payload "$3" || return 2; sg_jar=$sg_source
    sg_bin=${SG_TARGET_ROOT:-}/system/bin/app_process
    sg_stock=${SG_TARGET_ROOT:-}/system/bin/app_process.hmg-seogaemo-stock
    sg_lib=${SG_TARGET_ROOT:-}/system/lib/libhmg_seogaemo_wireless_hook.so
    sg_framework=${SG_TARGET_ROOT:-}/system/framework/hmg-seogaemo-wireless-hook.jar
    for sg_target in "$sg_bin" "$sg_stock" "$sg_lib" "$sg_framework" \
        "$HMG_SEOGAEMO_STATE/enable" "$HMG_SEOGAEMO_STATE/enabled" \
        "$HMG_SEOGAEMO_STATE/starts" "$HMG_SEOGAEMO_STATE/disable"; do
        sg_validate owns_files "$sg_target" || return 2
        sg_whole_exclusive "$sg_target" || return 3
    done
    [ -f "$sg_bin" ] || return 3
    if sg_applied "$sg_bin"; then
        [ -f "$sg_stock" ] && [ -x "$sg_stock" ] &&
            sg_applied "$sg_stock" && sg_applied "$sg_lib" &&
            sg_applied "$sg_framework"
        return $?
    fi
    [ ! -e "$sg_stock" ] || return 3
    for sg_target in "$sg_bin" "$sg_stock" "$sg_lib" "$sg_framework" \
        "$HMG_SEOGAEMO_STATE/enable" "$HMG_SEOGAEMO_STATE/enabled" \
        "$HMG_SEOGAEMO_STATE/starts" "$HMG_SEOGAEMO_STATE/disable"; do
        sg_file_baseline "$sg_target" || return 1
    done
    sg_stock_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$sg_bin")
    read sg_stock_mode sg_stock_uid sg_stock_gid < "$sg_stock_saved/meta" || return 1
    sg_empty=$HMG_SEOGAEMO_STATE/zygote.empty.$$
    sg_zero=$HMG_SEOGAEMO_STATE/zygote.zero.$$
    : > "$sg_empty"; sg_write "0" > "$sg_zero"
    sg_rc=0
    sg_commit_file "$sg_stock" "$sg_stock_saved/bytes" "$sg_stock_mode" "$sg_stock_uid:$sg_stock_gid" || sg_rc=1
    if [ "$sg_rc" -eq 0 ]; then sg_commit_file "$sg_lib" "$sg_loader" 0644 0:0 || sg_rc=1; fi
    if [ "$sg_rc" -eq 0 ]; then sg_commit_file "$sg_framework" "$sg_jar" 0644 0:0 || sg_rc=1; fi
    if [ "$sg_rc" -eq 0 ]; then sg_commit_file "$HMG_SEOGAEMO_STATE/starts" "$sg_zero" 0600 0:0 || sg_rc=1; fi
    if [ "$sg_rc" -eq 0 ]; then sg_file_delete "$HMG_SEOGAEMO_STATE/disable" || sg_rc=1; fi
    if [ "$sg_rc" -eq 0 ]; then sg_commit_file "$HMG_SEOGAEMO_STATE/enabled" "$sg_empty" 0600 0:0 || sg_rc=1; fi
    if [ "$sg_rc" -eq 0 ]; then sg_commit_file "$HMG_SEOGAEMO_STATE/enable" "$sg_empty" 0600 0:0 || sg_rc=1; fi
    if [ "$sg_rc" -eq 0 ]; then sg_commit_file "$sg_bin" "$sg_wrapper" 0755 0:0 || sg_rc=1; fi
    rm -f "$sg_empty" "$sg_zero"
    if [ "$sg_rc" -ne 0 ]; then sg_revert_all || sg_log 'zygote rollback blocked'; return 1; fi
    [ -f "$sg_stock" ] && [ -x "$sg_stock" ] && sg_equal "$sg_stock_saved/bytes" "$sg_stock"
}
sg_revert_all() {
    sg_zygote_finalize_guard || return 3
    sg_dex_finalize || return 3
    sg_restore_preflight || return 3
    sg_line_revert_all || return $?
    [ -f "$HMG_SEOGAEMO_STATE/changes" ] || { sg_baseline_clear; return $?; }
    sg_reverse=
    while IFS='	' read -r sg_kind sg_target; do
        sg_reverse="$sg_kind:$sg_target $sg_reverse"
    done < "$HMG_SEOGAEMO_STATE/changes"
    for sg_item in $sg_reverse; do
        sg_kind=${sg_item%%:*}; sg_target=${sg_item#*:}
        sg_revert_before=$HMG_SEOGAEMO_STATE/revert-before.$$
        case "$sg_kind" in
            prop)
                sg_saved=$HMG_SEOGAEMO_STATE/prop.$sg_target
                sg_copy "$sg_saved/applied" "$sg_revert_before" || return 1
                sg_prop_restore "$sg_target" || return 3
                sg_ledger revert "$sg_target" "$sg_revert_before" "$sg_saved/live" "" || return 1 ;;
            file)
                sg_saved=$HMG_SEOGAEMO_STATE/file.$(sg_file_key "$sg_target")
                if [ -f "$sg_saved/applied" ]; then
                    sg_copy "$sg_saved/applied" "$sg_revert_before" || return 1
                else rm -f "$sg_revert_before"; fi
                sg_bind=$HMG_SEOGAEMO_STATE/bind.$(sg_file_key "$sg_target")
                if [ -f "$sg_bind" ]; then
                    if sg_mount_present "$sg_target"; then umount "$sg_target" || return 1; fi
                    rm -f "$sg_bind"
                fi
                case "$sg_target" in /system/*) sg_remount rw || return 1; sg_system=1;; *) sg_system=0;; esac
                sg_file_restore "$sg_target"; sg_rc=$?
                if [ "$sg_system" -eq 1 ]; then sg_remount ro || return 1; fi
                [ "$sg_rc" -eq 0 ] || return "$sg_rc"
                sg_ledger revert "$sg_target" "$sg_revert_before" "$sg_saved/bytes" "$sg_saved/meta" || return 1 ;;
            *) return 1 ;;
        esac
        rm -f "$sg_revert_before"
    done
    if [ -f "$HMG_SEOGAEMO_STATE/dex.app-dir" ] &&
        [ "$(cat "$HMG_SEOGAEMO_STATE/dex.dir-existed")" = absent ]; then
        rmdir "$(cat "$HMG_SEOGAEMO_STATE/dex.app-dir")" 2>/dev/null || true
    fi
    rm -f "$HMG_SEOGAEMO_STATE/dex.cache" "$HMG_SEOGAEMO_STATE/dex.app-dir" "$HMG_SEOGAEMO_STATE/dex.dir-existed"
    sg_baseline_clear
}
sg_owner_present() {
    case "$1" in
        nemesis-adb-tcp) [ -d /data/nemesis/apps/adb.tcp ] || [ -d /data/nemesis/backup/adb.tcp ] ;;
        hmgapi) [ -d /data/nemesis/apps/hmgapi ] || [ -f /data/hmgapi/uninstall.sh ] ;;
        leafia) [ -d /data/nemesis/apps/wireless.carplay.aa ] || [ -d /data/nemesis/backup/wireless.carplay.aa ] ;;
        local-hkmc-factory82) [ -d /data/nemesis/apps/local.hkmc.factory82 ] || [ -d /data/nemesis/backup/local.hkmc.factory82 ] ;;
        *) return 2 ;;
    esac
}
sg_status() {
    case "$1" in off|on|pending-restart|not-implemented) sg_code=0;; conflict) sg_code=3;; incompatible) sg_code=4;; failed) sg_code=1;; *) return 2;; esac
    case "$2" in none|adbd|head_unit) ;; *) return 2;; esac
    sg_detail=$(sg_clean "${3:-}")
    sg_print "state=$1" "restart=$2" "detail=$sg_detail"
    return "$sg_code"
}

# Core car maps are package-owned data. Publish immutable verified files, then
# replace one pointer file so readers see either the prior map or the new map.
hmg_seogaemo_map_work() {
    HMG_SEOGAEMO_MAP_WORK=$SG_DATA_ROOT/car-maps/.work.$$
    [ ! -e "$HMG_SEOGAEMO_MAP_WORK" ] || return 1
    mkdir -p "$HMG_SEOGAEMO_MAP_WORK" && chmod 700 "$SG_DATA_ROOT/car-maps" "$HMG_SEOGAEMO_MAP_WORK"
}
hmg_seogaemo_map_work_cleanup() {
    case "$HMG_SEOGAEMO_MAP_WORK" in "$SG_DATA_ROOT"/car-maps/.work.*) rm -rf "$HMG_SEOGAEMO_MAP_WORK";; *) return 2;; esac
}
hmg_seogaemo_map_publish() {
    hmg_seogaemo_map_input=$1
    [ "$hmg_seogaemo_map_input" = "$HMG_SEOGAEMO_MAP_WORK" ] || return 2
    for hmg_seogaemo_map_name in map.json car.json verified hash-id map-id source layout; do
        [ -s "$hmg_seogaemo_map_input/$hmg_seogaemo_map_name" ] && [ ! -L "$hmg_seogaemo_map_input/$hmg_seogaemo_map_name" ] || return 1
    done
    hmg_seogaemo_map_product=$(getprop ro.build.product)
    case "$hmg_seogaemo_map_product" in ''|*[!a-zA-Z0-9._-]*) return 2;; esac
    hmg_seogaemo_map_hash=$(cat "$hmg_seogaemo_map_input/hash-id")
    hmg_seogaemo_map_id=$(cat "$hmg_seogaemo_map_input/map-id")
    sg_print "$hmg_seogaemo_map_hash" "$hmg_seogaemo_map_id" | grep -Eqv '^[0-9a-f]{32}$' && return 2
    hmg_seogaemo_map_source=$(cat "$hmg_seogaemo_map_input/source")
    case "$hmg_seogaemo_map_source" in bundled|generated) ;; *) return 2;; esac
    hmg_seogaemo_map_layout=$(cat "$hmg_seogaemo_map_input/layout")
    case "$hmg_seogaemo_map_layout" in
        maps/*/*/*/*) case "$hmg_seogaemo_map_layout" in *..*|*[!a-zA-Z0-9_./-]*) return 2;; esac ;;
        *) return 2;;
    esac
    hmg_seogaemo_map_root=$SG_DATA_ROOT/car-maps
    hmg_seogaemo_map_rel=$hmg_seogaemo_map_product/$hmg_seogaemo_map_hash/$hmg_seogaemo_map_id
    hmg_seogaemo_map_final=$hmg_seogaemo_map_root/$hmg_seogaemo_map_rel
    hmg_seogaemo_map_stage=$hmg_seogaemo_map_root/.publish.$$
    mkdir -p "${hmg_seogaemo_map_final%/*}" "$hmg_seogaemo_map_stage" || return 1
    chmod 700 "$hmg_seogaemo_map_root" "$hmg_seogaemo_map_root/$hmg_seogaemo_map_product" "$hmg_seogaemo_map_root/$hmg_seogaemo_map_product/$hmg_seogaemo_map_hash" "$hmg_seogaemo_map_stage" || return 1
    for hmg_seogaemo_map_name in map.json car.json verified hash-id map-id source layout; do
        cp "$hmg_seogaemo_map_input/$hmg_seogaemo_map_name" "$hmg_seogaemo_map_stage/$hmg_seogaemo_map_name" &&
            cmp "$hmg_seogaemo_map_input/$hmg_seogaemo_map_name" "$hmg_seogaemo_map_stage/$hmg_seogaemo_map_name" >/dev/null 2>&1 &&
            chmod 600 "$hmg_seogaemo_map_stage/$hmg_seogaemo_map_name" || { rm -rf "$hmg_seogaemo_map_stage"; return 1; }
    done
    if [ -d "$hmg_seogaemo_map_final" ]; then
        for hmg_seogaemo_map_name in map.json car.json verified layout; do
            cmp "$hmg_seogaemo_map_stage/$hmg_seogaemo_map_name" "$hmg_seogaemo_map_final/$hmg_seogaemo_map_name" >/dev/null 2>&1 ||
                { rm -rf "$hmg_seogaemo_map_stage"; return 3; }
        done
        rm -rf "$hmg_seogaemo_map_stage" || return 1
    else
        mv "$hmg_seogaemo_map_stage" "$hmg_seogaemo_map_final" || { rm -rf "$hmg_seogaemo_map_stage"; return 1; }
    fi
    [ -f "$hmg_seogaemo_map_root/active" ] && cp "$hmg_seogaemo_map_root/active" "$hmg_seogaemo_map_root/.before.$$" || true
    sg_print "$hmg_seogaemo_map_rel" > "$hmg_seogaemo_map_root/active.tmp.$$" &&
        chmod 600 "$hmg_seogaemo_map_root/active.tmp.$$" &&
        mv -f "$hmg_seogaemo_map_root/active.tmp.$$" "$hmg_seogaemo_map_root/active" || return 1
    HMG_SEOGAEMO_STATE=$SG_DATA_ROOT/core/map
    mkdir -p "$HMG_SEOGAEMO_STATE" || return 1
    sg_ledger map_publish "$hmg_seogaemo_map_root/active" "$hmg_seogaemo_map_root/.before.$$" "$hmg_seogaemo_map_root/active" "" || return 1
    sg_ledger map_contents "$hmg_seogaemo_map_final/map.json" "" "$hmg_seogaemo_map_final/map.json" "" || return 1
    sg_ledger map_contents "$hmg_seogaemo_map_final/car.json" "" "$hmg_seogaemo_map_final/car.json" "" || return 1
    rm -f "$hmg_seogaemo_map_root/.before.$$"
}
hmg_seogaemo_map_active_dir() {
    [ -f "$SG_DATA_ROOT/car-maps/active" ] || return 1
    hmg_seogaemo_map_rel=$(cat "$SG_DATA_ROOT/car-maps/active")
    case "$hmg_seogaemo_map_rel" in
        *..*|/*|*[!a-zA-Z0-9_./-]*) return 2;;
        */*/*) ;;
        *) return 2;;
    esac
    [ -f "$SG_DATA_ROOT/car-maps/$hmg_seogaemo_map_rel/verified" ] || return 1
    sg_print "$SG_DATA_ROOT/car-maps/$hmg_seogaemo_map_rel"
}
hmg_seogaemo_map_export() {
    hmg_seogaemo_map_from=$(hmg_seogaemo_map_active_dir) || return 1
    hmg_seogaemo_map_layout=$(cat "$hmg_seogaemo_map_from/layout") || return 1
    case "$hmg_seogaemo_map_layout" in maps/*/*/*/*) ;; *) return 2;; esac
    case "$hmg_seogaemo_map_layout" in *..*|*[!a-zA-Z0-9_./-]*) return 2;; esac
    hmg_seogaemo_map_to=$1/$hmg_seogaemo_map_layout
    mkdir -p "$hmg_seogaemo_map_to" || return 1
    cp "$hmg_seogaemo_map_from/map.json" "$hmg_seogaemo_map_to/map.json" &&
        cp "$hmg_seogaemo_map_from/car.json" "$hmg_seogaemo_map_to/car.json" &&
        cmp "$hmg_seogaemo_map_from/map.json" "$hmg_seogaemo_map_to/map.json" >/dev/null 2>&1 &&
        cmp "$hmg_seogaemo_map_from/car.json" "$hmg_seogaemo_map_to/car.json" >/dev/null 2>&1
}
