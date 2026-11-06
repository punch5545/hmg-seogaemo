package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Stroke icons drawn on a 24-unit grid: the contract icon set (box, wifi, phone, terminal, car,
 * gear, display, speaker, camera, map, power, key, skip, button, link, bolt) plus the app's own
 * glyphs (plus, check, restart, download, globe, help, code, undo). No image assets.
 */
public final class LineIcon extends Drawable {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final String name;
    private final int sizePx;
    private float unit;

    public LineIcon(Context context, String name, int color, int sizeDp) {
        this.name = name == null ? "box" : name;
        sizePx = Theme.dp(context, sizeDp);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float size = Math.min(bounds.width(), bounds.height());
        if (size <= 0) return;
        unit = size / 24f;
        stroke.setStrokeWidth(unit * 1.7f);
        canvas.save();
        canvas.translate(bounds.exactCenterX() - size / 2f, bounds.exactCenterY() - size / 2f);
        drawGlyph(canvas);
        canvas.restore();
    }

    private void drawGlyph(Canvas canvas) {
        path.reset();
        if ("wifi".equals(name)) {
            arc(canvas, 12, 19, 12, 225, 90);
            arc(canvas, 12, 19, 8, 225, 90);
            arc(canvas, 12, 19, 4, 225, 90);
            dot(canvas, 12, 19, 1.6f);
        } else if ("phone".equals(name)) {
            roundRect(canvas, 7, 2, 17, 22, 2.5f);
            line(canvas, 10.5f, 18.5f, 13.5f, 18.5f);
        } else if ("terminal".equals(name)) {
            roundRect(canvas, 2, 4, 22, 20, 2);
            poly(canvas, false, 6, 9, 9.5f, 12, 6, 15);
            line(canvas, 12.5f, 15, 17.5f, 15);
        } else if ("car".equals(name)) {
            poly(canvas, true, 2, 16, 2, 11, 5.5f, 6, 18.5f, 6, 22, 11, 22, 16);
            line(canvas, 2, 11, 22, 11);
            circle(canvas, 7, 16.5f, 2.4f);
            circle(canvas, 17, 16.5f, 2.4f);
        } else if ("gear".equals(name)) {
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2d * (i - 0.5d) / 16d - Math.PI / 2d;
                float radius = (i % 2 == 0) ? 10f : 7.4f;
                float x = 12f + (float) Math.cos(angle) * radius;
                float y = 12f + (float) Math.sin(angle) * radius;
                if (i == 0) path.moveTo(x * unit, y * unit); else path.lineTo(x * unit, y * unit);
            }
            path.close();
            canvas.drawPath(path, stroke);
            circle(canvas, 12, 12, 3);
        } else if ("display".equals(name)) {
            roundRect(canvas, 2, 4, 22, 17, 1.5f);
            line(canvas, 12, 17, 12, 21);
            line(canvas, 8, 21, 16, 21);
        } else if ("speaker".equals(name)) {
            poly(canvas, true, 3, 9, 8, 9, 13.5f, 4, 13.5f, 20, 8, 15, 3, 15);
            arc(canvas, 13.5f, 12, 4, -40, 80);
            arc(canvas, 13.5f, 12, 7.5f, -40, 80);
        } else if ("camera".equals(name)) {
            roundRect(canvas, 2, 7, 22, 20, 2);
            poly(canvas, false, 8, 7, 9.5f, 4, 14.5f, 4, 16, 7);
            circle(canvas, 12, 13.5f, 3.5f);
        } else if ("map".equals(name)) {
            poly(canvas, true, 3, 5, 9, 3, 15, 5, 21, 3, 21, 19, 15, 21, 9, 19, 3, 21);
            line(canvas, 9, 3, 9, 19);
            line(canvas, 15, 5, 15, 21);
        } else if ("power".equals(name)) {
            arc(canvas, 12, 13, 8, -60, 300);
            line(canvas, 12, 3, 12, 12);
        } else if ("key".equals(name)) {
            circle(canvas, 8, 12, 4.5f);
            line(canvas, 12.5f, 12, 21, 12);
            line(canvas, 17, 12, 17, 15.5f);
            line(canvas, 20.5f, 12, 20.5f, 15.5f);
        } else if ("skip".equals(name)) {
            poly(canvas, true, 5, 5, 15, 12, 5, 19);
            line(canvas, 18.5f, 5, 18.5f, 19);
        } else if ("button".equals(name)) {
            circle(canvas, 12, 12, 9);
            circle(canvas, 12, 12, 3.5f);
        } else if ("link".equals(name)) {
            roundRect(canvas, 2.5f, 8, 14.5f, 16, 4);
            roundRect(canvas, 9.5f, 8, 21.5f, 16, 4);
        } else if ("bolt".equals(name)) {
            poly(canvas, true, 13, 2, 5, 13, 11, 13, 10, 22, 19, 10, 13, 10);
        } else if ("plus".equals(name)) {
            line(canvas, 12, 4, 12, 20);
            line(canvas, 4, 12, 20, 12);
        } else if ("check".equals(name)) {
            poly(canvas, false, 4, 12.5f, 9.5f, 18, 20, 6.5f);
        } else if ("cross".equals(name)) {
            line(canvas, 6, 6, 18, 18);
            line(canvas, 18, 6, 6, 18);
        } else if ("alert".equals(name)) {
            line(canvas, 12, 4, 12, 14);
            dot(canvas, 12, 19, 1.3f);
        } else if ("record".equals(name)) {
            circle(canvas, 12, 12, 9);
            dot(canvas, 12, 12, 3.5f);
        } else if ("restart".equals(name)) {
            arc(canvas, 12, 12, 8, -30, 300);
            poly(canvas, false, 15, 2, 19, 6, 14.5f, 9);
        } else if ("download".equals(name)) {
            line(canvas, 12, 3, 12, 15);
            poly(canvas, false, 7, 10, 12, 15, 17, 10);
            poly(canvas, false, 4, 15, 4, 21, 20, 21, 20, 15);
        } else if ("globe".equals(name)) {
            circle(canvas, 12, 12, 9);
            oval.set(8 * unit, 3 * unit, 16 * unit, 21 * unit);
            canvas.drawOval(oval, stroke);
            line(canvas, 3, 12, 21, 12);
        } else if ("help".equals(name)) {
            circle(canvas, 12, 12, 9);
            oval.set(9 * unit, 6 * unit, 15 * unit, 12 * unit);
            path.arcTo(oval, 190, 250, true);
            path.lineTo(12 * unit, 14.5f * unit);
            canvas.drawPath(path, stroke);
            dot(canvas, 12, 18, 1.1f);
        } else if ("code".equals(name)) {
            poly(canvas, false, 8, 7, 3, 12, 8, 17);
            poly(canvas, false, 16, 7, 21, 12, 16, 17);
        } else if ("undo".equals(name)) {
            poly(canvas, false, 9, 5, 4, 10, 9, 15);
            path.moveTo(4 * unit, 10 * unit);
            path.lineTo(15 * unit, 10 * unit);
            oval.set(10 * unit, 10 * unit, 20 * unit, 20 * unit);
            path.arcTo(oval, 270, 180, false);
            path.lineTo(9 * unit, 20 * unit);
            canvas.drawPath(path, stroke);
        } else {
            poly(canvas, true, 12, 2, 21, 7, 21, 17, 12, 22, 3, 17, 3, 7);
            poly(canvas, false, 3, 7, 12, 12, 21, 7);
            line(canvas, 12, 12, 12, 22);
        }
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1 * unit, y1 * unit, x2 * unit, y2 * unit, stroke);
    }

    private void poly(Canvas canvas, boolean close, float... points) {
        path.reset();
        path.moveTo(points[0] * unit, points[1] * unit);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i] * unit, points[i + 1] * unit);
        if (close) path.close();
        canvas.drawPath(path, stroke);
    }

    private void arc(Canvas canvas, float cx, float cy, float radius, float start, float sweep) {
        oval.set((cx - radius) * unit, (cy - radius) * unit, (cx + radius) * unit, (cy + radius) * unit);
        canvas.drawArc(oval, start, sweep, false, stroke);
    }

    private void circle(Canvas canvas, float cx, float cy, float radius) {
        canvas.drawCircle(cx * unit, cy * unit, radius * unit, stroke);
    }

    private void dot(Canvas canvas, float cx, float cy, float radius) {
        canvas.drawCircle(cx * unit, cy * unit, radius * unit, fill);
    }

    private void roundRect(Canvas canvas, float l, float t, float r, float b, float radius) {
        oval.set(l * unit, t * unit, r * unit, b * unit);
        canvas.drawRoundRect(oval, radius * unit, radius * unit, stroke);
    }

    @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { stroke.setColorFilter(filter); fill.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return sizePx; }
    @Override public int getIntrinsicHeight() { return sizePx; }
}
