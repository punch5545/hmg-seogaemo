package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.seogaemo.hmg.ui.settings.SettingsUi;

/** Colour tokens and the framework-view primitives every screen is built from (ui-guidelines.md). */
public final class Theme {
    static final float TEXT_SCALE = 0.86f;
    public static final int CANVAS = 0xFF111111;
    public static final int SURFACE = 0xFF171717;
    public static final int RAISED = 0xFF202020;
    public static final int HAIRLINE = 0xFF343434;
    public static final int WHITE = 0xFFF5F5F5;
    public static final int MUTED = 0xFF9A9A9A;
    public static final int BLUE = 0xFF0070F3;
    public static final int ERROR = 0xFFEE0000;
    public static final int WARNING = 0xFFF5A623;
    /** Pressed fills and the selected sidebar row keep the tone of the shipped screens. */
    public static final int PRESS = 0xFF294563;
    public static final int RED_PRESS = 0xFF633030;
    public static final int SELECTED = 0xFF1C2938;
    public static final int DIM = 0xFFD0D0D0;

    public static final int ACTION_HEIGHT = 60;
    public static final int ROW_HEIGHT = 64;
    public static final int BUTTON_RADIUS = 6;
    public static final int CARD_RADIUS = 8;

    private Theme() { }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    public static Typeface sans() { return Typeface.create("sans-serif", Typeface.NORMAL); }

    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    public static TextView text(Context context, CharSequence value, float sp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        // Android 4.4 Korean glyphs render larger than the approved mockups; scale every label to match them visually.
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * TEXT_SCALE);
        view.setTextColor(color);
        view.setTypeface(medium ? medium() : sans());
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    /**
     * Wraps the view's text only at spaces. API 19 breaks Hangul between any two syllables,
     * so this inserts the line breaks itself whenever the width or the text changes.
     */
    public static void keepAll(final TextView view) {
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            private String source = "";
            private String wrapped = "";
            private int width = -1;

            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                String current = view.getText().toString();
                if (!current.equals(wrapped)) source = current;
                int available = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
                if (available <= 0 || (available == width && current.equals(wrapped))) return;
                width = available;
                wrapped = wrapAtSpaces(source, view, available);
                if (!wrapped.equals(current)) {
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setText(wrapped);
                        }
                    });
                }
            }
        });
    }

    static String wrapAtSpaces(String text, TextView view, int width) {
        StringBuilder out = new StringBuilder();
        String line = "";
        for (String word : text.replace('\n', ' ').split(" ")) {
            if (word.length() == 0) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() == 0 || view.getPaint().measureText(candidate) <= width) {
                line = candidate;
            } else {
                out.append(line).append('\n');
                line = word;
            }
        }
        return out.append(line).toString();
    }

    public static GradientDrawable shape(Context context, int fill, int border, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (border != Color.TRANSPARENT) drawable.setStroke(dp(context, 1), border);
        return drawable;
    }

    public static StateListDrawable selectable(Context context, int fill, int border, int radiusDp,
            boolean destructive) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[] {android.R.attr.state_pressed},
                shape(context, destructive ? RED_PRESS : PRESS, destructive ? ERROR : BLUE, radiusDp));
        drawable.addState(new int[] {}, shape(context, fill, border, radiusDp));
        return drawable;
    }

    public static TextView button(Context context, CharSequence label, boolean primary, boolean destructive) {
        TextView button = text(context, label, 21, destructive ? ERROR : WHITE, true);
        SettingsUi.button(context, button, selectable(context,
                primary ? BLUE : RAISED, primary ? BLUE : destructive ? ERROR : HAIRLINE, BUTTON_RADIUS, destructive));
        return button;
    }

    public static View hairline(Context context) {
        View line = new View(context);
        line.setBackgroundColor(HAIRLINE);
        return line;
    }

    public static void addHairline(Context context, LinearLayout parent, boolean vertical) {
        parent.addView(hairline(context), new LinearLayout.LayoutParams(
                vertical ? dp(context, 1) : -1, vertical ? -1 : dp(context, 1)));
    }

    /** Korean object particle for a name; empty for other languages (format strings keep a %2$s slot). */
    public static String objectParticle(java.util.Locale locale, String name) {
        if (locale == null || !"ko".equals(locale.getLanguage()) || name == null || name.length() == 0) return "";
        char last = name.charAt(name.length() - 1);
        if (last >= 0xAC00 && last <= 0xD7A3) return (last - 0xAC00) % 28 == 0 ? "\uB97C" : "\uC744";
        return "\uB97C"; // Latin or digit endings in our names read as vowels (ADB, CarPlay, Wi-Fi, 5).
    }
}
