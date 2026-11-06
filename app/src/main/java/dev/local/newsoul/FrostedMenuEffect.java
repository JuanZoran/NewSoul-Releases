package dev.local.newsoul;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Window-owned backdrop blur: never applies a RenderEffect to foreground controls or the host. */
final class FrostedMenuEffect {
    static final String ENABLED = "menu_frosted_glass_v1";

    static void attach(Window window, UiAppearance appearance, SharedPreferences preferences) {
        if (Build.VERSION.SDK_INT >= 31 && window != null) {
            android.util.TypedValue translucent = new android.util.TypedValue();
            boolean ready = window.getContext().getTheme().resolveAttribute(
                    android.R.attr.windowIsTranslucent, translucent, true) && translucent.data != 0;
            android.util.Log.i("SoulHook102", "Frosted menu window: translucent=" + ready);
            // Fail closed rather than showing a clear panel when the framework rejects its blur.
            if (!ready) return;
            Session session = new Session(window, appearance, preferences);
            window.getDecorView().addOnAttachStateChangeListener(session);
            if (window.getDecorView().isAttachedToWindow()) session.start();
        }
    }

    @android.annotation.TargetApi(31)
    private static final class Session implements View.OnAttachStateChangeListener {
        final Window window;
        final View decor;
        final UiAppearance appearance;
        final SharedPreferences preferences;
        final WindowManager manager;
        final Drawable originalBackground;
        final float originalDim;
        final int originalAnimations;
        final boolean originalBlurFlag;
        final int originalBehindRadius;
        final Map<View, UiAppearance.CardPresentation> cardPresentations = new IdentityHashMap<>();
        final Map<GlassTabTrack, Boolean> tracks = new IdentityHashMap<>();
        final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
        final Consumer<Boolean> blurListener;
        final android.view.ViewTreeObserver.OnGlobalLayoutListener layoutListener;
        ValueAnimator animator;
        boolean attached, listening, available, active;

        Session(Window window, UiAppearance appearance, SharedPreferences preferences) {
            this.window = window;
            this.decor = window.getDecorView();
            this.appearance = appearance;
            this.preferences = preferences;
            manager = window.getContext().getSystemService(WindowManager.class);
            originalBackground = decor.getBackground();
            originalDim = window.getAttributes().dimAmount;
            originalAnimations = window.getAttributes().windowAnimations;
            originalBlurFlag = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0;
            originalBehindRadius = window.getAttributes().getBlurBehindRadius();
            preferenceListener = (prefs, key) -> {
                if (key == null || ENABLED.equals(key)) decor.post(() -> update(false));
            };
            blurListener = enabled -> {
                available = enabled;
                update(false);
            };
            layoutListener = () -> { if (active) tintCards(decor); };
        }

        void start() {
            attached = true;
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
            try {
                available = manager != null && manager.isCrossWindowBlurEnabled();
                if (manager != null) {
                    manager.addCrossWindowBlurEnabledListener(window.getContext().getMainExecutor(), blurListener);
                    listening = true;
                }
                update(true);
            } catch (RuntimeException unavailable) {
                available = false;
                restore();
            }
        }

        void update(boolean opening) {
            if (!attached) return;
            boolean enabled = available && preferences.getBoolean(ENABLED, ConfigDefaults.booleanValue(ENABLED));
            if (!enabled) { restore(); return; }
            if (active && !opening) return;
            cancelAnimation();
            active = true;
            appearance.frostedWindow(window);
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setDimAmount(appearance.dark ? .20f : .12f);
            tintCards(decor);
            if (opening && ValueAnimator.areAnimatorsEnabled()) {
                window.setWindowAnimations(0);
                decor.setAlpha(0);
                decor.setTranslationY(0);
                animator = ValueAnimator.ofFloat(0, 1);
                animator.setDuration(300);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(value -> {
                    if (!attached || !active) return;
                    float progress = (float) value.getAnimatedValue();
                    decor.setAlpha(progress);
                    decor.setTranslationY(0);
                    setBlur(progress);
                });
                animator.start();
            } else setBlur(1);
        }

        void setBlur(float progress) {
            // Android's recommended radii are pixels, not dp; cap cost on dense displays.
            window.setBackgroundBlurRadius(Math.round(80 * progress));
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.setBlurBehindRadius(Math.round(20 * progress));
            window.setAttributes(attributes);
        }

        void tintCards(View view) {
            if (UiAppearance.isCard(view) && view.getBackground() != null) {
                if (!cardPresentations.containsKey(view))
                    cardPresentations.put(view, appearance.outlineCard(view));
            }
            if (view instanceof GlassTabTrack) {
                GlassTabTrack track = (GlassTabTrack) view;
                if (!tracks.containsKey(track)) {
                    tracks.put(track, track.isFrostedOutline());
                    track.setFrostedOutline(true);
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) tintCards(group.getChildAt(i));
            }
        }

        void cancelAnimation() {
            if (animator != null) { animator.cancel(); animator = null; }
            decor.setAlpha(1);
            decor.setTranslationY(0);
        }

        void restore() {
            if (!active) return;
            active = false;
            cancelAnimation();
            window.setBackgroundBlurRadius(0);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.setBlurBehindRadius(originalBehindRadius);
            attributes.dimAmount = originalDim;
            attributes.windowAnimations = originalAnimations;
            if (!originalBlurFlag) attributes.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            window.setAttributes(attributes);
            appearance.restoreWindow(window, originalBackground);
            for (Map.Entry<View, UiAppearance.CardPresentation> entry : cardPresentations.entrySet())
                entry.getValue().restore(entry.getKey());
            cardPresentations.clear();
            for (Map.Entry<GlassTabTrack, Boolean> entry : tracks.entrySet())
                entry.getKey().setFrostedOutline(entry.getValue());
            tracks.clear();
        }

        @Override public void onViewAttachedToWindow(View view) { start(); }
        @Override public void onViewDetachedFromWindow(View view) {
            attached = false;
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
            if (listening) { manager.removeCrossWindowBlurEnabledListener(blurListener); listening = false; }
            if (decor.getViewTreeObserver().isAlive())
                decor.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
            restore();
        }
    }

    private FrostedMenuEffect() {}
}
