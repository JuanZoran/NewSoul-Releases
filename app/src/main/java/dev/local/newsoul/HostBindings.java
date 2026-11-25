package dev.local.newsoul;

import java.lang.reflect.*;

/** Fixed, verified host ABI. Capabilities are resolved independently and never cached globally.
 * Dynamic DexKit bindings remain in HostSymbols; this class does not guess replacement names. */
final class HostBindings {
    static final String FRAGMENT = "cn.soulapp.android.component.chat.fragment.ConversationFragment";
    private static final String SENDER_643 = "cn.soulapp.android.component.chat.utils.f5";
    static final String SENDER = "cn.soulapp.android.component.chat.utils.y4";
    static final String LEGACY_SENDER = "cn.soulapp.android.component.chat.utils.t2";
    static final String USER = "cn.soulapp.android.client.component.middle.platform.model.api.user.ImUserBean";
    static final String CONVERSATION = "cn.soulapp.imlib.Conversation";
    static final String KEYBOARD = "cn.soulapp.lib_input.view.IMediaKeyBoard";
    static final String MESSAGE = "cn.soulapp.imlib.msg.ImMessage";
    static final String REQUEST_BUILDER = "okhttp3.t$a", REQUEST_BODY = "okhttp3.u";
    static final String RAW_CALL = "retrofit2.OkHttpCall";
    private HostBindings() {}

    static boolean legacyChat(ClassLoader loader) throws ClassNotFoundException {
        Class<?> parent = loader.loadClass(FRAGMENT).getSuperclass();
        return parent != null && parent.getName().equals(
                "cn.soulapp.android.component.chat.fragment.BaseConversationFragment");
    }
    static boolean uses643Bindings(ClassLoader loader) {
        try {
            return loader.loadClass(FRAGMENT).getDeclaredField("R").getType().getName().equals(SENDER_643);
        } catch (ReflectiveOperationException missing) {
            return false;
        }
    }
    static Class<?> sender(ClassLoader loader) throws ClassNotFoundException {
        if (uses643Bindings(loader)) return loader.loadClass(SENDER_643);
        try { return loader.loadClass(SENDER); }
        catch (ClassNotFoundException missing) { return loader.loadClass(LEGACY_SENDER); }
    }
    static Method commandSend(ClassLoader loader, boolean retryArgument) throws Exception {
        Class<?> manager = loader.loadClass("cn.soulapp.imlib.ChatManager");
        Class<?> im = loader.loadClass(MESSAGE);
        Class<?>[] args = retryArgument ? new Class<?>[]{im, boolean.class} : new Class<?>[]{im};
        if (uses643Bindings(loader)) return method(manager, retryArgument ? "A0" : "z0", void.class, false, args);
        try { return method(manager, retryArgument ? "p0" : "o0", void.class, false, args); }
        catch (NoSuchMethodException missing) {
            return method(manager, retryArgument ? "a0" : "Z", void.class, false, args);
        }
    }
    static Method chatManager(ClassLoader loader) throws Exception {
        Class<?> owner = loader.loadClass("cn.soulapp.imlib.ChatManager");
        if (uses643Bindings(loader)) return method(owner, "Q", owner, true);
        try { return method(owner, "M", owner, true); }
        catch (NoSuchMethodException missing) { return method(owner, "E", owner, true); }
    }
    static Method conversationFor(ClassLoader loader) throws Exception {
        Class<?> owner = loader.loadClass("cn.soulapp.imlib.ChatManager");
        Class<?> result = loader.loadClass(CONVERSATION);
        if (uses643Bindings(loader)) return method(owner, "J", result, false, String.class);
        try { return method(owner, "F", result, false, String.class); }
        catch (NoSuchMethodException missing) { return method(owner, "z", result, false, String.class); }
    }

