package dev.local.newsoul;

import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Use the host no-ad startup transition. */
final class SplashBlocker {
    static volatile boolean installed;
    static volatile boolean hotInstalled;
    private static final String TAG = "SoulHook102";

    static void install(XposedModule module, ClassLoader loader) {
        if (installed) return;
        installLaunchFlag(module, loader);
        installHotLaunch(module, loader);
        try {
            Class<?> scheduler = loader.loadClass(
                    "cn.soulapp.android.component.startup.main.MainScheduler");
            boolean legacy = HostBindings.legacyChat(loader);
            Method showAd = scheduler.getDeclaredMethod(legacy ? "t" : "v");
            Method continueHome = scheduler.getDeclaredMethod(legacy ? "q" : "s");
            Field owner = scheduler.getDeclaredField("a");
            if (showAd.getReturnType() != void.class || continueHome.getReturnType() != void.class) {
                throw new IllegalStateException("Unexpected Soul startup method signature");
            }
            owner.setAccessible(true);
            continueHome.setAccessible(true);
            module.hook(showAd)
                    .setId("new-soul/skip-startup-ad")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object instance = chain.getThisObject();
                        Object host = owner.get(instance);
                        if (!(host instanceof Context)) return chain.proceed();
                        if (!PluginPreferences.get((Context) host)
                                .getBoolean("block_splash", true)) {
                            Log.i(TAG, "new soul: splash filter OFF; original startup continues");
                            return chain.proceed();
                        }
                        // Same transition selected by Soul when display_ad is false.
                        // Do not enter stage 11 or create AdViewProvider/timers at all.
                        continueHome.invoke(instance);
                        Log.i(TAG, "new soul: splash blocked; " + showAd.getName() + " -> " + continueHome.getName());
                        module.log(Log.INFO, TAG,
                                "new soul: splash blocked; " + showAd.getName() + " -> " + continueHome.getName());
                        return null;
                    });
            installed = true;
            Log.i(TAG, "new soul: splash hook installed");
            module.log(Log.INFO, TAG, "new soul: splash hook installed");
        } catch (Throwable error) {
            Log.e(TAG, "new soul: splash hook unavailable; original startup retained", error);
            module.log(Log.ERROR, TAG, "new soul: splash hook unavailable", error);
        }
    }

    private static void installHotLaunch(XposedModule module, ClassLoader loader) {
        try {
            if (HostBindings.legacyChat(loader)) {
                Class<?> callback = loader.loadClass("o5.c$a");
                Method request = callback.getDeclaredMethod("b");
                Method caller = callback.getDeclaredMethod("a");
                Method context = HostSymbols.method(HostDexRules.CONTEXT, loader);
                if (request.getReturnType() != void.class || !java.lang.reflect.Modifier.isStatic(request.getModifiers()))
                    throw new IllegalStateException("Unknown legacy hot startup request");
                module.hook(request).setId("new-soul/hot-splash-legacy-request")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Context app = (Context) context.invoke(null);
                            if (app != null && PluginPreferences.get(app).getBoolean("block_splash", true)) return null;
                            return chain.proceed();
                        });
                // Preserve foreground bookkeeping; intercept only the delayed ad request.
                module.deoptimize(caller);
                hotInstalled = true;
                module.log(Log.INFO, TAG, "new soul: legacy hot splash hook installed");
                return;
            }
            // Soul 6.37.0 HotADManager application-status callback.
            // j(String) is the first boolean gate inside m(String). Returning false
            // selects its normal no-ad result before requests or HotAdActivity launch.
            Class<?> callback = loader.loadClass("tc.e$a");
            Method gate = callback.getDeclaredMethod("j", String.class);
            Method checks = callback.getDeclaredMethod("m", String.class);
            Method foreground = callback.getDeclaredMethod("onApplicationForeground");
            Method getContext = loader.loadClass("ea.a").getDeclaredMethod("a");
            if (gate.getReturnType() != boolean.class
                    || foreground.getReturnType() != void.class
                    || !Context.class.isAssignableFrom(getContext.getReturnType())) {
                throw new IllegalStateException("Unexpected Soul hot startup signature");
            }
            module.hook(gate).setId("new-soul/hot-splash-gate")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object context = getContext.invoke(null);
                        if (!(context instanceof Context)
                                || !PluginPreferences.get((Context) context)
                                        .getBoolean("block_splash", true)) {
                            return chain.proceed();
                        }
                        String message = "new soul: hot splash blocked; HotADManager gate=false";
                        Log.i(TAG, message);
                        module.log(Log.INFO, TAG, message);
                        return false;
                    });
            // Limit deoptimization to the two known callers of this private gate.
            module.deoptimize(checks);
            module.deoptimize(foreground);
            hotInstalled = true;
            module.log(Log.INFO, TAG, "new soul: hot splash hook installed (Soul 6.37.0)");
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "new soul: hot splash hook unavailable", error);
        }
    }

    private static void installLaunchFlag(XposedModule module, ClassLoader loader) {
        try {
            Class<?> main = loader.loadClass(
                    "cn.soulapp.android.component.startup.main.MainActivity");
            module.hook(main.getDeclaredMethod("onCreate", Bundle.class))
                    .setId("new-soul/cold-launch-flag")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity activity = (Activity) chain.getThisObject();
                        HookDiagnostics.checkOnStart(activity);
                        disableLaunchAd(module, activity, activity.getIntent(), "onCreate");
                        return chain.proceed();
                    });
            module.hook(main.getDeclaredMethod("onNewIntent", Intent.class))
                    .setId("new-soul/relaunch-flag")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        disableLaunchAd(module, (Activity) chain.getThisObject(),
                                (Intent) chain.getArg(0), "onNewIntent");
                        return chain.proceed();
                    });
            Log.i(TAG, "new soul: startup intent hooks installed");
        } catch (Throwable error) {
            Log.e(TAG, "new soul: startup intent hooks unavailable", error);
        }
    }

    private static void disableLaunchAd(XposedModule module, Activity activity,
            Intent intent, String entry) {
        if (intent != null && PluginPreferences.get(activity).getBoolean("block_splash", true)) {
            // Soul's own startup scheduler reads this exact flag, defaulting to true.
            intent.putExtra("display_ad", false);
            String message = "new soul: " + entry + " display_ad=false";
            Log.i(TAG, message);
            module.log(Log.INFO, TAG, message);
        }
    }
}
