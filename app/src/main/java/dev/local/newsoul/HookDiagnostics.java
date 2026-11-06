package dev.local.newsoul;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/** Read-only compatibility scan. Resolving a method never executes the host feature. */
final class HookDiagnostics {
    private static final String TAG = "SoulHook102";
    private static final String BASELINE = "6.37.0";
    private static final String LAST_VERSION = "hook_diagnostics_last_host_version";
    private static final String LAST_SHOWN = "hook_diagnostics_last_shown_version";
    private static boolean checkedThisProcess;

    private HookDiagnostics() {}

    static final class Result {
        final String version;
        final String previous;
        final List<String> lines;
        final int missing;
        final int unready;
        Result(String version, String previous, List<String> lines, int missing) {
            this.version = version;
            this.previous = previous;
            this.lines = lines;
            this.missing = missing;
            int count = 0;
            for (String line : lines) if (notReady(line)) count++;
            unready = count;
        }
        String summary() {
            int issues = 0;
            for (String line : lines) if (issue(line)) issues++;
            return (issues == 0 ? "关键检测项适配通过" : "部分检测项需要检查（" + issues + " 项）")
                    + "\nSoul " + version
                    + (previous != null && !previous.equals(version) ? "\n上次检测 " + previous : "")
                    + (!knownVersion(version) ? "\n当前版本与适配基线不同，请优先检查异常项" : "");
        }
        String entries(boolean issues) {
            StringBuilder out = new StringBuilder();
            for (String line : lines) if (issue(line) == issues) {
                int detail = line.indexOf('\n');
                if (out.length() > 0) out.append("\n\n");
                out.append(detail < 0 ? line : line.substring(0, detail));
            }
            return out.toString();
        }
        String report() {
            StringBuilder out = new StringBuilder(summary());
            String problems = entries(true);
            if (!problems.isEmpty()) out.append("\n\n需要检查\n").append(problems);
            String normal = entries(false);
            if (!normal.isEmpty()) out.append("\n\n正常检测项\n").append(normal);
            out.append("\n检测通过不代表功能实测通过");
            return out.toString();
        }
        private static boolean issue(String line) {
            return line.contains("Hook 点缺失") || notReady(line);
        }
        private static boolean notReady(String line) {
            return line.contains("Hook 未就绪") || line.contains("字段未就绪")
                    || line.contains("字段或绑定入口未就绪");
        }
    }

    static void checkOnStart(Activity activity) {
        if (checkedThisProcess) return;
        checkedThisProcess = true;
        try {
            Result result = scan(activity);
            String shown = PluginPreferences.get(activity).getString(LAST_SHOWN, null);
            boolean updated = shown == null ? !knownVersion(result.version)
                    : !shown.equals(result.version);
            Log.i(TAG, "hook scan: host=" + result.version + "; baseline=" + BASELINE
                    + "; previous=" + result.previous + "; missing=" + result.missing
                    + "; unready=" + result.unready);
            if (result.previous == null || !result.previous.equals(result.version)
                    || result.missing > 0 || result.unready > 0) {
                for (String line : result.lines) Log.i(TAG, "hook scan: " + line);
            }
            if (updated) showAfterResume(activity, result.previous);
        } catch (Throwable error) {
            Log.e(TAG, "hook compatibility scan failed", error);
        }
    }

    static void markShown(Activity activity, String version) {
        PluginPreferences.get(activity).edit().putString(LAST_SHOWN, version).apply();
    }

