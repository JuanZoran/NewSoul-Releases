package dev.local.newsoul;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wraps short keyword chips; full-width phrases remain readable at any font scale. */
final class KeywordFlowLayout extends ViewGroup {
    private final int gap;
    KeywordFlowLayout(Context context) {
        super(context);
        gap = SettingsUi.dp(context, 8);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int requested = child.getLayoutParams().width;
            int childWidth = requested == LayoutParams.MATCH_PARENT ? width : Math.min(width, requested);
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + childWidth > width) { y += rowHeight + gap; x = 0; rowHeight = 0; }
            x += childWidth + gap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(width, resolveSize(y + rowHeight, heightSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, x = 0, y = 0, rowHeight = 0;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            if (x > 0 && x + w > width) { y += rowHeight + gap; x = 0; rowHeight = 0; }
            int start = rtl ? width - x - w : x;
            child.layout(start, y, start + w, y + h);
            x += w + gap; rowHeight = Math.max(rowHeight, h);
        }
    }
    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
}
