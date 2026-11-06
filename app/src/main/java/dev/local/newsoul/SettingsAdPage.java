package dev.local.newsoul;

import android.content.SharedPreferences;
import android.widget.LinearLayout;

import static dev.local.newsoul.SettingsUi.*;

/** Category content; shared navigation, styling and controls remain in SettingsUi. */
final class SettingsAdPage {
    private SettingsAdPage() {}

    static void populate(MenuPanel panel) {
        SharedPreferences prefs = PluginPreferences.get(panel.activity);
        panel.help = "勾选即屏蔽广告\n点分组更多或长按标题可批量操作，跳过未就绪项\n已显示的广告可能需重进页面或重启 Soul";
        String[][] groups = {
                {"启动与浮层", "block_splash", "ad_float", "ad_interstitial"},
                {"广场与浏览", "ad_feed", "ad_detail", "ad_video", "ad_tag", "ad_tag_web", "ad_search"},
                {"聊天与通知", "ad_group", "ad_conversation", "ad_envelope", "ad_notice", "ad_chat_tools"},
                {"其他页面推广", "ad_planet", "ad_me"},
                {"激励广告", "ad_reward_chat", "ad_reward_drama", "ad_reward_web"}
        };
        java.util.Map<String, String[]> items = new java.util.HashMap<>();
        for (String[] item : AdControls.ITEMS) items.put(item[0], item);
        for (String[] group : groups) {
            boolean supported = false;
            for (int i = 1; i < group.length; i++) if (AdControls.supported(group[i])) supported = true;
            if (!supported) continue;
            java.util.List<SettingsMultiChoice.Choice> choices = new java.util.ArrayList<>();
            LinearLayout body = section(panel, group[0], null,
                    anchor -> SettingsMultiChoice.showMenu(panel, anchor, prefs, choices, "屏蔽本组", "取消屏蔽本组"));
            if ("激励广告".equals(group[0])) addSummary(body, "屏蔽后不会获得奖励或解锁内容");
            for (int i = 1; i < group.length; i++) {
                if (!AdControls.supported(group[i])) continue;
                String[] item = java.util.Objects.requireNonNull(items.get(group[i]), group[i]);
                java.util.function.BooleanSupplier ready = () -> "block_splash".equals(item[0])
                        ? SplashBlocker.installed && SplashBlocker.hotInstalled
                        : AdControls.ready.contains(item[0]);
                choices.add(SettingsMultiChoice.add(panel, body, prefs, item[0], item[1], item[2], "勾选表示屏蔽", ready));
            }
            SettingsMultiChoice.finish(panel, body);
        }
    }

}
