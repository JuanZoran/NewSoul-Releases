package dev.local.newsoul;

import android.content.Context;
import android.graphics.*;
import android.animation.ValueAnimator;
import android.widget.FrameLayout;

/** Shared glass track for navigation and segmented settings; the real text Views own all input/accessibility. */
final class GlassTabTrack extends FrameLayout {
    private final boolean dark;
    private final boolean compact;
    private final UiAppearance appearance;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF lens = new RectF(), outer = new RectF();
    private final Path clip = new Path();
    private final float density;
    private final Shader trackFill, trackRim;
    private final Matrix trackMatrix = new Matrix();
    private float left, width, pulse;
    private float lensAlpha = 1;
    private float verticalPaintInset;
    private final GlassLensRenderer renderer = new GlassLensRenderer();
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassLensRenderer.Source source = this::recordBackdrop;
    private ValueAnimator shapeAnimator;
    private boolean expanding;
    private boolean frostedOutline;
    private boolean actionCapsule;

    void useActionCapsule() { actionCapsule = true; invalidate(); }

    boolean isFrostedOutline() { return frostedOutline; }

    void setFrostedOutline(boolean enabled) {
        if (frostedOutline == enabled) return;
        frostedOutline = enabled;
        cancelShapeAnimator();
        pulse = 0;
        expanding = false;
        renderer.release();
        invalidate();
    }

    GlassTabTrack(Context context, UiAppearance appearance) {
        this(context, appearance, false);
    }

