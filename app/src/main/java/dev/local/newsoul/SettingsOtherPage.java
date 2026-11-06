package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import android.widget.TextView;
import android.widget.Toast;

import static dev.local.newsoul.SettingsUi.*;

/** Category content; shared navigation, styling and controls remain in SettingsUi. */
final class SettingsOtherPage {
    private static final String TAG = "SoulHook102";
    private SettingsOtherPage() {}

    static void populate(MenuPanel panel) {
        LinearLayout miscellaneous = section(panel, "杂项");
        addSwitch(miscellaneous, PluginPreferences.get(panel.activity), FrostedMenuEffect.ENABLED,
                "毛玻璃效果", "系统不支持或暂停模糊时使用普通背景", panel.foreground, panel.secondary);
        InteractionPrivacyControls.toggle(panel, miscellaneous, ForegroundNotificationControls.ENABLED,
                "进入前台清除通知", () -> ForegroundNotificationControls.ready);
        MenuSwitch teenagePopup = addSwitch(miscellaneous, PluginPreferences.get(panel.activity),
                TeenagePopupControls.ENABLED, "屏蔽青少年弹窗",
                TeenagePopupControls.ready ? "重启 Soul 后生效" : "当前版本未就绪", panel.foreground, panel.secondary);
        bindAvailability(panel, teenagePopup, () -> TeenagePopupControls.ready);
        LocationOverrideUi.addEntry(panel, miscellaneous);
        AccountAutomationUi.addSign(panel);
        Activity activity = panel.activity;
        boolean dark = panel.dark;
        Context themed = panel.context;
        LinearLayout body = panel.body;
        int foreground = panel.foreground, secondary = panel.secondary;
        SharedPreferences prefs = PluginPreferences.get(activity);
        body = section(panel, "版本与检测");
        MenuSwitch updatePopup = addSwitch(body, prefs, SoulUpdateControls.ENABLED, "屏蔽 Soul 更新弹窗",
                SoulUpdateControls.ready ? "" : "当前版本未就绪", foreground, secondary);
        bindAvailability(panel, updatePopup, () -> SoulUpdateControls.ready);
        try {
            Context module = activity.createPackageContext(VariantFeatures.PACKAGE_ID, 0);
            android.content.pm.PackageInfo packageInfo = module.getPackageManager()
                    .getPackageInfo(module.getPackageName(), 0);
            body.addView(label(themed, "版本 " + packageInfo.versionName, 15, foreground));
        } catch (Exception ignored) {
            body.addView(label(themed, "版本信息暂不可用", 15, secondary));
        }
        TextView update = UpdateCheckUi.createAction(panel);
        TextView diagnostics = label(themed, "功能兼容性检测", 15, panel.accentText);
        buttonSemantics(diagnostics);
        diagnostics.setContentDescription("检测当前 Soul 版本的功能兼容性");
        diagnostics.setOnClickListener(v -> showHookDiagnostics(activity, dark, false));
        addInfoActionPair(panel, body, update, diagnostics);
        body = section(panel, "交流与支持");
        TextView group = label(themed, "加入 TG 群组", 15, panel.accentText);
        buttonSemantics(group);
        group.setContentDescription("加入 TG 群组，打开 t.me 链接");
        group.setOnClickListener(v -> {
            try {
                activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://t.me/+3Hl2lJ1X-oZlZTZl")));
            } catch (android.content.ActivityNotFoundException error) {
                Toast.makeText(activity, "没有可打开链接的应用", Toast.LENGTH_SHORT).show();
            }
        });
        TextView donation = label(themed, "支持作者", 15, panel.accentText);
        buttonSemantics(donation);
        donation.setOnClickListener(v -> DonationUi.show(activity, dark));
        addInfoActionPair(panel, body, group, donation);
        body = section(panel, "运行选项");
            addSwitch(body, prefs, "page_log", "记录页面日志",
                    "用于排查页面问题，日志包含页面类名", foreground, secondary);
            MenuSwitch startup = addSwitch(body, prefs, "startup_toast", "显示启动提示",
                    "", foreground, secondary);
            attachSwitchPreview(startup, panel, () -> {
                Toast.makeText(activity, "提示预览：New Soul 已生效", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "settings preview clicked");
            });
        TextView debug = label(themed, "调试", 15, panel.accentText);
        buttonSemantics(debug);
        debug.setOnClickListener(v -> DebugUi.show(activity, dark));
        styleInfoAction(panel, debug);
        body.addView(debug, new LinearLayout.LayoutParams(-1, -2));
        ConfigTransferUi.addRows(panel);
    }

    static void showHookDiagnostics(Activity activity, boolean dark, boolean automatic) {
        showHookDiagnostics(activity, dark, automatic, null);
    }

