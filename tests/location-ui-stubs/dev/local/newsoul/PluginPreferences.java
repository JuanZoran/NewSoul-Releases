package dev.local.newsoul;
import android.content.Context;
import android.content.SharedPreferences;
final class PluginPreferences {
    static SharedPreferences get(Context context) { return SettingsUi.preferences(context); }
}
