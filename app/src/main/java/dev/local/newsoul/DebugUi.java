package dev.local.newsoul;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import dev.local.newsoul.SettingsUi.MenuSwitch;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Shared settings controls and SAF export; never sends diagnostic files automatically. */
final class DebugUi {
    private static XposedModule module;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<Method> HOOKED = new HashSet<>();
    private static final WeakHashMap<Activity,Pending> PENDING = new WeakHashMap<>();
    private static final Random RANDOM = new java.security.SecureRandom();
    private static final class Pending {
        final int code = 0x4000 + RANDOM.nextInt(0x1000);
        final WeakReference<Ui> ui;
        byte[] bytes;
        boolean choosing;
        Pending(Ui ui) { this.ui = new WeakReference<>(ui); }
    }
    static void install(XposedModule value) { module = value; }
    static void show(Activity activity, boolean dark) {
        DebugDiagnostics.init(activity);
        new Ui(activity, dark);
    }
    private static final class Ui {
        final SettingsUi.MenuPanel panel;
        final MenuSwitch toggle;
        final TextView status, result, export, clear;
        boolean refreshing, busy;
        Ui(Activity activity, boolean dark) {
            panel = new SettingsUi.MenuPanel(activity, dark);
            panel.help = "开启调试 → 复现问题 → 导出给作者\n"
                    + "启动问题需开启后重启 Soul\n达到 30 分钟或 4 MB 自动停止\n关闭保留记录，清除会停止调试"
                    + "\n仅记录版本、页面、功能状态和错误，不含聊天正文、语音、凭据或密钥";
            LinearLayout body = SettingsUi.section(panel, "记录");
            toggle = SettingsUi.addSwitch(body, null, "debug_session", "调试模式", "", panel.foreground, panel.secondary);
            status = SettingsUi.addSummary(body, "");
            export = SettingsUi.label(panel.context, "导出记录", 15, panel.accentText);
            clear = SettingsUi.label(panel.context, "清除记录", 15, panel.accentText);
            SettingsUi.buttonSemantics(export); SettingsUi.buttonSemantics(clear);
            SettingsUi.addInfoActionPair(panel, body, export, clear);
            result = SettingsUi.addSummary(body, ""); result.setVisibility(android.view.View.GONE);
            result.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (refreshing || busy) return;
                busy = true; refresh();
                DebugDiagnostics.setEnabled(checked, () -> { busy = false; refresh(); });
            });
            export.setOnClickListener(v -> launch(this));
            clear.setOnClickListener(v -> {
                busy = true; refresh();
                DebugDiagnostics.clear(() -> { busy = false; result.setVisibility(android.view.View.GONE); refresh(); });
            });
            panel.show("调试", "关闭");
            refresh();
            MAIN.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!panel.dialog.isShowing()) return;
                    refresh(); MAIN.postDelayed(this, 1000);
                }
            }, 1000);
        }
        void refresh() {
            refreshing = true; toggle.setChecked(DebugDiagnostics.active()); refreshing = false;
            status.setText(DebugDiagnostics.status());
            boolean waiting = busy || PENDING.containsKey(panel.activity);
            toggle.setEnabled(!waiting);
            export.setEnabled(!waiting && DebugDiagnostics.hasRecords());
            clear.setEnabled(!waiting && (DebugDiagnostics.hasRecords() || DebugDiagnostics.active()));
            export.setAlpha(export.isEnabled() ? 1 : .5f); clear.setAlpha(clear.isEnabled() ? 1 : .5f);
        }
        void message(String text) { result.setText(text); result.setVisibility(android.view.View.VISIBLE); refresh(); }
    }
    private static void launch(Ui ui) {
        Activity activity = ui.panel.activity;
        if (PENDING.containsKey(activity)) return;
        Pending pending = new Pending(ui); PENDING.put(activity, pending);
        ui.message("正在准备记录…");
        DebugDiagnostics.IO.execute(() -> {
            try {
                byte[] bytes = DebugDiagnostics.export();
                MAIN.post(() -> {
                    if (activity.isDestroyed() || activity.isFinishing() || !ui.panel.dialog.isShowing()) {
                        finish(activity, pending, "已取消导出"); return;
                    }
                    try {
                        ensureResultHook(activity);
                        pending.bytes = bytes; pending.choosing = true;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("application/octet-stream")
                                .putExtra(Intent.EXTRA_TITLE, "NewSoul-debug-" + new java.text.SimpleDateFormat(
                                        "yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".nsdebug");
                        activity.startActivityForResult(intent, pending.code);
                        ui.message("请选择保存位置");
                    } catch (Exception error) { finish(activity, pending, "无法打开保存位置，请重试"); }
                });
            } catch (Exception error) { MAIN.post(() -> finish(activity, pending, "导出失败，请确认已有记录后重试")); }
        });
    }
    private static void ensureResultHook(Activity activity) throws Exception {
        if (module == null) throw new IllegalStateException();
        Class<?> type = activity.getClass(); Method callback = null;
        while (type != null) {
            try { callback = type.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class); break; }
            catch (NoSuchMethodException ignored) { type = type.getSuperclass(); }
        }
        if (callback == null) throw new NoSuchMethodException();
        if (HOOKED.contains(callback)) return;
        module.hook(callback).setId("new-soul/debug-export-result")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Activity owner = (Activity) chain.getThisObject(); Pending pending = PENDING.get(owner);
                    if (pending == null || pending.code != (int) chain.getArg(0)) return chain.proceed();
                    if (!pending.choosing) return null;
                    pending.choosing = false;
                    Intent data = (Intent) chain.getArg(2);
                    if ((int) chain.getArg(1) != Activity.RESULT_OK || data == null || data.getData() == null) {
                        finish(owner, pending, "已取消导出"); return null;
                    }
                    android.net.Uri uri = data.getData();
                    Ui ui = pending.ui.get(); if (ui != null) ui.message("正在保存记录…");
                    DebugDiagnostics.IO.execute(() -> {
                        String message;
                        try {
                            if (!"content".equals(uri.getScheme())) throw new java.io.IOException();
                            try (java.io.OutputStream out = owner.getContentResolver().openOutputStream(uri, "wt")) {
                                if (out == null) throw new java.io.IOException(); out.write(pending.bytes); out.flush();
                            }
                            message = "记录已导出，可发送给作者";
                        } catch (Exception error) { message = "保存失败，请重新选择位置"; }
                        final String result = message; MAIN.post(() -> finish(owner, pending, result));
                    });
                    return null;
                });
        HOOKED.add(callback);
    }
    private static void finish(Activity activity, Pending pending, String text) {
        if (PENDING.get(activity) != pending) return;
        PENDING.remove(activity); pending.bytes = null;
        Ui ui = pending.ui.get();
        if (ui != null && !activity.isDestroyed()) ui.message(text);
    }
}
