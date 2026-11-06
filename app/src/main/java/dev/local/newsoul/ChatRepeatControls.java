package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Soul 6.37.0 native private-chat menu; only explicit item clicks can send. */
final class ChatRepeatControls {
    static boolean ready;
    static boolean forwardReady;
    private static final int REPEAT = 0x4e5301;
    private static final int FORWARD_MESSAGE = 0x4e5302;
    private static final int SAVE_VOICE = 0x4e5303;
    private static final String BODY = "cn.soulapp.imlib.msg.chat.";
    private final Map<Object, Selection> selections = new WeakHashMap<>();
    private Method account, itemId, createChat, createMessage, setType, setBody, send, sendText, gate;
    private Constructor<?> item;
    private Field peerField, senderField, userField;
    private HostMessageDecoration decoration;
    private Field keyboardField;
    private boolean legacy;
    private MessageForwardControls forward;
    private VoiceLibraryControls library;

    private static final class Selection {
        final Object fragment, message;
        final String account, peer;
        boolean consumed;
        Selection(Object fragment, Object message, String account, String peer) {
            this.fragment = fragment; this.message = message; this.account = account; this.peer = peer;
        }
    }

    static void install(XposedModule module, ClassLoader loader) {
        try { new ChatRepeatControls().installHooks(module, loader); }
        catch (Throwable error) {
            DebugDiagnostics.registration("repeat", error);
            Log.e("SoulHook102", "repeat unavailable");
            module.log(Log.ERROR, "SoulHook102", "repeat unavailable");
        }
    }