    static final class ContractFailure extends ReflectiveOperationException {
        ContractFailure(String detail) { super("Host contract: " + detail); }
    }
    /** Resolve on the named ABI owner, not a runtime subclass which can hide/override members. */
    static Method method(Class<?> owner, String name, Class<?> result, boolean isStatic, Class<?>... args)
            throws ReflectiveOperationException {
        Method m = owner.getDeclaredMethod(name, args);
        if (Modifier.isStatic(m.getModifiers()) != isStatic || m.getReturnType() != result)
            throw new ContractFailure(owner.getName() + "." + name + " signature");
        m.setAccessible(true); return m;
    }
    static Field field(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        if (Modifier.isStatic(f.getModifiers()) || f.getType() != type)
            throw new ContractFailure(owner.getName() + "." + name + " field signature");
        f.setAccessible(true); return f;
    }
    static Object require(Object value, Class<?> type) throws ContractFailure {
        if (!type.isInstance(value)) throw new ContractFailure(type.getName() + " receiver");
        return value;
    }
    static Field uniqueField(Class<?> owner, Class<?> type) throws ReflectiveOperationException {
        Field found = null;
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != type) continue;
            if (found != null) throw new ContractFailure(owner.getName() + " ambiguous " + type.getName());
            found = f;
        }
        if (found == null) throw new NoSuchFieldException(owner.getName() + ": " + type.getName());
        found.setAccessible(true); return found;
    }
    static Class<?> imManager(ClassLoader loader, boolean legacy) throws ClassNotFoundException {
        return loader.loadClass(legacy ? "cn.soulapp.imlib.b"
                : uses643Bindings(loader) ? "cn.soulapp.imlib.p0" : "cn.soulapp.imlib.i0");
    }
    static Method recallCommands(ClassLoader loader, boolean legacy) throws Exception {
        Class<?> manager = loader.loadClass("cn.soulapp.imlib.ChatManager");
        return method(manager, legacy ? "G" : uses643Bindings(loader) ? "S" : "O",
                void.class, false, java.util.List.class);
    }
    static Class<?> hotSplashCallback(ClassLoader loader, boolean legacy) throws ClassNotFoundException {
        return loader.loadClass(legacy ? "o5.c$a" : uses643Bindings(loader) ? "wc.e$a" : "tc.e$a");
    }
    static Method shareRestriction(Class<?> sheet, boolean legacy) throws Exception {
        return method(sheet, legacy ? "P0" : uses643Bindings(sheet.getClassLoader()) ? "s0" : "r0",
                legacy ? boolean.class : void.class, false);
    }
    static Method settingsPageLoader(ClassLoader loader, boolean legacy) throws Exception {
        Class<?> h5 = loader.loadClass("cn.soulapp.android.h5.activity.H5Activity");
        if (legacy) return h5.getDeclaredMethod("onResume");
        Method load = method(h5, uses643Bindings(loader) ? "k1" : "m1", void.class, false, boolean.class);
        if (!Modifier.isPrivate(load.getModifiers())) throw new ContractFailure("H5Activity loader visibility");
        return load;
    }
    static Method forwardSelection(Class<?> selector) throws Exception {
        return method(selector, uses643Bindings(selector.getClassLoader()) ? "H" : "F",
                void.class, false, int.class);
    }
    static Class<?> shareSender(ClassLoader loader) throws Exception {
        return loader.loadClass("cn.soulapp.android.component.chat.utils."
                + (legacyChat(loader) ? "t3" : uses643Bindings(loader) ? "u6" : "n6"));
    }
    static Method account(ClassLoader loader) throws Exception {
        if (uses643Bindings(loader)) return method(loader.loadClass("cn.soulapp.imlib.q0"), "g", String.class, true);
        Class<?> owner;
        boolean legacy = false;
        try { owner = loader.loadClass("cn.soulapp.imlib.j0"); }
        catch (ClassNotFoundException missing) { owner = loader.loadClass("nr.y"); legacy = true; }
        return method(owner, legacy ? "d" : "g", String.class, true);
    }

    /** Public model names in 6.x and their verified 4.88.0 equivalents. */
    static Method messageMethod(Class<?> owner, String name, Class<?>... args) throws NoSuchMethodException {
        try { return owner.getMethod(name, args); }
        catch (NoSuchMethodException missing) {
            String mapped = null;
            if (owner.getName().equals(MESSAGE)) mapped = switch (name) {
                case "getFrom" -> "z"; case "getTo" -> "W";
                case "getMsgId" -> "G"; case "getMsgType" -> "L";
                case "getChatMessage" -> "x"; case "isAnonymityChat" -> "Z";
                case "getIsAck" -> "C"; case "getServerTime" -> "T";
                case "getLocalTime" -> "D"; case "getTransCmd" -> "X";
                case "getMsgStatus" -> "J"; case "createChatSendMsg" -> "c";
                default -> null;
            };
            else if (owner.getName().equals("cn.soulapp.imlib.msg.chat.ChatMessage")) mapped = switch (name) {
                case "getMsgType" -> "k"; case "getMsgContent" -> "i";
                case "getSnapChat" -> "o"; case "getStringTransExt" -> "p";
                case "setMsgType" -> "A"; case "setMsgContent" -> "z"; case "setTransExtMap" -> "E";
                case "create" -> "a"; default -> null;
            };
            else if (owner.getName().equals("cn.soulapp.imlib.msg.transcmd.TransCmdMsg")
                    && name.equals("getParamJson")) mapped = "d";
            else if (owner.getName().equals(CONVERSATION)) mapped = switch (name) {
                case "getSessionId" -> "Z"; case "getToUserId" -> "c0";
                case "getChatType" -> "M"; case "getMessage" -> "X";
                default -> null;
            };
            if (mapped == null) throw missing;
            return owner.getMethod(mapped, args);
        }
    }
    static Method postMethod(Class<?> post, String name) throws NoSuchMethodException {
        try { return post.getMethod(name); }
        catch (NoSuchMethodException missing) {
            if (!post.getName().equals("cn.soulapp.android.square.post.bean.Post")) throw missing;
            String mapped = switch (name) {
                case "hasDeclare" -> "n"; case "getAdResponse" -> "c"; case "showName" -> "L";
                default -> null;
            };
            if (mapped == null) throw missing;
            return post.getMethod(mapped);
        }
    }
    static final class Account {
        final Method mine;
        final Field encryptedId;
        Account(ClassLoader loader) throws Exception {
            Class<?> model = loader.loadClass("cn.soulapp.android.client.component.middle.platform.model.api.user.Mine");
            Class<?> owner;
            boolean legacy = false;
            try { owner = loader.loadClass("cn.soulapp.android.client.component.middle.platform.utils.user.a"); }
            catch (ClassNotFoundException missing) { owner = loader.loadClass("l9.a"); legacy = true; }
            mine = method(owner, legacy ? "r" : "v", model, true);
            // Mine inherits this field in some supported hosts; resolve its actual declaration.
            encryptedId = inheritedField(model, "userIdEcpt", String.class);
        }
        String id() throws Exception {
            Object value = encryptedId.get(mine.invoke(null));
            return value instanceof String ? (String) value : "";
        }
    }
    private static Field inheritedField(Class<?> owner, String name, Class<?> type) throws Exception {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { return field(c, name, type); } catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
    static final class Chat {
        final Class<?> fragmentType, senderType, userType, conversationType;
        final Field peer, sender, user, encryptedId, senderFragment, conversation;
        final Method resumed, activity, session, chatType, recipient;
        Chat(ClassLoader loader) throws Exception {
            fragmentType = loader.loadClass(FRAGMENT); senderType = sender(loader);
            boolean legacy = legacyChat(loader);
            Class<?> fields = legacy ? fragmentType.getSuperclass() : fragmentType;
            userType = loader.loadClass(USER); conversationType = loader.loadClass(CONVERSATION);
            peer = field(fields, legacy ? "D" : "L", String.class);
            sender = field(fields, legacy ? "I" : uses643Bindings(loader) ? "R" : "Q", senderType);
            user = field(fields, legacy ? "G" : "O", userType); encryptedId = field(userType, "userIdEcpt", String.class);
            senderFragment = field(senderType, "a", fragmentType);
            conversation = uniqueField(fragmentType, conversationType);
            resumed = inheritedMethod(fragmentType, "isResumed", boolean.class);
            activity = inheritedMethod(fragmentType, "getActivity", loader.loadClass("androidx.fragment.app.FragmentActivity"));
            session = method(conversationType, legacy ? "Z" : "getSessionId", String.class, false);
            chatType = method(conversationType, legacy ? "M" : "getChatType", int.class, false);
            recipient = method(conversationType, legacy ? "c0" : "getToUserId", String.class, false);
        }
        Object current(Object fragment) throws Exception {
            return require(conversation.get(require(fragment, fragmentType)), conversationType);
        }
    }
    static Method inheritedMethod(Class<?> owner, String name, Class<?> result) throws Exception {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try { return method(c, name, result, false); } catch (NoSuchMethodException missing) { }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }
    static Method currentFragment(ClassLoader loader) throws Exception {
        Class<?> owner = loader.loadClass("cn.soulapp.android.component.chat.ConversationActivity");
        Class<?> fragment = loader.loadClass("androidx.fragment.app.Fragment");
        if (legacyChat(loader)) return method(owner, "r", fragment, false);
        if (uses643Bindings(loader)) return method(owner, "A", fragment, false);
        try { return method(owner, "y", fragment, false); }
        catch (ReflectiveOperationException changed) { return method(owner, "z", fragment, false); }
    }
    static Method profileNumberFormatter(ClassLoader loader, boolean oldStyle) throws Exception {
        if (legacyChat(loader)) return method(loader.loadClass("cn.soulapp.android.client.component.middle.platform.utils.w1"),
                "b", String.class, true, int.class);
        String name = oldStyle ? "e" : "f";
        String prefix = "cn.soulapp.android.client.component.middle.platform.utils.";
        if (uses643Bindings(loader))
            return method(loader.loadClass(prefix + "s1"), name, String.class, true, int.class);
        try { return method(loader.loadClass(prefix + "m2"), name, String.class, true, int.class); }
        catch (ReflectiveOperationException changed) {
            return method(loader.loadClass("cn.soulapp.android.client.component.middle.platform.utils.r1"),
                    name, String.class, true, int.class);
        }
    }
    static Method officialSuggestionEntry(Class<?> fragment) throws NoSuchMethodException {
        if (uses643Bindings(fragment.getClassLoader())) return fragment.getDeclaredMethod("x9");
        try { return fragment.getDeclaredMethod("P8"); }
        catch (NoSuchMethodException changed) { return fragment.getDeclaredMethod("T8"); }
    }
    static Method voiceSend(ClassLoader loader) throws Exception {
        Class<?> sender = sender(loader);
        return method(sender, sender.getName().equals(LEGACY_SENDER) ? "V1" : "q2", void.class, false,
                String.class, int.class, loader.loadClass(MESSAGE));
    }
    static Method textSend(ClassLoader loader) throws Exception {
        Class<?> sender = sender(loader);
        return method(sender, sender.getName().equals(LEGACY_SENDER) ? "L1" : "Y1", void.class, false,
                String.class, loader.loadClass(KEYBOARD), loader.loadClass(USER));
    }
    static Field keyboard(ClassLoader loader) throws Exception {
        Class<?> owner = loader.loadClass(FRAGMENT);
        boolean legacy = legacyChat(loader);
        Field f = (legacy ? owner.getSuperclass() : owner).getDeclaredField(legacy ? "j" : "s");
        if (Modifier.isStatic(f.getModifiers()) || !loader.loadClass(KEYBOARD).isAssignableFrom(f.getType()))
            throw new ContractFailure(FRAGMENT + ".s keyboard signature");
        f.setAccessible(true); return f;
    }
    static Method gameSend(ClassLoader loader, boolean dice) throws Exception {
        Class<?> sender = sender(loader);
        boolean legacy = sender.getName().equals(LEGACY_SENDER);
        return method(sender, dice ? (legacy ? "l0" : "w0") : (legacy ? "R0" : "h1"), void.class, false);
    }
    static Constructor<?> gameResult(ClassLoader loader) throws Exception {
        return loader.loadClass("cn.soulapp.imlib.msg.chat.DiceFingerMsg").getDeclaredConstructor(int.class);
    }
    static Field gameNumber(ClassLoader loader) throws Exception {
        return field(loader.loadClass("cn.soulapp.imlib.msg.chat.DiceFingerMsg"), "number", int.class);
    }
    static Method lifecycle(ClassLoader loader, boolean resume) throws Exception {
        return inheritedMethod(loader.loadClass(FRAGMENT), resume ? "onResume" : "onPause", void.class);
    }
    /** All version-specific private-chat menu members belong to the host boundary. */
    static final class ChatMenu {
        final Class<?> fragmentType, messageType, helperType;
        final Constructor<?> item;
        final Method itemId, send, gate, click, list;
        final boolean legacy;
        final int clickContextIndex;

        ChatMenu(ClassLoader loader, Class<?> modernPresenter) throws Exception {
            legacy = legacyChat(loader);
            fragmentType = loader.loadClass(FRAGMENT);
            messageType = loader.loadClass(MESSAGE);
            Class<?> owner = legacy ? fragmentType.getSuperclass() : fragmentType;
            Class<?> context = loader.loadClass("android.content.Context");
            Class<?> view = loader.loadClass("android.view.View");
            Class<?> menu = loader.loadClass("cn.soulapp.android.component.chat.window.ChatLongClickPopUp$a");
            boolean abi643 = uses643Bindings(loader);
            helperType = menuHelper(loader, legacy);
            Class<?> presenter = legacy ? owner.getDeclaredField("x").getType() : modernPresenter;
            if (presenter == null) throw new ContractFailure("chat presenter unavailable");
            item = menu.getConstructor(String.class, int.class, int.class);
            itemId = menu.getDeclaredMethod("b");
            send = sender(loader).getDeclaredMethod(legacy ? "a1" : "p1", messageType);
            send.setAccessible(true);
            // Legacy ChatManager already runs pre-send and message listeners.
            gate = legacy ? null : loader.loadClass("cn.soulapp.android.chat.utils." + (abi643 ? "g" : "f")).getMethod("a", messageType);
            click = helperType.getDeclaredMethod(legacy ? "f" : "p", messageType,
                    legacy ? context : fragmentType, legacy ? owner : context, presenter,
                    view, int.class, view, menu, int.class);
            list = menuList(loader, legacy);
            clickContextIndex = legacy ? 1 : 2;
        }
    }

    private static Class<?> menuHelper(ClassLoader loader, boolean legacy) throws ClassNotFoundException {
        return loader.loadClass("cn.soulapp.android.component.chat.utils."
                + (legacy ? "o" : uses643Bindings(loader) ? "t0" : "u0"));
    }
    static Method menuList(ClassLoader loader, boolean legacy) throws Exception {
        Class<?> fragment = loader.loadClass(FRAGMENT);
        return menuHelper(loader, legacy).getDeclaredMethod(legacy ? "d" : "k",
                legacy ? fragment.getSuperclass() : fragment, loader.loadClass("android.view.View"),
                loader.loadClass(MESSAGE));
    }
    static String chatShareIcon(boolean legacy) {
        return legacy ? "c_ct_group_share_white" : "c_ct_longclick_menu_share";
    }

    static Method bubbleLongClick(Class<?> fragment, Class<?> message, Class<?> view, boolean legacy)
            throws NoSuchMethodException {
        return legacy ? fragment.getSuperclass().getDeclaredMethod("onBubbleLongClick", view, message, int.class)
                : modernBubbleLongClick(fragment, message, view);
    }
    private static Method modernBubbleLongClick(Class<?> fragment, Class<?> message, Class<?> view)
            throws NoSuchMethodException {
        if (uses643Bindings(fragment.getClassLoader())) return fragment.getDeclaredMethod("Tc", view, message, int.class);
        try { return fragment.getDeclaredMethod("ac", view, message, int.class); }
        catch (NoSuchMethodException changed) { return fragment.getDeclaredMethod("hc", view, message, int.class); }
    }

    static final class SquareFeed {
        final Class<?> adapterType, holderType;
        final Method bind, recycle, attach;
        SquareFeed(ClassLoader loader, boolean legacy) throws Exception {
            adapterType = loader.loadClass("cn.soulapp.android.component.square.main.squarepost.SquareAdapter");
            holderType = loader.loadClass("cn.soulapp.android.component.square.main.squarepost.viewholder.BaseSquareViewHolder");
            boolean abi643 = uses643Bindings(loader);
            bind = adapterType.getDeclaredMethod(legacy ? "c" : abi643 ? "e" : "d", holderType, Object.class);
            recycle = adapterType.getDeclaredMethod(legacy ? "h" : abi643 ? "k" : "j", holderType);
            attach = adapterType.getDeclaredMethod(legacy ? "g" : abi643 ? "i" : "h", holderType);
        }
    }

    static final class CityFeed {
        final Class<?> activityType, adapterType;
        final Field itemView;
        final Method item, bind;
        CityFeed(ClassLoader loader, Class<?> post, boolean legacy) throws Exception {
            activityType = loader.loadClass("cn.soulapp.android.component.square.location.LocationSquareActivity");
            adapterType = loader.loadClass(legacy ? "cn.soulapp.android.component.square.main.PostVH"
                    : "cn.soulapp.android.component.square.location.PostLightAdapter");
            Class<?> holder = loader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
            itemView = holder.getField("itemView");
            item = legacy ? null : adapterType.getDeclaredMethod("i", int.class);
            bind = legacy ? adapterType.getDeclaredMethod("onBind", post, int.class)
                    : adapterType.getDeclaredMethod("onBindViewHolder", holder, int.class);
        }
    }
    /** Verified Soul 6.37/6.38 web paths; retain the real Call for cancellation. */
    static final class Welfare {
        final Class<?> callback;
        final Constructor<?> request;
        final Method instance, send, cancel;
        Welfare(ClassLoader loader) throws Exception {
            Class<?> owner;
            String modelName;
            if (uses643Bindings(loader)) {
                owner = loader.loadClass("ry.w"); modelName = "vy.a";
            } else {
                try { owner = loader.loadClass("vy.w"); modelName = "zy.a"; }
                catch (ClassNotFoundException missing639) {
                    try { owner = loader.loadClass("ty.w"); modelName = "xy.a"; }
                    catch (ClassNotFoundException missing638) { owner = loader.loadClass("sy.w"); modelName = "wy.a"; }
                }
            }
            Class<?> model = loader.loadClass(modelName);
            Class<?> call = loader.loadClass("okhttp3.Call");
            callback = loader.loadClass("cn.soulapp.android.net.winter.api.INetCallBack");
            if (!callback.isInterface()) throw new ContractFailure("welfare callback interface");
            method(callback, "success", void.class, false, loader.loadClass("org.json.JSONObject"));
            method(callback, "failed", void.class, false, int.class, String.class);
            request = model.getDeclaredConstructor(String.class, String.class, long.class,
                    loader.loadClass("okhttp3.m"), java.util.Map.class);
            request.setAccessible(true);
            instance = method(owner, "d", owner, true);
            send = method(owner, "k", call, false, model, callback);
            cancel = method(call, "cancel", void.class, false);
        }
    }
    static final class Transport {
        final Class<?> factoryType;
        final Constructor<?> builder;
        final Field service, factory;
        final Method create, request, url, setUrl, get, build, newCall, execute, cancel, code, body, string, close;
        Transport(ClassLoader loader) throws Exception {
            HttpTypes http = new HttpTypes(loader);
            Class<?> builderType = loader.loadClass(http.legacy ? "okhttp3.r$a" : REQUEST_BUILDER), requestType = http.request;
            Class<?> call = http.call, response = http.response;
            Class<?> responseBody = loader.loadClass(http.legacy ? "okhttp3.u" : "okhttp3.w"), serviceType = loader.loadClass("retrofit2.ServiceMethod");
            factoryType = loader.loadClass("okhttp3.Call$Factory");
            Class<?> raw = loader.loadClass(RAW_CALL);
            builder = builderType.getDeclaredConstructor();
            create = method(raw, "createRawCall", call, false);
            service = field(raw, "serviceMethod", serviceType); factory = field(serviceType, "callFactory", factoryType);
            request = method(call, "request", requestType, false);
            url = method(requestType, "k", http.url, false);
            setUrl = method(builderType, http.legacy ? "m" : "q", builderType, false, String.class);
            get = method(builderType, http.legacy ? "d" : "f", builderType, false); build = method(builderType, "b", requestType, false);
            newCall = method(factoryType, "newCall", call, false, requestType);
            execute = method(call, "execute", response, false);
            cancel = method(call, "cancel", void.class, false);
            code = method(response, "h", int.class, false);
            body = method(response, "e", responseBody, false); string = method(responseBody, "string", String.class, false);
            close = method(response, "close", void.class, false);
        }
        Object capture(Object rawCall) throws Exception {
            return require(factory.get(service.get(rawCall)), factoryType);
        }
    }
    /** POST is optional for GET-only transport; resolve only for an actual POST request. */
    static final class Post {
        final Method createBody, header, method;
        Post(ClassLoader loader) throws Exception {
            HttpTypes http = new HttpTypes(loader);
            Class<?> builder = loader.loadClass(http.legacy ? "okhttp3.r$a" : REQUEST_BUILDER);
            Class<?> body = loader.loadClass(http.legacy ? "okhttp3.s" : REQUEST_BODY);
            createBody = HostBindings.method(body, "create", body, true, loader.loadClass(http.legacy ? "okhttp3.n" : "okhttp3.p"), String.class);
            header = HostBindings.method(builder, http.legacy ? "f" : "h", builder, false, String.class, String.class);
            method = HostBindings.method(builder, http.legacy ? "h" : "j", builder, false, String.class, body);
        }
    }

    /** OkHttp's stable Call interface identifies the two verified obfuscation layouts. */
    static final class HttpTypes {
        final boolean legacy;
        final Class<?> call, request, response, url;
        HttpTypes(ClassLoader loader) throws Exception {
            call = loader.loadClass("okhttp3.Call");
            request = call.getDeclaredMethod("request").getReturnType();
            response = call.getDeclaredMethod("execute").getReturnType();
            Class<?> oldRequest = null;
            try { oldRequest = loader.loadClass("okhttp3.r"); }
            catch (ClassNotFoundException missing) { }
            legacy = request == oldRequest;
            if (request != loader.loadClass(legacy ? "okhttp3.r" : "okhttp3.t")
                    || response != loader.loadClass(legacy ? "okhttp3.t" : "okhttp3.v"))
                throw new ContractFailure("Unknown OkHttp Call layout");
            url = loader.loadClass(legacy ? "okhttp3.m" : "okhttp3.n");
            method(call, "request", request, false);
            method(call, "execute", response, false);
        }
    }
}
