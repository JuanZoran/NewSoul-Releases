package dev.local.newsoul;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;

/** Execute the real interceptors with fake host previews; no network or Android runtime. */
public final class PrivacyRepeatControlsTest {
    static final Map<Executable, XposedInterface.Hooker> hooks = new HashMap<>();
    static final Set<Executable> deoptimized = new HashSet<>();
    static int calls;
    static int registrationErrors;
    static class TestIntent extends Intent {
        boolean sender;
        @Override public boolean getBooleanExtra(String key, boolean fallback) {
            return key.equals("isSender") ? sender : fallback;
        }
        @Override public int getIntExtra(String key, int fallback) { return 1; }
    }
    static class Preview extends Activity {
        TestIntent intent;
        boolean repeat;
        @Override public Intent getIntent() { return intent; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return proxy(SharedPreferences.class, (p, m, a) ->
                    m.getName().equals("getBoolean") ? repeat : null);
        }
        public void init(Bundle state) {}
        private void C() {}
        private void G() {}
        public void onStop() {}
        static class Callback { public void onAnimationCancel() {} }
    }
    static class InvalidPreview extends Activity { public boolean C() { return true; } }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
    static Object invoke(XposedInterface.Hooker hook, Preview preview) throws Throwable {
        return hook.intercept(proxy(XposedInterface.Chain.class, (p, m, a) -> {
            if (m.getName().equals("getThisObject")) return preview;
            if (m.getName().equals("proceed")) { calls++; return null; }
            throw new AssertionError(m);
        }));
    }
    static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Throwable {
        XposedModule module = new XposedModule() {};
        module.attachFramework(proxy(XposedInterface.class, (p, m, a) -> {
            if (m.getName().equals("hook")) {
                Executable target = (Executable) a[0];
                return proxy(XposedInterface.HookBuilder.class, (builder, method, values) -> {
                    if (method.getName().equals("intercept")) {
                        hooks.put(target, (XposedInterface.Hooker) values[0]);
                        return null;
                    }
                    return builder;
                });
            }
            if (m.getName().equals("deoptimize")) { deoptimized.add((Executable) a[0]); return true; }
            if (m.getName().equals("log")) {
                for(Object value:a) if(value instanceof Throwable) registrationErrors++;
                return null;
            }
            throw new AssertionError(m);
        }), () -> {});
        Field moduleField = PrivacyPhotoControls.class.getDeclaredField("module");
        moduleField.setAccessible(true);
        moduleField.set(null, module);
        Method install = PrivacyPhotoControls.class.getDeclaredMethod("installRepeatGuard",
                ClassLoader.class, String.class, String.class, String.class);
        install.setAccessible(true);
        for (String route : List.of("snap", "privacy")) {
            String method = route.equals("snap") ? "C" : "G";
            check((boolean) install.invoke(null, Preview.class.getClassLoader(), Preview.class.getName(), method, route), "install " + route);
            XposedInterface.Hooker hook = hooks.get(Preview.class.getDeclaredMethod(method));
            Preview preview = allocate(Preview.class);
            preview.intent = allocate(TestIntent.class);
            for (boolean sender : new boolean[]{false, true}) {
                for (boolean repeat : new boolean[]{false, true}) {
                    preview.intent.sender = sender;
                    preview.repeat = repeat;
                    calls = 0;
                    invoke(hook, preview);
                    invoke(hook, preview); // timeout followed by onStop must both be suppressed
                    check(calls == (repeat && !sender ? 0 : 2), route + " sender=" + sender + " repeat=" + repeat);
                }
            }
            preview.intent.sender = false;
            preview.repeat = true;
            calls = 0;
            invoke(hook, preview);
            preview.repeat = false;
            invoke(hook, preview);
            check(calls == 1, "live preference refresh " + route);
        }
        check(deoptimized.contains(Preview.class.getDeclaredMethod("onStop")), "lifecycle caller deoptimized");
        check(deoptimized.contains(Preview.Callback.class.getDeclaredMethod("onAnimationCancel")), "animation caller deoptimized");
        check(!(boolean) install.invoke(null, Preview.class.getClassLoader(), InvalidPreview.class.getName(), "C", "snap"), "reject changed signature");
        check(!(boolean) install.invoke(null, Preview.class.getClassLoader(), Preview.class.getName(), "missing", "snap"), "reject missing gate");
        ClassLoader absent = new ClassLoader() {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if(name.startsWith("cn.soulapp.android.component.chat.snapchat."))throw new ClassNotFoundException(name);
                return super.loadClass(name);
            }
        };
        hooks.clear();int errorsBefore=registrationErrors;
        PrivacyPhotoControls.previewReady=PrivacyPhotoControls.privacyRepeatReady=PrivacyPhotoControls.snapRepeatReady=true;
        PrivacyPhotoControls.install(module,absent);
        check(!PrivacyPhotoControls.previewSupported&&!PrivacyPhotoControls.snapSupported,"missing scenes classified absent");
        check(!PrivacyPhotoControls.previewReady&&!PrivacyPhotoControls.privacyRepeatReady&&!PrivacyPhotoControls.snapRepeatReady,"absent scenes clear stale ready state");
        check(hooks.isEmpty()&&registrationErrors==errorsBefore,"missing scenes install nothing and log no error");
        ClassLoader invalid = new ClassLoader() {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if(name.endsWith("PrivacyProtectPreviewActivity"))return InvalidPreview.class;
                return absent.loadClass(name);
            }
        };
        PrivacyPhotoControls.install(module,invalid);
        check(PrivacyPhotoControls.previewSupported&&!PrivacyPhotoControls.previewReady&&!PrivacyPhotoControls.privacyRepeatReady,
                "existing incompatible scene remains unavailable");
        check(!PrivacyPhotoControls.snapSupported&&!PrivacyPhotoControls.snapRepeatReady,"missing second scene independent");
        System.out.println("PrivacyRepeatControls: both routes, receiver/sender, switch off/on, repeated callbacks, live switch and incompatible signatures passed");
    }
}
