package dev.local.newsoul;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;
import dev.local.newsoul.SettingsUi.MenuSwitch;

final class AccountAutomationUi {
    static void addSign(SettingsUi.MenuPanel panel) {
        SharedPreferences prefs = PluginPreferences.get(panel.activity);
        java.util.List<SettingsMultiChoice.Choice> choices = new java.util.ArrayList<>();
        LinearLayout body = SettingsUi.section(panel, "签到", null, anchor ->
                SettingsMultiChoice.showMenu(panel, anchor, prefs, choices, "开启本组", "关闭本组"));
        addSignChoice(panel, body, prefs, choices, AccountAutomationControls.SIGN, "Soul币签到",
                () -> AccountAutomationControls.signStatus, () -> HostAccountHttp.ready);
        for (String[] item : SignInTasks.ITEMS) {
            String key = item[0];
            addSignChoice(panel, body, prefs, choices, key, item[1], () -> SignInAutomation.status(key),
                    () -> HostAccountHttp.ready && HostWelfareHttp.ready);
        }
        SettingsMultiChoice.finish(panel, body);
    }
    private static void addSignChoice(SettingsUi.MenuPanel panel, LinearLayout body, SharedPreferences prefs,
            java.util.List<SettingsMultiChoice.Choice> choices, String key, String label,
            java.util.function.Supplier<String> state, java.util.function.BooleanSupplier available) {
        choices.add(SettingsMultiChoice.add(panel, body, prefs, key, label, "", "勾选表示开启",
                () -> AccountAutomationControls.ready && available.getAsBoolean()));
        addStatus(panel, body, prefs, key, state, available);
    }
    static void addHide(SettingsUi.MenuPanel panel) {
        addAutomation(panel, SettingsUi.section(panel, "在线状态"), AccountAutomationControls.HIDE, "上线自动隐身",
            () -> AccountAutomationControls.hideStatus, () -> HostAccountHttp.ready);
    }
    private static void addAutomation(SettingsUi.MenuPanel panel, LinearLayout body, String key, String label,
            java.util.function.Supplier<String> state, java.util.function.BooleanSupplier available) {
        SharedPreferences prefs = PluginPreferences.get(panel.activity);
        MenuSwitch toggle = SettingsUi.addSwitch(body, prefs, key, label, "", panel.foreground, panel.secondary);
        SettingsUi.bindAvailability(panel, toggle, () -> AccountAutomationControls.ready && available.getAsBoolean());
        addStatus(panel, body, prefs, key, state, available);
    }
    private static void addStatus(SettingsUi.MenuPanel panel, LinearLayout body, SharedPreferences prefs, String key,
            java.util.function.Supplier<String> state, java.util.function.BooleanSupplier available) {
        TextView status = SettingsUi.label(panel.context, "", 14, panel.secondary);
        body.addView(status);
        Runnable refresh = () -> {
            String value = state.get();
            if (!prefs.getBoolean(key, false)) value = "";
            else if (!available.getAsBoolean()) value = "当前 Soul 版本未就绪";
            else if (!HostAccountHttp.available()) value = "等待登录并连接 Soul";
            else if (value.isEmpty()) value = "等待执行";
            status.setText(value); status.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        };
        panel.addPreferenceRefresher(refresh); refresh.run();
        status.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            final Runnable update = new Runnable() { public void run() {
                if (!status.isAttachedToWindow()) return;
                refresh.run(); status.postDelayed(this, 1500);
            }};
            public void onViewAttachedToWindow(View view) { status.post(update); }
            public void onViewDetachedFromWindow(View view) { status.removeCallbacks(update); }
        });
    }
    static void addDays(SettingsUi.MenuPanel panel, LinearLayout body) {
        SharedPreferences prefs = PluginPreferences.get(panel.activity);
        MenuSwitch enabled = SettingsUi.addSwitch(body, prefs, RegistrationDaysFilter.ENABLED, "按注册天数筛选",
            "", panel.foreground, panel.secondary);
        LinearLayout toggleRow = (LinearLayout) enabled.getParent();
        toggleRow.addView(SettingsUi.reminderButton(panel, "按注册天数筛选",
            "查询注册天数后显示帖子，可能稍慢\n资料缺失或查询失败时保留帖子\n天数至少为 1，上限不能小于下限\n右端拉满或清空上限表示不限"), 1);
        SettingsUi.bindAvailability(panel, enabled, () -> HostAccountHttp.ready && SquareFeedFilter.ready);
        RegistrationRangeUi range = new RegistrationRangeUi(panel, prefs);
        body.addView(range, new LinearLayout.LayoutParams(-1, -2));
        SettingsUi.bindSwitchContent(enabled, range);
    }
}
