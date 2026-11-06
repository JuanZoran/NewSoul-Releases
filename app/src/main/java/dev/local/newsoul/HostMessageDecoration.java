package dev.local.newsoul;

import java.lang.reflect.Method;

/** Apply the active host bubble to a newly created payload, as the native sender does. */
final class HostMessageDecoration {
    private final Method modern, helper, bubble, put;
    HostMessageDecoration(ClassLoader loader) throws Exception {
        Class<?> chat = loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage");
        if (HostBindings.legacyChat(loader)) {
            modern = null;
            Class<?> owner = loader.loadClass("cn.soulapp.android.component.chat.helper.m0");
            helper = HostBindings.method(owner, "s", owner, true);
            bubble = HostBindings.method(owner, "p", String.class, false);
            put = HostBindings.method(chat, "w", void.class, false, String.class, String.class);
        } else {
            modern = HostBindings.method(HostBindings.sender(loader), "u", void.class, true, chat);
            helper = bubble = put = null;
        }
    }
    void apply(Object chat) throws Exception {
        if (modern != null) { modern.invoke(null, chat); return; }
        String value = (String) bubble.invoke(helper.invoke(null));
        if (value != null && !value.isEmpty()) put.invoke(chat, "bubble", value);
    }
}
