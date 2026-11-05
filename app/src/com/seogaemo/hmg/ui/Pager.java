package com.seogaemo.hmg.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Equal-width pages with swipe settling, the NEMESIS interaction pattern. */
public final class Pager extends HorizontalScrollView {
    private final LinearLayout strip;
    private Runnable listener;
    private int page;
    private float downX;

    public Pager(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        addView(strip, new FrameLayout.LayoutParams(-2, -1));
    }

    public void clearPages() { strip.removeAllViews(); page = 0; }

    public void addPage(View row) {
        int width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        strip.addView(row, new LinearLayout.LayoutParams(width, -1));
    }

    public int pageCount() { return strip.getChildCount(); }

    public int page() { return page; }

    public void syncWidths() {
        int width = getWidth();
        if (width <= 0) return;
        for (int i = 0; i < strip.getChildCount(); i++) strip.getChildAt(i).getLayoutParams().width = width;
        strip.requestLayout();
        scrollTo(page * width, 0);
    }

    public void setPageListener(Runnable callback) { listener = callback; }

    public void setPage(int value) {
        page = Math.max(0, Math.min(Math.max(0, pageCount() - 1), value));
        smoothScrollTo(page * getWidth(), 0);
        if (listener != null) listener.run();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        syncWidths();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) downX = event.getX();
        return super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) downX = event.getX();
        boolean result = super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float distance = event.getX() - downX;
            if (Math.abs(distance) > getWidth() / 5f) setPage(page + (distance < 0 ? 1 : -1));
            else setPage(page);
        }
        return result;
    }
}