    GlassTabTrack(Context context, UiAppearance appearance, boolean compact) {
        super(context);
        this.compact = compact;
        this.appearance = appearance;
        this.dark = appearance.dark;
        density = getResources().getDisplayMetrics().density;
        trackFill = new LinearGradient(0, 0, 0, 1,
                appearance.style == UiAppearance.Style.ORIGINAL_GLASS_TABS ? appearance.tabTrackTop() : dark ? 0x107f80b0 : 0x0861689a,
                appearance.style == UiAppearance.Style.ORIGINAL_GLASS_TABS ? appearance.tabTrackBottom() : dark ? 0x047f80b0 : 0x1061689a, Shader.TileMode.CLAMP);
        trackRim = new LinearGradient(0, 0, 1, 1,
                dark ? (appearance.style == UiAppearance.Style.ORIGINAL_GLASS_TABS
                        ? new int[]{0x24ffffff, 0x04ffffff, 0x12ffffff}
                        : new int[]{0x50ffffff, 0x0affffff, 0x26ffffff})
                        : new int[]{0xb3ffffff, 0x0c55555d, 0x1855555d},
                new float[]{0, .55f, 1}, Shader.TileMode.CLAMP);
        accentPaint.setColorFilter(new PorterDuffColorFilter(dark ? 0xff00b5a2 : 0xff00796b, PorterDuff.Mode.SRC_IN));
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Reduce the painted capsule without reducing text layout or touch targets. */
    void setVerticalPaintInset(float inset) {
        verticalPaintInset = inset;
        invalidate();
    }

    /** Fade only the selected lens; keep the track and child views independent. */
    void setLensAlpha(float alpha) {
        lensAlpha = Math.max(0, Math.min(1, alpha));
        invalidate();
    }

    void selection(float left, float width, boolean moving) {
        this.left = left;
        this.width = width;
        boolean enabled = appearance.glassTabs() && ValueAnimator.areAnimatorsEnabled();
        float target = moving && enabled ? (compact ? .35f : 1f) : 0;
        if (!enabled || !isAttachedToWindow()) {
            cancelShapeAnimator();
            pulse = target;
            expanding = moving && enabled;
        } else if (expanding != moving) {
            expanding = moving;
            cancelShapeAnimator();
            ValueAnimator next = ValueAnimator.ofFloat(pulse, target);
            shapeAnimator = next;
            next.setDuration(moving ? 120 : 180);
            next.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
            next.addUpdateListener(value -> {
                if (shapeAnimator != value) return;
                pulse = (float) value.getAnimatedValue();
                invalidate();
            });
            next.start();
        }
        invalidate();
    }

    private void cancelShapeAnimator() {
        ValueAnimator old = shapeAnimator;
        shapeAnimator = null;
        if (old != null) old.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Contract only the painted shell: text positions, hit targets and layout never shrink.
        float insetX = density * (1 + 5 * pulse);
        float insetY = verticalPaintInset + density * (1 + 4 * pulse);
        outer.set(insetX, insetY, getWidth() - insetX, getHeight() - insetY);
        trackMatrix.setScale(outer.width(), outer.height());
        trackMatrix.postTranslate(outer.left, outer.top);
        trackFill.setLocalMatrix(trackMatrix);
        trackRim.setLocalMatrix(trackMatrix);
        drawTrack(canvas);
        if (width <= 0) return;
        // The lens grows beyond the contracting shell, then both return on the same progress.
        float expansion = density * 9 * pulse;
        float l = Math.max(density, left - expansion);
        float r = Math.min(getWidth() - density, left + width + expansion);
        float vertical = verticalPaintInset + appearance.segmentLensInset() - density * 6 * pulse;
        lens.set(l, getPaddingTop() + vertical, r, getHeight() - getPaddingBottom() - vertical);
    }

    private void drawTrack(Canvas canvas) {
        if (frostedOutline) {
            // Keep the original navigation capsule as a thin outline, including during search.
            if (compact && !actionCapsule) appearance.drawFrostedTab(canvas, outer, false, 1);
            else appearance.drawFrostedCapsule(canvas, outer);
            return;
        }
        if (appearance.style == UiAppearance.Style.MATTE) return;
        if (appearance.style == UiAppearance.Style.FLAT) {
            paint.setColor(dark ? 0xff282838 : 0xffe9ebef);
            canvas.drawRoundRect(outer, outer.height()/2, outer.height()/2, paint);
            return;
        }
        // Translucent layers inherit MenuPanel's navy/lilac or white surface.
        paint.setShader(trackFill);
        canvas.drawRoundRect(outer, outer.height()/2, outer.height()/2, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * .5f);
        // Neutral, directional rim instead of a continuous cyan outline.
        paint.setShader(trackRim);
        canvas.drawRoundRect(outer, outer.height()/2, outer.height()/2, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (width <= 0 || lensAlpha <= 0) { super.dispatchDraw(canvas); return; }
        if (appearance.style == UiAppearance.Style.MATTE) {
            // A quiet sliding underline; no track shell, lens, magnification or pulse.
            float indicatorWidth = Math.min(width, 24*density);
            float x = left + (width-indicatorWidth)/2;
            paint.setColor(appearance.accent());
            int fade = lensAlpha >= 1 ? canvas.save()
                    : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * lensAlpha));
            canvas.drawRoundRect(x, getHeight()-verticalPaintInset-5*density, x+indicatorWidth,
                    getHeight()-verticalPaintInset-2*density, 1.5f*density, 1.5f*density, paint);
            canvas.restoreToCount(fade);
            super.dispatchDraw(canvas);
            return;
        }
        if (appearance.style == UiAppearance.Style.FLAT) {
            paint.setColor(dark ? 0xff214940 : 0xffd8f5ed);
            int fade = lensAlpha >= 1 ? canvas.save()
                    : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * lensAlpha));
            canvas.drawRoundRect(lens, lens.height()/2, lens.height()/2, paint);
            canvas.restoreToCount(fade);
            super.dispatchDraw(canvas);
            return;
        }
        if (compact) {
            int fade = lensAlpha >= 1 ? canvas.save()
                    : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * lensAlpha));
            renderer.draw(canvas, getWidth(), getHeight(), lens, lens.height()/2,
                    pulse, density, dark, source);
            canvas.restoreToCount(fade);
            // Settings labels are never clipped, magnified or sampled into the lens.
            super.dispatchDraw(canvas);
            return;
        }
        clip.reset();
        clip.addRoundRect(lens, lens.height()/2, lens.height()/2, Path.Direction.CW);
        int save = canvas.save();
        if (lensAlpha >= 1) canvas.clipOutPath(clip);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        int fade = lensAlpha >= 1 ? canvas.save()
                    : canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * lensAlpha));
        renderer.draw(canvas, getWidth(), getHeight(), lens, lens.height()/2,
                pulse, density, dark, source);
        canvas.restoreToCount(fade);
    }

    private void recordBackdrop(Canvas canvas) {
        // A frosted window already owns the backdrop. Do not bake the old opaque
        // panel into the lens texture; only sample the film, rim and real labels.
        if (!frostedOutline) canvas.drawColor(appearance.panelColor());
        drawTrack(canvas);
        if (frostedOutline) {
            appearance.drawFrostedLens(canvas, lens);
        } else if (appearance.style == UiAppearance.Style.ORIGINAL_GLASS_TABS) {
            // Only the selected lens is close to the page tone; the exposed track stays deep.
            paint.setColor(appearance.tabLensColor());
            canvas.drawRoundRect(lens, lens.height()/2, lens.height()/2, paint);
        }
        if (compact) return;
        // Every label crossed by the lens uses the same accent, without changing real TextViews.
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), accentPaint);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override protected void onDetachedFromWindow() {
        cancelShapeAnimator();
        pulse = 0;
        expanding = false;
        renderer.release();
        super.onDetachedFromWindow();
    }
}
