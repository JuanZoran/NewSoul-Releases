package dev.local.newsoul;

import android.content.Context;
import java.lang.reflect.Method;

/** Verified native download entry points; storage and callbacks stay in the host. */
final class HostMediaBindings {
    private HostMediaBindings() {}

    static Method image(ClassLoader loader) throws Exception {
        Class<?> downloader = loader.loadClass("cn.soulapp.android.square.utils.ImageDownloader");
        try {
            Class<?> mark = loader.loadClass("cn.soulapp.android.square.utils.ImageMarkProcessor");
            Class<?> callback = loader.loadClass("cn.soulapp.android.square.utils.b");
            try { return HostBindings.method(downloader, "f", void.class, true,
                    String.class, boolean.class, boolean.class, mark, callback); }
            catch (NoSuchMethodException changed) { return HostBindings.method(downloader, "p", void.class, true,
                    String.class, boolean.class, boolean.class, mark, callback); }
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            // 4.88.0: e(url, showToast, addWatermark) forwards its last argument
            // to ImageDownloader$a.b; false takes the original BufferedSource path.
            return HostBindings.method(downloader, "e", void.class, true,
                    String.class, boolean.class, boolean.class);
        }
    }

    static Method video(ClassLoader loader) throws Exception {
        Class<?> downloader;
        try { downloader = loader.loadClass("cn.soulapp.android.square.utils.l2");
            return HostBindings.method(downloader, "b", void.class, true,
                    String.class, Context.class, long.class, boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException missing639) { }
        try { downloader = loader.loadClass("cn.soulapp.android.square.utils.h2"); }
        catch (ClassNotFoundException missing) {
            // 4.88.0 VideoDownloader, same arguments and native storage branch.
            downloader = loader.loadClass("cn.soulapp.android.square.utils.x0");
        }
        return HostBindings.method(downloader, "b", void.class, true,
                String.class, Context.class, long.class, boolean.class);
    }
}
