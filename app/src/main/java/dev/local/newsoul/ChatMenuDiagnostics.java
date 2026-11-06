package dev.local.newsoul;

import android.view.View;
import java.lang.reflect.Method;
import java.util.List;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Soul 6.37.0 actual DEX signatures. Observers never alter menu arguments or invoke sends. */
final class ChatMenuDiagnostics {
    static void install(XposedModule module, Class<?> fragment, Class<?> helper, Class<?> im) {
        installBoundary(module, fragment, "ac", new Class<?>[]{View.class, im, int.class}, true, 1, -1);
        installBoundary(module, helper, "y", new Class<?>[]{fragment, List.class, View.class, im, int.class}, false, 3, 1);
    }
    private static void installBoundary(XposedModule module, Class<?> owner, String name,
            Class<?>[] parameters, boolean caller, int messageIndex, int listIndex) {
        String point = caller ? "repeat.caller" : "repeat.show";
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            if (method.getReturnType() != void.class
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers()) == caller)
                throw new NoSuchMethodException("Unexpected boundary signature");
            module.hook(method).setId("new-soul/" + point)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (!DebugDiagnostics.active()) return chain.proceed();
                        Object list = listIndex < 0 ? null : chain.getArg(listIndex);
                        try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(caller, list)) {
                            if (!caller) { scope.trace.show++; ChatMenuTrace.remember(list); }
                            event(point + ".enter", messageFacts(chain.getArg(messageIndex))
                                    + "; count=" + (list instanceof List<?> ? ((List<?>) list).size() : -1)
                                    + "; source=" + source());
                            try { return chain.proceed(); }
                            catch (Throwable error) { failure(point + ".host_failed", error); throw error; }
                            finally { event(point + ".exit", scope.trace.summary()); }
                        }
                    });
            DebugDiagnostics.registration(point, null);
            // ac calls k and y; deoptimizing k itself does not undo code inlined into ac.
            deoptimize(module, method, point);
        } catch (Throwable error) { DebugDiagnostics.registration(point, error); }
    }
    static void deoptimize(XposedModule module, Method method, String point) {
        try {
            boolean success = module.deoptimize(method);
            DebugDiagnostics.setupFact(point + ".deoptimize", "success=" + success
                    + "; method=" + method.getDeclaringClass().getName() + "." + method.getName());
        } catch (Throwable error) { DebugDiagnostics.registration(point + ".deoptimize", error); }
    }
    static ChatMenuTrace.Scope scope(Object list) {
        return DebugDiagnostics.active() ? ChatMenuTrace.enter(false, list) : null;
    }
    static void event(String point, String facts) {
        if (!DebugDiagnostics.active()) return;
        ChatMenuTrace.Trace trace = ChatMenuTrace.current();
        DebugDiagnostics.event(point, "trace=" + (trace == null ? 0 : trace.id) + "; " + facts);
    }
    static void failure(String point, Throwable error) {
        ChatMenuTrace.Trace trace = ChatMenuTrace.current();
        DebugDiagnostics.failure(point, "trace=" + (trace == null ? 0 : trace.id), error);
    }
    static String messageFacts(Object message) {
        if (!DebugDiagnostics.active()) return "";
        if (message == null) return "message_present=false";
        try {
            Object chat = HostBindings.messageMethod(message.getClass(), "getChatMessage").invoke(message);
            if (chat == null) return "message_present=true; chat_present=false";
            Object type = HostBindings.messageMethod(chat.getClass(), "getMsgType").invoke(chat);
            return "message_present=true; type=" + (type instanceof Integer ? type : "unknown");
        } catch (Throwable error) { return "message_present=true; type=unavailable; error=" + error.getClass().getName(); }
    }
    static String source() {
        StringBuilder out = new StringBuilder(); int count = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            // Host callers only; no exception messages, parameters or message text.
            String owner = frame.getClassName();
            if (owner.startsWith("dev.local.newsoul.") || owner.equals(Thread.class.getName())) continue;
            if (!(owner.startsWith("cn.soulapp.") || owner.startsWith("uj.") || owner.startsWith("vj.") || owner.contains("patch"))) continue;
            if (out.length() > 0) out.append(" <- ");
            out.append(owner).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (++count == 6) break;
        }
        return out.length() == 0 ? "unavailable" : out.toString();
    }
}
