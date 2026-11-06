package dev.local.newsoul;

import android.content.Context;
import java.lang.reflect.*;
import org.json.JSONObject;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Uses Soul's authenticated transport; credentials are never copied or logged. */
final class HostAccountHttp {
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    static volatile boolean ready;
    private static volatile State active;
    private static final class State {
        final ClassLoader loader;
        final Method context;
        final HostBindings.Account account;
        final HostBindings.Transport transport;
        volatile Object factory;
        State(ClassLoader loader) throws Exception {
            this.loader = loader;
            context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            account = new HostBindings.Account(loader);
            transport = new HostBindings.Transport(loader);
        }
    }
    static void install(XposedModule module, ClassLoader host) {
        ready = false; active = null;
        try {
            State state = new State(host);
            Method create = state.transport.create;
            module.hook(create).setId("new-soul/account-transport")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        HostBindings.Transport transport = state.transport;
                        Object request = transport.request.invoke(result);
                        String domain = java.net.URI.create(String.valueOf(transport.url.invoke(request))).getHost();
                        if (active == state && java.util.Arrays.asList("api-user.soulapp.cn", "api-a.soulapp.cn",
                                "post.soulapp.cn", "user.soulapp.cn").contains(domain)) {
                            boolean first = state.factory == null;
                            state.factory = transport.capture(chain.getThisObject());
                            if (first) module.log(android.util.Log.INFO, "SoulHook102", "account transport captured");
                            if (first) MAIN.post(SquareFeedFilter::refresh);
                            AccountAutomationControls.kick();
                        }
                    } catch (Throwable error) {
                        DebugDiagnostics.failure("account.transport.capture", error);
                    }
                    return result;
                });
            module.deoptimize(create);
            // enqueue / execute / request can inline createRawCall in the host's ART profile.
            for (Method caller : create.getDeclaringClass().getDeclaredMethods())
                if (!Modifier.isAbstract(caller.getModifiers()) && !Modifier.isNative(caller.getModifiers())) module.deoptimize(caller);
            active = state; ready = true;
            module.log(android.util.Log.INFO, "SoulHook102", "account transport registered");
            DebugDiagnostics.registration("account.transport", null);
        } catch (Throwable error) { DebugDiagnostics.registration("account.transport", error); module.log(android.util.Log.ERROR, "SoulHook102", "account transport unavailable", error); }
    }
    static Context context() {
        State state = active;
        try { return state == null ? null : (Context) state.context.invoke(null); } catch (Exception e) { return null; }
    }
    static String account() {
        State state = active;
        try { return state == null ? "" : state.account.id(); } catch (Exception e) { return ""; }
    }
    static boolean available() { State state = active; return ready && state != null && state.factory != null && !account().isEmpty(); }
    private static void validate(State state, String account) throws Exception {
        if (state == null || active != state || !ready || state.factory == null || account.isEmpty() || !account.equals(state.account.id()))
            throw new IllegalStateException("登录状态已变化");
    }
    static JSONObject request(String account, String url, boolean post) throws Exception {
        return request(account, url, post, new HostRequestScope(30000));
    }
    /** Takes ownership of a one-shot scope; its deadline also covers reading the response body. */
    static JSONObject request(String account, String url, boolean post, HostRequestScope scope) throws Exception {
        try (HostRequestScope ignored = scope) {
            return execute(account, url, post, scope);
        }
    }
    private static JSONObject execute(String account, String url, boolean post, HostRequestScope scope) throws Exception {
        State state = active;
        scope.check();
        validate(state, account);
        java.net.URI uri = java.net.URI.create(url);
        if (!"https".equals(uri.getScheme()) || !java.util.Arrays.asList("api-user.soulapp.cn", "api-a.soulapp.cn", "increase-openapi.soulapp.cn").contains(uri.getHost()))
            throw new IllegalArgumentException("Unexpected host");
        HostBindings.Transport transport = state.transport;
        Object builder = transport.builder.newInstance();
        transport.setUrl.invoke(builder, url);
        if (post) {
            HostBindings.Post binding = new HostBindings.Post(state.loader);
            Object body = binding.createBody.invoke(null, null, "");
            binding.header.invoke(builder, "Content-Type", "application/x-www-form-urlencoded");
            binding.method.invoke(builder, "POST", body);
        } else transport.get.invoke(builder);
        validate(state, account);
        Object call = transport.newCall.invoke(state.factory, transport.build.invoke(builder));
        scope.attach(() -> {
            try { transport.cancel.invoke(call); }
            catch (Exception failure) { DebugDiagnostics.failure("account.request.cancel", failure); }
        }, () -> {
            try { validate(state, account); return true; }
            catch (Exception changed) { return false; }
        });
        scope.check();
        Object response = transport.execute.invoke(call);
        try {
            scope.check();
            int code = (int) transport.code.invoke(response);
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            JSONObject json = new JSONObject((String) transport.string.invoke(transport.body.invoke(response)));
            scope.check();
            validate(state, account);
            return json;
        } finally { transport.close.invoke(response); }
    }
    static Object field(Object object, String name) throws Exception {
        if (object == null) return null;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
}
