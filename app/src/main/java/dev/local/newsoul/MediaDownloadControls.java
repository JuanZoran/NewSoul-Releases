package dev.local.newsoul;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Soul 6.37.0 download flags; never changes global media rendering. */
final class MediaDownloadControls {
    static final String IMAGE = "download_image_no_watermark";
    static final String VIDEO = "download_video_no_watermark";
    static boolean imageReady;
    static boolean videoReady;

    static void install(XposedModule module, ClassLoader loader) {
        try {
            // ImageDownloader.f(url, showToast, addWatermark, processor, callback).
            // false preserves the response bytes, GIFs, storage permission handling
            // and callbacks, instead of decoding/re-encoding with a watermark.
            Method context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            Method image = HostMediaBindings.image(loader);
            installFlag(module, image, IMAGE, 2, -1, context);
            imageReady = true;
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "image watermark control unavailable", error);
        }
        try {
            // h2.b(url, context, duration, addWatermark). false uses Soul's
            // direct stream-to-album branch and skips video watermark encoding.
            Method video = HostMediaBindings.video(loader);
            installFlag(module, video, VIDEO, 3, 1, null);
            videoReady = true;
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "video watermark control unavailable", error);
        }
    }

    private static void installFlag(XposedModule module, Method method, String key,
            int flagIndex, int contextIndex, Method contextGetter) {
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
            throw new IllegalStateException("Unexpected download signature: " + method);
        }
        module.hook(method).setId("new-soul/" + key)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    boolean enabled = false;
                    try {
                        Context context = (Context) (contextIndex >= 0
                                ? chain.getArg(contextIndex) : contextGetter.invoke(null));
                        enabled = context != null && PluginPreferences.get(context).getBoolean(key, true);
                    } catch (Throwable error) {
                        module.log(Log.WARN, "SoulHook102", "download preference unavailable", error);
                    }
                    // Keep proceed outside the catch: never retry a failed download.
                    if (!enabled) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[flagIndex] = false;
                    Log.i("SoulHook102", "watermark bypass: " + key
                            + "; original=" + chain.getArg(flagIndex));
                    return chain.proceed(args);
                });
        module.log(Log.INFO, "SoulHook102", "watermark control installed: " + key);
    }
}
