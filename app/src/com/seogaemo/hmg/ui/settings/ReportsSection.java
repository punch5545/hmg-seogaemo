package com.seogaemo.hmg.ui.settings;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.RootClient;
import com.seogaemo.hmg.module.RootResult;
import com.seogaemo.hmg.ui.ConfirmDialog;
import com.seogaemo.hmg.ui.OptionsList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings rows for USB report recording (HSM-REPORTS) and the car check. Both go through fixed
 * root verbs; the recorder can only be turned on while a USB drive is mounted, and every action
 * is confirmed first like any other state change.
 */
public final class ReportsSection {
    private static String cachedState;   // "on", "off", or null until the status was read
    private static boolean cachedUsb;
    private static boolean fetching;

    private ReportsSection() { }

    public static void add(final Activity activity, LinearLayout content, final Runnable refresh) {
        OptionsList rows = new OptionsList(activity);
        rows.setPadding(SettingsUi.dp(activity, SettingsUi.PAGE), 0, SettingsUi.dp(activity, SettingsUi.PAGE), 0);
        rows.addSection(activity.getString(R.string.reports_section));
        CharSequence value;
        if (cachedState == null) value = "…";
        else if ("on".equals(cachedState)) value = activity.getString(cachedUsb ? R.string.on : R.string.reports_waiting);
        else value = activity.getString(R.string.off);
        rows.addValue(activity.getString(R.string.reports_record), value, true, new View.OnClickListener() {
            public void onClick(View v) { confirmToggle(activity, refresh); }
        });
        rows.addLink(activity.getString(R.string.reports_check), new View.OnClickListener() {
            public void onClick(View v) { confirmCheck(activity); }
        });
        rows.addInfo(activity.getString(R.string.reports_hint));
        content.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        if (cachedState == null && !fetching) fetch(activity, refresh);
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

    private static void confirmToggle(final Activity activity, final Runnable refresh) {
        final boolean turnOn = !"on".equals(cachedState);
        final String title = activity.getString(R.string.reports_record);
        ConfirmDialog.show(activity, title,
                activity.getString(turnOn ? R.string.reports_record_on_confirm : R.string.reports_record_off_confirm),
                activity.getString(R.string.apply), activity.getString(R.string.cancel), false,
                new ConfirmDialog.Action() {
            public void run(final ConfirmDialog dialog) {
                dialog.showProgress();
                new Thread(new Runnable() {
                    public void run() {
                        final RootResult result = RootClient.recorder(turnOn ? "enable" : "disable");
                        cachedState = null;
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                dialog.dismiss();
                                ConfirmDialog.showResult(activity, title, toggleMessage(activity, turnOn, result),
                                        activity.getString(R.string.close));
                                refresh.run();
                            }
                        });
                    }
                }, "reports-toggle").start();
            }
        }, null);
    }

    static String toggleMessage(Activity activity, boolean turnOn, RootResult result) {
        boolean worked = result.exitCode == 0 && (turnOn ? "on" : "off").equals(result.state);
        if (worked) return activity.getString(turnOn ? R.string.reports_on_done : R.string.reports_off_done);
        if (result.detail != null && result.detail.startsWith("No USB drive")) {
            return activity.getString(R.string.reports_need_usb);
        }
        return result.detail == null || result.detail.length() == 0 ? activity.getString(R.string.reports_check_failed) : result.detail;
    }

    private static void confirmCheck(final Activity activity) {
        final String title = activity.getString(R.string.reports_check_title);
        ConfirmDialog.show(activity, title, activity.getString(R.string.reports_check_confirm),
                activity.getString(R.string.reports_check), activity.getString(R.string.cancel), false,
                new ConfirmDialog.Action() {
            public void run(final ConfirmDialog dialog) {
                dialog.showProgress();
                new Thread(new Runnable() {
                    public void run() {
                        final RootResult result = RootClient.preflight();
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                dialog.dismiss();
                                ConfirmDialog.showResult(activity, title, format(activity, result),
                                        activity.getString(R.string.close));
                            }
                        });
                    }
                }, "reports-check").start();
            }
        }, null);
    }

    /** The check result as plain lines: verdict first, then one line per check. */
    static CharSequence format(Activity activity, RootResult result) {
        Map<String, String> checks = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : result.info.entrySet()) {
            if (entry.getKey().startsWith("check_")) checks.put(entry.getKey(), entry.getValue());
        }
        if (result.exitCode != 0 || checks.isEmpty()) {
            return result.detail == null || result.detail.length() == 0
                    ? activity.getString(R.string.reports_check_failed) : result.detail;
        }
        StringBuilder text = new StringBuilder();
        text.append(activity.getString(verdictString(result.info.get("verdict")))).append("\n");
        for (String line : checks.values()) {
            String[] part = line.split("\\|", 4);
            if (part.length < 4) continue;
            text.append("\n[").append(activity.getString(levelString(part[1]))).append("] ")
                    .append(title(activity, part[0])).append(": ").append(part[3]);
            if ("inferred".equals(part[2])) text.append(' ').append(activity.getString(R.string.reports_guess));
        }
        return text.toString();
    }

    static String title(Activity activity, String id) {
        if (id.startsWith("lib_")) return activity.getString(R.string.reports_check_lib, id.substring(4));
        if (id.startsWith("module_")) return activity.getString(R.string.reports_check_module, id.substring(7));
        int resource = activity.getResources().getIdentifier("reports_check_" + id, "string", activity.getPackageName());
        return resource == 0 ? id : activity.getString(resource);
    }

    static int levelString(String level) {
        if ("ok".equals(level)) return R.string.reports_level_ok;
        if ("warn".equals(level)) return R.string.reports_level_warn;
        if ("block".equals(level)) return R.string.reports_level_block;
        return R.string.reports_level_unknown;
    }

    static int verdictString(String verdict) {
        if ("ok".equals(verdict)) return R.string.reports_verdict_ok;
        if ("warn".equals(verdict)) return R.string.reports_verdict_warn;
        if ("block".equals(verdict)) return R.string.reports_verdict_block;
        return R.string.reports_verdict_unknown;
    }
}
