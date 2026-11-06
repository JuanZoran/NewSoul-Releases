package dev.local.newsoul;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Transient help anchored to the existing question button; never writes preferences. */
final class HelpBubble {
    private static WeakReference<HelpBubble> active = new WeakReference<>(null);
    private final SettingsUi.MenuPanel panel;
    private final View anchor;
    private final CharSequence message;
    private final boolean preferAbove;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::fadeOut;
    private PopupWindow popup;
    private FrameLayout content;
    private ViewTreeObserver observer;
    private long dismissedAt;
    private long duration;
    private boolean fadingOut;
    private final int[] initialPosition = new int[2];
    private final ViewTreeObserver.OnPreDrawListener positionWatcher = this::checkPosition;
    private boolean checkPosition() {
        int[] position = new int[2];
        anchor.getLocationOnScreen(position);
        if (!anchor.isShown() || !anchor.hasWindowFocus()
                || position[0] != initialPosition[0] || position[1] != initialPosition[1]) dismiss();
        return true;
    }
    private final View.OnAttachStateChangeListener lifetime = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { dismiss(); }
    };

    private HelpBubble(SettingsUi.MenuPanel panel, View anchor, CharSequence message, boolean preferAbove) {
        this.panel = panel;
        this.anchor = anchor;
        this.message = message;
        this.preferAbove = preferAbove;
    }

    static void bind(SettingsUi.MenuPanel panel, View anchor, CharSequence message) {
        bind(panel, anchor, message, false);
    }

    static void bind(SettingsUi.MenuPanel panel, View anchor, CharSequence message, boolean preferAbove) {
        HelpBubble bubble = new HelpBubble(panel, anchor, message, preferAbove);
        anchor.setOnClickListener(v -> bubble.toggle());
    }

    private int dp(float value) {
        return Math.round(value * panel.context.getResources().getDisplayMetrics().density);
    }

    private void toggle() {
        if (popup != null) { fadeOut(); return; }
        // An outside DOWN may have just dismissed this popup before this button's UP.
        if (SystemClock.uptimeMillis() - dismissedAt < 300) return;
        if (!anchor.isAttachedToWindow() || !anchor.isShown()
                || panel.activity.isFinishing() || panel.activity.isDestroyed()) return;
        HelpBubble previous = active.get();
        if (previous != null) previous.dismiss();

        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        View root = anchor.getRootView();
        int[] rootScreen = new int[2], rootWindow = new int[2];
        root.getLocationOnScreen(rootScreen);
        root.getLocationInWindow(rootWindow);
        if (!frame.intersect(rootScreen[0], rootScreen[1],
                rootScreen[0] + root.getWidth(), rootScreen[1] + root.getHeight())) return;
        frame.inset(dp(8), dp(8));
        anchor.getLocationOnScreen(initialPosition);
        int centerX = initialPosition[0] + anchor.getWidth() / 2;
        int centerY = initialPosition[1] + anchor.getHeight() / 2;
        // Position relative to the visible 18dp icon, not the larger 48dp touch target.
        int belowY = centerY + dp(13), aboveY = centerY - dp(13);
        int belowSpace = frame.bottom - belowY, aboveSpace = aboveY - frame.top;
        int width = Math.min(dp(300), frame.width());
        if (width < dp(80) || Math.max(belowSpace, aboveSpace) < dp(64)
                || preferAbove && aboveSpace < dp(64)) return;

        TextView text = new TextView(panel.context);
        text.setText(message);
        if (message instanceof android.text.Spanned && ((android.text.Spanned) message)
                .getSpans(0, message.length(), android.text.style.ClickableSpan.class).length > 0) {
            text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            text.setLinksClickable(true);
        }
        text.setTextSize(15);
        text.setTextColor(panel.foreground);
        text.setLineSpacing(dp(4), 1f);
        text.setGravity(Gravity.START);
        text.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        text.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        text.setPadding(dp(14), dp(12), dp(14), dp(12));
        text.measure(View.MeasureSpec.makeMeasureSpec(width - dp(8), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int desiredHeight = text.getMeasuredHeight() + dp(16);
        boolean below = !preferAbove && (desiredHeight <= belowSpace || belowSpace >= aboveSpace);
        int height = Math.min(desiredHeight, below ? belowSpace : aboveSpace);
        int x = Math.max(frame.left, Math.min(centerX - width + dp(28), frame.right - width));
        int y = below ? belowY : aboveY - height;

        ScrollView scroll = new ScrollView(panel.context);
        scroll.setFillViewport(false);
        scroll.addView(text);
        content = new FrameLayout(panel.context);
        content.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        panel.appearance.helpBubble(content, below, centerX - x);
        panel.appearance.frostHelpBubble(content, panel.activity, root, x, y, width, height);
        content.setPadding(dp(4), dp(below ? 12 : 4), dp(4), dp(below ? 4 : 12));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        popup = new PopupWindow(content, width, height, false);
        panel.appearance.transparentPopup(popup);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAnimationStyle(0);
        popup.setTouchInterceptor((v, event) -> {
            // Consume outside events so PopupWindow cannot remove its decor before the fade.
            boolean outsideDown = event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && (event.getX() < 0 || event.getX() >= v.getWidth()
                    || event.getY() < 0 || event.getY() >= v.getHeight());
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE || outsideDown) {
                fadeOut();
                return true;
            }
            if (fadingOut) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    handler.removeCallbacks(timeout);
                    content.animate().cancel();
                    content.setAlpha(1f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: schedule(); break;
            }
            return false;
        });
        duration = Math.max(5000L, Math.min(30000L, message.length() * 180L));
        AccessibilityManager accessibility = (AccessibilityManager) panel.context.getSystemService(
                android.content.Context.ACCESSIBILITY_SERVICE);
        if (accessibility != null && android.os.Build.VERSION.SDK_INT >= 29)
            duration = accessibility.getRecommendedTimeoutMillis((int) duration,
                    AccessibilityManager.FLAG_CONTENT_TEXT);
        // Let screen-reader users finish reading and dismiss by tapping outside.
        if (accessibility != null && accessibility.isTouchExplorationEnabled()) duration = 0;
        popup.setOnDismissListener(this::cleanup);
        anchor.addOnAttachStateChangeListener(lifetime);
        observer = anchor.getViewTreeObserver();
        observer.addOnPreDrawListener(positionWatcher);
        active = new WeakReference<>(this);
        try {
            popup.showAtLocation(root, Gravity.TOP | Gravity.LEFT,
                    x - (rootScreen[0] - rootWindow[0]), y - (rootScreen[1] - rootWindow[1]));
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(140).start();
            text.announceForAccessibility(message);
            schedule();
        } catch (android.view.WindowManager.BadTokenException | IllegalStateException error) {
            dismiss();
        }
    }

    private void schedule() {
        handler.removeCallbacks(timeout);
        if (popup != null && !fadingOut && duration > 0) handler.postDelayed(timeout, duration);
    }

    private void fadeOut() {
        if (content == null || fadingOut) return;
        fadingOut = true;
        handler.removeCallbacks(timeout);
        content.animate().cancel();
        content.animate().alpha(0f).setDuration(180).withEndAction(this::dismiss).start();
    }

    private void dismiss() {
        if (popup != null) {
            PopupWindow window = popup;
            window.dismiss();
            if (popup != null) cleanup(); // Also clean up if showAtLocation failed.
        }
    }

    private void cleanup() {
        handler.removeCallbacks(timeout);
        if (content != null) content.animate().cancel();
        anchor.removeOnAttachStateChangeListener(lifetime);
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(positionWatcher);
        observer = null;
        popup = null;
        content = null;
        fadingOut = false;
        dismissedAt = SystemClock.uptimeMillis();
        if (active.get() == this) active.clear();
    }

}
