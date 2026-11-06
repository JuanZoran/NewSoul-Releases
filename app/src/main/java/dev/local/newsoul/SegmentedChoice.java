package dev.local.newsoul;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** A shared single-choice control; persistence and feature callbacks belong to the caller. */
final class SegmentedChoice extends FrameLayout {
    private final GlassTabTrack track;
    private final LinearLayout row;
    private final TextView[] options;
    private int selected = -1;
    private float lensLeft, lensWidth;
    private ValueAnimator animator;
    private IntConsumer listener;

    SegmentedChoice(SettingsUi.MenuPanel panel, TextView heading, String[] names, int textSize) {
        this(panel, heading, names, textSize, false);
    }

    SegmentedChoice(SettingsUi.MenuPanel panel, TextView heading, String[] names, int textSize,
            boolean compact) {
        super(panel.context);
        track = new GlassTabTrack(panel.context, panel.appearance, true);
        int inset = SettingsUi.dp(panel.context, 4);
        track.setPadding(inset, compact ? 0 : inset, inset, compact ? 0 : inset);
        if (compact) track.setVerticalPaintInset(SettingsUi.dp(panel.context, 6));
        addView(track, new FrameLayout.LayoutParams(compact ? -2 : -1, -2));
        row = new LinearLayout(panel.context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        track.addView(row, new FrameLayout.LayoutParams(compact ? -2 : -1, -2));
        SettingsUi.singleChoiceGroup(row, heading, names.length);
        options = new TextView[names.length];
        ColorStateList colors = new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{panel.accentText, panel.foreground});
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView option = SettingsUi.label(panel.context, names[i], textSize, panel.foreground);
            options[i] = option;
            option.setGravity(Gravity.CENTER);
            option.setTextColor(colors);
            // Wrap at larger font sizes. Never shrink or ellipsize a choice label.
            option.setPadding(SettingsUi.dp(panel.context, compact ? 6 : 14), SettingsUi.dp(panel.context, 10),
                    SettingsUi.dp(panel.context, compact ? 6 : 14), SettingsUi.dp(panel.context, 10));
            option.setMinHeight(SettingsUi.dp(panel.context, 48));
            panel.appearance.applySegmentedFeedback(option);
            if (compact) {
                int feedbackInset = SettingsUi.dp(panel.context, 6);
                option.setForeground(new android.graphics.drawable.InsetDrawable(
                        option.getForeground(), 0, feedbackInset, 0, feedbackInset));
            }
            SettingsUi.singleChoiceOption(option, i);
            option.setOnClickListener(v -> {
                if (listener != null) listener.accept(index);
            });
            row.addView(option, compact ? new LinearLayout.LayoutParams(-2, -1)
                    : new LinearLayout.LayoutParams(0, -1, 1));
        }
    }

    void onSelection(IntConsumer listener) { this.listener = listener; }

    void selectValue(String value, String[] values, boolean animate) {
        int index = -1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) { index = i; break; }
        if (index == selected) return;
        selected = index;
        for (int i = 0; i < options.length; i++) options[i].setSelected(i == selected);
        moveLens(animate);
    }

    private void cancelAnimation() {
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
    }

    private void moveLens(boolean animate) {
        cancelAnimation();
        float endLeft = selected < 0 ? 0 : track.getPaddingLeft() + options[selected].getLeft();
        float endWidth = selected < 0 ? 0 : options[selected].getWidth();
        if (!animate || !isAttachedToWindow() || !ValueAnimator.areAnimatorsEnabled()
                || lensWidth <= 0 || endWidth <= 0) {
            lensLeft = endLeft; lensWidth = endWidth;
            track.selection(lensLeft, lensWidth, false);
            return;
        }
        float startLeft = lensLeft, startWidth = lensWidth;
        ValueAnimator next = ValueAnimator.ofFloat(0, 1);
        animator = next;
        next.setDuration(200);
        next.setInterpolator(new DecelerateInterpolator(1.5f));
        next.addUpdateListener(value -> {
            if (animator != value) return;
            float progress = (float) value.getAnimatedValue();
            lensLeft = startLeft + (endLeft - startLeft) * progress;
            lensWidth = startWidth + (endWidth - startWidth) * progress;
            track.selection(lensLeft, lensWidth, true);
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                track.selection(lensLeft, lensWidth, false);
            }
        });
        next.start();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (animator == null || changed) moveLens(false);
    }

    @Override protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }
}
