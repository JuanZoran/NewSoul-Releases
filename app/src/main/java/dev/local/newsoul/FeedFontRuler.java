package dev.local.newsoul;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import java.util.function.BiConsumer;

/** Horizontal detents with a fixed reading pointer; vertical gestures remain page scrolling. */
final class FeedFontRuler extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SettingsUi.MenuPanel panel;
    private final BiConsumer<String, Boolean> changed;
    private float position, downX, downY, initial;
    private boolean dragging;
    private int last;
    FeedFontRuler(SettingsUi.MenuPanel panel, BiConsumer<String, Boolean> changed) {
        super(panel.context); this.panel = panel; this.changed = changed;
        setMinimumHeight(SettingsUi.dp(getContext(), 48)); setFocusable(true); setClickable(true);
        setContentDescription("字体大小，左右滑动调节");
    }
    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    private String value() { return last == 0 ? "default" : Integer.toString(last + 7); }
    void select(String value) {
        int size = FeedLabelStyle.size(value);
        last = size == 0 ? 0 : size - 7;
        position = last; invalidate();
        if (android.os.Build.VERSION.SDK_INT >= 30) setStateDescription(last == 0 ? "默认" : value + " sp");
    }
    private void move(float next, boolean commit) {
        position = Math.max(0, Math.min(17, next));
        int selected = Math.round(position);
        boolean different = selected != last; last = selected;
        if (android.os.Build.VERSION.SDK_INT >= 30) setStateDescription(last == 0 ? "默认" : value() + " sp");
        if (different || commit) changed.accept(value(), commit);
        invalidate();
    }
    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(SettingsUi.dp(getContext(), 200), w),
                resolveSize(SettingsUi.dp(getContext(), 64), h));
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float center = getWidth() / 2f, step = dp(22), baseline = getHeight() - dp(10);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(12));
        for (int i = 0; i <= 17; i++) {
            float x = center + (i - position) * step;
            if (x < dp(5) || x > getWidth() - dp(5)) continue;
            paint.setColor(panel.secondary); paint.setStrokeWidth(dp(1));
            paint.setAlpha((int) (255 * Math.min(1, Math.min(x, getWidth() - x) / dp(30))));
            c.drawLine(x, baseline - dp(i % 2 == 0 ? 13 : 8), x, baseline, paint);
        }
        paint.setAlpha(255); paint.setColor(panel.accentText); paint.setStrokeWidth(dp(2));
        c.drawLine(center, baseline - dp(18), center, baseline + dp(3), paint);
        paint.setColor(panel.foreground); paint.setTextSize(dp(14));
        c.drawText(last == 0 ? "默认" : value() + " sp", center, dp(22), paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=e.getX(); downY=e.getY(); initial=position; dragging=false; return true;
            case MotionEvent.ACTION_MOVE:
                float dx=e.getX()-downX, dy=e.getY()-downY;
                if (!dragging && Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop()
                        && Math.abs(dx)>Math.abs(dy)) {
                    dragging=true; getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) move(initial-dx/dp(22), false);
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) { performClick(); move(position+(e.getX()-getWidth()/2f)/dp(22), false); }
                move(Math.round(position), true); getParent().requestDisallowInterceptTouchEvent(false); return true;
            case MotionEvent.ACTION_CANCEL:
                move(initial, true); getParent().requestDisallowInterceptTouchEvent(false); return true;
        }
        return super.onTouchEvent(e);
    }
    // Tell MenuPager this horizontal gesture belongs to the ruler, including at the endpoints.
    @Override public boolean canScrollHorizontally(int direction) { return true; }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,17,last));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { move(last+1,true); return true; }
        if (action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { move(last-1,true); return true; }
        if (action==android.R.id.accessibilityActionSetProgress && args!=null) {
            move(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)),true); return true;
        }
        return super.performAccessibilityAction(action,args);
    }
}