    private static void showAfterResume(Activity startup, String previousVersion) {
        Application application = startup.getApplication();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private boolean shown;
            @Override public void onActivityResumed(Activity activity) {
                if (shown || !"cn.soulapp.android".equals(activity.getPackageName())) return;
                shown = true;
                application.unregisterActivityLifecycleCallbacks(this);
                activity.getWindow().getDecorView().post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    boolean dark = (activity.getResources().getConfiguration().uiMode
                            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    SettingsUi.showHookDiagnostics(activity, dark, true, previousVersion);
                });
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    static Result scan(android.content.Context activity) {
        String version = hostVersion(activity);
        SharedPreferences prefs = PluginPreferences.get(activity);
        String previous = prefs.getString(LAST_VERSION, null);
        ClassLoader loader = activity.getClassLoader();
        boolean legacy;
        try { legacy = HostBindings.legacyChat(loader); }
        catch (Throwable unavailable) { legacy = false; }
        List<String> lines = new ArrayList<>();
        int missing = 0;
        missing += probe(lines, loader, "签到 / 在线隐身 / 注册天数请求入口", "retrofit2.OkHttpCall", "createRawCall", HostAccountHttp.ready && AccountAutomationControls.ready);
        missing += probeBinding(lines, "签到抽奖请求入口", HostWelfareHttp.ready,
                () -> new HostBindings.Welfare(loader).send);
        missing += probe(lines, loader, "设置入口", "cn.soulapp.android.h5.activity.H5Activity", legacy ? "onResume" : "m1", SoulHook.settingsHookReady, legacy ? new String[0] : new String[]{"boolean"});
        missing += probe(lines, loader, "冷启动广告", "cn.soulapp.android.component.startup.main.MainScheduler", legacy ? "t" : "v", SplashBlocker.installed);
        missing += probe(lines, loader, "热启动广告", legacy ? "o5.c$a" : "tc.e$a", legacy ? "b" : "j", SplashBlocker.hotInstalled, legacy ? new String[0] : new String[]{"java.lang.String"});
        missing += probeBinding(lines, "广告位识别", !AdControls.ready.isEmpty(), () -> new HostAdBindings(loader).slotId);
        int adReady = 0;
        int adSupported = 0;
        for (int i = 1; i < AdControls.ITEMS.length; i++) {
            String[] item = AdControls.ITEMS[i];
            if (!AdControls.supported(item[0])) { lines.add(item[1] + "：当前宿主无此广告场景"); continue; }
            adSupported++;
            if (AdControls.ready.contains(item[0])) adReady++;
            else lines.add(item[1] + "：Hook 未就绪 · 请查看 SoulHook102 安装日志");
        }
        lines.add("信息伪装 · " + (FollowDisguiseControls.ready() ? "Hook 已注册，待实测" : "Hook 未就绪")
                + "\n个人页 " + FollowDisguiseControls.revisionReady + " / 新个人页 " + FollowDisguiseControls.metaReady
                + " / 旧个人页 " + FollowDisguiseControls.headerReady);
        lines.add("天数伪装 · " + (FollowDisguiseControls.daysReady() ? "Hook 已注册，待实测" : "Hook 未就绪")
                + "\n个人页 " + FollowDisguiseControls.revisionDaysReady + " / 新个人页 " + FollowDisguiseControls.metaDaysReady);
        lines.add("广告子项已注册 " + adReady + "/" + adSupported);
        missing += probeBinding(lines, "自定义骰子", ChatGames.diceReady, () -> HostBindings.gameSend(loader, true));
        missing += probeBinding(lines, "自定义猜拳", ChatGames.fingerReady, () -> HostBindings.gameSend(loader, false));
        missing += probeResolved(lines, loader, "自动已读消息入口", HostDexRules.RECEIVE);
        missing += probeResolved(lines, loader, "自动已读回执入口", HostDexRules.READ_ACK);
        lines.add("修改定位 · " + (LocationOverrideControls.ready && LocationOverridePicker.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("地区长按定位入口 · " + (LocationCityEntry.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("广场发布时间排序 · " + (SquareTimeOrder.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("广场 IP 属地显示 · " + (SquareIpLocation.ready && SquareFeedFilter.ready ? "字段与绑定入口已识别，待实测" : "字段或绑定入口未就绪"));
        lines.add("城市二级页帖子筛选 · " + (SquareFeedFilter.cityReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("城市二级页点赞 · " + (SquareLikeControls.ready && LoadedPostLikes.cityReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("消息时间格式 · " + (ChatTimeControls.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("Soul 更新弹窗 · " + (SoulUpdateControls.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        missing += probeBinding(lines, "青少年弹窗", TeenagePopupControls.ready, () -> new TeenagePopupResponse(loader).parse);
        lines.add("自动已读 · " + (AutoReadControls.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("已读回执拦截 · " + (InteractionPrivacyControls.commandReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("前台清除通知 · " + (ForegroundNotificationControls.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("主页查看状态拦截 · " + (InteractionPrivacyControls.profileReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("挚友邀请弹窗 · " + (!InteractionPrivacyControls.inviteSupported ? "当前宿主无此功能"
                : InteractionPrivacyControls.inviteReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("照片选择上限 · " + (InteractionPrivacyControls.photosReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("视频选择上限 · " + (InteractionPrivacyControls.videosReady ? "Hook 已注册，待实测" : "Hook 未就绪"));
        lines.add("内容类型筛选 · " + (SquareFeedFilter.contentReady ? "字段已识别，待实测" : "字段未就绪"));
        lines.add("VIP 帖子筛选 · " + (SquareFeedFilter.vipReady ? "字段已识别，待实测" : "字段未就绪"));
        lines.add("主页瞬间点赞 · " + (ProfileMomentLikes.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        missing += probe(lines, loader, "消息防撤回", "cn.soulapp.imlib.ChatManager", legacy ? "G" : "O", ChatRecallControls.ready, "java.util.List");
        missing += probe(lines, loader, "自己撤回", legacy ? "cn.soulapp.android.component.chat.helper.j1" : "cn.soulapp.android.component.chat.helper.WithDrawHandler", legacy ? "a" : "d", OwnRecallControls.ready, "cn.soulapp.imlib.msg.ImMessage");
        missing += probeBinding(lines, "隐藏输入状态", ChatExperienceControls.typingReady, () -> HostBindings.commandSend(loader, true));
        missing += probe(lines, loader, "匹配音乐", legacy ? "cn.soulapp.android.component.planet.soulmatch.helper.MusicHelper" : "cn.soulapp.android.component.planet.soulmatch.utils.SoulMusicHelper", legacy ? "n" : "k", ChatExperienceControls.musicReady);
        missing += probeBinding(lines, "聊天礼物限制", ChatGateControls.giftReady, () -> new ChatGateResponse(loader).parse);
        if (ChatGateControls.bellSupported)
            missing += probe(lines, loader, "奇遇铃送礼限制", "cn.soulapp.android.component.home.lovebell.bean.LoveBellProConfig", "getShowPopup", ChatGateControls.bellReady);
        else lines.add("奇遇铃送礼限制：当前宿主无此功能");
        missing += probe(lines, loader, "聊天复读/转发", legacy ? "cn.soulapp.android.component.chat.utils.o" : "cn.soulapp.android.component.chat.utils.u0", legacy ? "d" : "k", ChatRepeatControls.ready, legacy ? "cn.soulapp.android.component.chat.fragment.BaseConversationFragment" : "cn.soulapp.android.component.chat.fragment.ConversationFragment", "android.view.View", "cn.soulapp.imlib.msg.ImMessage");
        missing += probe(lines, loader, "底栏长按设置", "cn.soulapp.android.component.startup.main.MainActivity", "onCreate", BottomSettingsEntry.ready, "android.os.Bundle");
        missing += probe(lines, loader, "消息转发选择", "cn.soulapp.android.component.chat.SelectConversationActivity", "F", ChatRepeatControls.forwardReady, "int");
        missing += probeBinding(lines, "语音库发送", VoiceLibraryControls.ready, () -> HostBindings.voiceSend(loader));
        if (PrivacyPhotoControls.previewSupported) {
            missing += probe(lines, loader, "聊天隐私媒体", "cn.soulapp.android.component.chat.snapchat.PrivacyProtectPreviewActivity", "init", PrivacyPhotoControls.previewReady, "android.os.Bundle");
            missing += probe(lines, loader, "隐私媒体多次查看", "cn.soulapp.android.component.chat.snapchat.PrivacyProtectPreviewActivity", "G", PrivacyPhotoControls.privacyRepeatReady);
        } else lines.add("隐私照片 / 视频：当前宿主无此功能");
        if (PrivacyPhotoControls.snapSupported)
            missing += probe(lines, loader, "闪照多次查看", "cn.soulapp.android.component.chat.snapchat.SnapChatActivity", "C", PrivacyPhotoControls.snapRepeatReady);
        else lines.add("闪照：当前宿主无此功能");
        missing += probeBinding(lines, "图片下载", MediaDownloadControls.imageReady, () -> HostMediaBindings.image(loader));
        missing += probeBinding(lines, "视频下载", MediaDownloadControls.videoReady, () -> HostMediaBindings.video(loader));
        missing += probe(lines, loader, "帖子分享限制", "cn.soulapp.android.square.SeedsShareDialogFragment", legacy ? "P0" : "r0", PostShareControls.ready);
        lines.add("主页插眼 · " + (ProfileBookmarkControls.ready ? "Hook 已注册，待实测" : "Hook 未就绪"));
        missing += probe(lines, loader, "礼仪分本地限制", SocialScoreControls.MODEL, legacy ? "b" : "getCreateSessionLimit", SocialScoreControls.modelReady);
        missing += probe(lines, loader, "礼仪分限制弹窗", SocialScoreControls.DIALOG, "b", SocialScoreControls.dialogReady,
                "androidx.fragment.app.FragmentManager", SocialScoreControls.DIALOG + "$a");
        missing += probe(lines, loader, "帖子多人分享", legacy ? "cn.soulapp.android.component.chat.utils.t3" : "cn.soulapp.android.component.chat.utils.n6", "g", PostBatchShareControls.ready, "cn.soulapp.android.square.post.bean.Post", "java.lang.String");
        missing += probe(lines, loader, "帖子文案复制", "cn.soulapp.android.square.SeedsShareDialogFragment", "initViews", PostCopyControls.ready, "android.view.View");
        missing += probe(lines, loader, "广场帖子筛选", "cn.soulapp.android.component.square.main.squarepost.SquareAdapter", legacy ? "c" : "d", SquareFeedFilter.ready, "cn.soulapp.android.component.square.main.squarepost.viewholder.BaseSquareViewHolder", "java.lang.Object");
        missing += probe(lines, loader, "广场一键点赞", "cn.soulapp.android.component.startup.main.MainActivity", "onCreate", SquareLikeControls.ready, "android.os.Bundle");
        missing += probeLoadedLikes(lines, loader);
        missing += probeResolved(lines, loader, "公共上下文", HostDexRules.CONTEXT);
        if (legacy) {
            String owner = "cn.soulapp.android.component.home.meta.adapter.MetaUserProfileProvider";
            String user = "com.soul.component.componentlib.service.user.bean.User";
            missing += probe(lines, loader, "个人页数据绑定", owner, "d1", FollowDisguiseControls.metaReady, user, owner + "$a");
            missing += probe(lines, loader, "个人页天数绑定", owner, "k1", FollowDisguiseControls.metaDaysReady, user, owner + "$a");
        } else {
            missing += probeResolved(lines, loader, "个人页数据绑定", HostDexRules.FOLLOW);
            missing += probeResolved(lines, loader, "个人页天数绑定", HostDexRules.DAYS);
        }
        probeAiEntry(lines);
        prefs.edit().putString(LAST_VERSION, version).apply();
        return new Result(version, previous, lines, missing);
    }

    private static void probeAiEntry(List<String> lines) {
        if (VariantFeatures.hasAi()) lines.add("AI 原生入口：" + (VariantFeatures.aiEntryReady()
                ? "已注册 · 待功能实测" : "Hook 未就绪"));
    }

    private static int probeLoadedLikes(List<String> lines, ClassLoader loader) {
        try {
            Class<?> result = loader.loadClass(HostBindings.legacyChat(loader) ? "cn.soulapp.android.component.square.post.q0" : "cn.soulapp.android.component.square.post.b1")
                    .getMethod("h", int.class).getReturnType();
            return probe(lines, loader, "已加载帖子点赞请求", result.getName(), "subscribe", LoadedPostLikes.ready,
                    "io.reactivex.functions.Consumer", "io.reactivex.functions.Consumer");
        } catch (Throwable error) {
            lines.add("已加载帖子点赞请求：Hook 点缺失（" + error.getClass().getSimpleName() + "）");
            return 1;
        }
    }

    private interface BindingProbe { java.lang.reflect.Method resolve() throws Exception; }
    private static int probeBinding(List<String> lines, String feature, boolean registered, BindingProbe binding) {
        try {
            java.lang.reflect.Method method = binding.resolve();
            lines.add(feature + "：" + (registered ? "已注册 · 待功能实测" : "签名存在 · Hook 未就绪") + "\n  " + method);
            return 0;
        } catch (Throwable error) {
            lines.add(feature + "：Hook 点缺失（" + error.getClass().getSimpleName() + "）");
            return 1;
        }
    }

    private static int probeResolved(List<String> lines, ClassLoader loader, String feature, String key) {
        try {
            java.lang.reflect.Method method = HostSymbols.method(key, loader);
            lines.add(feature + "：入口已定位，待功能实测\n  " + method.toString());
            return 0;
        } catch (Throwable error) {
            lines.add(feature + "：Hook 点缺失（" + error.getClass().getSimpleName() + "）");
            return 1;
        }
    }

    private static int probe(List<String> lines, ClassLoader loader, String feature,
            String owner, String name, Boolean registered, String... parameterNames) {
        String point = owner + "#" + name + "("
                + android.text.TextUtils.join(",", parameterNames) + ")";
        try {
            Class<?> type = loader.loadClass(owner);
            Class<?>[] args = new Class<?>[parameterNames.length];
            for (int i = 0; i < args.length; i++) args[i] = resolve(loader, parameterNames[i]);
            try { type.getDeclaredMethod(name, args); }
            catch (NoSuchMethodException inherited) { type.getMethod(name, args); }
            String status = registered == null ? "签名存在 · 注册状态未记录"
                    : registered ? "已注册 · 待功能实测" : "签名存在 · Hook 未就绪";
            lines.add(feature + "：" + status + "\n  " + point);
            return 0;
        } catch (Throwable error) {
            lines.add(feature + "：Hook 点缺失（" + error.getClass().getSimpleName() + "）\n  " + point);
            return 1;
        }
    }

    private static Class<?> resolve(ClassLoader loader, String name) throws ClassNotFoundException {
        if ("boolean".equals(name)) return boolean.class;
        if ("int".equals(name)) return int.class;
        if ("long".equals(name)) return long.class;
        return loader.loadClass(name);
    }

    private static boolean knownVersion(String version) {
        return HomeCheck.supported(version.split(" ", 2)[0]);
    }

    private static String hostVersion(android.content.Context activity) {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (Exception error) {
            return "未知版本";
        }
    }
}
