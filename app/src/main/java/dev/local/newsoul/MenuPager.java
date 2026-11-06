package dev.local.newsoul;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** A bounded settings pager. Child controls keep their gestures; ordinary rows can page. */
final class MenuPager extends FrameLayout {
    interface Listener {
        ViewGroup page(int index);
        void progress(int from, int to, float fraction, boolean moving);
        void selected(int index);
    }

    private final Listener listener;
    private final ViewGroup[] pages;
    private final int slop, minimumFling, maximumFling;
    private int selected = -1, from = -1, to = -1;
    private float fraction;
    private boolean shortTransition, redirected, settlingAdvance;
    private float indicatorPosition, redirectStartPosition;
    private final float[] startX, startAlpha;
    private final boolean[] redirectVisible;
    private ValueAnimator animator;
    private VelocityTracker velocity;
    private int pointer = -1;
    private long downTime;
    private float downX, downY, dragX;
    private boolean dragging, childGesture, swallowStream, motionTouch;
    private float touchStartFraction, motionDirection;

    MenuPager(Context context, int count, Listener listener) {
        super(context);
        this.listener = listener;
        pages = new ViewGroup[count];
        startX = new float[count];
        startAlpha = new float[count];
        redirectVisible = new boolean[count];
        ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop();
        minimumFling = Math.max(config.getScaledMinimumFlingVelocity(),
                Math.round(400 * getResources().getDisplayMetrics().density));
        maximumFling = config.getScaledMaximumFlingVelocity();
        setClipChildren(true);
    }

    void select(int index, boolean animate) {
        if (index < 0 || index >= pages.length) return;
        clearTouch();
        if (selected < 0 || !animate) { complete(index); return; }
        cancelAnimator();
        if (from != to || redirected) {
            if (!redirected && (index == to || index == from)) settle(index == to);
            else redirectTo(index);
            return;
        }
        if (selected == index) return;
        from = selected;
        to = index;
        fraction = 0;
        shortTransition = Math.abs(to - from) > 1;
        obtain(from);
        obtain(to);
        render();
        settle(true);
    }

    /** Capture the actual visual state when a third tab replaces an in-flight target. */
    private void redirectTo(int index) {
        cancelAnimator();
        obtain(index);
        redirectStartPosition = indicatorPosition;
        float direction = Math.signum(index - redirectStartPosition);
        if (direction == 0) direction = 1;
        for (int i = 0; i < pages.length; i++) {
            ViewGroup page = pages[i];
            boolean visible = page != null && page.getVisibility() == VISIBLE;
            redirectVisible[i] = visible || i == index;
            startX[i] = visible ? page.getTranslationX() : direction * getWidth() * .18f;
            startAlpha[i] = visible ? page.getAlpha() : 0;
        }
        from = selected;
        to = index;
        fraction = 0;
        redirected = true;
        shortTransition = true;
        render();
        settle(true);
    }

    private ViewGroup obtain(int index) {
        if (pages[index] == null) {
            ViewGroup page = listener.page(index);
            pages[index] = page;
            page.setVisibility(INVISIBLE);
            addView(page, new FrameLayout.LayoutParams(-1, -1));
        }
        return pages[index];
    }

