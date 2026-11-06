package dev.local.newsoul;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Only bypass the first timestamp read in Soul's own-message recall age check. */
final class OwnRecallControls {
    static final String ENABLED = "chat_own_recall_no_time_limit";
    static boolean ready;
    private static final ThreadLocal<Object> AGE_CHECK = new ThreadLocal<>();

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Method time = HostBindings.messageMethod(im, "getLocalTime");
            Method sender = HostBindings.messageMethod(im, "getFrom");
            Method anonymous = HostBindings.messageMethod(im, "isAnonymityChat");
            Method account = HostBindings.account(loader);
            Method context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            Method recall;
            try { recall = loader.loadClass("cn.soulapp.android.component.chat.helper.WithDrawHandler").getDeclaredMethod("d", im); }
            catch (ClassNotFoundException missing) {
                recall = HostBindings.method(loader.loadClass("cn.soulapp.android.component.chat.helper.j1"),
                        "a", void.class, false, im);
            }
            module.hook(time).setId("new-soul/own-recall-age-read")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object original = chain.proceed();
                        if (AGE_CHECK.get() != chain.getThisObject()) return original;
                        // Consume immediately: subsequent database, UI and send calls must
                        // read the real timestamp. Never edit the message's stored time.
                        AGE_CHECK.remove();
                        long now = System.currentTimeMillis();
                        if (now - (long) original <= 120000L) return original;
                        Log.i("SoulHook102", "own recall local age check bypassed");
                        return now;
                    });
            module.hook(recall).setId("new-soul/own-recall-entry")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object message = chain.getArg(0);
                        boolean enabled = false;
                        try {
                            String own = (String) account.invoke(null);
                            enabled = message != null && own != null && !own.isEmpty()
                                    && own.equals(sender.invoke(message))
                                    && !(boolean) anonymous.invoke(message)
                                    && PluginPreferences.get((Context) context.invoke(null)).getBoolean(ENABLED, true);
                        } catch (Throwable error) {
                            Log.w("SoulHook102", "own recall eligibility unavailable", error);
                        }
                        if (!enabled) return chain.proceed();
                        Object previous = AGE_CHECK.get();
                        AGE_CHECK.set(message);
                        try { return chain.proceed(); }
                        finally {
                            if (previous == null) AGE_CHECK.remove(); else AGE_CHECK.set(previous);
                        }
                    });
            module.deoptimize(recall);
            ready = true;
            Log.i("SoulHook102", "own recall time limit hook installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "own recall time limit unavailable", error);
        }
    }
}
