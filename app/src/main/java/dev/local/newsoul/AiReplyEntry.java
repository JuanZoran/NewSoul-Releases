package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Reuses Soul's own AI entry. Its original listener and layout remain owned by Soul. */
final class AiReplyEntry {
    private static final String CONVERSATION = "cn.soulapp.android.component.chat.ConversationActivity";
    private static final String DESCRIPTION = "New Soul AI 回复";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, EntryState> ENTRIES = new WeakHashMap<>();
    private static boolean installed;
    static boolean ready() { return installed; }

    private static XposedModule hostModule;
    private static Field listenerInfo, clickListener;
    private static final Set<Method> HOOKED_CLICKS = new HashSet<>();

    static void install(XposedModule module, ClassLoader loader) {
        if (installed) return;
        try {
            listenerInfo = View.class.getDeclaredField("mListenerInfo");
            listenerInfo.setAccessible(true);
            clickListener = listenerInfo.getType().getDeclaredField("mOnClickListener");
            clickListener.setAccessible(true);
            // Invalidate callers that may have inlined a host listener before it was discovered.
            module.deoptimize(View.class.getDeclaredMethod("performClick"));
            hostModule = module;
            installed = true;
        } catch (Throwable ignored) {
            Log.i("SoulHook102", "AI native entry unavailable; original Soul entry retained");
        }
    }

    /** Follow the listener actually attached to this button, including host rebindings. */
    private static boolean ensureClickHook(View view) {
        try {
            Object info = listenerInfo.get(view);
            Object listener = info == null ? null : clickListener.get(info);
            if (!(listener instanceof View.OnClickListener)) return false;
            Method click = listener.getClass().getMethod("onClick", View.class);
            if (click.getReturnType() != void.class) return false;
            if (HOOKED_CLICKS.contains(click)) return true;
            hostModule.hook(click).setId("new-soul/ai-native-entry")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        View clicked = (View) chain.getArg(0);
                        Activity activity = owner(clicked);
                        // A listener class can also serve other buttons. Always retain their action.
                        boolean target;
                        try { target = isConversation(activity) && clicked == find(activity, "img_chat_aigc"); }
                        catch (Throwable ignored) { return chain.proceed(); }
                        if (!target) return chain.proceed();
                        boolean enabled;
                        try { enabled = AiReplyConfig.enabled(activity); }
                        catch (Throwable ignored) { unavailable(activity); return null; }
                        if (!enabled) return chain.proceed();
                        // After claiming this enabled entry, failures must never replay official AI.
                        try {
                            EntryState state = bind(activity, clicked);
                            if (state == null || !state.refresh(true)) unavailable(activity);
                            else AiReplyUi.open(activity);
                        } catch (Throwable ignored) { unavailable(activity); }
                        return null;
                    });
            hostModule.deoptimize(click);
            HOOKED_CLICKS.add(click);
            hostModule.log(Log.INFO, "SoulHook102",
                    "AI native entry bound to " + click.getDeclaringClass().getName(), null);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    static void observe(Activity activity) {
        if (!installed || !isConversation(activity)) return;
        for (long delay : new long[]{100, 350, 900, 1800}) MAIN.postDelayed(() -> {
            if (!isConversation(activity)) return;
            try {
                View view = find(activity, "img_chat_aigc");
                EntryState state = bind(activity, view);
                if (state != null) state.refresh(true);
            } catch (Throwable ignored) { /* Unsupported layouts retain the original entry. */ }
        }, delay);
    }

    private static EntryState bind(Activity activity, View container) {
        if (!isConversation(activity) || container == null) return null;
        EntryState known = ENTRIES.get(container);
        if (known != null) return known;
        int imageId = id(activity, "img_chat_aigc_iv");
        View child = imageId == 0 ? null : container.findViewById(imageId);
        if (!(child instanceof ImageView)) return null;
        int animationId = id(activity, "img_chat_aigc_lt");
        View animation = animationId == 0 ? null : container.findViewById(animationId);
        EntryState state = new EntryState(container, (ImageView) child, animation);
        ENTRIES.put(container, state);
        container.addOnAttachStateChangeListener(state);
        if (container.isAttachedToWindow()) state.connect();
        return state;
    }

    /** All references to host Views are weak so a finished Activity cannot be retained here. */
    private static final class EntryState implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
        final WeakReference<View> container;
        final WeakReference<ImageView> image;
        final WeakReference<View> animation;
        Drawable originalDrawable;
        ColorStateList originalTint;
        CharSequence originalDescription;
        float originalAnimationAlpha;
        IconDrawable replacement;
        SharedPreferences preferences;
        ViewTreeObserver observer;
        boolean connected, active, eligible;

        EntryState(View container, ImageView image, View animation) {
            this.container = new WeakReference<>(container);
            this.image = new WeakReference<>(image);
            this.animation = new WeakReference<>(animation);
        }

        void connect() {
            if (connected) return;
            View view = container.get();
            Activity activity = owner(view);
            if (!isConversation(activity)) return;
            preferences = PluginPreferences.get(activity);
            preferences.registerOnSharedPreferenceChangeListener(this);
            observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
            connected = true;
            refresh(true);
        }

        void disconnect() {
            if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            preferences = null;
            observer = null;
            connected = false;
            restore();
        }

