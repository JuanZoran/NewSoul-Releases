package dev.local.newsoul;

import android.app.Activity;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Main-thread adapter to Soul's ordinary text-send path; never touches the composer. */
final class AiReplySender implements AiSendSequence.Transport {
    private final Activity activity;
    private final AiChatContext.Snapshot snapshot;
    private final Object fragment, conversation, sender, keyboard, user;
    private final java.lang.reflect.Method send, currentFragment;
    private final HostBindings.Chat chat;
    private final Field keyboardField;
    private final String recipient;
    private Set<String> before;
    private String pending, pendingId;
    private Object created;
    private boolean multipleCreated;
    private static final ThreadLocal<AiReplySender> DISPATCH = new ThreadLocal<>();
    private static final Set<Class<?>> CAPTURE_READY = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Y1 creates the message synchronously, even when k2.b submits it asynchronously. */
    static synchronized void install(SoulHook module, ClassLoader loader) {
        try {
            Class<?> message = loader.loadClass(HostBindings.MESSAGE);
            if (CAPTURE_READY.contains(message)) return;
            java.lang.reflect.Method factory = HostBindings.method(message, "createChatSendMsg", message, true,
                    loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage"), String.class);
            module.deoptimize(factory);
            module.deoptimize(HostBindings.textSend(loader));
            module.hook(factory).setId("new-soul/ai-send-message-identity")
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        AiReplySender dispatch = DISPATCH.get();
                        if (dispatch != null) {
                            if (dispatch.created != null) dispatch.multipleCreated = true;
                            else dispatch.created = result;
                        }
                        return result;
                    });
            CAPTURE_READY.add(message);
            DebugDiagnostics.registration("ai.send.identity", null);
        } catch (Throwable error) {
            DebugDiagnostics.registration("ai.send.identity", error);
            android.util.Log.w("SoulHook102", "AI send identity hook unavailable", error);
        }
    }

    AiReplySender(Activity activity, AiChatContext.Snapshot snapshot) throws Exception {
        this.activity = activity; this.snapshot = snapshot;
        ClassLoader loader = activity.getClassLoader();
        synchronized (AiReplySender.class) {
            if (!CAPTURE_READY.contains(loader.loadClass(HostBindings.MESSAGE))) throw new Failure("send_identity_unavailable");
        }
        chat = new HostBindings.Chat(loader);
        keyboardField = HostBindings.keyboard(loader);
        currentFragment = HostBindings.currentFragment(loader);
        fragment = HostBindings.require(currentFragment.invoke(activity), chat.fragmentType);
        sender = HostBindings.require(chat.sender.get(fragment), chat.senderType);
        keyboard = keyboardField.get(fragment); user = chat.user.get(fragment);
        conversation = AiChatContext.currentIdentity(activity).conversation;
        Object plainRecipient = chat.recipient.invoke(conversation);
        if(!(plainRecipient instanceof String)||((String)plainRecipient).isEmpty())throw new Failure("recipient_unavailable");
        recipient=(String)plainRecipient;
        send = HostBindings.textSend(loader);
        validate();
    }

    static final class Failure extends IllegalStateException {
        final String code;
        Failure(String code){super(code);this.code=code;}
    }

    void validate() throws Exception {
        if (activity.isFinishing() || activity.isDestroyed())throw new Failure("activity_closed");
        if (!AiChatContext.sameConversation(activity, snapshot))throw new Failure("account_or_conversation_changed");
        if (currentFragment.invoke(activity) != fragment
                || !Boolean.TRUE.equals(chat.resumed.invoke(fragment)))throw new Failure("fragment_changed_or_paused");
        // snapshot.target is userIdEcpt; fragment.L and Conversation.getToUserId are plain IM IDs.
        // Compare each namespace separately, never encrypted target to plain recipient.
        if (!recipient.equals(chat.peer.get(fragment))
                || !recipient.equals(chat.recipient.invoke(AiChatContext.currentIdentity(activity).conversation)))
            throw new Failure("recipient_changed");
        if (!snapshot.target.equals(chat.encryptedId.get(user)))throw new Failure("profile_target_changed");
        if(sender!=chat.sender.get(fragment)||keyboard!=keyboardField.get(fragment)||user!=chat.user.get(fragment))
            throw new Failure("send_components_changed");
    }

    static String failureCode(Exception error) {
        if(error instanceof Failure)return ((Failure)error).code;
        Throwable root=error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null?error.getCause():error;
        // Never emit exception messages, which can include host payloads or identifiers.
        return root.getClass().getSimpleName();
    }

    public void dispatch(String text) throws Exception {
        validate();
        if (before == null) {
            if (!AiChatContext.sameVisibleConversation(activity, snapshot)) throw new Failure("chat_context_changed");
        } else {
            // Our preceding segment changes the snapshot. Permit it, but stop the sequence
            // if any other new event arrived since that dispatch (including unknown senders).
            for (Object message : cache()) {
                if (!before.contains(String.valueOf(call(message, "getMsgId")))
                        && !pendingId.equals(String.valueOf(call(message, "getMsgId"))))
                    throw new Failure("chat_context_changed");
            }
        }
        before = new HashSet<>();
        for (Object message : cache()) before.add(String.valueOf(call(message, "getMsgId")));
        pending = text; pendingId = null; created = null; multipleCreated = false;
        if (DISPATCH.get() != null) throw new Failure("nested_dispatch");
        DISPATCH.set(this);
        try { send.invoke(sender, text, keyboard, user); }
        finally { DISPATCH.remove(); }
        if (multipleCreated) throw new Failure("ambiguous_created_messages");
        if (created == null) throw new Failure("message_not_created");
        Object id = call(created, "getMsgId");
        if (!(id instanceof String) || ((String) id).isEmpty() || before.contains(id))
            throw new Failure("message_identity_unavailable");
        pendingId = (String) id;
        if (!matchesPending(created)) throw new Failure("created_message_mismatch");
    }

    /** 3=read, 4=sent, 5=failed, 0=not confirmed. No speculative automatic retries. */
    public int status() throws Exception {
        validate();
        if (pendingId == null) throw new Failure("message_identity_unavailable");
        for (Object message : cache()) {
            if (!pendingId.equals(call(message, "getMsgId"))) continue;
            if (!matchesPending(message)) throw new Failure("created_message_mismatch");
            return ((Number) call(message, "getMsgStatus")).intValue();
        }
        return 0; // Created by this invocation, but not yet submitted to the conversation.
    }

    private boolean matchesPending(Object message) throws Exception {
        if (!snapshot.account.equals(call(message, "getFrom")) || !recipient.equals(call(message, "getTo"))) return false;
        Object content = call(message, "getChatMessage");
        if (content == null || ((Number) call(content, "getMsgType")).intValue() != 1) return false;
        Object body = call(content, "getMsgContent");
        return body != null && pending.equals(field(body, "text"));
    }

    private List<?> cache() throws Exception { return (List<?>) call(AiChatContext.currentIdentity(activity).conversation, "getCacheMsgs"); }
    private static Object call(Object object, String name) throws Exception {
        return object.getClass().getMethod(name).invoke(object);
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> cls = object.getClass(); cls != null; cls = cls.getSuperclass()) {
            try { Field field = cls.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
