package dev.local.newsoul;

import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Changes the decoded limitInfo model before host subscribers receive it. */
final class ChatGateControls {
    static final String GIFT = "chat_remove_gift_gate";
    static final String BELL = "chat_remove_bell_gift_gate";
    static boolean giftReady, bellReady;
    static boolean bellSupported = true;

    static void install(XposedModule module, ClassLoader loader) {
        giftReady = false;
        bellReady = false;
        try {
            ChatGateResponse response = new ChatGateResponse(loader);
            module.hook(response.parse).setId("new-soul/gift-chat-response")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            if (response.apply(chain.getArg(0), result, ChatExperienceControls.enabled(GIFT))) {
                                Log.i("SoulHook102", "chat limitInfo model cleared");
                            }
                        } catch (Exception error) {
                            unavailable("response", error);
                        }
                        return result;
                    });
            giftReady = true;
            deoptimize(module, response.parse);
            deoptimizeCallers(module, loader, "retrofit2.OkHttpCall", null);
            deoptimizeCallers(module, loader, "retrofit2.OkHttpCall$1", null);
        } catch (Throwable error) {
            unavailable("response registration", error);
        }
        installBell(module, loader);
        Log.i("SoulHook102", "gift chat hooks: response=" + giftReady + "; bell=" + bellReady);
    }

    private static void installBell(XposedModule module, ClassLoader loader) {
        try {
            Class<?> config;
            try { config = loader.loadClass("cn.soulapp.android.component.home.lovebell.bean.LoveBellProConfig"); }
            catch (ClassNotFoundException absent) { bellSupported = false; return; }
            bellSupported = true;
            Method showPopup = config.getDeclaredMethod("getShowPopup");
            if (showPopup.getReturnType() != Boolean.class && showPopup.getReturnType() != boolean.class) {
                throw new NoSuchMethodException("unexpected getShowPopup return type");
            }
            // The host invokes its normal continuation when showPopup is false.
            // Do not just hide the dialog: that would leave the continuation pending.
            module.hook(showPopup).setId("new-soul/bell-gift-popup-config")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (ChatExperienceControls.enabled(BELL)) {
                            if (Boolean.TRUE.equals(result)) {
                                Log.i("SoulHook102", "special bell gift popup bypassed");
                            }
                            return false;
                        }
                        return result;
                    });
            bellReady = true;
            deoptimize(module, showPopup);
            // Resolve the Kotlin companion structurally, without its obfuscated name.
            try {
                Class<?> dialog = loader.loadClass(
                        "cn.soulapp.android.component.home.lovebell.LoveBellProGiftDialog");
                for (Class<?> nested : dialog.getDeclaredClasses()) {
                    for (Method caller : nested.getDeclaredMethods()) {
                        if (Arrays.asList(caller.getParameterTypes()).contains(config)) {
                            deoptimize(module, caller);
                        }
                    }
                }
            } catch (Throwable error) {
                unavailable("bell callers", error);
            }
        } catch (Throwable error) {
            unavailable("bell popup config", error);
        }
    }

    private static void deoptimizeCallers(XposedModule module, ClassLoader loader, String className, String name) {
        try {
            for (Method method : loader.loadClass(className).getDeclaredMethods()) {
                if ((name == null || name.equals(method.getName()))
                        && !Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers())) {
                    deoptimize(module, method);
                }
            }
        } catch (Throwable error) {
            unavailable("callers " + className, error);
        }
    }

    private static void deoptimize(XposedModule module, Method method) {
        try {
            module.deoptimize(method);
        } catch (Throwable error) {
            unavailable("deopt " + method.getName(), error);
        }
    }

    private static void unavailable(String part, Throwable error) {
        Log.w("SoulHook102", "gift chat " + part + " unavailable", error);
    }
}
