package dev.local.newsoul;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Stop automatic update prompts before they enter Soul's global popup queue. */
final class SoulUpdateControls {
    static final String ENABLED = "soul_block_update_popup_v1";
    static boolean ready;
    static void install(XposedModule module, ClassLoader loader) {
        try {
            Method context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            Class<?> callback = loader.loadClass(HostBindings.legacyChat(loader) ? "n9.c$a"
                    : "cn.soulapp.android.client.component.middle.platform.version.UpdateManager$checkVersion$1");
            Class<?> entity = loader.loadClass("cn.soulapp.android.client.component.middle.platform.version.bean.VersionUpdateEntity");
            Method result = callback.getDeclaredMethod("a", entity, boolean.class);
            Field trigger = callback.getDeclaredField("b"), prompt = callback.getDeclaredField("c");
            if (result.getReturnType() != void.class || trigger.getType() != int.class || prompt.getType() != boolean.class)
                throw new IllegalStateException("Unknown update callback");
            trigger.setAccessible(true); prompt.setAccessible(true);
            module.hook(result).setId("new-soul/soul-update-popup")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        boolean block = false;
                        try {
                            Context app = (Context) context.invoke(null);
                            block = SoulUpdateRules.block(PluginPreferences.get(app).getBoolean(ENABLED, false),
                                    trigger.getInt(chain.getThisObject()), prompt.getBoolean(chain.getThisObject()));
                        } catch (Throwable error) { Log.w("SoulHook102", "update popup preference unavailable", error); }
                        if (block) {
                            Log.i("SoulHook102", "automatic Soul update popup blocked before enqueue");
                            return null;
                        }
                        return chain.proceed();
                    });
            module.deoptimize(result);
            ready = true;
            module.log(Log.INFO, "SoulHook102", "Soul update popup control installed");
        } catch (Throwable error) { module.log(Log.ERROR, "SoulHook102", "Soul update popup control unavailable", error); }
    }
}
