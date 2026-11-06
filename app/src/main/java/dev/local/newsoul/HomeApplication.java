package dev.local.newsoul;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** No launcher is exposed until the user explicitly chooses to show it. */
public final class HomeApplication extends Application {
    private static final String PREFS = "home_launcher_choice";
    private static final String VISIBLE = "user_visible";
    @Override public void onCreate() {
        super.onCreate();
        initializeLauncher(this);
    }
    private static SharedPreferences preferences(Context context) {
        return TransactionalPreferences.wrap(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }
    static void initializeLauncher(Context context) {
        boolean visible = preferences(context).getBoolean(VISIBLE, false);
        try { apply(context, visible); }
        catch (RuntimeException error) {
            android.util.Log.w("NewSoulHome", "Launcher initialization unavailable", error);
        }
    }
    static void setVisible(Context context, boolean visible) {
        SharedPreferences preferences = preferences(context);
        boolean previous = preferences.getBoolean(VISIBLE, false);
        // Persist the explicit choice before the package change, which may recreate the app.
        if (!preferences.edit().putBoolean(VISIBLE, visible).commit())
            throw new IllegalStateException("Cannot save launcher choice");
        try { apply(context, visible); }
        catch (RuntimeException error) {
            preferences.edit().putBoolean(VISIBLE, previous).commit();
            throw error;
        }
    }
    private static void apply(Context context, boolean visible) {
        PackageManager pm = context.getPackageManager();
        ComponentName alias = new ComponentName(context.getPackageName(), "dev.local.newsoul.Launcher");
        int state = visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(alias) != state)
            pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
    }
}
