package dev.local.newsoul;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** A content-sized reply panel below Soul's input row, without entering its official AI board. */
final class AiReplyDock implements AutoCloseable {
    private static final String CONVERSATION = "cn.soulapp.android.component.chat.ConversationActivity";
    private static final String INPUT_BAR = "cn.soulapp.android.component.chat.inputmenu.InputBar";
    private static final String MEDIA_MENU = "cn.soulapp.android.component.chat.inputmenu.SingleChatMediaMenu";
    private static final String TAG = "newsoul.ai.reply.dock.v1";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, WeakReference<AiReplyDock>> ACTIVE = new WeakHashMap<>();
    private static boolean backHookInstalled;

    private final WeakReference<Activity> activity;
    private final LinearLayout inputBar;
    private final View composer;
    private final View mediaMenu;
    private final Method getMenuState;
    private final DockFrame frame;
    private final View content;
    private final ViewGroup.LayoutParams originalContentParams;
    private final Runnable onClose;
    private final View.OnAttachStateChangeListener attachment;
    private final ViewTreeObserver.OnGlobalLayoutListener layout;
    private final ViewTreeObserver.OnWindowFocusChangeListener windowFocus;
    private final Runnable deferredCheck;
    private final long openedAt = SystemClock.uptimeMillis();
    private long focusedAt = openedAt;
    private ViewTreeObserver observer;
    private Runnable unregisterBack;
    private boolean closed;
    private boolean attached;
    private boolean backDown;
    private boolean boardWatchArmed;
    private View officialOpening;
    private ViewGroup.LayoutParams officialOpeningParams;
    private int officialOpeningHeight;

