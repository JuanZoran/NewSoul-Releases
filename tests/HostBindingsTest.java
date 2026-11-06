package dev.local.newsoul;

import java.lang.reflect.*;
import java.util.Map;

/** Executes production ABI resolution, including deliberately incompatible host shapes. */
public final class HostBindingsTest {
    private static int checks, invoked, cancelled;
    interface Attempt { void run() throws Exception; }
    static void check(boolean pass) { checks++; if (!pass) throw new AssertionError(); }
    static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); throw new AssertionError("contract accepted incompatible shape"); }
        catch (ReflectiveOperationException expected) { checks++; }
    }
    static class Fields { private String id; static String shared; }
    static class HiddenFields extends Fields { public int id; }
    static class Conversation {}
    static class OneConversation { Conversation first; static Conversation shared; }
    static class TwoConversations extends OneConversation { Conversation second; }
    static class Methods {
        private void send(String value) { invoked++; }
        void send(Integer value) { invoked++; }
        static String account() { invoked++; return "own"; }
        int wrongReturn() { invoked++; return 0; }
    }
    static class Derived extends Methods { void unrelated() {} }
    interface KeyBoard {}
    static class User { public String userIdEcpt; }
    static class Sender { public void Y1(String s, KeyBoard k, User u) { invoked++; } public void Y1(Object s, KeyBoard k, User u) { invoked++; } }
    static class WrongSender { public static void Y1(String s, KeyBoard k, User u) {} }
    static class WrongResultSender { public boolean Y1(String s, KeyBoard k, User u) { return false; } }
    static ClassLoader loader(Map<String, Class<?>> types) {
        return new ClassLoader(HostBindingsTest.class.getClassLoader()) {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return types.containsKey(name) ? types.get(name) : super.loadClass(name);
            }
        };
    }
    static ClassLoader senderLoader(Class<?> sender) {
        return loader(Map.of(HostBindings.SENDER, sender, HostBindings.KEYBOARD, KeyBoard.class, HostBindings.USER, User.class));
    }
    public static class MediaType {}
    public static class RequestBody {
        public static RequestBody create(MediaType type, String content) { invoked++; return new RequestBody(); }
        public static RequestBody create(MediaType type, byte[] content) { throw new AssertionError("wrong overload invoked"); }
    }
    public static class Url { public String toString() { return "https://api-user.soulapp.cn/path"; } }
    public static class Request { public Url k() { return new Url(); } }
    public static class Builder {
        public Builder() {}
        public Builder q(String url) { return this; } public Builder f() { return this; }
        public Builder h(String k, String v) { return this; } public Builder j(String v, RequestBody b) { return this; }
        public Request b() { return new Request(); }
    }
    public static class LegacyBuilder {
        String url, verb, header; RequestBody body;
        public LegacyBuilder() {}
        public LegacyBuilder m(String value) { url=value; return this; }
        public LegacyBuilder d() { verb="GET"; return this; }
        public LegacyBuilder f(String k, String v) { header=k+":"+v; return this; }
        public LegacyBuilder h(String v, RequestBody b) { verb=v; body=b; return this; }
        public Request b() { return new Request(); }
    }
    public static class ResponseBody { public String string() { return "{}"; } }
    public static class Response {
        public int h() { return 200; } public ResponseBody e() { return new ResponseBody(); } public void close() {}
    }
    interface Call { Request request(); Response execute(); void cancel(); }
    interface Factory { Call newCall(Request request); }
    // Invoking the verified interface member supports inherited/default implementations.
    interface DefaultFactory extends Factory {
        default Call newCall(Request request) { return new Call() { public Request request(){return request;} public Response execute(){return new Response();} public void cancel(){cancelled++;} }; }
    }
    static class FactoryImplementation implements DefaultFactory {
        public Call newCall(String unrelated) { throw new AssertionError("wrong factory overload invoked"); }
    }
    static class Service { final Factory callFactory = new FactoryImplementation(); }
    static class RawCall { private final Service serviceMethod = new Service(); private Call createRawCall() { return serviceMethod.callFactory.newCall(new Request()); } }
    static class WrongRaw { private final Service serviceMethod = new Service(); private Object createRawCall(){return null;} }
    public static class MissingPost { public static RequestBody create(MediaType type, byte[] content) { return new RequestBody(); } }
    static class Mine extends User {}
    static class Account { public static Mine v(){return new Mine();} }
    static class InstanceAccount { public Mine v(){return new Mine();} }
    static ClassLoader transportLoader() { return transportLoader(Map.of()); }
    static ClassLoader transportLoader(Map<String,Class<?>> changes) {
        java.util.HashMap<String,Class<?>> types = new java.util.HashMap<>(Map.ofEntries(
            Map.entry("okhttp3.t$a", Builder.class), Map.entry("okhttp3.t", Request.class),
            Map.entry("okhttp3.Call", Call.class), Map.entry("okhttp3.Call$Factory", Factory.class),
            Map.entry("okhttp3.v", Response.class), Map.entry("okhttp3.w", ResponseBody.class),
            Map.entry("okhttp3.n", Url.class), Map.entry("okhttp3.u", RequestBody.class), Map.entry("okhttp3.p", MediaType.class),
            Map.entry("retrofit2.ServiceMethod", Service.class), Map.entry("retrofit2.OkHttpCall", RawCall.class)));
        types.putAll(changes); return loader(types);
    }
    public static void main(String[] args) throws Exception {
        Method m = HostBindings.method(Methods.class, "send", void.class, false, String.class);
        check(m.getParameterTypes()[0] == String.class);
        check(invoked == 0); // Signature resolution must never execute candidates.
        rejects(() -> HostBindings.method(Methods.class, "send", void.class, false, Object.class));
        rejects(() -> HostBindings.method(Methods.class, "wrongReturn", void.class, false));
        rejects(() -> HostBindings.method(Methods.class, "account", String.class, false));
        rejects(() -> HostBindings.method(Methods.class, "send", void.class, true, String.class));
        rejects(() -> HostBindings.method(Derived.class, "send", void.class, false, String.class));
        check(HostBindings.inheritedMethod(Derived.class, "wrongReturn", int.class).getDeclaringClass() == Methods.class);
        check(HostBindings.field(Fields.class, "id", String.class).getDeclaringClass() == Fields.class);
        rejects(() -> HostBindings.field(HiddenFields.class, "id", String.class));
        rejects(() -> HostBindings.field(Fields.class, "shared", String.class));
        rejects(() -> HostBindings.require(new Object(), Fields.class));
        check(HostBindings.uniqueField(OneConversation.class, Conversation.class).getName().equals("first"));
        rejects(() -> HostBindings.uniqueField(TwoConversations.class, Conversation.class));
        rejects(() -> HostBindings.uniqueField(Fields.class, Conversation.class));
        check(HostBindings.textSend(senderLoader(Sender.class)).getParameterTypes()[0] == String.class);
        rejects(() -> HostBindings.textSend(senderLoader(WrongSender.class)));
        rejects(() -> HostBindings.textSend(senderLoader(WrongResultSender.class)));
        check(invoked == 0);
        rejects(() -> new HostBindings.Transport(transportLoader(Map.of("retrofit2.OkHttpCall", WrongRaw.class))));
        ClassLoader noPost = transportLoader(Map.of("okhttp3.u", MissingPost.class));
        check(new HostBindings.Transport(noPost) != null);
        rejects(() -> new HostBindings.Post(noPost));
        ClassLoader accountLoader = loader(Map.of("cn.soulapp.android.client.component.middle.platform.model.api.user.Mine", Mine.class,
            "cn.soulapp.android.client.component.middle.platform.utils.user.a", Account.class));
        HostBindings.Account account = new HostBindings.Account(accountLoader);
        check(account.encryptedId.getDeclaringClass() == User.class);
        rejects(() -> new HostBindings.Account(loader(Map.of("cn.soulapp.android.client.component.middle.platform.model.api.user.Mine", Mine.class,
            "cn.soulapp.android.client.component.middle.platform.utils.user.a", InstanceAccount.class))));
        HostBindings.Transport transport = new HostBindings.Transport(transportLoader());
        RawCall raw = new RawCall(); Object factory = transport.capture(raw);
        Object request = transport.build.invoke(transport.builder.newInstance());
        Object call = transport.newCall.invoke(factory, request);
        check(transport.request.invoke(call) == request);
        check((int) transport.code.invoke(transport.execute.invoke(call)) == 200);
        transport.cancel.invoke(call); check(cancelled == 1);
        HostBindings.Post post = new HostBindings.Post(transportLoader());
        check(post.createBody.getParameterTypes()[1] == String.class);
        post.createBody.invoke(null, null, ""); check(invoked == 1);
        rejects(() -> HostBindings.voiceSend(senderLoader(Sender.class))); // Text remains independently available.
        check(HostBindings.textSend(senderLoader(Sender.class)) != null);
        ClassLoader legacy = transportLoader(Map.ofEntries(
            Map.entry("okhttp3.r", Request.class), Map.entry("okhttp3.t", Response.class),
            Map.entry("okhttp3.r$a", LegacyBuilder.class), Map.entry("okhttp3.s", RequestBody.class),
            Map.entry("okhttp3.u", ResponseBody.class), Map.entry("okhttp3.m", Url.class),
            Map.entry("okhttp3.n", MediaType.class)));
        HostBindings.Transport oldTransport = new HostBindings.Transport(legacy);
        LegacyBuilder oldBuilder = (LegacyBuilder)oldTransport.builder.newInstance();
        oldTransport.setUrl.invoke(oldBuilder, "https://api-user.soulapp.cn/test");
        oldTransport.get.invoke(oldBuilder);
        check("GET".equals(oldBuilder.verb));
        check("https://api-user.soulapp.cn/test".equals(oldBuilder.url));
        HostBindings.Post oldPost = new HostBindings.Post(legacy);
        Object oldBody = oldPost.createBody.invoke(null, null, "");
        oldPost.header.invoke(oldBuilder, "Content-Type", "application/x-www-form-urlencoded");
        oldPost.method.invoke(oldBuilder, "POST", oldBody);
        check("POST".equals(oldBuilder.verb) && oldBuilder.body == oldBody);
        check("Content-Type:application/x-www-form-urlencoded".equals(oldBuilder.header));
        check((int)oldTransport.code.invoke(oldTransport.execute.invoke(
            oldTransport.newCall.invoke(oldTransport.capture(raw), oldTransport.build.invoke(oldBuilder)))) == 200);
        rejects(() -> new HostBindings.Transport(transportLoader(Map.of("okhttp3.v", Request.class))));
        System.out.println("HostBindingsTest: " + checks + " production contract checks passed");
    }
}
