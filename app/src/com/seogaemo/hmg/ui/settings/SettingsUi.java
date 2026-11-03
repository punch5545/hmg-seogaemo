package com.seogaemo.hmg.ui.settings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared spacing and control geometry for the framework Settings views. */
public final class SettingsUi {
    public static final int XS = 8;
    public static final int SM = 12;
    public static final int MD = 16;
    public static final int LG = 24;
    public static final int PAGE = 32;
    public static final int STATUS_WIDTH = 144;
    public static final int STATUS_HEIGHT = 54;

    private SettingsUi() { }

    public static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    public static void contentRow(Context context, LinearLayout row) {
        row.setPadding(dp(context, PAGE), 0, dp(context, PAGE), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
    }

    public static void cardRow(Context context, LinearLayout row) {
        row.setPadding(dp(context, MD), 0, dp(context, MD), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
    }

    public static void pill(Context context, TextView view, Drawable background) {
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(context, MD), 0, dp(context, MD), 0);
        view.setBackgroundDrawable(background);
    }

    public static void button(Context context, TextView view, Drawable background) {
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, LG), dp(context, XS),
                dp(context, LG), dp(context, XS));
        view.setMinHeight(dp(context, 60));
        view.setBackgroundDrawable(background);
        view.setClickable(true);
    }

    public static LinearLayout.LayoutParams contentAction(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(context, 60));
        params.leftMargin = dp(context, PAGE);
        params.rightMargin = dp(context, PAGE);
        return params;
    }
}
