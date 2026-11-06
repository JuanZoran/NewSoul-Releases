package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;

import android.widget.TextView;

import static dev.local.newsoul.SettingsUi.*;

/** Category content; shared navigation, styling and controls remain in SettingsUi. */
final class SettingsSquarePage {
    private SettingsSquarePage() {}

    static void populate(MenuPanel panel) {
        Activity activity = panel.activity;
        boolean dark = panel.dark;
        Context themed = panel.context;
        LinearLayout body = panel.body;
        int foreground = panel.foreground, secondary = panel.secondary;
        SharedPreferences prefs = PluginPreferences.get(activity);
        body = section(panel, "帖子显示");
        MenuSwitch timeOrder = addSwitch(body, prefs, SquareTimeOrder.ENABLED, "按发布时间排序",
                "", foreground, secondary);
        LinearLayout timeOrderRow = (LinearLayout) timeOrder.getParent();
        timeOrderRow.addView(reminderButton(panel, "按发布时间排序",
                "已加载帖子按发布时间从新到旧排列\n可能造成帖子闪烁"), 1);
        bindAvailability(panel, timeOrder, () -> SquareTimeOrder.ready);
        MenuSwitch ipLocation = addSwitch(body, prefs, SquareIpLocation.ENABLED, "显示 IP 属地",
                "", foreground, secondary);
        bindAvailability(panel, ipLocation, () -> SquareIpLocation.ready && SquareFeedFilter.ready);
        MenuSwitch registrationDays = addSwitch(body, prefs, RegistrationDaysFilter.DISPLAY, "显示注册天数",
                "", foreground, secondary);
        bindAvailability(panel, registrationDays, () -> HostAccountHttp.ready && SquareFeedFilter.ready);
        FeedLabelStyleUi.attach(panel, body, ipLocation, registrationDays);
        body = section(panel, "帖子筛选");
        MenuSwitch enabled = addSwitch(body, prefs, SquareFeedFilter.ENABLED, "按性别筛选",
                ""
                + (SquareFeedFilter.ready ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, enabled, () -> SquareFeedFilter.ready);
        LinearLayout genderOptions = new LinearLayout(themed);
        genderOptions.setOrientation(LinearLayout.VERTICAL);
        genderOptions.setTag(panel);
        body.addView(genderOptions, new LinearLayout.LayoutParams(-1, -2));
        addGenderOptions(panel, genderOptions, prefs, enabled);
        bindSwitchContent(enabled, genderOptions);

        MenuSwitch contentFilter = addSwitch(body, prefs, SquareFeedFilter.CONTENT_ENABLED,
                "内容筛选", "", foreground, secondary);
        bindAvailability(panel, contentFilter, () -> SquareFeedFilter.ready && SquareFeedFilter.contentReady);
        FeedContentChoice contentChoices = new FeedContentChoice(panel, prefs);
        body.addView(contentChoices, new LinearLayout.LayoutParams(-1, -2));
        bindSwitchContent(contentFilter, contentChoices);

        AccountAutomationUi.addDays(panel, body);
        InteractionPrivacyControls.toggle(panel, body, SquareFeedFilter.VIP,
                "去除 VIP 用户", () -> SquareFeedFilter.ready && SquareFeedFilter.vipReady);

        KeywordFilterUi.add(panel, body, "内容屏蔽词", SquareFeedFilter.KEYWORDS_ENABLED,
                SquareFeedFilter.KEYWORDS, () -> SquareFeedFilter.keywordsReady && SquareFeedFilter.ready,
                "屏蔽词匹配正文、标题、话题，英文不区分大小写\n不识别图片文字、语音或评论");
        KeywordFilterUi.add(panel, body, "用户名屏蔽词", SquareFeedFilter.NAME_KEYWORDS_ENABLED,
                SquareFeedFilter.NAME_KEYWORDS, () -> SquareFeedFilter.nameKeywordsReady && SquareFeedFilter.ready,
                "匹配帖子显示的用户名，包含即过滤，英文不区分大小写\n有备注名时匹配备注名");

        MenuSwitch cityPages = addSwitch(body, prefs, SquareFeedFilter.CITY_PAGES, "在城市二级页生效",
                "", foreground, secondary);
        bindAvailability(panel, cityPages, () -> SquareFeedFilter.cityReady);

        body = section(panel, "点赞");
        MenuSwitch autoLike = addSwitch(body, prefs, SquareLikeControls.AUTO,
                "自动点赞", "广场长按爱心开关快速自动点赞\n失败时自动关闭", foreground, secondary);
        bindAvailability(panel, autoLike, () -> SquareLikeControls.ready && LoadedPostLikes.ready);
        MenuSwitch squareLike = addSwitch(body, prefs, SquareLikeControls.ENABLED,
                "一键点赞", "", foreground, secondary);
        bindAvailability(panel, squareLike, () -> SquareLikeControls.ready);
        MenuSwitch allLoaded = addSwitch(body, prefs, SquareLikeControls.ALL_LOADED,
                "包含屏幕外已加载帖子", "", foreground, secondary);
        bindAvailability(panel, allLoaded, () -> SquareLikeControls.ready && LoadedPostLikes.ready);
        MenuSwitch cityLikes = addSwitch(body, prefs, SquareLikeControls.CITY_PAGES,
                "在城市二级页生效", "", foreground, secondary);
        bindAvailability(panel, cityLikes, () -> SquareLikeControls.ready && LoadedPostLikes.cityReady);
        View rangeRow = (View) allLoaded.getParent();
        LinearLayout.LayoutParams rangeParams = (LinearLayout.LayoutParams) rangeRow.getLayoutParams();
        rangeParams.leftMargin = dp(themed, 16);
        rangeRow.setLayoutParams(rangeParams);
        int rangeIndex = body.indexOfChild(rangeRow);
        body.removeView(rangeRow);
        LocalReveal range = revealRow(panel, rangeRow, 160);
        body.addView(range, rangeIndex, new LinearLayout.LayoutParams(-1, -2));
        TextView likeState = addSummary(body, "");
        Runnable refreshSummary = () -> {
            boolean automatic = autoLike.isEnabled() && autoLike.isChecked();
            boolean manual = squareLike.isEnabled() && squareLike.isChecked();
            boolean loaded = allLoaded.isEnabled() && allLoaded.isChecked();
            String state;
            if (automatic) {
                state = "自动点赞已开启 · 范围：已加载的全部帖子\n一键点赞配置暂不生效";
            } else if (manual) {
                state = loaded ? "一键点赞可用 · 范围：已加载的全部帖子"
                        : "一键点赞可用 · 范围：屏幕内帖子";
            } else if (autoLike.isChecked() || squareLike.isChecked()) {
                state = "已开启的点赞功能尚未就绪";
            } else {
                state = "一键点赞和自动点赞均已关闭";
            }
            setLiveSummary(likeState, state);
        };
        Runnable updateLikeState = () -> {
            MenuSwitch trigger = autoLike.wasUserChanged() ? (MenuSwitch) autoLike
                    : squareLike.wasUserChanged() ? (MenuSwitch) squareLike : null;
            range.setExpanded(squareLike.isEnabled() && squareLike.isChecked()
                    && !(autoLike.isEnabled() && autoLike.isChecked()), trigger);
            likeState.removeCallbacks(refreshSummary);
            if (likeState.isAttachedToWindow()) likeState.post(refreshSummary);
            else refreshSummary.run();
        };
        ((MenuSwitch) autoLike).observe(updateLikeState);
        ((MenuSwitch) squareLike).observe(updateLikeState);
        ((MenuSwitch) allLoaded).observe(updateLikeState);

        body = section(panel, "下载与分享",
                "用 Soul 原有保存按钮下载，目录不变；原文件自带水印无法去除");
        MenuSwitch image = addSwitch(body, prefs, MediaDownloadControls.IMAGE, "图片无水印", "", foreground, secondary);
        bindAvailability(panel, image, () -> MediaDownloadControls.imageReady);
        MenuSwitch video = addSwitch(body, prefs, MediaDownloadControls.VIDEO, "视频无水印", "", foreground, secondary);
        bindAvailability(panel, video, () -> MediaDownloadControls.videoReady);
        MenuSwitch shareLimit = addSwitch(body, prefs, PostShareControls.ENABLED, "去除分享限制", "", foreground, secondary);
        bindAvailability(panel, shareLimit, () -> PostShareControls.ready);
    }

    private static void addGenderOptions(MenuPanel panel, LinearLayout body, SharedPreferences prefs,
            MenuSwitch enabled) {
        String[] values = {"FEMALE", "MALE"};
        String[] names = {"仅女生", "仅男生"};
        SegmentedChoice choices = new SegmentedChoice(panel, null, names, 15);
        body.addView(choices, new LinearLayout.LayoutParams(-1, -2));
        addSwitch(body, prefs, SquareFeedFilter.UNKNOWN, "保留性别未知的帖子",
                "", panel.foreground, panel.secondary);
        Runnable update = () -> {
            String selected = prefs.getString(SquareFeedFilter.GENDER, "FEMALE");
            // Old settings and imported backups keep their unfiltered behavior via the switch.
            if ("ANY".equals(selected)) {
                java.util.Map<String, Object> updates = new java.util.LinkedHashMap<>();
                updates.put(SquareFeedFilter.ENABLED, false);
                updates.put(SquareFeedFilter.GENDER, "FEMALE");
                PreferenceChanges.applyUpdates(prefs, updates);
                enabled.setChecked(false);
                selected = "FEMALE";
            }
            choices.selectValue(selected, values, false);
        };
        choices.onSelection(index -> {
            PreferenceChanges.putString(prefs, SquareFeedFilter.GENDER, values[index]);
            choices.selectValue(values[index], values, true);
            update.run();
        });
        update.run();
        panel.addPreferenceRefresher(update);
    }
}
