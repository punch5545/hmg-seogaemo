package com.seogaemo.hmg.ui.settings;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.RootClient;
import com.seogaemo.hmg.module.RootResult;
import com.seogaemo.hmg.ui.ConfirmDialog;
import com.seogaemo.hmg.ui.OptionsList;

/**
 * USB report recording (HSM-REPORTS). Started from the Support page's problem report choice; while it
 * is on, Support shows one row to turn it off. Every change goes through fixed root verbs and a confirm.
 */
public final class ReportsSection {
    private static String cachedState;   // "on", "off", or null until the status was read
    private static boolean cachedUsb;
    private static boolean fetching;

    private ReportsSection() { }

    /** The "recording" row on the Support page; shown only while recording is on. */
    public static void addRecordingRow(final Activity activity, LinearLayout content, final Runnable refresh) {
        if (cachedState == null && !fetching) fetch(activity, refresh);
        if (!"on".equals(cachedState)) return;
        OptionsList rows = new OptionsList(activity);
        rows.setPadding(SettingsUi.dp(activity, SettingsUi.PAGE), 0, SettingsUi.dp(activity, SettingsUi.PAGE), 0);
        rows.addValue(activity.getString(cachedUsb ? R.string.reports_recording : R.string.reports_waiting),
                activity.getString(R.string.reports_stop), true, new View.OnClickListener() {
            public void onClick(View v) { confirmStop(activity, refresh); }
        });
        content.addView(rows, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void fetch(final Activity activity, final Runnable refresh) {
        fetching = true;
        new Thread(new Runnable() {
            public void run() {
                RootResult status = RootClient.recorder("status");
                // A failed read must not loop: treat it as off until the next explicit action.
                cachedState = status.exitCode == 0 && "on".equals(status.state) ? "on" : "off";
                cachedUsb = "present".equals(status.info.get("usb"));
                fetching = false;
                activity.runOnUiThread(refresh);
            }
        }, "reports-status").start();
    }

    /** Runs after the problem report dialog was confirmed with "keep recording". */
    public static void startRecording(final Activity activity, final ConfirmDialog dialog, final Runnable refresh) {
        dialog.showProgress();
        run(activity, dialog, true, refresh);
    }

    private static void confirmStop(final Activity activity, final Runnable refresh) {
        ConfirmDialog.show(activity, activity.getString(R.string.reports_record),
                activity.getString(R.string.reports_record_off_confirm), activity.getString(R.string.reports_stop),
                activity.getString(R.string.cancel), false, new ConfirmDialog.Action() {
            public void run(ConfirmDialog dialog) {
                dialog.showProgress();
                ReportsSection.run(activity, dialog, false, refresh);
            }
        }, null);
    }

    private static void run(final Activity activity, final ConfirmDialog dialog, final boolean turnOn,
                            final Runnable refresh) {
        new Thread(new Runnable() {
            public void run() {
                final RootResult result = RootClient.recorder(turnOn ? "enable" : "disable");
                cachedState = null;
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        dialog.dismiss();
                        ConfirmDialog.showResult(activity, activity.getString(R.string.reports_record),
                                toggleMessage(activity, turnOn, result), activity.getString(R.string.close));
                        refresh.run();
                    }
                });
            }
        }, "reports-toggle").start();
    }

    static String toggleMessage(Activity activity, boolean turnOn, RootResult result) {
        boolean worked = result.exitCode == 0 && (turnOn ? "on" : "off").equals(result.state);
        if (worked) return activity.getString(turnOn ? R.string.reports_on_done : R.string.reports_off_done);
        if (result.detail != null && result.detail.startsWith("No USB drive")) {
            return activity.getString(R.string.reports_need_usb);
        }
        return result.detail == null || result.detail.length() == 0
                ? activity.getString(R.string.reports_failed) : result.detail;
    }

    /** Display name of a car check id (library and module checks are folded by the caller). */
    public static String title(Activity activity, String id) {
        int resource = activity.getResources().getIdentifier("reports_check_" + id, "string", activity.getPackageName());
        return resource == 0 ? id : activity.getString(resource);
    }
}
