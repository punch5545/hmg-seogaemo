package com.seogaemo.hmg.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.seogaemo.hmg.R;
import com.seogaemo.hmg.ui.settings.SettingsUi;

/** Shown above the Features cards only while a head-unit restart is pending. */
public final class RestartBar extends LinearLayout {
    public RestartBar(Context context, OnClickListener later, OnClickListener restartNow) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = Theme.dp(context, SettingsUi.MD);
        setPadding(pad, 0, pad, 0);
        setBackgroundDrawable(Theme.shape(context, Theme.RAISED, Theme.WARNING, Theme.CARD_RADIUS));

        ImageView glyph = new ImageView(context);
        glyph.setImageDrawable(new LineIcon(context, "restart", Theme.WARNING, 24));
        glyph.setScaleType(ImageView.ScaleType.CENTER);
        addView(glyph, new LayoutParams(Theme.dp(context, 28), Theme.dp(context, 28)));

        TextView text = Theme.text(context, context.getString(R.string.restart_bar_text), 20, Theme.WHITE, true);
        LayoutParams textParams = new LayoutParams(0, -2, 1);
        textParams.leftMargin = Theme.dp(context, SettingsUi.SM);
        addView(text, textParams);

        TextView laterButton = Theme.button(context, context.getString(R.string.restart_later), false, false);
        laterButton.setOnClickListener(later);
        addView(laterButton, new LayoutParams(Theme.dp(context, 150), Theme.dp(context, 52)));

        TextView nowButton = Theme.button(context, context.getString(R.string.restart_now), true, false);
        nowButton.setOnClickListener(restartNow);
        LayoutParams nowParams = new LayoutParams(Theme.dp(context, 190), Theme.dp(context, 52));
        nowParams.leftMargin = Theme.dp(context, SettingsUi.SM);
        addView(nowButton, nowParams);
    }

    public static LayoutParams layoutParams(Context context) {
        LayoutParams params = new LayoutParams(-1, Theme.dp(context, 68));
        params.bottomMargin = Theme.dp(context, SettingsUi.SM);
        return params;
    }
}
