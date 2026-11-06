package dev.local.newsoul;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class TeenagePopupControls {
    static final String ENABLED = "soul_block_teenage_popup_v1";
    static boolean ready;

    static void install(XposedModule module, ClassLoader loader) {
        ready = false;
        try {
            TeenagePopupResponse response = new TeenagePopupResponse(loader);
            Method context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            module.hook(response.parse).setId("new-soul/teenage-popup-response")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Context app = (Context) context.invoke(null);
                            if (response.apply(chain.getArg(0), result,
                                    PluginPreferences.get(app).getBoolean(ENABLED, false)))
                                Log.i("SoulHook102", "teenager popup config suppressed");
                        } catch (Exception error) {
                            Log.w("SoulHook102", "teenager popup response unavailable", error);
                        }
                        return result;
                    });
            module.deoptimize(response.parse);
            for (String owner : new String[]{"retrofit2.OkHttpCall", "retrofit2.OkHttpCall$1"})
                for (Method method : loader.loadClass(owner).getDeclaredMethods())
                    if (!Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers()))
                        module.deoptimize(method);
            ready = true;
            DebugDiagnostics.registration("teenage.popup", null);
        } catch (Throwable error) {
            DebugDiagnostics.registration("teenage.popup", error);
            module.log(Log.ERROR, "SoulHook102", "teenager popup control unavailable", error);
        }
    }
}