    static void showHookDiagnostics(Activity activity, boolean dark,
            boolean automatic, String previousVersion) {
        MenuPanel progress = new MenuPanel(activity, dark);
        LinearLayout loading = section(progress, automatic ? "检测到 Soul 版本变化" : "正在检测功能兼容性");
        android.widget.ProgressBar indicator = new android.widget.ProgressBar(progress.context);
        loading.addView(indicator, new LinearLayout.LayoutParams(-1, dp(activity, 56)));
        loading.addView(label(progress.context, "正在检查当前 Soul 版本的功能适配状态…",
                13, progress.secondary));
        progress.builder.setNeutralButton("重新检测", null);
        progress.show("功能兼容性检测", "关闭");
        progress.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        progress.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            progress.dialog.dismiss();
            showHookDiagnostics(activity, dark, automatic, previousVersion);
        });
        long started = android.os.SystemClock.elapsedRealtime();
        new Thread(() -> {
            HookDiagnostics.Result result;
            try { result = HookDiagnostics.scan(activity); }
            catch (Throwable error) {
                Log.e(TAG, "Hook diagnostics failed", error);
                activity.runOnUiThread(() -> {
                    if (!progress.dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                    loading.removeAllViews();
                    loading.addView(label(progress.context, "检测失败，请重新检测", 16, progress.foreground));
                    loading.addView(label(progress.context, "未能完成本次检查，已有设置未改变", 13, progress.secondary));
                    progress.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);
                    progress.dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                });
                return;
            }
            if (automatic) result = new HookDiagnostics.Result(
                    result.version, previousVersion, result.lines, result.missing);
            HookDiagnostics.Result shown = result;
            long remaining = Math.max(0, 450 - (android.os.SystemClock.elapsedRealtime() - started));
            activity.getWindow().getDecorView().postDelayed(() -> {
                if (!progress.dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                progress.dialog.dismiss();
                showHookReport(activity, dark, shown, automatic);
            }, remaining);
        }, "NewSoul-HookScan").start();
    }

    private static void showHookReport(Activity activity, boolean dark,
            HookDiagnostics.Result result, boolean automatic) {
        MenuPanel panel = new MenuPanel(activity, dark);
        LinearLayout body = section(panel, automatic ? "Soul 更新检测结果" : "宿主兼容检测",
                "仅检查功能适配，实际效果需到对应页面确认");
        TextView summary = label(panel.context, result.summary(), 16, panel.foreground);
        summary.setTextIsSelectable(true);
        body.addView(summary);
        String issues = result.entries(true).replace("Hook 点缺失", "适配入口缺失")
                .replace("Hook 未就绪", "尚未就绪")
                .replace(" · 请查看 SoulHook102 安装日志", "")
                .replace("签名存在 · ", "").replaceAll("（[^）]*）", "");
        if (!issues.isEmpty()) {
            TextView issueText = label(panel.context, issues, 14, panel.foreground);
            issueText.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
            issueText.setTextIsSelectable(true);
            body.addView(issueText);
        }
        addReportDisclosure(panel, body, "检测详情", result.report());
        TextView copy = label(panel.context, "复制检测结果", 15, panel.accentText);
        copy.setGravity(Gravity.CENTER);
        copy.setMinHeight(dp(activity, 48));
        buttonSemantics(copy);
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("New Soul 功能兼容性检测", result.report()));
            Toast.makeText(activity, "检测结果已复制", Toast.LENGTH_SHORT).show();
        });
        body.addView(copy);
        panel.builder.setNeutralButton("重新检测", (dialog, which) ->
                showHookDiagnostics(activity, dark, false));
        panel.show("功能兼容性检测", "返回");
        HookDiagnostics.markShown(activity, result.version);
    }

    private static void addReportDisclosure(MenuPanel panel, LinearLayout body, String title, String content) {
        if (content.isEmpty()) return;
        TextView action = label(panel.context, title, 15, panel.accentText);
        action.setMinHeight(dp(panel.context, 48));
        action.setGravity(Gravity.CENTER_VERTICAL);
        buttonSemantics(action);
        TextView detail = label(panel.context, content, 13, panel.foreground);
        detail.setTextIsSelectable(true);
        detail.setVisibility(View.GONE);
        action.setContentDescription(title + "，展开");
        action.setOnClickListener(v -> {
            boolean expand = detail.getVisibility() != View.VISIBLE;
            detail.setVisibility(expand ? View.VISIBLE : View.GONE);
            action.setText(expand ? "收起" + title : title);
            action.setContentDescription(title + (expand ? "，收起" : "，展开"));
        });
        body.addView(action, new LinearLayout.LayoutParams(-1, -2));
        body.addView(detail, new LinearLayout.LayoutParams(-1, -2));
    }

}
