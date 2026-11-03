#!/system/bin/sh
# Shared package-only stock restore. Caller holds operation.lock throughout.
hmg_seogaemo_stock_key() {
    stock_chars=$1; stock_key=
    while [ -n "$stock_chars" ]; do
        stock_char=${stock_chars%"${stock_chars#?}"}
        stock_chars=${stock_chars#?}
        [ "$stock_char" != - ] || stock_char=_
        stock_key=$stock_key$stock_char
    done
    sg_write "$stock_key"
}
hmg_seogaemo_stock_context() {
    HMG_SEOGAEMO_STATE=$SG_DATA_ROOT/modules/$1
    HMG_SEOGAEMO_MODULE_DIR=$base/modules/$1.d
    export HMG_SEOGAEMO_STATE HMG_SEOGAEMO_MODULE_DIR
    unset SG_SKIP_CONSENT_FRAMEWORK SG_BOOT_ID_FILE HMG_SEOGAEMO_SERVICE_BIN
}
hmg_seogaemo_stock_snapshots() {
    for stock_item in "$HMG_SEOGAEMO_STATE"/prop.*/ready "$HMG_SEOGAEMO_STATE"/file.*/ready \
        "$HMG_SEOGAEMO_STATE"/bind.* "$HMG_SEOGAEMO_STATE"/changes \
        "$HMG_SEOGAEMO_STATE"/dex.cache; do
        [ ! -f "$stock_item" ] || return 0
    done
    return 1
}
hmg_seogaemo_stock_receipt() {
    {
        sg_print "state=$stock_state" "restart=$stock_restart" "detail=$stock_detail"
        for stock_receipt_id in $stock_reverse; do
            stock_value=$(cat "$stock/outcome.$stock_receipt_id" 2>/dev/null)
            sg_print "info.module_$(hmg_seogaemo_stock_key "$stock_receipt_id")=${stock_value:-not_attempted}"
        done
    } > "$stock/result.tmp.$$" && mv "$stock/result.tmp.$$" "$stock/result"
}
hmg_seogaemo_stock_ledger() {
    stock_saved_context=${HMG_SEOGAEMO_STATE:-}
    HMG_SEOGAEMO_STATE=$stock
    sg_ledger "$1" stock /dev/null "$stock/result" '' "${2:-ok}"
    stock_ledger_code=$?
    HMG_SEOGAEMO_STATE=$stock_saved_context
    return "$stock_ledger_code"
}
hmg_seogaemo_stock_finish() {
    hmg_seogaemo_stock_receipt && hmg_seogaemo_stock_ledger stock_finish "$stock_state" || {
        stock_state=failed; stock_detail='Could not record restore result; backups retained'
        hmg_seogaemo_stock_receipt
        return 1
    }
    case "$stock_state" in off|pending-restart) return 0;; conflict) return 3;; *) return 1;; esac
}
hmg_seogaemo_stock_restore() {
    stock_reverse=
    [ -f "$base/modules.list" ] || return 1
    for stock_id in $(cat "$base/modules.list"); do stock_reverse="$stock_id $stock_reverse"; done
    stock_state=failed; stock_restart=none; stock_detail='Restore interrupted; retry required'
    # A second restore (notably preuninstall) cannot erase an outstanding
    # framework restart requirement from a successful restore in this boot.
    if [ "$(cat "$stock/boot" 2>/dev/null)" = "$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)" ] &&
        grep -Fxq restart=head_unit "$stock/result" 2>/dev/null; then stock_restart=head_unit; fi
    for stock_id in $stock_reverse; do
        sg_write not_attempted > "$stock/outcome.$stock_id" || return 1
    done
    # The marker is retained on every failure and across boots. In-flight watch
    # calls finished before we obtained the operation lock.
    sg_write paused > "$stock/paused" &&
        getprop ro.build.product > "$stock/product" &&
        cat /proc/sys/kernel/random/boot_id > "$stock/boot" &&
        hmg_seogaemo_stock_receipt && hmg_seogaemo_stock_ledger stock_start || return 1
    # Preflight ALL modules before any restore; finalize only runtime-owned
    # counters/cache entries, exactly as sg_revert_all does.
    for stock_id in $stock_reverse; do
        {
            hmg_seogaemo_stock_context "$stock_id"
            if hmg_seogaemo_stock_snapshots; then
                sg_zygote_finalize_guard && sg_dex_finalize && sg_restore_preflight
            fi
        } > "$stock/module-output" 2>&1
        stock_code=$?
        if [ "$stock_code" -ne 0 ]; then
            stock_state=failed
            [ "$stock_code" -ne 3 ] || stock_state=conflict
            sg_write "$stock_state" > "$stock/outcome.$stock_id"
            stock_detail="$stock_id preflight blocked; backups retained; watch paused"
            hmg_seogaemo_stock_finish; return $?
        fi
    done
    for stock_id in $stock_reverse; do
        stock_old=$(cat "$SG_DATA_ROOT/desired/$stock_id" 2>/dev/null)
        # sg_revert_all consumes verified baselines before a module's service
        # cleanup can fail. Retain an operation copy for that late-failure case.
        stock_recovery=$stock/recovery.$(date +%s).$$
        if [ -d "$SG_DATA_ROOT/modules/$stock_id" ]; then
            if ! { mkdir -p "$stock_recovery" &&
                cp -R "$SG_DATA_ROOT/modules/$stock_id" "$stock_recovery/$stock_id"; }; then
                sg_write failed > "$stock/outcome.$stock_id"
                stock_detail="$stock_id recovery copy failed; no further restores attempted"
                hmg_seogaemo_stock_finish; return $?
            fi
        fi
        (
            hmg_seogaemo_stock_context "$stock_id"
            if [ "$stock_old" != on ] && ! hmg_seogaemo_stock_snapshots; then
                # Nothing applied: do not fail on an unavailable unused module.
                # Reboot-only modules still report their transient effect.
                if ! grep -Fxq 'restore=reboot' "$base/modules/$stock_id.properties"; then
                    sg_status off none 'Already off'; exit $?
                fi
            fi
            sh "$base/modules/$stock_id.sh" revert
        ) > "$stock/module-output" 2>&1
        stock_code=$?
        if [ "$stock_code" -eq 0 ]; then
            if grep -Fxq state=off "$stock/module-output"; then :
            elif grep -Fxq state=pending-restart "$stock/module-output" &&
                grep -Fxq restart=head_unit "$stock/module-output"; then
                stock_restart=head_unit
            else stock_code=1; fi
        fi
        # A successful module response must have consumed all restore material.
        if [ "$stock_code" -eq 0 ] && (
            hmg_seogaemo_stock_context "$stock_id"
            hmg_seogaemo_stock_snapshots
        ); then stock_code=1; fi
        if [ "$stock_code" -eq 0 ]; then
            sg_write off > "$SG_DATA_ROOT/desired/$stock_id.tmp.$$" &&
                mv "$SG_DATA_ROOT/desired/$stock_id.tmp.$$" "$SG_DATA_ROOT/desired/$stock_id" || stock_code=1
        fi
        if [ "$stock_code" -ne 0 ]; then
            stock_state=failed
            if [ "$stock_code" -eq 3 ] || grep -Fxq state=conflict "$stock/module-output"; then stock_state=conflict; fi
            sg_write "$stock_state" > "$stock/outcome.$stock_id"
            stock_detail="$stock_id restore blocked; backups retained; watch paused"
            hmg_seogaemo_stock_finish; return $?
        fi
        sg_write reverted > "$stock/outcome.$stock_id" || return 1
        hmg_seogaemo_stock_receipt && hmg_seogaemo_stock_ledger stock_module "$stock_id" || return 1
        rm -rf "$stock_recovery/$stock_id"
        rmdir "$stock_recovery" 2>/dev/null || true
    done
    stock_state=off; stock_detail='All modules restored; HMG-Seogaemo remains installed'
    if [ "$stock_restart" = head_unit ]; then
        stock_state=pending-restart; stock_detail='All modules off; restart head unit to complete restore'
    fi
    hmg_seogaemo_stock_finish
}
hmg_seogaemo_stock_status() {
    if [ ! -f "$stock/result" ]; then
        sg_status off none 'No stock restore recorded'
        return 0
    fi
    stock_new_boot=0
    [ "$(cat "$stock/boot" 2>/dev/null)" = "$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)" ] || stock_new_boot=1
    while IFS= read -r stock_line; do
        if [ "$stock_new_boot" -eq 1 ]; then
            case "$stock_line" in
                state=pending-restart) stock_line=state=off ;;
                restart=head_unit) stock_line=restart=none ;;
                detail=*)
                    if grep -Fxq state=pending-restart "$stock/result"; then
                        stock_line='detail=Head unit restarted after restore'
                    fi ;;
            esac
        fi
        sg_print "$stock_line"
    done < "$stock/result"
    if [ -f "$SG_DATA_ROOT/uninstall/result" ]; then
        sg_print "info.uninstall=$(cat "$SG_DATA_ROOT/uninstall/result")"
    fi
    case "$(cat "$stock/result")" in state=conflict*) return 3;; state=failed*) return 1;; esac
    return 0
}
hmg_seogaemo_stock_uninstall_pending() {
    if [ -f "$SG_DATA_ROOT/uninstall/pending" ]; then
        stock_pid=$(cat "$SG_DATA_ROOT/uninstall/pending")
        case "$stock_pid" in ''|*[!0-9]*) ;; *)
            kill -0 "$stock_pid" 2>/dev/null && return 0 ;;
        esac
    fi
    return 1
}
hmg_seogaemo_stock_uninstall() {
    stock_uninstall=$SG_DATA_ROOT/uninstall
    mkdir -p "$stock_uninstall" && chmod 700 "$stock_uninstall" || return 1
    rm -f "$stock_uninstall/ready" || return 1
    cp "$base/core/uninstall-detached.sh" "$stock_uninstall/run.sh" &&
        cp "$base/lib/common.sh" "$stock_uninstall/common.sh" &&
        chmod 700 "$stock_uninstall/run.sh" "$stock_uninstall/common.sh" || return 1
    sg_write queued > "$stock_uninstall/result" || return 1
    # Ignore HUP before forking: ignored signals survive exec. No inherited
    # pipe/terminal or working directory refers to the app/package.
    (
        trap '' HUP
        cd / || exit 1
        exec sh "$stock_uninstall/run.sh"
    ) < /dev/null > "$stock_uninstall/log" 2>&1 &
    stock_pid=$!
    stock_detail='Uninstall queued with NEMESIS; app will close when removed'
    if sg_write "$stock_pid" > "$stock_uninstall/pending" &&
        hmg_seogaemo_stock_receipt && hmg_seogaemo_stock_ledger package_uninstall queued; then return 0; fi
    # The runner cannot start removal until the caller publishes ready at exit.
    kill "$stock_pid" 2>/dev/null
    rm -f "$stock_uninstall/pending"
    sg_write failed > "$stock_uninstall/result"
    return 1
}
