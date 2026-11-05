package com.seogaemo.hmg.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.seogaemo.hmg.ui.settings.SettingsUi;
import java.util.ArrayList;
import java.util.List;

/** Modal progress after a confirmation: one check row per planned step and a bar below them. */
public final class StepProgressDialog extends Dialog {
    public static final int PENDING = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    private final List<LinearLayout> rows = new ArrayList<LinearLayout>();
    private final List<TextView> labels = new ArrayList<TextView>();
    private final int[] states;
    private final Bar bar;

    private StepProgressDialog(Activity activity, String title, List<String> steps) {
        super(activity, android.R.style.Theme_Holo_Dialog);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(false);
        setCancelable(false);
        states = new int[steps.size()];
        bar = new Bar(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int page = dp(activity, SettingsUi.PAGE);
        panel.setPadding(page, dp(activity, SettingsUi.LG), page, dp(activity, SettingsUi.LG));
        panel.setBackgroundDrawable(Theme.shape(activity, Theme.SURFACE, Theme.HAIRLINE, Theme.CARD_RADIUS));
        panel.addView(Theme.text(activity, title, 26, Theme.WHITE, true), new LinearLayout.LayoutParams(-1, -2));
        for (String step : steps) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = Theme.text(activity, step, 20, Theme.MUTED, false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(activity, 44));
            rowParams.topMargin = dp(activity, SettingsUi.XS);
            panel.addView(row, rowParams);
            rows.add(row);
            labels.add(label);
            setStep(rows.size() - 1, PENDING);
        }
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(activity, 8));
        barParams.topMargin = dp(activity, SettingsUi.LG);
        panel.addView(bar, barParams);
        setContentView(panel);
    }

    public static StepProgressDialog show(Activity activity, String title, List<String> steps) {
        if (activity.isFinishing()) return null;
        StepProgressDialog dialog = new StepProgressDialog(activity, title, steps);
        dialog.show();
        Window window = dialog.getWindow();
        window.setLayout(dp(activity, 760), WindowManager.LayoutParams.WRAP_CONTENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.75f;
        window.setAttributes(attributes);
        return dialog;
    }

    public void setStep(int index, int state) {
        if (index < 0 || index >= rows.size()) return;
        Context context = getContext();
        LinearLayout row = rows.get(index);
        TextView label = labels.get(index);
        row.removeAllViews();
        View marker;
        int color = state == DONE ? Theme.BLUE : state == FAILED ? Theme.ERROR
                : state == RUNNING ? Theme.WHITE : Theme.MUTED;
        if (state == RUNNING) {
            ProgressBar spinner = new ProgressBar(context);
            spinner.setIndeterminate(true);
            marker = spinner;
        } else {
            ImageView glyph = new ImageView(context);
            glyph.setScaleType(ImageView.ScaleType.CENTER);
            glyph.setImageDrawable(new LineIcon(context, state == DONE ? "check" : state == FAILED ? "plus" : "button",
                    color, state == PENDING ? 16 : 22));
            if (state == FAILED) glyph.setRotation(45f);
            marker = glyph;
        }
        row.addView(marker, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));
        label.setTextColor(state == DONE ? Theme.WHITE : color);
        label.setTypeface(state == RUNNING ? Theme.medium() : Theme.sans());
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(context, SettingsUi.SM);
        row.addView(label, labelParams);
        states[index] = state;
        int done = 0;
        for (int value : states) if (value == DONE) done++;
        bar.setFraction(states.length == 0 ? 1f : done / (float) states.length);
    }

    public void finishAll() { bar.setFraction(1f); }

    private static int dp(Context context, int value) { return Theme.dp(context, value); }

    private static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float fraction;

        Bar(Context context) { super(context); }

        void setFraction(float value) {
            fraction = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = getHeight() / 2f;
            paint.setColor(Theme.HAIRLINE);
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setColor(Theme.BLUE);
            rect.set(0, 0, getWidth() * fraction, getHeight());
            if (rect.width() > 0) canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
