package dev.local.newsoul;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Adds a settings shortcut to the four native navigation targets, preserving normal clicks. */
final class BottomSettingsEntry {
    static boolean ready;
    private static final String MAIN = "cn.soulapp.android.component.startup.main.MainActivity";
    private static final String[] TABS = {"main_tab_planet", "main_tab_square", "main_tab_msg", "main_tab_me"};
    private static final WeakHashMap<View, Boolean> BOUND = new WeakHashMap<>();
    private static Method nightMode;

    static void install(XposedModule module, ClassLoader loader) {
        try {
            try {
                nightMode = loader.loadClass("cn.soulapp.android.lib.common.utils.settings.SoulSettings")
                        .getMethod("isNightMode");
            } catch (ReflectiveOperationException ignored) { /* System theme is the fallback. */ }
            Method create = loader.loadClass(MAIN).getDeclaredMethod("onCreate", Bundle.class);
            module.hook(create).setId("new-soul/bottom-settings-entry")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getThisObject();
                        try {
                            View root = activity.getWindow().getDecorView();
                            // The host can inflate/rebuild its navigation after onCreate or a theme change.
                            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> bind(activity));
                            root.post(() -> bind(activity));
                        } catch (Throwable error) { Log.w("SoulHook102", "bottom settings observation failed", error); }
                        return result;
                    });
            module.deoptimize(create);
            ready = true;
            Log.i("SoulHook102", "bottom settings entry installed");
        } catch (Throwable error) { Log.e("SoulHook102", "bottom settings entry unavailable", error); }
    }

    private static void bind(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            int barId = activity.getResources().getIdentifier("main_bottom_tab_bar", "id", activity.getPackageName());
            View bar = barId == 0 ? null : activity.findViewById(barId);
            if (bar == null) return;
            for (String name : TABS) {
                int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
                View tab = id == 0 ? null : bar.findViewById(id);
                // Do not displace host or other module long-press actions.
                if (tab == null || BOUND.containsKey(tab) || tab.hasOnLongClickListeners()) continue;
                tab.setOnLongClickListener(view -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !view.isShown()) return false;
                    SettingsUi.showSettings(activity, dark(activity));
                    return true;
                });
                BOUND.put(tab, true);
            }
        } catch (Throwable error) { Log.w("SoulHook102", "bottom settings binding failed", error); }
    }

    static boolean dark(Activity activity) {
        try { if (nightMode != null) return Boolean.TRUE.equals(nightMode.invoke(null)); }
        catch (ReflectiveOperationException ignored) { }
        return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
