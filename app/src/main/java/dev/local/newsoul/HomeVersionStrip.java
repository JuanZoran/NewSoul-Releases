package dev.local.newsoul;

import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Objects;

/** Scroll all supported versions; frame up to four around the installed version initially. */
final class HomeVersionStrip extends LinearLayout {
    private final HomeViews views;
    private final String[] versions;
    private final TextView[] chips;
    private final TextView before, after;
    private final HorizontalScrollView scroll;
    private final LinearLayout track;
    private String selected;
    private int selectedIndex = -1, lastWidth = -1, itemWidth, visible;
    private boolean needsAnchor = true;

    HomeVersionStrip(HomeViews views, String[] values) {
        super(views.activity);
        this.views = views;
        versions = values.clone();
        chips = new TextView[versions.length];
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        before = hint("左侧还有适配版本，可向右滑动查看");
        after = hint("右侧还有适配版本，可向左滑动查看");
        addView(before, new LayoutParams(views.dp(18), -2));
        scroll = new HorizontalScrollView(views.activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        addView(scroll, new LayoutParams(0, -2, 1));
        addView(after, new LayoutParams(views.dp(18), -2));
        track = new LinearLayout(views.activity);
        track.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(track, new HorizontalScrollView.LayoutParams(-2, -2));
        for (int i = 0; i < versions.length; i++) {
            TextView chip = views.text(versions[i], 16, views.foreground, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(views.dp(44));
            chip.setPadding(views.dp(8), views.dp(10), views.dp(8), views.dp(10));
            views.appearance.homeSurface(chip, false, false);
            LayoutParams params = new LayoutParams(-2, -2);
            if (i > 0) params.leftMargin = views.dp(8);
            track.addView(chip, params);
            chips[i] = chip;
        }
        if (versions.length <= 4) { before.setVisibility(GONE); after.setVisibility(GONE); }
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutVersions());
        track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateHints());
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateHints());
    }
    private TextView hint(String description) {
        TextView label = views.text("…", 16, views.secondary, false);
        label.setGravity(Gravity.CENTER);
        label.setContentDescription(description);
        return label;
    }
    void select(String version) {
        if (!Objects.equals(selected, version)) needsAnchor = true;
        selected = version;
        selectedIndex = -1;
        for (int i = 0; i < chips.length; i++) {
            boolean match = versions[i].equals(version);
            if (match) selectedIndex = i;
            chips[i].setTextColor(match ? 0xff00bf62 : views.foreground);
            chips[i].setContentDescription(versions[i] + (match ? "，已安装，适配版本" : ""));
            views.appearance.homeSurface(chips[i], false, match);
        }
        post(this::layoutVersions);
    }
    private void layoutVersions() {
        int width = scroll.getWidth();
        if (width <= 0 || versions.length == 0) return;
        if (width != lastWidth) {
            lastWidth = width;
            int gap = views.dp(8), minimum = 0;
            for (int i = 0; i < chips.length; i++)
                minimum = Math.max(minimum, (int) Math.ceil(chips[i].getPaint().measureText(versions[i]))
                        + views.dp(16));
            visible = Math.min(versions.length, Math.max(1, Math.min(4, (width + gap) / (minimum + gap))));
            itemWidth = (width - gap * (visible - 1)) / visible;
            for (TextView chip : chips) {
                LayoutParams params = (LayoutParams) chip.getLayoutParams();
                params.width = itemWidth;
                chip.setLayoutParams(params);
            }
            needsAnchor = true;
        }
        if (needsAnchor) {
            needsAnchor = false;
            int target = HomeCheck.windowStart(versions.length, selectedIndex, visible)
                    * (itemWidth + views.dp(8));
            scroll.post(() -> { scroll.scrollTo(target, 0); updateHints(); });
        }
    }
    private void updateHints() {
        if (versions.length <= 4) return;
        before.setVisibility(scroll.getScrollX() > 1 ? VISIBLE : INVISIBLE);
        after.setVisibility(scroll.getScrollX() + scroll.getWidth() < track.getWidth() - 1
                ? VISIBLE : INVISIBLE);
    }
}
