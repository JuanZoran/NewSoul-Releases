package dev.local.newsoul;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Independent checkable keys; the final selected key cannot be released. */
final class FeedContentChoice extends LinearLayout {
    private final SharedPreferences prefs;
    private final Key[] keys = new Key[3];
    FeedContentChoice(SettingsUi.MenuPanel panel, SharedPreferences prefs) {
        super(panel.context); this.prefs = prefs;
        setOrientation(HORIZONTAL);
        String[] names = {"文字", "图文", "视频"};
        for (int i = 0; i < keys.length; i++) {
            final int bit = 1 << i;
            Key key = new Key(panel, names[i]); keys[i] = key;
            LayoutParams lp = new LayoutParams(0, -1, 1);
            if (i > 0) lp.leftMargin = SettingsUi.dp(getContext(), 2);
            addView(key, lp);
            key.setOnClickListener(v -> {
                int current = FeedContentRules.normalize(prefs.getInt(SquareFeedFilter.CONTENT_TYPES, FeedContentRules.ALL));
                int next = FeedContentRules.toggle(current, bit);
                if (next == current) { key.reject(); return; }
                PreferenceChanges.putInt(prefs, SquareFeedFilter.CONTENT_TYPES, next);
                refresh();
            });
        }
        refresh(); panel.addPreferenceRefresher(this::refresh);
    }
    private void refresh() {
        int mask = FeedContentRules.normalize(prefs.getInt(SquareFeedFilter.CONTENT_TYPES, FeedContentRules.ALL));
        for (int i = 0; i < keys.length; i++) keys[i].selection((mask & (1 << i)) != 0);
    }
    private static final class Key extends TextView {
        private final SoftFilterBackground surface;
        private final int accent, normal;
        private ValueAnimator shake;
        private final Runnable reset = () -> { cancelFeedback(); appearance(false); };
        Key(SettingsUi.MenuPanel panel, String name) {
            super(panel.context);
            accent = 0xffb5f5e1; normal = panel.foreground;
            surface = new SoftFilterBackground(panel.appearance.dark, panel.appearance.density);
            setBackground(surface); setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setText(name); setTextSize(15); setGravity(Gravity.CENTER);
            setMinHeight(SettingsUi.dp(getContext(), 58));
            int pad = SettingsUi.dp(getContext(), 10); setPadding(pad, pad, pad, pad);
            setClickable(true); setFocusable(true);
        }
        void selection(boolean selected) {
            cancelFeedback(); setSelected(selected); appearance(false);
        }
        private void appearance(boolean error) {
            surface.state(isSelected() || isPressed(), error);
            setTextColor(error ? 0xffffb4b4 : isSelected() ? accent : normal);
        }
        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (surface != null && shake == null) appearance(false);
        }
        void reject() {
            cancelFeedback(); appearance(true);
            announceForAccessibility("至少保留一种类型");
            if (ValueAnimator.areAnimatorsEnabled()) {
                shake = ValueAnimator.ofFloat(0, -1, 1, -.7f, .7f, 0);
                shake.setDuration(280);
                shake.addUpdateListener(a -> setTranslationX((float)a.getAnimatedValue() * SettingsUi.dp(getContext(), 3)));
                shake.start();
            }
            postDelayed(reset, 450);
        }
        private void cancelFeedback() {
            removeCallbacks(reset);
            if (shake != null) { shake.cancel(); shake = null; }
            setTranslationX(0);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.ToggleButton"); info.setCheckable(true); info.setChecked(isSelected());
        }
        @Override protected void onDetachedFromWindow() {
            cancelFeedback(); appearance(false); super.onDetachedFromWindow();
        }
    }
}
