package dev.local.newsoul;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.lang.ref.WeakReference;

/** Process-level service listener: never retain an Activity across recreation. */
final class HomeFramework {
    static volatile XposedService service;
    private static WeakReference<Runnable> observer = new WeakReference<>(null);
    private static boolean initialized;
    static synchronized void observe(Runnable callback) {
        observer = new WeakReference<>(callback);
        if (initialized) return;
        initialized = true;
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService value) {
                service = value;
                notifyObserver();
            }
            @Override public void onServiceDied(XposedService value) {
                if (service == value) service = null;
                notifyObserver();
            }
        });
    }
    private static synchronized void notifyObserver() {
        Runnable callback = observer.get();
        if (callback != null) callback.run();
    }
}
