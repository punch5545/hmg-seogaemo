package com.seogaemo.hmg.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.seogaemo.hmg.R;
import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.HomeTileCatalog;
import com.seogaemo.hmg.module.ModuleAction;
import java.util.List;

/** Portrait card Home pager for built-in and module shortcuts. */
public final class HomeView extends LinearLayout {
    public interface ActionHandler { void run(ModuleAction action); }
    private static final int WHITE = 0xFFF5F5F5;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int BLUE = 0xFF0070F3;
    private static final int RED = 0xFFEE0000;
    private final Pager pager;
    private final LinearLayout dots;
    private final TextView counter;
    private final TextView previous;
    private final TextView next;
    private final Runnable openSettings;
    private final ActionHandler actionHandler;
    private int pageCount;

    public HomeView(Context context, Runnable openSettings, ActionHandler actionHandler) {
        super(context);
        this.openSettings = openSettings;
        this.actionHandler = actionHandler;
        setOrientation(VERTICAL);
        setPadding(dp(32), dp(16), dp(32), dp(12));

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(context.getString(R.string.home_title), 26, WHITE, true);
        heading.addView(title, new LayoutParams(0, dp(46), 1));
        addView(heading, new LayoutParams(-1, dp(52)));

        LinearLayout stage = new LinearLayout(context);
        stage.setGravity(Gravity.CENTER_VERTICAL);
        previous = arrow("‹");
        next = arrow("›");
        stage.addView(previous, new LayoutParams(dp(52), -1));
        pager = new Pager(context);
        LayoutParams pagerParams = new LayoutParams(0, -1, 1);
        pagerParams.leftMargin = dp(6);
        pagerParams.rightMargin = dp(6);
        stage.addView(pager, pagerParams);
        stage.addView(next, new LayoutParams(dp(52), -1));
        addView(stage, new LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER);
        dots = new LinearLayout(context);
        dots.setGravity(Gravity.CENTER);
        footer.addView(dots, new LayoutParams(dp(80), -1));
        counter = text("", 15, MUTED, false);
        counter.setTypeface(Typeface.MONOSPACE);
        footer.addView(counter, new LayoutParams(dp(130), -1));
        addView(footer, new LayoutParams(-1, dp(46)));
        previous.setOnClickListener(new OnClickListener() { public void onClick(View v) { pager.setPage(pager.page - 1); } });
        next.setOnClickListener(new OnClickListener() { public void onClick(View v) { pager.setPage(pager.page + 1); } });
        pager.setPageListener(new Runnable() { public void run() { updateNavigation(); } });
        setTiles(HomeTileCatalog.collect(settingsTile(), new HomeTileCatalog.Entry[0]));
    }

    public static HomeTile settingsTile() {
        return new HomeTile("settings", R.string.settings, "", null, 0, true);
    }

