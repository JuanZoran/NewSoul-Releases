package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bundled payment codes; saving and opening another app require an explicit button tap. */
final class DonationUi {
    private static final String[] RESOURCES = {"donation_wechat", "donation_alipay"};
    private static final String[] NAMES = {"微信", "支付宝"};
    private static final String[] PACKAGES = {"com.tencent.mm", "com.eg.android.AlipayGphone"};
    private static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DonationUi() {}

    static void show(Activity activity, boolean dark) {
        new Page(activity, dark).show();
    }

    private static final class Page {
        final Activity activity;
        final SettingsUi.MenuPanel panel;
        final SegmentedChoice choices;
        final ImageView code;
        final TextView status;
        int selected = -1;
        boolean saving;
        boolean loading;
        int requested;
        int generation;
        // Reuse a successfully saved code when retrying an app launch within this page.
        final android.net.Uri[] saved = new android.net.Uri[2];

        Page(Activity activity, boolean dark) {
            this.activity = activity;
            panel = new SettingsUi.MenuPanel(activity, dark);
            LinearLayout content = new SwipeContent(panel.context, this::select);
            panel.body.addView(content, new LinearLayout.LayoutParams(-1, -2));
            choices = new SegmentedChoice(panel, null, NAMES, 15);
            choices.setContentDescription("收款方式");
            content.addView(choices, new LinearLayout.LayoutParams(-1, -2));
            status = SettingsUi.label(panel.context, "", 14, panel.secondary);
            status.setVisibility(View.INVISIBLE);
            status.setGravity(Gravity.CENTER);
            status.setMinLines(1);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            status.setPadding(0, SettingsUi.dp(panel.context, 12), 0, 0);
            content.addView(status, new LinearLayout.LayoutParams(-1, -2));
            code = new ImageView(panel.context);
            code.setAdjustViewBounds(false);
            code.setScaleType(ImageView.ScaleType.FIT_CENTER);
            code.setVisibility(View.INVISIBLE);
            // A stable image viewport prevents differently sized payment images resizing the dialog.
            int size = Math.min(SettingsUi.dp(panel.context, 300),
                    Math.max(SettingsUi.dp(panel.context, 120),
                    activity.getResources().getDisplayMetrics().heightPixels / 3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, size);
            params.topMargin = SettingsUi.dp(panel.context, 14);
            content.addView(code, params);
            choices.onSelection(this::select);
        }

        void show() {
            panel.builder.setNegativeButton("保存并打开", (dialog, which) -> {});
            panel.show("支持作者", "返回");
            TextView action = panel.dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            action.setSingleLine(false);
            action.setMaxLines(Integer.MAX_VALUE);
            action.setVisibility(View.VISIBLE);
            action.setEnabled(false);
            action.setOnClickListener(v -> {
                if (selected < 0) select(requested);
                else saveAndOpen();
            });
            panel.dialog.setOnDismissListener(dialog -> {
                generation++;
                code.animate().cancel();
                code.setImageDrawable(null);
            });
            select(0);
        }

        void select(int index) {
            if (saving || (index == requested && (selected == index || loading))) return;
            loading = true;
            requested = index;
            selected = -1;
            int request = ++generation;
            choices.selectValue(RESOURCES[index], RESOURCES, true);
            code.animate().cancel();
            code.setAlpha(1f);
            // Keep the last preview in place until its replacement is ready.
            TextView action = panel.dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            action.setVisibility(View.VISIBLE);
            action.setEnabled(false);
            message("正在加载…");
            // Do not capture the page while decoding; only delivery needs the weak reference.
            java.lang.ref.WeakReference<Page> target = new java.lang.ref.WeakReference<>(this);
            IO.execute(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = DonationImages.preview(index);
                } catch (Exception | OutOfMemoryError failure) {
                    android.util.Log.w("SoulHook102", "Donation image load failed: " + index, failure);
                    DebugDiagnostics.failure("donation.load", "index=" + index, failure);
                }
                final Bitmap result = bitmap;
                MAIN.post(() -> {
                    Page page = target.get();
                    if (page == null || page.generation != request || !page.visible()) {
                        if (result != null) result.recycle();
                        return;
                    }
                    page.loaded(index, result);
                });
            });
        }

