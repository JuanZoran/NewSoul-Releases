package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Retain already received ordinary private messages before a recall overwrites them. */
final class ChatRecallControls {
    static final String ENABLED = "chat_anti_recall";
    static final String STYLE = "chat_recall_style";
    static boolean ready;
    private static final String TAG = "SoulHook102";
    private static Method contextGetter, account, getChat, getType, getFrom, getTo, getId,
            isAnonymous, getContent, transExt, managerInstance, conversationFor,
            findMessage, databaseMessage, imInstance, listeners, refresh;

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Class<?> chat = loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage");
            Class<?> manager = loader.loadClass("cn.soulapp.imlib.ChatManager");
            contextGetter = HostSymbols.method(HostDexRules.CONTEXT, loader);
            account = HostBindings.account(loader);
            getChat = HostBindings.messageMethod(im, "getChatMessage");
            getFrom = HostBindings.messageMethod(im, "getFrom"); getTo = HostBindings.messageMethod(im, "getTo");
            getId = HostBindings.messageMethod(im, "getMsgId"); isAnonymous = HostBindings.messageMethod(im, "isAnonymityChat");
            getType = HostBindings.messageMethod(chat, "getMsgType"); getContent = HostBindings.messageMethod(chat, "getMsgContent");
            transExt = HostBindings.messageMethod(chat, "getStringTransExt", String.class);
            boolean legacy = HostBindings.legacyChat(loader);
            managerInstance = HostBindings.chatManager(loader);
            conversationFor = HostBindings.conversationFor(loader);
            findMessage = HostBindings.messageMethod(loader.loadClass("cn.soulapp.imlib.Conversation"), "getMessage", String.class);
            databaseMessage = manager.getMethod(legacy ? "C" : "K", String.class);
            Class<?> client = loader.loadClass(legacy ? "cn.soulapp.imlib.b" : "cn.soulapp.imlib.i0");
            imInstance = client.getMethod(legacy ? "t" : "v"); listeners = client.getMethod(legacy ? "u" : "w");
            refresh = loader.loadClass("cn.soulapp.imlib.listener.MsgListener").getMethod("onRefreshUi");

