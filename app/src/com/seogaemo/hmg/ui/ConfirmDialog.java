package com.seogaemo.hmg.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.seogaemo.hmg.ui.settings.SettingsUi;

/** One car-sized confirmation surface for state changes and lengthy work. */
public final class ConfirmDialog extends Dialog {
    public interface Action { void run(ConfirmDialog dialog); }

    private static final int SURFACE = 0xFF171717;
    private static final int RAISED = 0xFF202020;
    private static final int HAIRLINE = 0xFF343434;
    private static final int WHITE = 0xFFF5F5F5;
    private static final int BLUE = 0xFF0070F3;
    private static final int ERROR = 0xFFEE0000;
    private final TextView confirm;
    private final TextView cancel;
    private final ProgressBar progress;
    private boolean submitted;
    private long shownAt;
    private boolean tapGuard = true; // Off for read-only results: their one button only closes.

    private ConfirmDialog(final Activity activity, String title, CharSequence message,
            String confirmLabel, String cancelLabel, boolean destructive,
            final Action onConfirm, final Runnable onCancel) {
        super(activity, android.R.style.Theme_Holo_Dialog);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(false);
        setCancelable(true);
        if (onCancel != null) setOnCancelListener(new OnCancelListener() {
            public void onCancel(android.content.DialogInterface ignored) { onCancel.run(); }
        });

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, SettingsUi.PAGE), dp(activity, SettingsUi.LG),
                dp(activity, SettingsUi.PAGE), dp(activity, SettingsUi.LG));
        panel.setBackgroundDrawable(background(activity, SURFACE, HAIRLINE, 8));

        TextView heading = label(activity, title, 26, WHITE, true);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        if (message != null && message.length() > 0) {
            ScrollView scroll = new ScrollView(activity);
            TextView body = label(activity, message, 20, WHITE, false);
            body.setLineSpacing(dp(activity, SettingsUi.XS), 1f);
            scroll.addView(body);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = dp(activity, SettingsUi.MD);
            panel.addView(scroll, bodyParams);
        }

        progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                dp(activity, 40), dp(activity, 40));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(activity, SettingsUi.MD);
        panel.addView(progress, progressParams);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dp(activity, SettingsUi.LG);
        panel.addView(buttons, buttonsParams);

        cancel = label(activity, cancelLabel, 21, WHITE, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackgroundDrawable(background(activity, RAISED, HAIRLINE, 6));
        cancel.setFocusable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { dismiss(); if (onCancel != null) onCancel.run(); }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(dp(activity, 220), dp(activity, 60)));

        confirm = label(activity, confirmLabel, 21, WHITE, true);
        confirm.setGravity(Gravity.CENTER);
        confirm.setBackgroundDrawable(background(activity, destructive ? ERROR : BLUE,
                destructive ? ERROR : BLUE, 6));
        confirm.setFocusable(true);
        confirm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (submitted) return;
                // A touch that began before the dialog appeared must not confirm it.
                if (tapGuard && !armed(shownAt, SystemClock.uptimeMillis())) return;
                submitted = true;
                confirm.setEnabled(false);
                cancel.setEnabled(false);
                setCancelable(false);
                onConfirm.run(ConfirmDialog.this);
            }
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                dp(activity, 220), dp(activity, 60));
        confirmParams.leftMargin = dp(activity, SettingsUi.MD);
        buttons.addView(confirm, confirmParams);
        setContentView(panel);
    }

    public static ConfirmDialog show(Activity activity, String title, CharSequence message,
            String confirmLabel, String cancelLabel, boolean destructive,
            Action onConfirm, Runnable onCancel) {
        return show(activity, title, message, confirmLabel, cancelLabel, destructive, true,
                onConfirm, onCancel);
    }

    private static ConfirmDialog show(Activity activity, String title, CharSequence message,
            String confirmLabel, String cancelLabel, boolean destructive, boolean tapGuard,
            Action onConfirm, Runnable onCancel) {
        if (activity.isFinishing()) return null; // Callers ignore the result.
        ConfirmDialog dialog = new ConfirmDialog(activity, title, message, confirmLabel,
                cancelLabel, destructive, onConfirm, onCancel);
        dialog.tapGuard = tapGuard;
        dialog.show();
        dialog.shownAt = SystemClock.uptimeMillis();
        Window window = dialog.getWindow();
        window.setLayout(dp(activity, 760), WindowManager.LayoutParams.WRAP_CONTENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.75f;
        window.setAttributes(attributes);
        dialog.cancel.requestFocus();
        return dialog;
    }

    /** Confirm taps are ignored for this long after a confirmation appears; Cancel and result Close are never delayed. */
    static final long CONFIRM_ARM_DELAY_MS = 600L;

    static boolean armed(long shownAtMs, long nowMs) {
        return shownAtMs != 0 && nowMs - shownAtMs >= CONFIRM_ARM_DELAY_MS;
    }

    /** Keep the dialog visible while the confirmed work runs. */
    public void showProgress() { progress.setVisibility(View.VISIBLE); }

    /** Read-only result using the same typography and touch targets as confirmations. */
    public static ConfirmDialog showResult(Activity activity, String title, CharSequence message,
            String closeLabel) {
        if (activity.isFinishing()) return null;
        // Close does nothing destructive, so it is never delayed by the confirm tap guard.
        ConfirmDialog dialog = show(activity, title, message, closeLabel, closeLabel, false, false,
                new Action() { public void run(ConfirmDialog dialog) { dialog.dismiss(); } }, null);
        dialog.cancel.setVisibility(View.GONE);
        dialog.confirm.requestFocus();
        return dialog;
    }

    private static int dp(Activity activity, int value) { return SettingsUi.dp(activity, value); }

    private static TextView label(Activity activity, CharSequence value, int sp,
            int color, boolean medium) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private static GradientDrawable background(Activity activity, int fill, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(activity, radius));
        shape.setStroke(dp(activity, 1), border);
        return shape;
    }
}
