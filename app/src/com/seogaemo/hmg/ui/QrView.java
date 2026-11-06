package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Black-on-white QR code with the standard four-module quiet zone, scaled to whole pixels per module. */
public final class QrView extends View {
    private static final int QUIET = 4;
    private final QrCode code;
    private final Paint dark = new Paint();

    public QrView(Context context, QrCode code) {
        super(context);
        this.code = code;
        dark.setColor(Color.BLACK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int side = Math.min(getWidth(), getHeight());
        int cells = code.size + QUIET * 2;
        int scale = Math.max(1, side / cells);
        int left = (getWidth() - cells * scale) / 2;
        int top = (getHeight() - cells * scale) / 2;
        canvas.drawColor(Color.WHITE);
        for (int y = 0; y < code.size; y++)
            for (int x = 0; x < code.size; x++)
                if (code.get(x, y)) {
                    int px = left + (x + QUIET) * scale, py = top + (y + QUIET) * scale;
                    canvas.drawRect(px, py, px + scale, py + scale, dark);
                }
    }
}