            installRows(module, loader, im);
            Method commands = manager.getDeclaredMethod(legacy ? "G" : "O", List.class);
            module.hook(commands).setId("new-soul/chat-anti-recall")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        boolean changed = false;
                        try {
                            Context context = context();
                            if (PluginPreferences.get(context).getBoolean(ENABLED, true)) {
                                // ChatHandler passes this same ArrayList on to onCmdMsgReceive.
                                // Remove only retained recalls, so the UI callback cannot replace
                                // the bubble later. All other commands and receipts stay intact.
                                Object value = chain.getArg(0);
                                if (value instanceof ArrayList<?>) {
                                    List<?> commandsList = (List<?>) value;
                                    for (int i = commandsList.size() - 1; i >= 0; i--) {
                                        Object command = commandsList.get(i);
                                        if (retain(context, chain.getThisObject(), command)) {
                                            commandsList.remove(i);
                                            changed = true;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable error) { Log.e(TAG, "recall interception failed", error); }
                        Object result = chain.proceed();
                        if (changed) refreshUi();
                        return result;
                    });
            module.deoptimize(commands);
            // Roaming may contain both the original message and its recall in one batch.
            Method roam = loader.loadClass("cn.soulapp.imlib.handler.RoamHandler")
                    .getDeclaredMethod("a", im);
            module.hook(roam).setId("new-soul/chat-anti-recall-roam")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Context context = context();
                            Object original = chain.getArg(0);
                            if (PluginPreferences.get(context).getBoolean(ENABLED, true)
                                    && incomingOrdinary(original) && hasBody(original)
                                    && mark(context, original)) {
                                Log.i(TAG, "recall retained; source=roam");
                                return null;
                            }
                        } catch (Throwable error) { Log.e(TAG, "roam recall interception failed", error); }
                        return chain.proceed();
                    });
            module.deoptimize(roam);
            ready = true;
            Log.i(TAG, "private chat anti-recall hooks installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "private chat anti-recall unavailable", error);
        }
    }

    private static boolean retain(Context context, Object manager, Object command) throws Exception {
        if (!incomingOrdinary(command)) return false;
        Object chat = getChat.invoke(command);
        if ((int) getType.invoke(chat) != 9 || "1".equals(transExt.invoke(chat, "isSnap"))) return false;
        String id = (String) getId.invoke(command);
        Object conversation = conversationFor.invoke(manager, getFrom.invoke(command));
        Object original = conversation == null ? null : findMessage.invoke(conversation, id);
        if (original == null) original = databaseMessage.invoke(manager, id);
        if (!incomingOrdinary(original) || !hasBody(original)
                || !getFrom.invoke(command).equals(getFrom.invoke(original))
                || !getTo.invoke(command).equals(getTo.invoke(original))
                || !id.equals(getId.invoke(original))) return false;
        if (!mark(context, original)) return false;
        Log.i(TAG, "recall retained; source=private; type=" + getType.invoke(getChat.invoke(original)));
        return true;
    }

    private static boolean incomingOrdinary(Object message) throws Exception {
        if (message == null || (boolean) isAnonymous.invoke(message) || getChat.invoke(message) == null) return false;
        String own = (String) account.invoke(null);
        return ChatRecallRules.incoming(own, (String) getFrom.invoke(message), (String) getTo.invoke(message));
    }

    private static boolean hasBody(Object message) throws Exception {
        Object chat = getChat.invoke(message);
        int type = (int) getType.invoke(chat);
        return type != 9 && getContent.invoke(chat) != null;
    }

    private static Context context() throws Exception { return (Context) contextGetter.invoke(null); }

    private static String key(Object message) throws Exception {
        String identity = account.invoke(null) + "\n" + getFrom.invoke(message) + "\n"
                + getTo.invoke(message) + "\n" + getId.invoke(message);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private static SharedPreferences marks(Context context) {
        return context.getSharedPreferences("dev.local.soulhookdemo.recalled.v1", Context.MODE_PRIVATE);
    }

    private static boolean mark(Context context, Object message) throws Exception {
        String key = key(message);
        SharedPreferences prefs = marks(context);
        return prefs.contains(key) || prefs.edit().putBoolean(key, true).commit();
    }

    static void refreshUi() {
        if (imInstance == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                for (Object listener : new ArrayList<>((List<?>) listeners.invoke(imInstance.invoke(null)))) {
                    try { refresh.invoke(listener); }
                    catch (Throwable error) { Log.w(TAG, "recall view refresh skipped", error); }
                }
            } catch (Throwable error) { Log.w(TAG, "recall view refresh unavailable", error); }
        });
    }

    private static void installRows(XposedModule module, ClassLoader loader, Class<?> im) throws Exception {
        Class<?> row = loader.loadClass("cn.soulapp.android.component.chat.widget.AbsChatDualItem");
        Class<?> holder = loader.loadClass(row.getName() + (HostBindings.legacyChat(loader) ? "$e" : "$ViewHolder"));
        Field itemView = holder.getField("itemView");
        Field user = row.getDeclaredField("e"); user.setAccessible(true);
        // Match actual dex signature rather than JADX's renamed bridge method.
        Method bind = null;
        for (Method candidate : row.getDeclaredMethods()) {
            Class<?>[] p = candidate.getParameterTypes();
            if (p.length == 4 && p[0] == holder && p[1] == im && p[2] == int.class && p[3] == List.class) {
                if (bind != null) throw new IllegalStateException("Ambiguous chat row binding");
                bind = candidate;
            }
        }
        if (bind == null) throw new NoSuchMethodException("AbsChatDualItem bind");
        module.hook(bind).setId("new-soul/chat-recall-mark")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    View root = (View) itemView.get(chain.getArg(0));
                    RecallMessageStyle.clear(root);
                    Object result = chain.proceed();
                    try {
                        Object message = chain.getArg(1);
                        if (incomingOrdinary(message) && marks(root.getContext()).contains(key(message))) {
                            Object author = user.get(chain.getThisObject());
                            String name = "对方";
                            if (author != null) for (String field : new String[]{"alias", "signature"}) {
                                Object text = author.getClass().getField(field).get(author);
                                if (text instanceof String && !((String) text).isEmpty()) { name = (String) text; break; }
                            }
                            RecallMessageStyle.apply(root, PluginPreferences.get(root.getContext())
                                    .getString(STYLE, "below"), name);
                        }
                    } catch (Throwable error) { Log.e(TAG, "recall message style unavailable", error); }
                    return result;
                });
        module.deoptimize(bind);
    }
}
