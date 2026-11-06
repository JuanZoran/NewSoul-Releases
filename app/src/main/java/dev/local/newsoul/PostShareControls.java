package dev.local.newsoul;

import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Fixed host share gates; the native actions and Post payload remain untouched. */
final class PostShareControls {
    static final String ENABLED = "post_remove_share_limit";
    static boolean ready;

    static boolean canShare(boolean relay, boolean bypass) { return !relay || bypass; }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> dialog = loader.loadClass("cn.soulapp.android.square.SeedsShareDialogFragment");
            Class<?> postType = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
            Field post;
            boolean legacy;
            try { post = HostBindings.field(dialog, "E", postType); legacy = false; }
            catch (NoSuchFieldException | HostBindings.ContractFailure absent) {
                post = HostBindings.field(dialog, "B", postType); legacy = true;
            }
            Field currentPost = post;
            boolean legacyGate = legacy;
            Method check = HostBindings.method(dialog, legacy ? "P0" : "r0",
                    legacy ? boolean.class : void.class, false);
            Method init = dialog.getDeclaredMethod("initViews", View.class);
            Method renderer = legacy ? HostBindings.method(dialog, "j0", void.class, false, View.class) : null;
            if (legacy ? !Modifier.isPublic(check.getModifiers()) : !Modifier.isPrivate(check.getModifiers()))
                throw new NoSuchMethodException("Unexpected share permission signature");
            module.hook(check).setId("new-soul/post-share-permission")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // 4.88 P0 returns !relay and controls whether j0 creates native share rows.
                        // 6.x r0 dims/rebinds controls and requests getUserPrivate(28).
                        if (currentPost.get(chain.getThisObject()) != null
                                && ChatExperienceControls.enabled(ENABLED)) {
                            Log.i("SoulHook102", "post share restriction bypassed");
                            return legacyGate ? true : null;
                        }
                        return chain.proceed();
                    });
            module.deoptimize(init);
            if (renderer != null) module.deoptimize(renderer);
            ready = true;
            Log.i("SoulHook102", "post share control installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "post share control unavailable", error);
        }
    }
}
