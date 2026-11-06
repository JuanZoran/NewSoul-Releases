package dev.local.newsoul;

import java.lang.reflect.Method;

/** Apply chat-limit changes to the decoded model before host subscribers receive it. */
final class ChatGateResponse {
    final Method parse;
    private final HostDecodedResponse response;
    private final Class<?> modelType;
    private final Method limited, setLimit;

    ChatGateResponse(ClassLoader loader) throws Exception {
        response = new HostDecodedResponse(loader);
        modelType = loader.loadClass("cn.soulapp.android.lib.common.bean.ChatLimitModel");
        parse = response.parse;
        limited = HostBindings.method(modelType, "isLimit", boolean.class, false);
        setLimit = HostBindings.method(modelType, "setLimit", void.class, false, boolean.class);
    }

    boolean apply(Object raw, Object result, boolean enabled) throws Exception {
        if (!enabled || raw == null || result == null) return false;
        Object model = response.data(raw, result, "/chat/limitInfo");
        if (!modelType.isInstance(model)) return false;
        boolean wasLimited = Boolean.TRUE.equals(limited.invoke(model));
        // Match Xyner's response strategy: clear the decoded field for every
        // limitInfo model, without classifying type or itemIdentity.
        setLimit.invoke(model, false);
        return wasLimited;
    }
}
