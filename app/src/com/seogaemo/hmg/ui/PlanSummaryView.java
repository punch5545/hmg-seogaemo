package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.seogaemo.hmg.ui.settings.SettingsUi;

/** Confirm-dialog body: the planned changes in order as icons, then the result line and warnings. */
public final class PlanSummaryView extends LinearLayout {
    private final LinearLayout steps;
    private int stepCount;

    public PlanSummaryView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        steps = new LinearLayout(context);
        steps.setOrientation(HORIZONTAL);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams stepsParams = new LayoutParams(-1, -2);
        stepsParams.topMargin = dp(SettingsUi.MD);
        addView(steps, stepsParams);
    }

    public void addStep(String icon, String name, String action, int actionColor) {
        Context context = getContext();
        if (stepCount > 0) {
            TextView arrow = Theme.text(context, "\u2192", 26, Theme.MUTED, false);
            arrow.setGravity(Gravity.CENTER);
            steps.addView(arrow, new LayoutParams(dp(40), -2));
        }
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView glyph = new ImageView(context);
        glyph.setScaleType(ImageView.ScaleType.CENTER);
        glyph.setImageDrawable(new LineIcon(context, icon, actionColor == Theme.BLUE ? Theme.BLUE : Theme.WHITE, 32));
        glyph.setBackgroundDrawable(Theme.shape(context, Theme.RAISED, Color.TRANSPARENT, Theme.CARD_RADIUS));
        item.addView(glyph, new LayoutParams(dp(56), dp(56)));
        TextView title = Theme.text(context, name, 17, Theme.MUTED, false);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setMaxLines(2);
        Theme.keepAll(title);
        LayoutParams titleParams = new LayoutParams(-1, -2);
        titleParams.topMargin = dp(SettingsUi.SM);
        item.addView(title, titleParams);
        TextView verb = Theme.text(context, action, 18, actionColor, true);
        verb.setGravity(Gravity.CENTER_HORIZONTAL);
        item.addView(verb, new LayoutParams(-1, -2));
        steps.addView(item, new LayoutParams(0, -2, 1));
        stepCount++;
    }

    public void addLine(CharSequence text, int color) {
        TextView line = Theme.text(getContext(), text, 19, color, false);
        line.setLineSpacing(dp(4), 1f);
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = dp(stepCount > 0 || getChildCount() > 1 ? SettingsUi.MD : SettingsUi.XS);
        addView(line, params);
    }

    public int stepCount() { return stepCount; }

    private int dp(int value) { return Theme.dp(getContext(), value); }
}