    static void installBackHook(XposedModule module, ClassLoader loader) {
        if (backHookInstalled) return;
        try {
            Class<?> fragment = loader.loadClass("cn.soulapp.android.component.chat.fragment.ConversationFragment");
            Method back = fragment.getDeclaredMethod("onBackPressed");
            Method getActivity = fragment.getMethod("getActivity");
            if (back.getReturnType() != boolean.class) return;
            module.hook(back).setId("new-soul/ai-dock-back")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity owner = null;
                        try {
                            Object value = getActivity.invoke(chain.getThisObject());
                            if (value instanceof Activity) owner = (Activity) value;
                        } catch (Throwable ignored) { }
                        if (owner != null && closeForBack(owner)) return true;
                        return chain.proceed();
                    });
            backHookInstalled = true;
        } catch (Throwable ignored) {
            Log.i("SoulHook102", "AI dock host back hook unavailable; window back handler retained");
        }
    }

    /** Returns null without taking ownership of content if this host structure is unsupported. */
    static AiReplyDock attach(Activity activity, View content, Runnable onClose) {
        if (Looper.myLooper() != Looper.getMainLooper() || activity == null || content == null
                || activity.isFinishing() || activity.isDestroyed() || content.getParent() != null
                || !CONVERSATION.equals(activity.getClass().getName())) return null;
        WeakReference<AiReplyDock> reference = ACTIVE.get(activity);
        AiReplyDock existing = reference == null ? null : reference.get();
        if (existing != null) return null; // Includes a dock awaiting deferred detach cleanup.
        LinearLayout bar = findInputBar(activity);
        if (bar == null || !bar.isAttachedToWindow() || bar.getHeight() <= 0) return null;
        for (int index = 0; index < bar.getChildCount(); index++)
            if (TAG.equals(bar.getChildAt(index).getTag())) return null;
        int height = height(activity, bar, 0, content);
        if (height <= 0) return null;
        View composer = composer(activity);
        View menu = findMediaMenu(bar);
        if (composer == null || menu == null) return null;
        Method state;
        int afterCollapse;
        try {
            state = menu.getClass().getMethod("getCurrentState");
            // The semantic resolver selects the existing keyboard/emoji/more collapse method.
            // It does not create LoveBoard, request official AI or send a message.
            boolean requested = Boolean.TRUE.equals(HostSymbols.method(HostDexRules.COLLAPSE, menu.getClass().getClassLoader()).invoke(menu));
            afterCollapse = ((Number) state.invoke(menu)).intValue();
            if (!requested && afterCollapse != 4) return null;
        } catch (Throwable ignored) { return null; }
        AiReplyDock dock = new AiReplyDock(activity, bar, composer, menu, state,
                afterCollapse == 4, content, onClose);
        try {
            dock.frame.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            dock.collapseOfficialOpening();
            // Keep the entire existing InputBar subtree in place. Its own onSizeChanged callback
            // adjusts SingleChatMediaMenu's input height/peek height for the added panel.
            bar.addView(dock.frame, bar.getChildCount(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            dock.attached = true;
            ACTIVE.put(activity, new WeakReference<>(dock));
            dock.frame.addOnAttachStateChangeListener(dock.attachment);
            dock.observer = bar.getViewTreeObserver();
            dock.observer.addOnGlobalLayoutListener(dock.layout);
            dock.observer.addOnWindowFocusChangeListener(dock.windowFocus);
            MAIN.postDelayed(dock.deferredCheck, 900);
            if (Build.VERSION.SDK_INT >= 33) {
                try { dock.unregisterBack = Back33.register(activity, dock); }
                catch (Throwable ignored) { /* closeForBack supplies the verified host fallback. */ }
            }
            return dock;
        } catch (Throwable ignored) {
            dock.release(false);
            return null;
        }
    }

    /** For the host's boolean ConversationFragment.onBackPressed hook on every Android version. */
    static boolean closeForBack(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) return false;
        WeakReference<AiReplyDock> reference = ACTIVE.get(activity);
        AiReplyDock dock = reference == null ? null : reference.get();
        if (dock == null || dock.closed) return false;
        try { dock.close(); } catch (Throwable ignored) { /* Consume this back even during host teardown. */ }
        return true;
    }

    private AiReplyDock(Activity activity, LinearLayout inputBar, View composer, View mediaMenu,
            Method getMenuState, boolean boardWatchArmed, View content, Runnable onClose) {
        this.activity = new WeakReference<>(activity);
        this.inputBar = inputBar;
        this.composer = composer;
        this.mediaMenu = mediaMenu;
        this.getMenuState = getMenuState;
        this.boardWatchArmed = boardWatchArmed;
        this.content = content;
        this.originalContentParams = content.getLayoutParams();
        this.onClose = onClose;
        this.frame = new DockFrame(activity);
        frame.setTag(TAG);
        frame.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 28) frame.setAccessibilityPaneTitle("New Soul AI 回复");
        attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                if (attached && !closed) release(true, true);
            }
        };
        layout = this::resize;
        deferredCheck = this::resize;
        windowFocus = focused -> {
            if (focused && !closed) {
                focusedAt = SystemClock.uptimeMillis();
                MAIN.removeCallbacks(deferredCheck);
                MAIN.postDelayed(deferredCheck, 350);
            }
        };
    }

    private void resize() {
        if (closed) return;
        Activity owner = activity.get();
        if (owner == null || owner.isFinishing() || owner.isDestroyed()
                || frame.getParent() != inputBar) { close(); return; }
        // Child dialogs have their own keyboard and window focus. They must not close this panel.
        if (!owner.hasWindowFocus()) return;
        long now = SystemClock.uptimeMillis();
        boolean settled = now - openedAt >= 800 && now - focusedAt >= 300;
        try {
            int state = ((Number) getMenuState.invoke(mediaMenu)).intValue();
            if (state == 4) boardWatchArmed = true;
            else if (settled && boardWatchArmed && (state == 3 || state == 6)) { close(); return; }
        } catch (Throwable ignored) { /* Ignore unknown future host state APIs. */ }
        if (settled && composer.hasFocus()) {
            boolean ime = false;
            try {
                if (Build.VERSION.SDK_INT >= 30) ime = Insets30.imeVisible(owner);
                else ime = Boolean.TRUE.equals(mediaMenu.getClass().getMethod("getKeyboardShow").invoke(mediaMenu));
            } catch (Throwable ignored) { }
            if (ime) { close(); return; }
        }
        int height = height(owner, inputBar, frame.getHeight(), content);
        if (height <= 0) { close(); return; }
        ViewGroup.LayoutParams params = frame.getLayoutParams();
        if (params != null && params.height != height) {
            params.height = height;
            frame.setLayoutParams(params);
        }
    }

    @Override public void close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(this::close);
            return;
        }
        release(true);
    }

    private void release(boolean notify) {
        release(notify, false);
    }

    private void release(boolean notify, boolean detached) {
        if (closed) return;
        closed = true;
        attached = false;
        frame.removeOnAttachStateChangeListener(attachment);
        MAIN.removeCallbacks(deferredCheck);
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layout);
            observer.removeOnWindowFocusChangeListener(windowFocus);
        }
        observer = null;
        if (unregisterBack != null) {
            try { unregisterBack.run(); } catch (Throwable ignored) { }
            unregisterBack = null;
        }
        // A parent can still be traversing its cached children during detach. Defer
        // all tree/layout changes and the owner callback until that dispatch returns.
        if (detached) MAIN.post(() -> finishRelease(notify));
        else finishRelease(notify);
    }

    private void finishRelease(boolean notify) {
        Activity owner = activity.get();
        WeakReference<AiReplyDock> reference = owner == null ? null : ACTIVE.get(owner);
        if (reference != null && reference.get() == this) ACTIVE.remove(owner);
        try { if (frame.getParent() == inputBar) inputBar.removeView(frame); }
        catch (Throwable ignored) { /* Host teardown must not replay a back action or crash. */ }
        restoreOfficialOpening();
        try {
            if (content.getParent() == frame) frame.removeView(content);
            if (originalContentParams != null) content.setLayoutParams(originalContentParams);
        } catch (Throwable ignored) { }
        if (notify && onClose != null) {
            try { onClose.run(); } catch (Throwable ignored) { }
        }
    }

    private static LinearLayout findInputBar(Activity activity) {
        View composer = composer(activity);
        if (composer == null) return null;
        LinearLayout result = null;
        View node = composer;
        for (int depth = 0; depth < 24 && node != null; depth++) {
            if (INPUT_BAR.equals(node.getClass().getName())) {
                if (!(node instanceof LinearLayout)
                        || ((LinearLayout) node).getOrientation() != LinearLayout.VERTICAL) return null;
                result = (LinearLayout) node;
            }
            if (MEDIA_MENU.equals(node.getClass().getName())) return result;
            ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static View composer(Activity activity) {
        int id = activity.getResources().getIdentifier("et_sendmessage", "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private static View findMediaMenu(View inputBar) {
        View node = inputBar;
        for (int depth = 0; depth < 24 && node != null; depth++) {
            if (MEDIA_MENU.equals(node.getClass().getName())) return node;
            ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    /** Collapse only the official opening slot; leave its visibility and business state untouched. */
    private void collapseOfficialOpening() {
        Activity owner = activity.get();
        if (owner == null) return;
        int id = owner.getResources().getIdentifier("cv_aigc_replay2", "id", owner.getPackageName());
        View opening = id == 0 ? null : inputBar.findViewById(id);
        if (opening == null || opening.getLayoutParams() == null) return;
        officialOpening = opening;
        officialOpeningParams = opening.getLayoutParams();
        officialOpeningHeight = officialOpeningParams.height;
        officialOpeningParams.height = 0;
        opening.setLayoutParams(officialOpeningParams);
    }

    private void restoreOfficialOpening() {
        // Restore only our unchanged height override. Host visibility updates remain authoritative.
        try {
            if (officialOpening != null && officialOpening.getLayoutParams() == officialOpeningParams
                    && officialOpeningParams.height == 0) {
                officialOpeningParams.height = officialOpeningHeight;
                officialOpening.setLayoutParams(officialOpeningParams);
            }
        } catch (Throwable ignored) { /* Host teardown must still release our panel and callback. */ }
        finally { officialOpening = null; officialOpeningParams = null; }
    }

    private static int height(Activity activity, LinearLayout bar, int currentDockHeight, View content) {
        Rect visible = new Rect();
        View decor = activity.getWindow().getDecorView();
        decor.getWindowVisibleDisplayFrame(visible);
        int windowHeight = visible.height();
        if (windowHeight <= 0) windowHeight = decor.getHeight();
        float density = activity.getResources().getDisplayMetrics().density;
        int nativeHeight = Math.max(0, bar.getHeight() - currentDockHeight);
        int available = windowHeight - nativeHeight - Math.round(96 * density);
        int maximum = Math.min(Math.round(360 * density), Math.round(windowHeight * .48f));
        maximum = Math.min(maximum, available);
        if (maximum < Math.round(160 * density) || bar.getWidth() <= 0) return 0;
        content.measure(View.MeasureSpec.makeMeasureSpec(bar.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.AT_MOST));
        return Math.min(maximum, content.getMeasuredHeight());
    }

    private final class DockFrame extends FrameLayout {
        DockFrame(Activity context) { super(context); }
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if (!closed && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) backDown = true;
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean shouldClose = backDown && !event.isCanceled();
                    backDown = false;
                    if (shouldClose) close();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }
    }

    /** Isolated API class keeps older devices from resolving Android 13 back dispatcher types. */
    private static final class Back33 {
        static Runnable register(Activity activity, AiReplyDock dock) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = () -> closeForBack(activity);
            dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }

    private static final class Insets30 {
        static boolean imeVisible(Activity activity) {
            android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
            return insets != null && insets.isVisible(android.view.WindowInsets.Type.ime());
        }
    }
}
