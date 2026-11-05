#!/usr/bin/env python3
"""Guard the UI entry points that can start work or persist state."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/com/seogaemo/hmg/MainActivity.java").read_text()
dialog = (root / "app/src/com/seogaemo/hmg/ui/ConfirmDialog.java").read_text()


def method(name):
    marker = "private void " + name + "("
    start = activity.index(marker)
    brace = activity.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        if activity[end] == "{":
            depth += 1
        elif activity[end] == "}":
            depth -= 1
        end += 1
    return activity[brace:end]


assert "prepareModuleChange" in method("dispatchModuleAction")
assert "dispatchModuleAction(action)" in method("renderPageItem")
assert "ConfirmDialog.show" in method("confirmModuleChange")
assert "module.rebootRestore()" in method("confirmModuleChange")
assert '"off".equals(result.info.get("desired"))' in activity
assert "ConfirmDialog.show" in method("addLanguageOption")
assert method("addLanguageOption").index("ConfirmDialog.show") < method("addLanguageOption").index('putString("language"')
assert "ConfirmDialog.show" in method("confirmCheckUpdate")
assert "confirmCheckUpdate()" in method("showUpdate")
assert "ConfirmDialog.show" in method("confirmInstall")
assert "ConfirmDialog.show" in method("confirmRollback")
assert "ConfirmDialog.show" in method("showSupport")  # USB export
assert "confirmTbtReset()" in method("dispatchModuleAction")
assert "ConfirmDialog.show" in method("confirmTbtReset")
assert method("confirmTbtReset").index("ConfirmDialog.show") < method("confirmTbtReset").index("RootClient.tbtReset()")
assert "dialog.showProgress()" in method("confirmTbtReset") and "new Thread" in method("confirmTbtReset")
assert "getString(R.string.cancel), true," in method("confirmTbtReset")  # destructive styling, Cancel stays the default
assert "ConfirmDialog.show" in method("confirmMapRun")
assert "ConfirmDialog.show" in method("confirmMapExport")
assert "mapOperation(\"run\", dialog)" in method("confirmMapRun")
assert "mapOperation(\"export\", dialog)" in method("confirmMapExport")
assert "confirmStock(false)" in method("showStock")
assert "confirmStock(true)" in method("showStock")
assert "RootClient.stock(" not in method("showStock").replace('RootClient.stock("status")', '')
stock = method("confirmStock")
assert "ConfirmDialog.show" in stock
assert "getString(R.string.cancel), uninstall, new ConfirmDialog.Action()" in stock
assert stock.index("ConfirmDialog.show") < stock.index(".edit().clear().commit()")
assert stock.index("ConfirmDialog.show") < stock.index("RootClient.uninstallPackage()")
assert stock.index("ConfirmDialog.show") < stock.index('RootClient.stock("restore")')
stock_result = activity.split('private String stockResultText(', 1)[1].split('private void updateMessage', 1)[0]
assert 'result.exitCode != 0 && result.detail.length() > 0' in stock_result
assert '.append(result.detail)' in stock_result
reboot = method("confirmReboot")
assert "confirmReboot(R.string.device_reboot_confirm)" in method("showStock")
assert "confirmReboot(R.string.module_restart_confirm)" in method("restartNow")  # restart bar
assert "RootClient.rebootDevice()" not in method("restartNow")
assert "RootClient.rebootDevice()" not in method("showStock")
assert "getString(R.string.cancel), true, new ConfirmDialog.Action()" in reboot
assert reboot.index("ConfirmDialog.show") < reboot.index("RootClient.rebootDevice()")
on_create = activity.split("@Override public void onCreate(", 1)[1].split("private void offerModuleRestore", 1)[0]
assert "restoreModules()" not in on_create  # no unconfirmed restore at app start
restore = method("confirmModuleRestore")
assert "ConfirmDialog.show" in restore
assert "restoreConfirmMessage(steps)" in restore  # lists modules and the boot-process warning
assert restore.index("ConfirmDialog.show") < restore.index("restoreModules()")
assert "RestoreGate.decide" in method("offerModuleRestore")
assert "confirmModuleRestore(steps)" in method("offerModuleRestore")
assert "setCanceledOnTouchOutside(false)" in dialog
assert "confirm.setEnabled(false)" in dialog
assert "cancel.requestFocus()" in dialog
# The tap guard delays only confirmations; a result dialog's Close must be immediate.
result_dialog = dialog.split("public static ConfirmDialog showResult(", 1)[1].split("private static int dp", 1)[0]
assert "false, false," in result_dialog
assert "tapGuard && !armed(" in dialog
print("confirmation routing: OK")
import subprocess
import sys
subprocess.run([sys.executable, str(root / 'tools/test-module-switch.py')], check=True)