        void loaded(int index, Bitmap bitmap) {
            loading = false;
            selected = bitmap == null ? -1 : index;
            boolean replacing = code.getDrawable() != null;
            code.setImageBitmap(bitmap);
            code.setVisibility(bitmap == null ? View.INVISIBLE : View.VISIBLE);
            code.setContentDescription(NAMES[index] + "收款码");
            if (bitmap != null && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                code.setAlpha(replacing ? .7f : 0f);
                code.animate().alpha(1f).setDuration(160).start();
            }
            TextView action = panel.dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            action.setVisibility(View.VISIBLE);
            action.setEnabled(true);
            action.setText(bitmap == null ? "重新加载" : "保存并打开" + NAMES[index]);
            message(bitmap == null ? "收款码加载失败，点击重新加载" : "");
        }

        void saveAndOpen() {
            if (saving || selected < 0) return;
            int index = selected;
            saving = true;
            panel.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            message("正在保存…");
            Context context = activity.getApplicationContext();
            IO.execute(() -> {
                android.net.Uri uri = saved[index];
                String error = null;
                try {
                    if (uri == null || !DonationCodeStore.exists(context, uri))
                        uri = DonationCodeStore.save(context, index);
                } catch (Exception failure) {
                    DebugDiagnostics.failure("donation.save", "index=" + index, failure);
                    error = failure instanceof SecurityException
                            ? "保存失败，请检查 Soul 的图片存储权限"
                            : "保存失败，请检查存储空间后重试";
                }
                final android.net.Uri result = uri;
                final String problem = error;
                MAIN.post(() -> {
                    saving = false;
                    if (problem == null) saved[index] = result;
                    // Closing this page or leaving Soul during save must not open another app later.
                    if (!visible()) return;
                    panel.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    if (problem != null) { message(problem); return; }
                    message("已保存到相册");
                    if (!panel.dialog.getWindow().getDecorView().hasWindowFocus()) return;
                    openApp(index);
                });
            });
        }

        boolean visible() {
            return !activity.isFinishing() && !activity.isDestroyed() && panel.dialog.isShowing();
        }

        void openApp(int index) {
            try {
                if (index == 0) {
                    // Use the verified WeChat launcher directly. The system IntentSender
                    // route can be intercepted by OEM multi-app dispatch before launch.
                    Intent intent = Intent.makeMainActivity(new android.content.ComponentName(
                            PACKAGES[index], "com.tencent.mm.ui.LauncherUI"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    activity.startActivity(intent);
                } else if (Build.VERSION.SDK_INT >= 33) {
                    activity.getPackageManager().getLaunchIntentSenderForPackage(PACKAGES[index])
                            .sendIntent(activity, 0, null, null, null);
                } else {
                    Intent intent = activity.getPackageManager().getLaunchIntentForPackage(PACKAGES[index]);
                    if (intent == null) throw new android.content.ActivityNotFoundException();
                    activity.startActivity(intent);
                }
            } catch (Exception unavailable) {
                android.util.Log.w("SoulHook102", "Donation app launch failed: " + PACKAGES[index], unavailable);
                message("收款码已保存，无法打开" + NAMES[index] + "，请确认已安装或手动打开");
            }
        }

        void message(String value) {
            status.setText(value);
            status.setVisibility(value.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /** Intercept horizontal drags while leaving taps and vertical scrolling to the panel. */
    private static final class SwipeContent extends LinearLayout {
        private final java.util.function.IntConsumer select;
        private final int slop;
        private float downX, downY;
        private boolean horizontal, cancelled;

        SwipeContent(Context context, java.util.function.IntConsumer select) {
            super(context);
            this.select = select;
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            setOrientation(VERTICAL);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                horizontal = false;
                cancelled = false;
            } else if (event.getPointerCount() > 1) {
                cancelled = true;
            }
            return super.dispatchTouchEvent(event);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !cancelled) {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dy > slop && dy >= dx) cancelled = true;
                if (dx > slop && dx > dy && !cancelled) {
                    horizontal = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            // Images and blank space have no click listener, so track those drags here too.
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !horizontal)
                onInterceptTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                float dx = event.getX() - downX;
                if (event.getActionMasked() == MotionEvent.ACTION_UP && horizontal && !cancelled
                        && Math.abs(dx) >= Math.max(slop * 2, SettingsUi.dp(getContext(), 48)))
                    select.accept(dx < 0 ? 1 : 0);
                getParent().requestDisallowInterceptTouchEvent(false);
                horizontal = false;
            }
            return true;
        }
    }

}