    private void installHooks(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> fragment = loader.loadClass("cn.soulapp.android.component.chat.fragment.ConversationFragment");
        Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
        Class<?> chat = loader.loadClass(BODY + "ChatMessage");
        legacy = HostBindings.legacyChat(loader);
        HostBindings.Chat host = new HostBindings.Chat(loader);
        peerField = host.peer; senderField = host.sender; userField = host.user;
        HostBindings.ChatMenu menu = new HostBindings.ChatMenu(loader,
                legacy ? null : HostSymbols.chatPresenter(loader));
        keyboardField = HostBindings.keyboard(loader);
        Class<?> helper = menu.helperType;
        item = menu.item;
        itemId = menu.itemId;
        account = HostBindings.account(loader);
        createChat = HostBindings.messageMethod(chat, "create", String.class);
        createMessage = HostBindings.messageMethod(im, "createChatSendMsg", chat, String.class);
        setType = HostBindings.messageMethod(chat, "setMsgType", int.class);
        setBody = HostBindings.messageMethod(chat, "setMsgContent", Serializable.class);
        decoration = new HostMessageDecoration(loader);
        send = menu.send;
        sendText = HostBindings.textSend(loader);
        gate = menu.gate;
        forward = MessageForwardControls.install(module, loader);
        forwardReady = forward.ready();
        library = VoiceLibraryControls.install(module, loader);
        Method click = menu.click;
        // Register the consumer first: no inert custom item if a signature is incompatible.
        module.hook(click).setId("new-soul/repeat-click")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    return handleAction(chain.getArg(7), (Context) chain.getArg(menu.clickContextIndex), "host_hook")
                            ? null : chain.proceed();
                });
        ChatMenuDiagnostics.deoptimize(module, click, "repeat.click");
        Method list = menu.list;
        module.hook(list).setId("new-soul/repeat-menu")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    try (ChatMenuTrace.Scope scope = ChatMenuDiagnostics.scope(null)) {
                    if (scope != null) scope.trace.menu++;
                    ChatMenuDiagnostics.event("repeat.menu.enter", "hook_hit=true; " + ChatMenuDiagnostics.messageFacts(chain.getArg(2))
                            + (scope == null ? "" : "; source=" + ChatMenuDiagnostics.source()));
                    Object result;
                    try { result = chain.proceed(); }
                    catch (Throwable error) { ChatMenuDiagnostics.failure("repeat.menu.host_failed", error); throw error; }
                    if (scope != null) ChatMenuTrace.remember(result);
                    try {
                        Object entries = appendActions(result, chain.getArg(0), chain.getArg(2));
                        ChatMenuTrace.remember(entries);
                        return entries;
                    } catch (Throwable error) {
                        ChatMenuDiagnostics.failure("repeat.menu.skipped", error);
                        Log.w("SoulHook102", "repeat menu skipped");
                        return result;
                    }
                    }
                });
        ChatMenuDiagnostics.deoptimize(module, list, "repeat.menu");
        // Install only after the action consumer and all payload dependencies are ready.
        installLongPressContext(module, fragment, im);
        installMenuMarks(module, loader);
        ChatMenuDiagnostics.install(module, fragment, helper, im);
        ready = true;
        DebugDiagnostics.registration("repeat", null);
        Log.i("SoulHook102", "repeat native menu installed; text/image/audio");
    }

    private static boolean pluginAction(int action) {
        return action == REPEAT || action == FORWARD_MESSAGE || action == SAVE_VOICE;
    }

    /** Shared consumer for the native hook and the direct plugin-only view callback. */
    private boolean handleAction(Object entry, Context context, String route) throws Exception {
        if (entry == null) return false;
        int action = (int) itemId.invoke(entry);
        if (!pluginAction(action)) return false;
        ChatMenuDiagnostics.event("repeat.action.enter", "action=" + action + "; route=" + route);
        Selection selection = selections.get(entry);
        if (selection == null || selection.consumed) {
            ChatMenuDiagnostics.event("repeat.action.skip", "action=" + action + "; reason="
                    + (selection == null ? "selection_missing" : "already_consumed"));
            if (selection == null) Toast.makeText(context, "消息已失效，请重新选择", Toast.LENGTH_SHORT).show();
            return true;
        }
        selection.consumed = true;
        try {
            if (action == SAVE_VOICE) {
                validate(selection);
                library.save(selection.fragment, copyBody(call(selection.message, "getChatMessage")));
            } else if (action == FORWARD_MESSAGE) {
                Activity activity = validate(selection);
                Object source = call(selection.message, "getChatMessage");
                forward.open(activity, selection.account, selection.peer,
                        (int) call(source, "getMsgType"), copyBody(source));
            } else repeat(selection);
        } catch (Throwable error) {
            ChatMenuDiagnostics.failure("repeat.action.failed", error);
            Log.w("SoulHook102", "repeat failed; no automatic retry");
            Toast.makeText(context, action == SAVE_VOICE ? "无法保存语音，请重新选择" : action == FORWARD_MESSAGE
                    ? "无法打开转发，请重新选择" : "复读未完成，请检查消息状态", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** Leaves every native item listener intact; f(true) is Soul's own animated dismiss. */
    private boolean bindAction(View view, Object entry, Object popup, Method dismiss) throws Exception {
        int action = (int) itemId.invoke(entry);
        if (!pluginAction(action)) return false;
        view.setOnClickListener(clicked -> {
            ChatMenuDiagnostics.event("repeat.action.click", "action=" + action + "; route=direct_view");
            try { handleAction(entry, clicked.getContext(), "direct_view"); }
            catch (Throwable error) { ChatMenuDiagnostics.failure("repeat.action.failed", error); }
            finally {
                try { dismiss.invoke(popup, true); }
                catch (Throwable error) { ChatMenuDiagnostics.failure("repeat.action.dismiss_failed", error); }
            }
        });
        return true;
    }

    private boolean menuContainer(Class<?> type) {
        return android.widget.GridLayout.class.isAssignableFrom(type)
                || (legacy && android.widget.LinearLayout.class.isAssignableFrom(type));
    }

    private void bindRenderedActions(Object popup, List<?> entries, Method dismiss) throws Exception {
        for (Field field : popup.getClass().getDeclaredFields()) {
            if (!menuContainer(field.getType())) continue;
            field.setAccessible(true);
            android.view.ViewGroup grid = (android.view.ViewGroup) field.get(popup);
            if (grid == null || grid.getChildCount() != entries.size()) {
                ChatMenuDiagnostics.event("repeat.action.bind_skip", "reason=grid_mismatch; children="
                        + (grid == null ? -1 : grid.getChildCount()) + "; entries=" + entries.size());
                continue;
            }
            int bound = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (bindAction(grid.getChildAt(i), entries.get(i), popup, dismiss)) bound++;
            }
            ChatMenuDiagnostics.event("repeat.action.bind", "route=direct_view; count=" + bound
                    + "; entries=" + entries.size());
        }
    }

    private Object appendActions(Object result, Object owner, Object message) throws Exception {
        if (!(result instanceof List<?>)) {
            ChatMenuDiagnostics.event("repeat.menu.skip", "reason=not_list"); return result;
        }
        ChatMenuDiagnostics.event("repeat.menu.original", "count=" + ((List<?>) result).size());
        if (((List<?>) result).isEmpty()) {
            ChatMenuDiagnostics.event("repeat.menu.skip", "reason=empty_menu"); return result;
        }
        for (Object existing : (List<?>) result) {
            int id = (int) itemId.invoke(existing);
            if (id == REPEAT || id == FORWARD_MESSAGE || id == SAVE_VOICE) {
                ChatMenuDiagnostics.event("repeat.menu.skip", "reason=already_added");
                return result;
            }
        }
        String own = (String) account.invoke(null), peer = (String) peerField.get(owner);
        if (!eligible(message, own, peer)) return result;
        // Validate all payload fields before offering an action; do not retain a mutable clone.
        copyBody(call(message, "getChatMessage"));
        Context context = (Context) call(owner, "getContext");
        int icon = context.getResources().getIdentifier("c_ct_resend_menu", "drawable", context.getPackageName());
        int messageType = (int) call(call(message, "getChatMessage"), "getMsgType");
        if (icon == 0 && messageType != 4) {
            ChatMenuDiagnostics.event("repeat.menu.skip", "reason=missing_repeat_icon"); return result;
        }
        ChatMenuDiagnostics.event("repeat.menu.resources", "repeat_icon=true; forward_ready=" + forward.ready());
        ArrayList<Object> entries = new ArrayList<>((List<?>) result);
        if (messageType != 4) {
            Object entry = item.newInstance("复读", icon, REPEAT);
            entries.add(entry);
            selections.put(entry, new Selection(owner, message, own, peer));
        }
        if (forward.ready()) {
            int shareIcon = context.getResources().getIdentifier(HostBindings.chatShareIcon(legacy), "drawable", context.getPackageName());
            ChatMenuDiagnostics.event("repeat.menu.forward", "share_icon=" + (shareIcon != 0));
            if (shareIcon != 0) {
                Object share = item.newInstance("转发", shareIcon, FORWARD_MESSAGE);
                entries.add(share);
                selections.put(share, new Selection(owner, message, own, peer));
                Log.i("SoulHook102", "message forward menu added");
            }
        }
        if (VoiceLibraryControls.ready && (int) call(call(message, "getChatMessage"), "getMsgType") == 5) {
            // Match the host menu's white line icons; gift-share save includes a blue disc.
            int saveIcon = context.getResources().getIdentifier("c_ct_chat_icon_photo_download", "drawable", context.getPackageName());
            Object save = item.newInstance("存入语音库", saveIcon != 0 ? saveIcon : icon, SAVE_VOICE);
            entries.add(save);
            selections.put(save, new Selection(owner, message, own, peer));
        }
        Log.i("SoulHook102", "repeat menu added; type=" + call(call(message, "getChatMessage"), "getMsgType")
                + "; own=" + own.equals(call(message, "getFrom")));
        ChatMenuDiagnostics.event("repeat.menu.return", "count=" + entries.size() + "; added=" + (entries.size() - ((List<?>) result).size()));
        ChatMenuTrace.remember(entries);
        return entries;
    }

    private void installLongPressContext(XposedModule module, Class<?> fragment, Class<?> im) {
        try {
            Method caller = HostBindings.bubbleLongClick(fragment, im, View.class, legacy);
            module.hook(caller).setId("new-soul/repeat-context")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        try (ChatMenuContext ignored = ChatMenuContext.enter(
                                chain.getThisObject(), chain.getArg(1), chain.getArg(0))) {
                            return chain.proceed();
                        }
                    });
            ChatMenuDiagnostics.deoptimize(module, caller, "repeat.context");
            DebugDiagnostics.registration("repeat.context", null);
        } catch (Throwable error) { DebugDiagnostics.registration("repeat.context", error); }
    }

    private Object rendererEntries(Object original) {
        ChatMenuContext current = ChatMenuContext.takeRenderer();
        if (current == null || !ready) return original;
        try {
            if (!(current.anchor instanceof View) || !((View) current.anchor).isAttachedToWindow()) return original;
            String own = (String) account.invoke(null), peer = (String) peerField.get(current.fragment);
            validate(new Selection(current.fragment, current.message, own, peer));
            Object result = appendActions(original, current.fragment, current.message);
            ChatMenuDiagnostics.event("repeat.fallback", "added=" + (result != original));
            return result;
        } catch (Throwable error) {
            ChatMenuDiagnostics.failure("repeat.fallback.skipped", error);
            return original;
        }
    }

    private void installMenuMarks(XposedModule module, ClassLoader loader) {
        try {
            Class<?> popup = loader.loadClass("cn.soulapp.android.component.chat.window.ChatLongClickPopUp");
            Method dismiss = popup.getMethod("f", boolean.class);
            Method bind = null;
            for (Method method : popup.getDeclaredMethods()) {
                if (method.getReturnType() == popup && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{List.class})) {
                    if (bind != null) throw new IllegalStateException("Ambiguous menu renderer");
                    bind = method;
                }
            }
            if (bind == null) throw new NoSuchMethodException("Menu renderer");
            module.hook(bind).setId("new-soul/action-marks-chat")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        try (ChatMenuTrace.Scope scope = ChatMenuDiagnostics.scope(chain.getArg(0))) {
                        if (scope != null) {
                            scope.trace.render++;
                            ChatMenuDiagnostics.event("repeat.render.enter", "source=" + ChatMenuDiagnostics.source());
                        }
                        Object originalEntries = chain.getArg(0);
                        Object renderedEntries = rendererEntries(originalEntries);
                        ChatMenuTrace.remember(renderedEntries);
                        // Never retry the host call after a failure: it may have already built views.
                        Object result = renderedEntries == originalEntries ? chain.proceed()
                                : chain.proceed(new Object[]{renderedEntries});
                        try {
                            List<?> entries = (List<?>) renderedEntries;
                            if (entries == null) { ChatMenuDiagnostics.event("repeat.render", "entries=null"); return result; }
                            if (DebugDiagnostics.active()) {
                                int custom = 0;
                                for (Object entry : entries) {
                                    int id = (int) itemId.invoke(entry);
                                    if (id == REPEAT || id == FORWARD_MESSAGE || id == SAVE_VOICE) custom++;
                                }
                                ChatMenuDiagnostics.event("repeat.render", "count=" + entries.size() + "; custom=" + custom);
                            }
                            // Bind all plugin actions before icon work so an appearance failure cannot
                            // leave later entries dependent on the host callback again.
                            try { bindRenderedActions(chain.getThisObject(), entries, dismiss); }
                            catch (Throwable error) { ChatMenuDiagnostics.failure("repeat.action.bind_failed", error); }
                            for (Field field : popup.getDeclaredFields()) {
                                if (!menuContainer(field.getType())) continue;
                                field.setAccessible(true);
                                android.view.ViewGroup grid = (android.view.ViewGroup) field.get(chain.getThisObject());
                                ChatMenuDiagnostics.event("repeat.render.grid", "children=" + (grid == null ? -1 : grid.getChildCount()) + "; entries=" + entries.size());
                                if (grid == null || grid.getChildCount() != entries.size()) continue;
                                for (int i = 0; i < entries.size(); i++) {
                                    int id = (int) itemId.invoke(entries.get(i));
                                    View child = grid.getChildAt(i);
                                    android.widget.ImageView menuIcon = PluginActionMark.icon(child);
                                    if (id == SAVE_VOICE && menuIcon != null) {
                                        menuIcon.setImageDrawable(new VoiceLibraryMenuIcon(
                                                menuIcon.getResources().getDisplayMetrics().density));
                                    }
                                    PluginActionMark.set(menuIcon,
                                            id == REPEAT || id == FORWARD_MESSAGE || id == SAVE_VOICE);
                                }
                            }
                        } catch (Throwable error) { ChatMenuDiagnostics.failure("repeat.render.failed", error); Log.w("SoulHook102", "chat action marks unavailable"); }
                        return result;
                        }
                    });
            ChatMenuDiagnostics.deoptimize(module, bind, "repeat.renderer");
            DebugDiagnostics.registration("repeat.renderer", null);
        } catch (Throwable error) { DebugDiagnostics.registration("repeat.renderer", error); Log.w("SoulHook102", "chat action marks unavailable"); }
    }

    private boolean eligible(Object message, String own, String peer) throws Exception {
        if (message == null) { ChatMenuDiagnostics.event("repeat.eligible", "reason=null_message"); return false; }
        boolean matching = RepeatMessageRules.sameChat(own, peer,
                (String) call(message, "getFrom"), (String) call(message, "getTo"));
        if (!matching) {
            ChatMenuDiagnostics.event("repeat.eligible", "reason=chat_mismatch; account_present=" + (own != null && !own.isEmpty())
                    + "; peer_present=" + (peer != null && !peer.isEmpty()));
            return false;
        }
        Object chat = call(message, "getChatMessage");
        if (chat == null) { ChatMenuDiagnostics.event("repeat.eligible", "reason=null_chat"); return false; }
        int envelope = (int) call(message, "getMsgType"), type = (int) call(chat, "getMsgType"), snap = (int) call(chat, "getSnapChat");
        String snapFlag = (String) HostBindings.messageMethod(chat.getClass(), "getStringTransExt", String.class).invoke(chat, "isSnap");
        boolean anonymous = (boolean) call(message, "isAnonymityChat");
        boolean accepted = RepeatMessageRules.forwardable(envelope, type, snap, snapFlag, anonymous);
        boolean snapMarked = !(snapFlag == null || snapFlag.isEmpty() || "0".equals(snapFlag) || "false".equals(snapFlag));
        ChatMenuDiagnostics.event("repeat.eligible", "chat_match=true; envelope=" + envelope + "; type=" + type
                + "; snap=" + snap + "; snap_marked=" + snapMarked + "; anonymous=" + anonymous + "; accepted=" + accepted);
        return accepted;
    }

    private Activity validate(Selection selection) throws Exception {
        Activity activity = (Activity) call(selection.fragment, "getActivity");
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !(boolean) call(selection.fragment, "isResumed")
                || !selection.account.equals(account.invoke(null))
                || !selection.peer.equals(peerField.get(selection.fragment))
                || !eligible(selection.message, selection.account, selection.peer)) {
            throw new IllegalStateException("Chat changed");
        }
        return activity;
    }

    private void repeat(Selection selection) throws Exception {
        validate(selection);
        Object source = call(selection.message, "getChatMessage");
        Object body = copyBody(source);
        int type = (int) call(source, "getMsgType");
        if (type == 4) throw new IllegalArgumentException("Video is forward-only");
        Object sender = senderField.get(selection.fragment);
        if (sender == null) throw new IllegalStateException("Sender unavailable");
        if (type == 1) {
            Object user = userField.get(selection.fragment);
            Object keyboard = keyboardField.get(selection.fragment);
            if (user == null || keyboard == null) throw new IllegalStateException("Chat not ready");
            sendText.invoke(sender, field(body, "text"), keyboard, user);
        } else {
            Object chat = createChat.invoke(null, selection.peer);
            setType.invoke(chat, type);
            setBody.invoke(chat, body);
            decoration.apply(chat);
            Object fresh = createMessage.invoke(null, chat, selection.peer);
            if (gate != null && !(boolean) gate.invoke(null, fresh)) return;
            send.invoke(sender, fresh);
        }
        Log.i("SoulHook102", "repeat dispatched; type=" + type);
    }

    static Object copyBody(Object chat) throws Exception {
        int type = (int) call(chat, "getMsgType");
        return copyBody(type, call(chat, "getMsgContent"));
    }

    static Object copyBody(int type, Object source) throws Exception {
        if (source == null) throw new IllegalArgumentException("Missing body");
        Class<?> cls = source.getClass();
        if (type == 1 && cls.getName().equals(BODY + "TextMsg")) {
            String text = (String) field(source, "text");
            if (text == null || text.trim().isEmpty() || text.length() > 500) throw new IllegalArgumentException("Invalid text");
            return cls.getConstructor(String.class).newInstance(text);
        }
        if (type == 2 && cls.getName().equals(BODY + "ImgMsg")) return copyImage(source);
        if (type == 3 && cls.getName().equals(BODY + "ImgMsgs")) {
            List<?> originals = (List<?>) field(source, "imgMsgList");
            if (originals == null || originals.isEmpty()) throw new IllegalArgumentException("Empty images");
            List<Object> images = new ArrayList<>();
            for (Object image : originals) images.add(copyImage(image));
            return cls.getConstructor(List.class).newInstance(images);
        }
        if (type == 4 && cls.getName().equals(BODY + "VideoMsg")) {
            String url = (String) field(source, "url");
            int width = (int) field(source, "width"), height = (int) field(source, "height");
            // Native remote-video forwarding accepts missing dimensions (0), but never local-only media.
            if (!RepeatMessageRules.mediaUrl(url) || width < 0 || height < 0)
                throw new IllegalArgumentException("Invalid video");
            return cls.getConstructor(String.class, String.class, int.class, int.class)
                    .newInstance(url, "", width, height);
        }
        if (type == 5 && cls.getName().equals(BODY + "AudioMsg")) {
            String url = (String) field(source, "url");
            int duration = (int) field(source, "duration");
            if (!RepeatMessageRules.mediaUrl(url) || duration <= 0) throw new IllegalArgumentException("Invalid audio");
            return cls.getConstructor(String.class, String.class, int.class, String.class).newInstance(url, "", duration, "");
        }
        throw new IllegalArgumentException("Unsupported message body");
    }

    private static Object copyImage(Object source) throws Exception {
        if (source == null || !source.getClass().getName().equals(BODY + "ImgMsg")) throw new IllegalArgumentException("Invalid image");
        String url = (String) field(source, "imageUrl");
        if (!RepeatMessageRules.mediaUrl(url) || (int) field(source, "imageW") <= 0
                || (int) field(source, "imageH") <= 0) throw new IllegalArgumentException("Image not uploaded");
        Object copy = source.getClass().getConstructor().newInstance();
        for (String name : new String[]{"imageUrl", "imageW", "imageH"}) {
            source.getClass().getField(name).set(copy, field(source, name));
        }
        return copy;
    }

    private static Object call(Object object, String name) throws Exception {
        return HostBindings.messageMethod(object.getClass(), name).invoke(object);
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
