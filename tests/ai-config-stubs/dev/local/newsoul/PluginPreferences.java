package dev.local.newsoul;
final class PluginPreferences {
    static android.content.SharedPreferences get(android.content.Context context) {
        return AiCustomConfigTest.preferences;
    }
}
