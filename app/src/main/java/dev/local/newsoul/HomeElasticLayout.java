package dev.local.newsoul;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fill the viewport by expanding only selected group gaps; overflow keeps its natural height. */
final class HomeElasticLayout extends LinearLayout {
    private final Map<View, Integer> gaps = new LinkedHashMap<>();
    private final int inset, maxWidth;

    HomeElasticLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
        inset = SettingsUi.dp(context, 20);
        maxWidth = SettingsUi.dp(context, 480);
        setPadding(inset, SettingsUi.dp(context, 22), inset, SettingsUi.dp(context, 18));
    }

    void expandBefore(View child) {
        gaps.put(child, ((LayoutParams) child.getLayoutParams()).topMargin);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int horizontal = inset + Math.max(0, MeasureSpec.getSize(widthSpec) - maxWidth) / 2;
        if (getPaddingLeft() != horizontal)
            setPadding(horizontal, getPaddingTop(), horizontal, getPaddingBottom());
        for (Map.Entry<View, Integer> gap : gaps.entrySet())
            ((LayoutParams) gap.getKey().getLayoutParams()).topMargin = gap.getValue();
        // ScrollView first measures natural height, then supplies an exact viewport if it fits.
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int natural = getMeasuredHeight();
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.EXACTLY || gaps.isEmpty()) return;
        int extra = Math.max(0, MeasureSpec.getSize(heightSpec) - natural);
        int count = 0;
        for (View child : gaps.keySet()) if (child.getVisibility() != GONE) count++;
        int remaining = count;
        for (Map.Entry<View, Integer> gap : gaps.entrySet()) {
            if (gap.getKey().getVisibility() == GONE) continue;
            int share = remaining == 0 ? 0 : extra / remaining;
            ((LayoutParams) gap.getKey().getLayoutParams()).topMargin = gap.getValue() + share;
            extra -= share;
            remaining--;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
