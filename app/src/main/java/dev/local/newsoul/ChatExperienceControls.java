package dev.local.newsoul;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Narrow, reversible controls for outgoing typing and match-only music. */
final class ChatExperienceControls {
    static final String TYPING = "chat_hide_typing";
    static final String MUSIC = "match_mute_background_music";
    static boolean typingReady, musicReady;
    private static Method context;

    static boolean enabled(String key) {
        try {
            return PluginPreferences.get((Context) context.invoke(null)).getBoolean(key, true);
        } catch (Throwable error) { return false; }
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Method getChat = HostBindings.messageMethod(im, "getChatMessage");
            Method getOuterType = HostBindings.messageMethod(im, "getMsgType");
            Method type = HostBindings.messageMethod(loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage"), "getMsgType");
            Class<?> manager = loader.loadClass("cn.soulapp.imlib.ChatManager");
            Method sendCommand = HostBindings.commandSend(loader, true);
            module.hook(sendCommand).setId("new-soul/hide-typing")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object message = chain.getArg(0);
                        if (message != null && enabled(TYPING) && (int) getOuterType.invoke(message) == 1) {
                            Object chat = getChat.invoke(message);
                            if (chat != null) {
                                int value = (int) type.invoke(chat);
                                if (value == 22 || value == 23) {
                                    Log.i("SoulHook102", "typing command blocked; type=" + value);
                                    return null;
                                }
                            }
                        }
                        return chain.proceed();
                    });
            module.deoptimize(HostBindings.commandSend(loader, false));
            module.deoptimize(sendCommand);
            typingReady = true;
            Log.i("SoulHook102", "typing command control installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "typing control unavailable", error);
        }
        try {
            boolean legacy = HostBindings.legacyChat(loader);
            Class<?> helper = loader.loadClass(legacy
                    ? "cn.soulapp.android.component.planet.soulmatch.helper.MusicHelper"
                    : "cn.soulapp.android.component.planet.soulmatch.utils.SoulMusicHelper");
            Method play = helper.getDeclaredMethod(legacy ? "n" : "k");
            Method prepared = helper.getDeclaredMethod("onPrepared", MediaPlayer.class);
            for (Method method : new Method[]{play, prepared}) {
                module.hook(method).setId("new-soul/match-music-" + method.getName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (enabled(MUSIC)) {
                                Log.i("SoulHook102", "soul match background music blocked");
                                return null;
                            }
                            return chain.proceed();
                        });
                module.deoptimize(method);
            }
            if (legacy) {
                for (Method caller : helper.getDeclaredMethods()) module.deoptimize(caller);
            } else module.deoptimize(helper.getDeclaredMethod("l"));
            musicReady = true;
            Log.i("SoulHook102", "soul match music control installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "match music control unavailable", error);
        }
    }
}
