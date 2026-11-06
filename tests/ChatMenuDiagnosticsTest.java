package dev.local.newsoul;

import android.view.View;
import io.github.libxposed.api.*;
import java.lang.reflect.*;
import java.util.*;

/** Run actual boundary interceptors: no Android UI, network or message sending. */
public final class ChatMenuDiagnosticsTest {
    static final Map<Executable,XposedInterface.Hooker> hooks = new HashMap<>();
    static final Set<Executable> deoptimized = new HashSet<>();
    static int checks;
    static boolean deoptResult = true;
    public static class Message { public Chat getChatMessage() { return new Chat(); } }
    public static class Chat { public int getMsgType() { return 5; } }
    public static class Fragment { private void ac(View view, Message message, int position) {} }
    public static class Helper { public static void y(Fragment fragment, List<?> list, View view, Message message, int position) {} }
    public static class WrongFragment {}
    public static class WrongHelper { public static void y(WrongFragment fragment, List<?> list, View view, Message message, int position) {} }
    interface Original { Object run() throws Throwable; }
    static void check(boolean condition, String name) { checks++; if (!condition) throw new AssertionError(name); }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    static Object invoke(Method method, Object[] args, Original original) throws Throwable {
        return hooks.get(method).intercept(proxy(XposedInterface.Chain.class, (p,m,a) -> {
            if (m.getName().equals("getArg")) return args[(Integer) a[0]];
            if (m.getName().equals("proceed")) return original.run();
            throw new AssertionError("Unexpected mutation/API: " + m);
        }));
    }
    public static void main(String[] args) throws Throwable {
        XposedModule module = new XposedModule() {};
        module.attachFramework(proxy(XposedInterface.class, (p,m,a) -> {
            if (m.getName().equals("hook")) {
                Executable target = (Executable) a[0];
                return proxy(XposedInterface.HookBuilder.class, (b,bm,ba) -> {
                    if (bm.getName().equals("intercept")) { hooks.put(target, (XposedInterface.Hooker) ba[0]); return null; }
                    return b;
                });
            }
            if (m.getName().equals("deoptimize")) { deoptimized.add((Executable) a[0]); return deoptResult; }
            return null;
        }), () -> {});
        ChatMenuDiagnostics.install(module, Fragment.class, Helper.class, Message.class);
        Method caller = Fragment.class.getDeclaredMethod("ac", View.class, Message.class, int.class);
        Method show = Helper.class.getDeclaredMethod("y", Fragment.class, List.class, View.class, Message.class, int.class);
        check(hooks.size() == 2, "both boundaries hooked");
        check(deoptimized.contains(caller) && deoptimized.contains(show), "callers deoptimized");
        check(DebugDiagnostics.events.stream().anyMatch(s -> s.contains("caller.deoptimize:success=true")), "deopt success recorded");
        Object[] callArgs = {null, new Message(), 0};
        Set<Long> attempts = new HashSet<>();
        for (int attempt = 0; attempt < 10; attempt++) {
            List<Object> list = new ArrayList<>(); list.add(new Object());
            Object[] showArgs = {new Fragment(), list, null, new Message(), 0};
            int[] originals = {0, 0};
            invoke(caller, callArgs, () -> {
                originals[0]++;
                long id = ChatMenuTrace.current().id; attempts.add(id);
                // Simulate the independently hooked k and renderer inside the real boundary hooks.
                try (ChatMenuTrace.Scope menu = ChatMenuDiagnostics.scope(null)) {
                    check(menu.trace.id == id, "menu keeps attempt"); menu.trace.menu++; ChatMenuTrace.remember(list);
                }
                invoke(show, showArgs, () -> {
                    originals[1]++;
                    try (ChatMenuTrace.Scope render = ChatMenuDiagnostics.scope(list)) {
                        check(render.trace.id == id, "render keeps attempt"); render.trace.render++;
                    }
                    return null;
                });
                check(ChatMenuTrace.current().menu == 1 && ChatMenuTrace.current().show == 1
                        && ChatMenuTrace.current().render == 1, "correlated hit counts");
                return null;
            });
            check(originals[0] == 1 && originals[1] == 1 && list.size() == 1 && showArgs[1] == list,
                    "original once with unmodified list");
            check(ChatMenuTrace.current() == null, "scope cleaned after original");
        }
        check(attempts.size() == 10, "repeated gestures get distinct IDs");
        check(DebugDiagnostics.events.stream().anyMatch(s -> s.contains("type=5")), "voice type recorded");
        RuntimeException failure = new RuntimeException("must not enter diagnostic text");
        int[] failures = {0};
        try { invoke(caller, callArgs, () -> { failures[0]++; throw failure; }); throw new AssertionError("swallowed original"); }
        catch (RuntimeException actual) { check(actual == failure && failures[0] == 1, "host exception unchanged and not retried"); }
        check(ChatMenuTrace.current() == null, "exception scope cleaned");
        check(DebugDiagnostics.events.stream().noneMatch(s -> s.contains(failure.getMessage())), "exception message omitted");
        DebugDiagnostics.enabled = false; DebugDiagnostics.events.clear();
        int[] disabled = {0}; invoke(caller, callArgs, () -> { disabled[0]++; return null; });
        check(disabled[0] == 1 && DebugDiagnostics.events.isEmpty() && ChatMenuTrace.current() == null, "disabled is passthrough");
        DebugDiagnostics.enabled = true;
        deoptResult = false; ChatMenuDiagnostics.deoptimize(module, caller, "test");
        check(DebugDiagnostics.events.stream().anyMatch(s -> s.contains("test.deoptimize:success=false")), "deopt false retained");
        ChatMenuDiagnostics.install(module, WrongFragment.class, WrongHelper.class, Message.class);
        check(DebugDiagnostics.events.stream().anyMatch(s -> s.contains("caller:NoSuchMethodException")), "unknown caller isolated");
        check(hooks.containsKey(WrongHelper.class.getDeclaredMethod("y", WrongFragment.class, List.class, View.class, Message.class, int.class)), "show diagnostic survives caller mismatch");
        List<Object> a = new ArrayList<>(), b = new ArrayList<>(); long original;
        try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(true, null)) { original = scope.trace.id; ChatMenuTrace.remember(a); }
        try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(false, a)) { check(scope.trace.id == original, "list reconnects after scope exit"); }
        try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(false, b)) { check(scope.trace.id != original, "equal lists do not alias"); }
        try (ChatMenuTrace.Scope outer = ChatMenuTrace.enter(true, null)) {
            try (ChatMenuTrace.Scope inner = ChatMenuTrace.enter(true, null)) { check(inner.trace.id != outer.trace.id, "nested gesture separated"); }
            check(ChatMenuTrace.current() == outer.trace, "outer scope restored");
            long[] other = {0}; var worker = TestWorkers.start(() -> {
                try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(false, null)) { other[0] = scope.trace.id; }
            }); TestWorkers.await(worker);
            check(other[0] != outer.trace.id, "thread-local attempts");
        }
        for (int i = 0; i < 65; i++) try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(true, null)) { ChatMenuTrace.remember(new Object()); }
        try (ChatMenuTrace.Scope scope = ChatMenuTrace.enter(false, a)) { check(scope.trace.id != original, "bounded list history"); }
        System.out.println("ChatMenuDiagnostics: " + checks + " checks passed");
    }
}