    public void setTiles(List<HomeTile> tiles) {
        pager.clearPages();
        pageCount = HomeTileCatalog.pageCount(tiles.size());
        for (int p = 0; p < pageCount; p++) {
            Context context = getContext();
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(4), dp(2), dp(4));
            for (int i = p * HomeTileCatalog.TILES_PER_PAGE; i < Math.min((p + 1) * HomeTileCatalog.TILES_PER_PAGE, tiles.size()); i++) {
                final HomeTile item = tiles.get(i);
                HomeTileView card = new HomeTileView(context, item);
                if ("settings".equals(item.id)) card.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) { openSettings.run(); }
                });
                else if (item.action != null) card.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) { actionHandler.run(item.action); }
                });
                LayoutParams cardParams = new LayoutParams(0, -1, 1);
                if (i > p * HomeTileCatalog.TILES_PER_PAGE) cardParams.leftMargin = dp(10);
                row.addView(card, cardParams);
            }
            // Preserve card width on partial pages.
            for (int i = tiles.size() - p * HomeTileCatalog.TILES_PER_PAGE; i < HomeTileCatalog.TILES_PER_PAGE && p == pageCount - 1; i++) {
                LayoutParams empty = new LayoutParams(0, -1, 1);
                empty.leftMargin = dp(10);
                row.addView(new View(context), empty);
            }
            pager.addPage(row);
        }
        pager.page = 0;
        pager.post(new Runnable() { public void run() { pager.syncWidths(); } });
        updateNavigation();
    }

    private void updateNavigation() {
        boolean multiple = pageCount > 1;
        dots.setVisibility(multiple ? VISIBLE : GONE);
        counter.setVisibility(multiple ? VISIBLE : GONE);
        previous.setVisibility(multiple ? VISIBLE : GONE);
        next.setVisibility(multiple ? VISIBLE : GONE);
        dots.removeAllViews();
        for (int i = 0; multiple && i < pageCount; i++) {
            final int target = i;
            View dot = new View(getContext());
            dot.setBackgroundDrawable(shape(i == pager.page ? BLUE : 0xFF575757, Color.TRANSPARENT, 20));
            LayoutParams params = new LayoutParams(dp(i == pager.page ? 12 : 8), dp(i == pager.page ? 12 : 8));
            params.leftMargin = dp(8);
            params.rightMargin = dp(8);
            dots.addView(dot, params);
            dot.setOnClickListener(new OnClickListener() { public void onClick(View v) { pager.setPage(target); } });
        }
        counter.setText(getContext().getString(R.string.home_page, pager.page + 1, pageCount));
        previous.setTextColor(pager.page == 0 ? 0xFF555555 : WHITE);
        next.setTextColor(pager.page == pageCount - 1 ? 0xFF555555 : WHITE);
    }

    private TextView arrow(String label) {
        TextView arrow = text(label, 52, WHITE, false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackgroundDrawable(selector(0xFF171717, 0xFF343434, false));
        return arrow;
    }

    private TextView text(String value, int sp, int color, boolean medium) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private GradientDrawable shape(int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) d.setStroke(dp(1), border);
        return d;
    }

    private StateListDrawable selector(int fill, int border, boolean destructive) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_pressed}, shape(destructive ? 0xFF633030 : 0xFF294563,
                destructive ? RED : BLUE, 8));
        d.addState(new int[] {}, shape(fill, border, 8));
        return d;
    }

    private final class HomeTileView extends FrameLayout {
        HomeTileView(Context c, HomeTile item) {
            super(c);
            int accent = "settings".equals(item.id) ? BLUE : 0xFFB7C6D7;
            setBackgroundDrawable(selector(0xFF202020, 0xFF3B3B3B, false));
            setContentDescription(c.getString(item.labelResId));

            LayoutParams gp = new LayoutParams(dp(104), dp(104), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            gp.topMargin = dp(62);
            if ("settings".equals(item.id)) {
                ImageView icon = new ImageView(c);
                icon.setBackgroundDrawable(shape(0xFF171C22, 0xFF354352, 10));
                icon.setScaleType(ImageView.ScaleType.CENTER);
                icon.setImageDrawable(new GearDrawable(accent));
                addView(icon, gp);
            } else {
                TextView glyph = text(item.glyph, item.glyph.length() > 3 ? 32 : 46, accent, true);
                glyph.setGravity(Gravity.CENTER);
                glyph.setSingleLine(true);
                glyph.setBackgroundDrawable(shape(0xFF171C22, 0xFF354352, 10));
                addView(glyph, gp);
            }

            View rule = new View(c);
            rule.setBackgroundColor(accent);
            LayoutParams rp = new LayoutParams(dp(36), dp(3), Gravity.BOTTOM | Gravity.LEFT);
            rp.leftMargin = dp(17);
            rp.bottomMargin = dp(82);
            addView(rule, rp);

            TextView label = text(c.getString(item.labelResId), 21, WHITE, true);
            label.setGravity(Gravity.TOP | Gravity.LEFT);
            label.setMaxLines(2);
            LayoutParams lp = new LayoutParams(-1, dp(67), Gravity.BOTTOM);
            lp.leftMargin = dp(17);
            lp.rightMargin = dp(12);
            lp.bottomMargin = dp(11);
            addView(label, lp);
        }
    }

    /** Font-independent settings gear, drawn in the tile icon's bounds. */
    private final class GearDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF bounds = new RectF();
        private final int color;

        GearDrawable(int color) { this.color = color; paint.setColor(color); paint.setStyle(Paint.Style.FILL); }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float size = Math.min(b.width(), b.height()) * 0.48f;
            float left = b.exactCenterX() - size / 2f;
            float top = b.exactCenterY() - size / 2f;
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(size / 100f, size / 100f);
            path.reset();
            // Eight teeth: two outer points per tooth, two root points per gap.
            for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2d * (i - 0.5d) / 32d - Math.PI / 2d;
                float radius = (i % 4 == 0 || i % 4 == 1) ? 49f : 38f;
                float x = 50f + (float) Math.cos(angle) * radius;
                float y = 50f + (float) Math.sin(angle) * radius;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            canvas.drawPath(path, paint);
            bounds.set(34f, 34f, 66f, 66f);
            paint.setColor(0xFF171C22);
            canvas.drawOval(bounds, paint);
            paint.setColor(color);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return dp(64); }
        @Override public int getIntrinsicHeight() { return dp(64); }
    }

    /** Equal-width pages with swipe settling, based on the NEMESIS interaction pattern. */
    private final class Pager extends HorizontalScrollView {
        private final LinearLayout strip;
        private Runnable listener;
        private int page;
        private float downX;

        Pager(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setFillViewport(true);
            strip = new LinearLayout(c);
            strip.setOrientation(HORIZONTAL);
            addView(strip, new FrameLayout.LayoutParams(-2, -1));
        }

        void clearPages() { strip.removeAllViews(); }

        void addPage(View row) {
            int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
            strip.addView(row, new LayoutParams(width, -1));
        }
        void syncWidths() {
            int width = getWidth();
            if (width <= 0) return;
            for (int i = 0; i < strip.getChildCount(); i++) strip.getChildAt(i).getLayoutParams().width = width;
            strip.requestLayout();
            scrollTo(page * width, 0);
        }
        void setPageListener(Runnable callback) { listener = callback; }
        void setPage(int value) {
            page = Math.max(0, Math.min(pageCount - 1, value));
            smoothScrollTo(page * getWidth(), 0);
            if (listener != null) listener.run();
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            syncWidths();
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) downX = e.getX();
            return super.onInterceptTouchEvent(e);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) downX = e.getX();
            boolean result = super.onTouchEvent(e);
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float distance = e.getX() - downX;
                if (Math.abs(distance) > getWidth() / 5f) setPage(page + (distance < 0 ? 1 : -1));
                else setPage(page);
            }
            return result;
        }
    }
}