        @Override public void onViewAttachedToWindow(View view) { connect(); }
        @Override public void onViewDetachedFromWindow(View view) { disconnect(); }
        @Override public boolean onPreDraw() { refresh(false); return true; }
        @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key == null || AiReplyConfig.ENABLED.equals(key)) {
                if (Looper.myLooper() == Looper.getMainLooper()) refresh(true);
                else MAIN.post(() -> refresh(true));
            }
        }

        boolean refresh(boolean verifyConversation) {
            View view = container.get();
            ImageView icon = image.get();
            Activity activity = owner(view);
            if (!installed || !isConversation(activity) || icon == null) { restore(); return false; }
            boolean enabled = AiReplyConfig.enabled(activity);
            if (verifyConversation && enabled) {
                try { AiChatContext.currentIdentity(activity); eligible = true; }
                catch (Exception ignored) { eligible = false; }
            }
            if (!enabled || !eligible || !ensureClickHook(view)) { restore(); return false; }
            try {
                // Soul updates this drawable when mode/night styling changes. Keep the latest
                // host value, not just the value observed when the Activity was first opened.
                Drawable current = icon.getDrawable();
                boolean hostChanged = !active || current != replacement;
                if (hostChanged) originalDrawable = current;
                int fallback = Math.round(28 * activity.getResources().getDisplayMetrics().density);
                int width = originalDrawable == null ? fallback : originalDrawable.getIntrinsicWidth();
                int height = originalDrawable == null ? fallback : originalDrawable.getIntrinsicHeight();
                if (width <= 0) width = fallback;
                if (height <= 0) height = fallback;
                if (hostChanged || replacement == null || replacement.getIntrinsicWidth() != width
                        || replacement.getIntrinsicHeight() != height) {
                    Bitmap bitmap = renderHostIcon(originalDrawable, width, height);
                    if (bitmap == null) { restore(); return false; }
                    replacement = new IconDrawable(bitmap, width, height);
                }
                if (!active) {
                    originalTint = icon.getImageTintList();
                    originalDescription = view.getContentDescription();
                    View lottie = animation.get();
                    originalAnimationAlpha = lottie == null ? 1f : lottie.getAlpha();
                } else {
                    if (icon.getImageTintList() != null) originalTint = icon.getImageTintList();
                    if (!DESCRIPTION.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
                        originalDescription = view.getContentDescription();
                    View lottie = animation.get();
                    if (lottie != null && lottie.getAlpha() != 0f) originalAnimationAlpha = lottie.getAlpha();
                }
                active = true;
                PluginActionMark.set(icon, true);
                if (icon.getImageTintList() != null) icon.setImageTintList(null);
                if (icon.getDrawable() != replacement) icon.setImageDrawable(replacement);
                if (!DESCRIPTION.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
                    view.setContentDescription(DESCRIPTION);
                // Preserve Soul's animation lifecycle/visibility, suppress only its drawing.
                View lottie = animation.get();
                if (lottie != null && lottie.getAlpha() != 0f) lottie.setAlpha(0f);
                return true;
            } catch (Throwable ignored) { restore(); return false; }
        }

        void restore() {
            if (!active) return;
            ImageView icon = image.get();
            View view = container.get();
            View lottie = animation.get();
            if (icon != null) {
                PluginActionMark.set(icon, false);
                // An intervening host update is already authoritative and must not be overwritten.
                if (icon.getDrawable() == replacement) icon.setImageDrawable(originalDrawable);
                if (icon.getImageTintList() == null) icon.setImageTintList(originalTint);
            }
            if (view != null && DESCRIPTION.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
                view.setContentDescription(originalDescription);
            if (lottie != null && lottie.getAlpha() == 0f) lottie.setAlpha(originalAnimationAlpha);
            active = false;
        }
    }

    /** Snapshot only the current host artwork; no custom replacement asset or theme guess. */
    private static Bitmap renderHostIcon(Drawable source, int width, int height) {
        if (source == null || width > 1024 || height > 1024) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(source.getBounds());
        try {
            source.setBounds(0, 0, width, height);
            source.draw(new Canvas(bitmap));
        } finally {
            source.setBounds(bounds);
        }
        return bitmap;
    }

    /** Preserve host geometry; PluginActionMark owns the shared overlay. */
    private static final class IconDrawable extends Drawable {
        final Bitmap bitmap;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final int width, height;
        int alpha = 255;
        IconDrawable(Bitmap bitmap, int width, int height) {
            this.bitmap = bitmap; this.width = width; this.height = height;
        }
        @Override public void draw(Canvas canvas) {
            paint.setAlpha(alpha);
            canvas.drawBitmap(bitmap, null, getBounds(), paint);

        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return height; }
    }

    private static int id(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }
    private static void unavailable(Activity activity) {
        try { Toast.makeText(activity, "New Soul AI 尚未就绪，请稍后重试", Toast.LENGTH_SHORT).show(); }
        catch (Throwable ignored) { /* Do not let a toast failure invoke the protected original chain. */ }
    }
    private static View find(Activity activity, String name) {
        int id = id(activity, name);
        return id == 0 ? null : activity.findViewById(id);
    }
    private static Activity owner(View view) {
        if (view == null) return null;
        Context context = view.getContext();
        for (int depth = 0; depth < 12 && context != null; depth++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return null;
            context = next;
        }
        return null;
    }
    private static boolean isConversation(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && "cn.soulapp.android".equals(activity.getPackageName())
                && CONVERSATION.equals(activity.getClass().getName());
    }
}
