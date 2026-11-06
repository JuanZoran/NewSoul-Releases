package dev.local.newsoul;

import android.content.Context;
import android.widget.LinearLayout;
import dev.local.newsoul.SettingsUi.MenuSwitch;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class InteractionPrivacyControls {
    static final String RECEIPT = "chat_block_read_receipt_v1";
    static final String PROFILE = "chat_block_profile_notice_v1";
    static final String INVITE = "chat_block_close_friend_invite_v1";
    static final String PHOTOS = "picker_photo_limit_99_v1";
    static final String VIDEOS = "picker_video_limit_99_v1";
    static boolean commandReady, profileReady, inviteReady, photosReady, videosReady;
    static boolean inviteSupported = true;
    private static Method context;

    static boolean enabled(String key) {
        try {
            Context app = (Context) context.invoke(null);
            return app != null && PluginPreferences.get(app).getBoolean(key, false);
        } catch (Throwable ignored) { return false; }
    }

    static void install(XposedModule module, ClassLoader loader) {
        inviteReady = false;
        inviteSupported = true;
        try { context = HostSymbols.method(HostDexRules.CONTEXT, loader); }
        catch (Throwable error) { DebugDiagnostics.registration("interaction.context", error); return; }
        try {
            Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Class<?> manager = loader.loadClass("cn.soulapp.imlib.ChatManager");
            Method outer = HostBindings.messageMethod(im, "getMsgType"), chat = HostBindings.messageMethod(im, "getChatMessage");
            Method inner = HostBindings.messageMethod(loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage"), "getMsgType");
            Method trans = null, params = null, target = null;
            Field command = null;
            try {
                trans = HostBindings.messageMethod(im, "getTransCmd");
                Class<?> payload = loader.loadClass("cn.soulapp.imlib.msg.transcmd.TransCmdMsg");
                params = HostBindings.messageMethod(payload, "getParamJson");
                command = payload.getField("messageType");
                target = HostBindings.messageMethod(im, "getTo");
            } catch (Throwable error) { DebugDiagnostics.registration("interaction.profile", error); }
            final Method transGetter = trans, paramsGetter = params, targetGetter = target;
            final Field commandField = command;
            final boolean profileResolved = trans != null && params != null && command != null && target != null;
            final InteractionPrivacyRules.ProfileNotices notices = new InteractionPrivacyRules.ProfileNotices();
            Method send = HostBindings.commandSend(loader, true);
            module.hook(send).setId("new-soul/interaction-command-privacy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object message = chain.getArg(0);
                        int profileState = -1;
                        String profileTarget = null;
                        if (message != null) {
                            int envelope = (int) outer.invoke(message);
                            if (envelope == 5 && profileResolved) {
                                try {
                                    Object transPayload = transGetter.invoke(message);
                                    if (transPayload != null) {
                                        String commandName = (String) commandField.get(transPayload);
                                        if ("ONLINE_STATE".equals(commandName)) {
                                            profileState = InteractionPrivacyRules.profileState(commandName,
                                                    (String) paramsGetter.invoke(transPayload));
                                            profileTarget = (String) targetGetter.invoke(message);
                                        }
                                    }
                                } catch (Throwable error) {
                                    profileState = -1;
                                    DebugDiagnostics.event("interaction.profile.parse", "unrecognized payload; passed through");
                                }
                                if (notices.block(enabled(PROFILE), profileState, profileTarget)) {
                                    DebugDiagnostics.event("interaction.profile.block", "state=" + profileState);
                                    return null;
                                }
                            }
                            Object payload = envelope == 1 ? chat.invoke(message) : null;
                            int content = payload == null ? -1 : (int) inner.invoke(payload);
                            if (InteractionPrivacyRules.blockReceipt(enabled(RECEIPT), envelope, content)) {
                                DebugDiagnostics.event("interaction.command.block", "type=" + envelope + "/" + content);
                                return null;
                            }
                        }
                        Object result = chain.proceed();
                        notices.forwarded(profileState, profileTarget);
                        return result;
                    });
            module.deoptimize(send);
            module.deoptimize(HostBindings.commandSend(loader, false));
            commandReady = true;
            profileReady = profileResolved;
            if (profileReady) DebugDiagnostics.registration("interaction.profile", null);
            DebugDiagnostics.registration("interaction.command", null);
        } catch (Throwable error) { DebugDiagnostics.registration("interaction.command", error); }
        photosReady = installLimit(module, loader, "getMaxSelectNum", PHOTOS);
        videosReady = installLimit(module, loader, "getMaxVideoSelectNum", VIDEOS);
        try {
            Class<?> callback = loader.loadClass("cn.soulapp.android.component.chat.business.g$a");
            Class<?> result = loader.loadClass("com.soulapp.soulgift.bean.CommonDataBo");
            Method next = callback.getDeclaredMethod("a", result);
            module.hook(next).setId("new-soul/close-friend-invite")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (enabled(INVITE)) return null;
                        return chain.proceed();
                    });
            module.deoptimize(next);
            for (Method method : callback.getDeclaredMethods()) module.deoptimize(method);
            inviteReady = true;
            DebugDiagnostics.registration("interaction.invite", null);
        } catch (Throwable error) {
            if (legacyInviteAbsent(loader)) inviteSupported = false;
            else DebugDiagnostics.registration("interaction.invite", error);
        }
    }

    /** 4.88 predates the relationCheckNotice route; secret-moment invites are unrelated. */
    private static boolean legacyInviteAbsent(ClassLoader loader) {
        try {
            if (!HostBindings.legacyChat(loader)) return false;
            try { loader.loadClass("cn.soulapp.android.component.chat.business.g$a"); return false; }
            catch (ClassNotFoundException absent) { }
            // An unknown API schema remains unavailable instead of being classified as absent.
            try { loader.loadClass("com.soulapp.soulgift.api.IActivityOpenApi"); return false; }
            catch (ClassNotFoundException absent) { return true; }
        } catch (Throwable unknown) { return false; }
    }

    private static boolean installLimit(XposedModule module, ClassLoader loader, String name, String key) {
        try {
            Method getter = loader.loadClass("cn.soulapp.android.lib.photopicker.manager.AlbumConfig").getDeclaredMethod(name);
            if (getter.getReturnType() != int.class) throw new NoSuchMethodException(name);
            module.hook(getter).setId("new-soul/picker-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                            InteractionPrivacyRules.selectionLimit(enabled(key), (int) chain.proceed()));
            module.deoptimize(getter);
            DebugDiagnostics.registration("picker." + name, null);
            return true;
        } catch (Throwable error) { DebugDiagnostics.registration("picker." + name, error); return false; }
    }

    static void toggle(SettingsUi.MenuPanel panel, LinearLayout body, String key, String title,
            java.util.function.BooleanSupplier ready) {
        MenuSwitch value = SettingsUi.addSwitch(body, PluginPreferences.get(panel.activity), key, title,
                ready.getAsBoolean() ? "" : "当前版本未就绪", panel.foreground, panel.secondary);
        SettingsUi.bindAvailability(panel, value, ready);
    }

    static void addPicker(SettingsUi.MenuPanel panel) {
        LinearLayout body = SettingsUi.section(panel, "媒体选择",
                "选择上限提高到 99 项\n需多选视频时，同时开启照片选项；发送和发布仍受对应页面限制");
        toggle(panel, body, PHOTOS, "去除照片选择上限", () -> photosReady);
        toggle(panel, body, VIDEOS, "去除视频选择上限", () -> videosReady);
    }

}
