package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Black-on-white QR code with the standard four-module quiet zone, filling the view. Module edges are
 * rounded to whole pixels without anti-aliasing, so modules differ by at most one pixel.
 */
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
        float scale = side / (float) cells;
        int left = (getWidth() - side) / 2;
        int top = (getHeight() - side) / 2;
        canvas.drawColor(Color.WHITE);
        for (int y = 0; y < code.size; y++)
            for (int x = 0; x < code.size; x++)
                if (code.get(x, y)) {
                    int x0 = left + Math.round((x + QUIET) * scale), x1 = left + Math.round((x + QUIET + 1) * scale);
                    int y0 = top + Math.round((y + QUIET) * scale), y1 = top + Math.round((y + QUIET + 1) * scale);
                    canvas.drawRect(x0, y0, x1, y1, dark);
                }
    }
}
