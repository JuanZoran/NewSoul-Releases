package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import android.widget.TextView;

import static dev.local.newsoul.SettingsUi.*;

/** Category content; shared navigation, styling and controls remain in SettingsUi. */
final class SettingsChatPage {
    private static final String TAG = "SoulHook102";
    private SettingsChatPage() {}

    static void populate(MenuPanel panel) {
        Activity activity = panel.activity;
        boolean dark = panel.dark;
        Context themed = panel.context;
        LinearLayout body = panel.body;
        int foreground = panel.foreground, secondary = panel.secondary;
        SharedPreferences prefs = PluginPreferences.get(activity);
        VariantFeatures.addSettings(panel);
        body = section(panel, "聊天小工具");
        MenuSwitch dice = addSwitch(body, prefs, "game_dice", "自定义骰子", "" + (ChatGames.diceReady ? "" : " · 未就绪"), foreground, secondary);
        bindAvailability(panel, dice, () -> ChatGames.diceReady);
        attachSwitchPreview(dice, panel, () -> ChatGames.preview(activity, true));
        MenuSwitch finger = addSwitch(body, prefs, "game_finger", "自定义猜拳", "" + (ChatGames.fingerReady ? "" : " · 未就绪"), foreground, secondary);
        bindAvailability(panel, finger, () -> ChatGames.fingerReady);
        attachSwitchPreview(finger, panel, () -> ChatGames.preview(activity, false));
        ChatTimeUi.addSettings(panel);
        body = section(panel, "已读回执");
        MenuSwitch autoRead = addSwitch(body, prefs, AutoReadControls.ENABLED, "自动已读",
                AutoReadControls.ready ? "" : " · 当前版本未就绪", foreground, secondary);
        bindAvailability(panel, autoRead, () -> AutoReadControls.ready);
        MenuSwitch blockReceipt = addSwitch(body, prefs, InteractionPrivacyControls.RECEIPT,
                "拦截发送小绿点", "", foreground, secondary);
        bindAvailability(panel, blockReceipt, () -> InteractionPrivacyControls.commandReady);
        bindExclusiveSwitchRows(panel, prefs, autoRead, blockReceipt,
                AutoReadControls.ENABLED, InteractionPrivacyControls.RECEIPT);
        InteractionPrivacyControls.addPicker(panel);
        body = section(panel, "消息撤回", "已清除的消息无法恢复；媒体需文件或原链接有效");
        MenuSwitch recall = addSwitch(body, prefs, ChatRecallControls.ENABLED, "聊天防撤回",
                ChatRecallControls.ready ? "" : " · 当前版本未就绪", foreground, secondary);
        bindAvailability(panel, recall, () -> ChatRecallControls.ready);
        addRecallStyles(panel, recall, prefs);
        MenuSwitch ownRecall = addSwitch(body, prefs, OwnRecallControls.ENABLED, "撤回不受两分钟限制",
                ""
                        + (OwnRecallControls.ready ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, ownRecall, () -> OwnRecallControls.ready);
        body = section(panel, "聊天弹窗");
        if (InteractionPrivacyControls.inviteSupported)
            InteractionPrivacyControls.toggle(panel, body, InteractionPrivacyControls.INVITE,
                    "禁止弹出挚友邀请", () -> InteractionPrivacyControls.inviteReady);
        MenuSwitch giftGate = addSwitch(body, prefs, ChatGateControls.GIFT, "去除送礼聊天限制",
                "" + (ChatGateControls.giftReady ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, giftGate, () -> ChatGateControls.giftReady);
        if (ChatGateControls.bellSupported) {
        MenuSwitch bellGate = addSwitch(body, prefs, ChatGateControls.BELL, "去除奇遇铃送礼限制",
                "" + (ChatGateControls.bellReady ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, bellGate, () -> ChatGateControls.bellReady);
        }
        MenuSwitch socialScore = addSwitch(body, prefs, SocialScoreControls.ENABLED, "去除礼仪分限制",
                ""
                        + (SocialScoreControls.modelReady && SocialScoreControls.dialogReady ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, socialScore, () -> SocialScoreControls.modelReady && SocialScoreControls.dialogReady);
        body = section(panel, "聊天与匹配");
        MenuSwitch typing = addSwitch(body, prefs, ChatExperienceControls.TYPING, "禁止上报输入状态",
                "" + (ChatExperienceControls.typingReady ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, typing, () -> ChatExperienceControls.typingReady);
        MenuSwitch profileNotice = addSwitch(body, prefs, InteractionPrivacyControls.PROFILE,
                "禁止发送正在查看主页", InteractionPrivacyControls.profileReady ? "" : "当前版本未就绪",
                foreground, secondary);
        bindAvailability(panel, profileNotice, () -> InteractionPrivacyControls.profileReady);
        MenuSwitch music = addSwitch(body, prefs, ChatExperienceControls.MUSIC, "关闭匹配背景音乐",
                "" + (ChatExperienceControls.musicReady ? "" : " · 当前版本未就绪"), foreground, secondary);
        bindAvailability(panel, music, () -> ChatExperienceControls.musicReady);
        if (!PrivacyPhotoControls.previewSupported && !PrivacyPhotoControls.snapSupported) return;
        body = section(panel, "隐私照片 / 视频", "无法恢复已撤回、过期或失效文件");
        {
        String status = PrivacyPhotoControls.previewReady ? "" : " · 当前版本未就绪";
        MenuSwitch download = addSwitch(body, prefs, PrivacyPhotoControls.ALLOW_DOWNLOAD,
                "允许下载", "保存目录：\n图片：Pictures/New Soul\n视频：Movies/New Soul" + status,
                foreground, secondary);
        MenuSwitch screenshot = addSwitch(body, prefs, PrivacyPhotoControls.ALLOW_SCREENSHOT,
                "允许截屏", "" + status,
                foreground, secondary);
        MenuSwitch repeat = addSwitch(body, prefs, PrivacyPhotoControls.ALLOW_REPEAT,
                "允许多次查看", "查看时不扣减本地次数，也不向对方发送已阅状态",
                foreground, secondary);
        bindAvailability(panel, download, () -> PrivacyPhotoControls.previewReady);
        bindAvailability(panel, screenshot, () -> PrivacyPhotoControls.previewReady);
        bindAvailability(panel, repeat, () -> PrivacyPhotoControls.privacyRepeatReady || PrivacyPhotoControls.snapRepeatReady);
        TextView repeatStatus = addSummary(body, "");
        AvailabilityGroup privacyAvailability = panel.availability.get(body);
        privacyAvailability.withInlineStatus.add(repeat);
        privacyAvailability.partial.add(() -> PrivacyPhotoControls.privacyRepeatReady != PrivacyPhotoControls.snapRepeatReady);
        Runnable refreshRepeat = () -> {
            boolean privacy = PrivacyPhotoControls.privacyRepeatReady;
            boolean snap = PrivacyPhotoControls.snapRepeatReady;
            String message = privacy && snap ? "" : privacy
                    ? "部分可用：隐私照片 / 视频可用，闪照尚未就绪"
                    : snap ? "部分可用：闪照可用，隐私照片 / 视频尚未就绪"
                    : "多次查看尚未就绪：隐私照片 / 视频与闪照均未就绪";
            setLiveSummary(repeatStatus, message);
            repeatStatus.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
            privacyAvailability.refresh();
        };
        panel.addPreferenceRefresher(refreshRepeat);
        refreshRepeat.run();
        body.removeView(privacyAvailability.status);
        body.removeView(privacyAvailability.action);
        body.addView(privacyAvailability.status);
        body.addView(privacyAvailability.action);
        }
    }

    private static void addRecallStyles(MenuPanel panel, MenuSwitch toggle, SharedPreferences prefs) {
        LinearLayout row = (LinearLayout) toggle.getParent();
        TextView heading = (TextView) row.getChildAt(0);
        String[] values = {"below", "nickname"};
        String[] names = {"气泡下方小字", "昵称旁标签"};
        SegmentedChoice choices = new SegmentedChoice(panel, heading, names, 13, true);
        attachSwitchEditor(toggle, panel, choices);
        LinearLayout preview = new LinearLayout(panel.context);
        preview.setOrientation(LinearLayout.VERTICAL);
        attachSwitchContentBelow(toggle, preview);
        final String[] renderedStyle = {null};
        Runnable update = () -> {
            String selected = prefs.getString(ChatRecallControls.STYLE, "below");
            choices.selectValue(selected, values, false);
            if (!toggle.isChecked() || !toggle.isEnabled() || selected.equals(renderedStyle[0])) return;
            preview.removeAllViews();
            try {
                preview.addView(RecallMessageStyle.preview(panel.context, selected),
                        new LinearLayout.LayoutParams(-1, -2));
                renderedStyle[0] = selected;
            } catch (Throwable error) {
                preview.addView(label(panel.context, "当前版本无法显示预览", 13, panel.secondary));
                Log.e(TAG, "recall preview unavailable", error);
            }
        };
        choices.onSelection(index -> {
            PreferenceChanges.putString(prefs, ChatRecallControls.STYLE, values[index]);
            choices.selectValue(values[index], values, true);
            update.run();
        });
        ((MenuSwitch) toggle).observe(update);
        panel.addPreferenceRefresher(update);
    }
}
