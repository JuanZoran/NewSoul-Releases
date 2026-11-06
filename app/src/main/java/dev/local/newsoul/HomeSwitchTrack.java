package dev.local.newsoul;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Same 40x22dp switch geometry, animated without changing the native control's hit target. */
final class HomeSwitchTrack extends Drawable implements View.OnAttachStateChangeListener {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator colors = new ArgbEvaluator();
    private final float density;
    private final int off, on;
    private float progress, target;
    private int alpha = 255;
    private boolean attached;
    private ValueAnimator animator;

    HomeSwitchTrack(float density, int off, int on) {
        this.density = density; this.off = off; this.on = on;
    }
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] state) {
        float next = 0;
        for (int item : state) if (item == android.R.attr.state_checked) next = 1;
        if (next == target) return false;
        target = next;
        if (animator != null) { animator.cancel(); animator = null; }
        if (!attached || !isVisible() || !ValueAnimator.areAnimatorsEnabled()) {
            progress = target;
            invalidateSelf();
            return true;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(180);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
        return true;
    }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 40f, getBounds().height() / 22f);
        paint.setColor((int) colors.evaluate(progress, off, on));
        paint.setAlpha(alpha);
        canvas.drawRoundRect(0, 0, 40, 22, 11, 11, paint);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setAlpha(alpha);
        canvas.drawCircle(11 + 18 * progress, 11, 9, paint);
        canvas.restoreToCount(save);
    }
    @Override public void jumpToCurrentState() {
        if (animator != null) { animator.cancel(); animator = null; }
        progress = target;
        invalidateSelf();
    }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) jumpToCurrentState();
        return changed;
    }
    @Override public void onViewAttachedToWindow(View view) { attached = true; jumpToCurrentState(); }
    @Override public void onViewDetachedFromWindow(View view) { attached = false; jumpToCurrentState(); }
    @Override public int getIntrinsicWidth() { return Math.round(40 * density); }
    @Override public int getIntrinsicHeight() { return Math.round(22 * density); }
    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
