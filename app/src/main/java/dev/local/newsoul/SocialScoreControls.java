package dev.local.newsoul;

import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Local etiquette-score gates; never rewrites the score or a server delivery result. */
final class SocialScoreControls {
    static final String ENABLED = "chat_remove_social_score_limit";
    static boolean modelReady, dialogReady;
    static final String MODEL = "cn.soulapp.android.component.chat.bean.SocialGraceScoreBean";
    static final String DIALOG = "cn.soulapp.android.component.planet.soulmatch.utils.SocialScoreUtils";

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> model = loader.loadClass(MODEL);
            boolean legacy = HostBindings.legacyChat(loader);
            String[] names = legacy ? new String[]{"a", "b", "c", "d"}
                    : new String[]{"getCanNotSendBeforeReply", "getCreateSessionLimit", "getForbidCall", "getForbidPic"};
            Method[] gates = new Method[names.length];
            for (int i = 0; i < names.length; i++) {
                gates[i] = model.getDeclaredMethod(names[i]);
                if (gates[i].getReturnType() != boolean.class)
                    throw new NoSuchMethodException("Unexpected social score gate " + names[i]);
            }
            for (Method gate : gates) {
                module.hook(gate).setId("new-soul/social-score-" + gate.getName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (Boolean.TRUE.equals(result) && ChatExperienceControls.enabled(ENABLED)) {
                                Log.i("SoulHook102", "social score gate bypassed: " + gate.getName());
                                return false;
                            }
                            return result;
                        });
            }
            // Follow the host presenter type and its nested callbacks instead of package names.
            java.util.List<Class<?>> callers = new java.util.ArrayList<>();
            Class<?> fragmentCallback = null;
            if (legacy) {
                // 4.88 reads forbidCall/forbidPic in the base fragment's model callback.
                callers.add(loader.loadClass("cn.soulapp.android.component.chat.fragment.BaseConversationFragment"));
            } else {
                Class<?> presenter = HostSymbols.chatPresenter(loader);
                callers.add(presenter);
                callers.addAll(java.util.Arrays.asList(presenter.getDeclaredClasses()));
                fragmentCallback = loader.loadClass(
                        "cn.soulapp.android.component.chat.fragment.ConversationFragment$d1");
                callers.add(fragmentCallback);
            }
            for (Class<?> callerClass : callers) {
                for (Method caller : callerClass.getDeclaredMethods()) {
                    if (java.util.Arrays.asList(caller.getParameterTypes()).contains(model)
                            || callerClass == fragmentCallback) module.deoptimize(caller);
                }
            }
            modelReady = true;
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "social score model unavailable", error);
        }
        try {
            Class<?> utils = loader.loadClass(DIALOG);
            Method show = utils.getDeclaredMethod("b", loader.loadClass("androidx.fragment.app.FragmentManager"),
                    loader.loadClass(DIALOG + "$a"));
            if (!Modifier.isStatic(show.getModifiers()) || show.getReturnType() != void.class)
                throw new NoSuchMethodException("Unexpected social score dialog signature");
            module.hook(show).setId("new-soul/social-score-dialog")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (ChatExperienceControls.enabled(ENABLED)) {
                            Log.i("SoulHook102", "social score dialog suppressed");
                            return null;
                        }
                        return chain.proceed();
                    });
            for (Method caller : utils.getDeclaredMethods()) module.deoptimize(caller);
            dialogReady = true;
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "social score dialog unavailable", error);
        }
        Log.i("SoulHook102", "social score hooks: model=" + modelReady + "; dialog=" + dialogReady);
    }
}
