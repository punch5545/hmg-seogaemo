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
assert "ConfirmDialog.show" in method("addLanguageOption")
assert method("addLanguageOption").index("ConfirmDialog.show") < method("addLanguageOption").index('putString("language"')
assert "ConfirmDialog.show" in method("confirmCheckUpdate")
assert "confirmCheckUpdate()" in method("showUpdate")
assert "ConfirmDialog.show" in method("confirmInstall")
assert "ConfirmDialog.show" in method("confirmRollback")
assert "ConfirmDialog.show" in method("showSettings")  # USB export
assert "ConfirmDialog.show" in method("confirmMapRun")
assert "ConfirmDialog.show" in method("confirmMapExport")
assert "mapOperation(\"run\", dialog)" in method("confirmMapRun")
assert "mapOperation(\"export\", dialog)" in method("confirmMapExport")
assert "setCanceledOnTouchOutside(false)" in dialog
assert "confirm.setEnabled(false)" in dialog
assert "cancel.requestFocus()" in dialog
print("confirmation routing: OK")
