package dev.local.newsoul;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class UpdateCheckUi {
    private UpdateCheckUi() {}
    static TextView createAction(SettingsUi.MenuPanel parent) {
        TextView row = SettingsUi.label(parent.context, "检查更新", 15, parent.accentText);
        SettingsUi.buttonSemantics(row);
        row.setOnClickListener(v -> show(parent.activity, parent.dark));
        return row;
    }
    static void show(Activity activity, boolean dark) {
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(activity, dark);
        boolean internal = VariantFeatures.hasAi();
        String installed;
        try {
            installed = activity.getPackageManager().getPackageInfo(VariantFeatures.PACKAGE_ID, 0).versionName;
            LanzouUpdateClient.compare(installed, installed);
        } catch (Exception error) { installed = null; }
        final String current = installed;
        panel.help = "蓝奏云提取码：" + LanzouUpdateClient.PASSWORD
                + "\n解压 ZIP，用其中的 APK 覆盖安装；检查更新不会自动下载安装"
                + (internal ? "\n此安装版本请通过原渠道更新，蓝奏云仅供查看发布信息" : "");
        panel.body.addView(SettingsUi.label(panel.context,
                "当前版本：" + (current == null ? "未知" : current), 15, panel.foreground));
        TextView status = SettingsUi.label(panel.context, "正在检查…", 14, panel.foreground);
        status.setPadding(0, SettingsUi.dp(panel.context, 8), 0, SettingsUi.dp(panel.context, 8));
        panel.body.addView(status);
        final String[] download = {null};
        action(panel, panel.body, "蓝奏云",
                () -> open(activity, download[0] == null ? LanzouUpdateClient.FOLDER : download[0]));
        panel.builder.setNegativeButton("重新检查", null);
        panel.show("检查更新", "返回");
        TextView retry = panel.dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
        final Thread[] worker = {null};
        Runnable start = () -> {
            retry.setEnabled(false);
            retry.setAlpha(0.5f);
            download[0] = null;
            status.setText("正在检查…");
            worker[0] = new Thread(() -> {
                LanzouUpdateClient.Release found = null;
                String error = null;
                try { found = LanzouUpdateClient.check(); }
                catch (Exception failure) {
                    DebugDiagnostics.failure("update.check", failure);
                    error = "检测失败，请重试或打开发布页";
                }
                final LanzouUpdateClient.Release result = found;
                final String failure = error;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || panel.dialog == null || !panel.dialog.isShowing()) return;
                    retry.setEnabled(true);
                    retry.setAlpha(1f);
                    if (failure != null) { status.setText(failure); return; }
                    if (internal) status.setText("发布渠道版本：" + result.version);
                    else if (current == null) status.setText("最新版本：" + result.version + "\n当前版本未知");
                    else {
                        int comparison = LanzouUpdateClient.compare(result.version, current);
                        status.setText("最新版本：" + result.version + "\n" + (comparison > 0 ? "发现新版本"
                                : comparison == 0 ? "当前已是最新版" : "当前版本高于发布版本"));
                    }
                    download[0] = result.url.substring(0, result.url.lastIndexOf('/')) + LanzouUpdateClient.FOLDER_PATH;
                    if (internal || current == null || LanzouUpdateClient.compare(result.version, current) > 0) {
                        download[0] = result.url;
                    }
                });
            }, "NewSoul-UpdateCheck");
            worker[0].start();
        };
        retry.setOnClickListener(v -> start.run());
        panel.dialog.setOnDismissListener(dialog -> { if (worker[0] != null) worker[0].interrupt(); });
        start.run();
    }
    private static TextView action(SettingsUi.MenuPanel panel, LinearLayout body, String title, Runnable run) {
        TextView row = SettingsUi.label(panel.context, title, 15, panel.accentText);
        row.setMinHeight(SettingsUi.dp(panel.context, 52));
        row.setGravity(Gravity.CENTER);
        SettingsUi.buttonSemantics(row);
        row.setOnClickListener(v -> run.run());
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }
    private static void open(Activity activity, String url) {
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (RuntimeException error) { Toast.makeText(activity, "无法打开浏览器，请稍后重试", Toast.LENGTH_SHORT).show(); }
    }
}
