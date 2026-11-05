package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.seogaemo.hmg.R;
import com.seogaemo.hmg.ui.settings.SettingsUi;

/** The app's fixed row components for a detail screen's option list (ui.txt blocks and built-in items). */
public final class OptionsList extends LinearLayout {
    public OptionsList(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void addSection(CharSequence title) {
        TextView view = Theme.text(getContext(), title, 17, Theme.MUTED, false);
        view.setPadding(0, dp(SettingsUi.MD), 0, dp(SettingsUi.XS));
        addView(view, new LayoutParams(-1, -2));
    }

    public void addValue(CharSequence label, CharSequence value, boolean chevron, OnClickListener tap) {
        Context context = getContext();
        LinearLayout row = row(tap);
        row.addView(Theme.text(context, label, 21, Theme.WHITE, true), new LayoutParams(0, -1, 1));
        if (value != null && value.length() > 0) {
            TextView trailing = Theme.text(context, value, 19, Theme.MUTED, false);
            trailing.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            trailing.setSingleLine(true);
            LayoutParams valueParams = new LayoutParams(-2, -1);
            valueParams.leftMargin = dp(SettingsUi.MD);
            row.addView(trailing, valueParams);
        }
        if (chevron) {
            TextView glyph = Theme.text(context, "\u203A", 26, Theme.MUTED, false);
            glyph.setGravity(Gravity.CENTER);
            row.addView(glyph, new LayoutParams(dp(28), -1));
        }
        add(row);
    }

    public void addReadOnly(CharSequence label, CharSequence value) {
        addValue(label, value == null || value.length() == 0 ? "\u2014" : value, false, null);
    }

    public void addLink(CharSequence label, OnClickListener tap) { addValue(label, null, true, tap); }

    public void addInfo(CharSequence text) { addLine(text, Theme.MUTED); }

    public void addWarning(CharSequence text) { addLine(text, Theme.WARNING); }

    public void addAction(CharSequence label, CharSequence buttonLabel, boolean destructive, boolean enabled,
            OnClickListener tap) {
        Context context = getContext();
        LinearLayout row = row(null);
        row.addView(Theme.text(context, label, 21, Theme.WHITE, true), new LayoutParams(0, -1, 1));
        TextView button = Theme.button(context, buttonLabel, false, destructive);
        button.setTextSize(19 * Theme.TEXT_SCALE);
        button.setMinHeight(0);
        button.setPadding(dp(SettingsUi.LG), 0, dp(SettingsUi.LG), 0);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.45f);
        button.setOnClickListener(enabled ? tap : null);
        LayoutParams buttonParams = new LayoutParams(-2, dp(48));
        buttonParams.leftMargin = dp(SettingsUi.MD);
        row.addView(button, buttonParams);
        add(row);
    }

    public void addRemove(CharSequence label, OnClickListener tap) {
        LinearLayout row = row(tap);
        row.addView(Theme.text(getContext(), label, 21, Theme.ERROR, true), new LayoutParams(0, -1, 1));
        add(row);
    }

    public void addEmpty() {
        addLine(getContext().getString(R.string.detail_no_options), Theme.MUTED);
    }

    private void addLine(CharSequence text, int color) {
        TextView view = Theme.text(getContext(), text, 18, color, false);
        view.setLineSpacing(dp(4), 1f);
        view.setPadding(0, dp(SettingsUi.SM), 0, dp(SettingsUi.SM));
        view.setMinHeight(dp(Theme.ROW_HEIGHT));
        addView(view, new LayoutParams(-1, -2));
        Theme.addHairline(getContext(), this, false);
    }

    private LinearLayout row(OnClickListener tap) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (tap != null) {
            row.setBackgroundDrawable(Theme.selectable(getContext(), Theme.CANVAS, Color.TRANSPARENT, 0, false));
            row.setOnClickListener(tap);
        }
        return row;
    }

    private void add(View row) {
        addView(row, new LayoutParams(-1, dp(Theme.ROW_HEIGHT)));
        Theme.addHairline(getContext(), this, false);
    }

    private int dp(int value) { return Theme.dp(getContext(), value); }
}
