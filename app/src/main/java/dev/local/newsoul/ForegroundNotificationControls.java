package dev.local.newsoul;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

final class ForegroundNotificationControls {
    static final String ENABLED = "foreground_clear_notifications_v1";
    static boolean ready;

    static void install(XposedModule module) {
        ForegroundNotificationState state = new ForegroundNotificationState();
        try {
            module.hook(Instrumentation.class.getDeclaredMethod("callActivityOnStop", Activity.class))
                    .setId("new-soul/notifications-stop")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getArg(0);
                        state.stop(activity, activity.isChangingConfigurations());
                        return result;
                    });
            module.hook(Instrumentation.class.getDeclaredMethod("callActivityOnStart", Activity.class))
                    .setId("new-soul/notifications-start")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getArg(0);
                        if (state.start(activity) && PluginPreferences.get(activity).getBoolean(ENABLED, false)) {
                            try {
                                NotificationManager manager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
                                if (manager != null) manager.cancelAll();
                                DebugDiagnostics.event("notifications.foreground", "cleared");
                            } catch (RuntimeException error) { DebugDiagnostics.failure("notifications.foreground", error); }
                        }
                        return result;
                    });
            ready = true;
            DebugDiagnostics.registration("notifications.foreground", null);
        } catch (Throwable error) { DebugDiagnostics.registration("notifications.foreground", error); }
    }
}
