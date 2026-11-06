package dev.local.newsoul;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.*;
import java.util.List;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Observes completed global dispatch and uses the host's receipt-only path. */
final class AutoReadControls {
    static final String ENABLED = "chat_auto_read_v1";
    static boolean ready;
    private static final String TAG = "SoulHook102";
    private static final AutoReadRules.Recent recent = new AutoReadRules.Recent(2048, 120_000);
    private static Method context, account, from, to, id, envelope, chat, type, body,
            snap, transExt, anonymous, ack, manager, conversation, send;
    private static Field batch;

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Class<?> content = loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage");
            Class<?> managerType = loader.loadClass("cn.soulapp.imlib.ChatManager");
            context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            account = HostBindings.account(loader);
            from = HostBindings.messageMethod(im, "getFrom"); to = HostBindings.messageMethod(im, "getTo"); id = HostBindings.messageMethod(im, "getMsgId");
            envelope = HostBindings.messageMethod(im, "getMsgType"); chat = HostBindings.messageMethod(im, "getChatMessage");
            anonymous = HostBindings.messageMethod(im, "isAnonymityChat"); ack = HostBindings.messageMethod(im, "getIsAck");
            type = HostBindings.messageMethod(content, "getMsgType"); body = HostBindings.messageMethod(content, "getMsgContent");
            snap = HostBindings.messageMethod(content, "getSnapChat"); transExt = HostBindings.messageMethod(content, "getStringTransExt", String.class);
            manager = HostBindings.chatManager(loader); conversation = HostBindings.conversationFor(loader);
            send = HostSymbols.method(HostDexRules.READ_ACK, loader);
            Method dispatch = HostSymbols.method(HostDexRules.RECEIVE, loader);
            batch = batchField(dispatch.getDeclaringClass());
            // Observe normal UI receipts, but never suppress or replace them.
            module.hook(send).setId("new-soul/auto-read-observe")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        AutoReadRules.Key key = null;
                        try {
                            if (enabled()) {
                                key = key(chain.getArg(0));
                                if (key != null) recent.remember(key, SystemClock.elapsedRealtime());
                            }
                        } catch (Throwable ignored) { }
                        try { return chain.proceed(); }
                        catch (Throwable error) { if (key != null) recent.forget(key); throw error; }
                    });
            module.deoptimize(send);
            module.hook(dispatch).setId("new-soul/auto-read-receive")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            if (enabled() && batch.get(chain.getThisObject()) instanceof List<?> messages) {
                                for (Object message : messages) {
                                    try { receive(message); }
                                    catch (Throwable error) { Log.e(TAG, "auto read receipt skipped", error); }
                                }
                            }
                        } catch (Throwable error) { Log.e(TAG, "auto read dispatch skipped", error); }
                        return result;
                    });
            module.deoptimize(dispatch);
            ready = true;
            Log.i(TAG, "auto read hooks installed; default=off");
        } catch (Throwable error) { module.log(Log.ERROR, TAG, "auto read unavailable", error); }
    }

    static Field batchField(Class<?> owner) throws Exception {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == List.class) {
                if (found != null) throw new IllegalStateException("Ambiguous incoming batch");
                found = field;
            }
        }
        if (found == null) throw new NoSuchFieldException("Incoming batch");
        found.setAccessible(true); return found;
    }

    private static boolean enabled() throws Exception {
        Context value = (Context) context.invoke(null);
        return value != null && PluginPreferences.get(value).getBoolean(ENABLED, false)
                && !PluginPreferences.get(value).getBoolean(InteractionPrivacyControls.RECEIPT, false);
    }

    private static AutoReadRules.Key key(Object message) throws Exception {
        if (message == null) return null;
        String own = (String) account.invoke(null), peer = (String) from.invoke(message);
        String target = (String) to.invoke(message), messageId = (String) id.invoke(message);
        if (!ChatRecallRules.incoming(own, peer, target) || messageId == null || messageId.isEmpty()) return null;
        return new AutoReadRules.Key(own, peer, messageId);
    }

    private static void receive(Object message) throws Exception {
        if (!enabled() || message == null) return;
        AutoReadRules.Key key = key(message);
        if (key == null) return;
        Object payload = chat.invoke(message);
        if (payload == null) return;
        Object data = body.invoke(payload);
        if (!AutoReadRules.eligible(key.account(), key.peer(), (String) to.invoke(message), key.id(),
                (int) envelope.invoke(message), (int) type.invoke(payload), data == null ? "" : data.getClass().getName(),
                (int) snap.invoke(payload), (String) transExt.invoke(payload, "isSnap"),
                (boolean) anonymous.invoke(message), (int) ack.invoke(message))) return;
        Object target = conversation.invoke(manager.invoke(null), key.peer());
        if (target == null || !enabled() || !key.account().equals(account.invoke(null))) return;
        if (!recent.claim(key, SystemClock.elapsedRealtime())) return;
        try {
            send.invoke(target, message);
            Log.i(TAG, "auto read receipt enqueued");
        } catch (Exception error) { recent.forget(key); throw error; }
        // No markMsgRead, clearUnReadCount, setMsgStatus, setIsAck or notification cancellation.
        // The host's asynchronous send acknowledgement owns its isAck persistence and retry.
    }
}
