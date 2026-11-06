package dev.local.newsoul;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import io.github.libxposed.api.XposedModule;

/** Uses the same signed web request path as Soul, without reading tokens or opening WebViews. */
final class HostWelfareHttp {
    static volatile boolean ready;
    private static volatile XposedModule logger;
    private static final ThreadLocal<String> LAST_SCHEMA = new ThreadLocal<>();
    private static volatile HostBindings.Welfare active;
    static void install(XposedModule module, ClassLoader loader) {
        ready = false; active = null;
        logger = module;
        try {
            active = new HostBindings.Welfare(loader); ready = true;
            diagnostic("welfare transport registered", null);
            DebugDiagnostics.registration("account.welfare.transport", null);
        } catch (Exception error) {
            diagnostic("welfare transport unavailable", error);
            DebugDiagnostics.registration("account.welfare.transport", error);
        }
    }
    static JSONObject request(String account, String url, boolean post, Map<String, Object> data,
            BooleanSupplier enabled) throws Exception {
        SignInTasks.validateRequest(url, post);
        HostBindings.Welfare binding = active;
        BooleanSupplier current = () -> ready && active == binding && HostAccountHttp.available()
                && account.equals(HostAccountHttp.account()) && enabled.getAsBoolean();
        try (HostRequestScope scope = new HostRequestScope(30000, current)) {
            scope.check();
            if (binding == null) throw new IllegalStateException("当前 Soul 版本未就绪");
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Object> result = new AtomicReference<>();
            Object callback = Proxy.newProxyInstance(binding.callback.getClassLoader(), new Class<?>[]{binding.callback},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        return "NewSoulWelfareCallback";
                    }
                    Object value = method.getName().equals("success") && args != null && args.length == 1
                            && args[0] instanceof JSONObject ? args[0]
                            : new IllegalStateException("服务返回 " + (args != null && args.length > 0 ? args[0] : "未知错误"));
                    if (result.compareAndSet(null, value)) finished.countDown();
                    return null;
                });
            Object model = binding.request.newInstance(url, post ? "POST" : "GET", 30000L, null,
                    new java.util.LinkedHashMap<>(data));
            scope.check();
            Object call = binding.send.invoke(binding.instance.invoke(null), model, callback);
            if (call == null) throw new IllegalStateException("未取得请求状态，请在 Soul 中检查结果");
            scope.attach(() -> {
                try { binding.cancel.invoke(call); }
                catch (Exception error) { DebugDiagnostics.failure("account.welfare.cancel", error); }
            }, current);
            try {
                while (!finished.await(100, TimeUnit.MILLISECONDS)) scope.check();
            } catch (InterruptedException interrupted) {
                scope.cancel(); Thread.currentThread().interrupt(); throw interrupted;
            }
            scope.check();
            Object value = result.get();
            if (value instanceof Exception) throw (Exception) value;
            if (!(value instanceof JSONObject)) throw new IllegalStateException("服务结果缺失");
            LAST_SCHEMA.set(url.substring(url.lastIndexOf('/') + 1) + " " + schema(value, 0));
            return (JSONObject) value;
        }
    }
    static void beginTask() { LAST_SCHEMA.remove(); }
    static void taskFailure(String key, Throwable error) {
        String structure = LAST_SCHEMA.get();
        diagnostic("welfare task failed: " + key + (structure == null ? "" : "\n" + structure), error);
        LAST_SCHEMA.remove();
    }
    static void diagnostic(String message, Throwable error) {
        XposedModule module = logger;
        if (module != null) {
            if (error == null) module.log(android.util.Log.INFO, "SoulHook102", message);
            else module.log(android.util.Log.ERROR, "SoulHook102", message, error);
        }
    }
    private static String schema(Object value, int depth) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (depth >= 4) return value.getClass().getSimpleName();
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            java.util.List<String> keys = new java.util.ArrayList<>();
            object.keys().forEachRemaining(keys::add);
            java.util.Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (String key : keys.subList(0, Math.min(32, keys.size())))
                out.append(key).append(':').append(schema(object.opt(key), depth + 1)).append(',');
            return out.append('}').toString();
        }
        if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            return "[count=" + array.length() + ";" + (array.length() > 0 ? schema(array.opt(0), depth + 1) : "") + "]";
        }
        return value.getClass().getSimpleName();
    }
    private HostWelfareHttp() {}
}