    private void render() {
        if (from < 0) return;
        float direction = redirected ? Math.signum(to - redirectStartPosition) : Integer.compare(to, from);
        if (redirected && direction == 0) direction = 1;
        float distance = getWidth() * (shortTransition ? .18f : 1f);
        for (int i = 0; i < pages.length; i++) {
            ViewGroup page = pages[i];
            if (page == null) continue;
            boolean visible = redirected ? redirectVisible[i] : i == from || i == to;
            page.setVisibility(visible ? VISIBLE : INVISIBLE);
            boolean active = i == selected;
            page.setImportantForAccessibility(active
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            page.setDescendantFocusability(active ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            if (!visible) {
                page.setTranslationX(0);
                page.setAlpha(1);
            } else if (redirected) {
                float endX = i == to ? 0 : startX[i] - direction * distance;
                float endAlpha = i == to ? 1 : 0;
                page.setTranslationX(startX[i] + (endX - startX[i]) * fraction);
                page.setAlpha(startAlpha[i] + (endAlpha - startAlpha[i]) * fraction);
            } else if (i == from) {
                page.setTranslationX(-direction * distance * fraction);
                page.setAlpha(shortTransition ? 1 - fraction : 1);
            } else {
                page.setTranslationX(direction * distance * (1 - fraction));
                page.setAlpha(shortTransition ? fraction : 1);
            }
        }
        indicatorPosition = redirected ? redirectStartPosition + (to - redirectStartPosition) * fraction
                : from + (to - from) * fraction;
        int left = Math.max(0, Math.min(pages.length - 1, (int) Math.floor(indicatorPosition)));
        int right = Math.min(pages.length - 1, left + 1);
        listener.progress(left, right, Math.max(0, Math.min(1, indicatorPosition - left)), from != to || redirected);
    }

    private void complete(int index) {
        cancelAnimator();
        obtain(index);
        boolean changed = selected != index;
        selected = from = to = index;
        fraction = 0;
        shortTransition = redirected = false;
        render();
        if (changed) listener.selected(index);
    }

    private void settle(boolean advance) {
        if (redirected && !advance) {
            // The snapshot at progress zero may itself be between pages. Return smoothly.
            redirectTo(selected);
            return;
        }
        settlingAdvance = advance;
        cancelAnimator();
        final int destination = advance ? to : from;
        if (!ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow() || getWidth() == 0) {
            complete(destination);
            return;
        }
        float end = advance ? 1 : 0;
        ValueAnimator next = ValueAnimator.ofFloat(fraction, end);
        animator = next;
        next.setDuration(Math.max(90, Math.round(210 * Math.abs(end - fraction))));
        next.setInterpolator(new android.view.animation.DecelerateInterpolator());
        next.addUpdateListener(value -> {
            if (animator != value) return;
            fraction = (float) value.getAnimatedValue();
            render();
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                complete(destination);
            }
        });
        next.start();
    }

    private void cancelAnimator() {
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
    }

    private void stopAtNearest() {
        if (selected < 0) return;
        int destination = dragging ? selected : (fraction >= .5f ? to : from);
        cancelAnimator();
        if (destination >= 0) complete(destination);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean moving = animator != null || from != to || redirected;
            cancelAnimator();
            clearTouch();
            motionTouch = moving;
            touchStartFraction = fraction;
            motionDirection = redirected ? Math.signum(to - redirectStartPosition) : Integer.compare(to, from);
            if (motionDirection == 0) motionDirection = 1;
            pointer = event.getPointerId(0);
            downTime = event.getDownTime();
            downX = event.getX();
            downY = event.getY();
            childGesture = !motionTouch && protectedHit(this, downX, downY);
            velocity = VelocityTracker.obtain();
        }
        if (velocity != null) velocity.addMovement(event);
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (dragging || motionTouch) {
                dragging = false;
                swallowStream = true;
                settle(false);
            } else childGesture = true;
        }
        if (swallowStream) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) clearTouch();
            return true;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) clearTouch();
        return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) maybeStartDrag(event);
        // A moving page owns this whole stream: never dispatch a tap to shifting controls.
        return dragging || motionTouch;
    }

    private void maybeStartDrag(MotionEvent event) {
        if (dragging || childGesture || pointer < 0 || event.getPointerCount() != 1) return;
        int index = event.findPointerIndex(pointer);
        if (index < 0) { childGesture = true; return; }
        float dx = event.getX(index) - downX, dy = event.getY(index) - downY;
        int axis = MenuPagerPolicy.direction(dx, dy, slop);
        if (axis == MenuPagerPolicy.VERTICAL) childGesture = true;
        if (axis != MenuPagerPolicy.HORIZONTAL) return;
        dragging = true;
        if (!motionTouch) {
            from = selected;
            shortTransition = false;
        }
        // ViewGroup interception sends ACTION_CANCEL to the pressed row before we move it.
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        moveDrag(dx);
    }

    private void moveDrag(float dx) {
        dragX = dx;
        if (motionTouch) {
            float distance = getWidth() * (shortTransition ? .18f : 1f);
            fraction = distance == 0 ? touchStartFraction
                    : Math.max(0, Math.min(1, touchStartFraction - dx * motionDirection / distance));
            render();
            return;
        }
        to = MenuPagerPolicy.neighbor(selected, pages.length, dx);
        obtain(to);
        fraction = to == selected || getWidth() == 0 ? 0 : Math.min(1, Math.abs(dx) / getWidth());
        render();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            maybeStartDrag(event);
            int index = event.findPointerIndex(pointer);
            if (dragging && index >= 0) moveDrag(event.getX(index) - downX);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragging) {
                float speed = 0;
                if (velocity != null && action != MotionEvent.ACTION_CANCEL) {
                    velocity.computeCurrentVelocity(1000, maximumFling);
                    speed = velocity.getXVelocity(pointer);
                }
                boolean advance;
                if (motionTouch) {
                    advance = MenuPagerPolicy.advances(fraction, -speed * motionDirection,
                            minimumFling, action == MotionEvent.ACTION_CANCEL);
                } else {
                    int destination = MenuPagerPolicy.destination(selected, pages.length,
                            dragX, getWidth(), speed, minimumFling, action == MotionEvent.ACTION_CANCEL);
                    advance = destination != selected;
                }
                dragging = false;
                settle(advance);
            } else if (motionTouch) {
                // A tap that paused an animation resumes it; CANCEL returns to the stable page.
                settle(action != MotionEvent.ACTION_CANCEL && settlingAdvance);
            }
        }
        return true;
    }

    private static boolean protectedHit(View view, float x, float y) {
        if (view.getVisibility() != VISIBLE || x < 0 || y < 0 || x >= view.getWidth() || y >= view.getHeight())
            return false;
        // CheckBox rows only need taps: let a horizontal drag reach the pager, whose
        // interception cancels the pending click. Keep switches and other controls protected.
        if (view instanceof SettingsUi.MenuSwitch || (view instanceof android.widget.CompoundButton && !(view instanceof android.widget.CheckBox))
                || view instanceof android.widget.AbsSpinner
                || view instanceof android.widget.EditText || view instanceof android.widget.HorizontalScrollView
                || view instanceof android.widget.AbsSeekBar || view instanceof android.webkit.WebView
                || (view instanceof android.widget.TextView && ((android.widget.TextView) view).isTextSelectable())
                || view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (protectedHit(child, x + group.getScrollX() - child.getLeft() - child.getTranslationX(),
                        y + group.getScrollY() - child.getTop() - child.getTranslationY())) return true;
            }
        }
        return false;
    }

    private void clearTouch() {
        if (velocity != null) velocity.recycle();
        velocity = null;
        pointer = -1;
        dragging = childGesture = swallowStream = motionTouch = false;
        dragX = 0;
    }

    private void interruptMotion() {
        boolean activeStream = pointer >= 0;
        if (activeStream) {
            // Geometry/window changes invalidate even a child-owned pressed target. Cancel
            // it once before swallowing the remainder, so its later UP cannot toggle a row.
            MotionEvent cancel = MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, downX, downY, 0);
            try { super.dispatchTouchEvent(cancel); }
            finally { cancel.recycle(); }
        }
        stopAtNearest();
        clearTouch();
        swallowStream = activeStream;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) interruptMotion();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) interruptMotion();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (!visible) interruptMotion();
    }

    @Override protected void onDetachedFromWindow() {
        interruptMotion();
        super.onDetachedFromWindow();
    }
}
