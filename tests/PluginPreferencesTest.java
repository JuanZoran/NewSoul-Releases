package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;

/** The production configuration boundary preserves the edition store and accepted snapshot. */
public final class PluginPreferencesTest {
    static final class App extends Context {
        final PreferenceChangesTest.Store disk = new PreferenceChangesTest.Store();
        final PreferenceChangesTest.Store legacy = new PreferenceChangesTest.Store();
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            if (PluginPreferences.LEGACY_INTERNAL.equals(name)) return legacy.prefs;
            if (!VariantFeatures.PREFERENCES.equals(name) || mode != MODE_PRIVATE)
                throw new AssertionError("configuration namespace changed");
            return disk.prefs;
        }
    }

    public static void main(String[] args) {
        App app = new App();
        app.disk.values.put("existing", "kept");
        SharedPreferences first = PluginPreferences.get(app);
        check(first == PluginPreferences.get(app), "views and hooks share one accepted snapshot");
        check("kept".equals(first.getString("existing", "")), "existing settings preserved");
        app.disk.save = false;
        check(!first.edit().putBoolean("enabled", true).commit(), "disk failure reported");
        check(!PluginPreferences.get(app).getBoolean("enabled", false), "failed edit remains inactive");
        App other = new App();
        check(PluginPreferences.get(other) != first, "independent contexts retain separate backing stores");
        App upgrading = new App();
        upgrading.disk.values.put("shared", "public");
        upgrading.legacy.values.put("shared", "internal");
        upgrading.legacy.values.put("ai_reply_credential_v1", "encrypted-key");
        upgrading.legacy.values.put("enabled", true);
        upgrading.legacy.values.put("count", 42);
        upgrading.legacy.values.put("long", 7L);
        upgrading.legacy.values.put("float", 2f);
        upgrading.legacy.values.put("set", java.util.Set.of("a"));
        SharedPreferences merged = PluginPreferences.get(upgrading);
        check("public".equals(merged.getString("shared", "")), "public choices take precedence");
        check("encrypted-key".equals(merged.getString("ai_reply_credential_v1", "")), "legacy AI credentials retained");
        check(merged.getBoolean("enabled", false) && merged.getInt("count", 0) == 42
                && merged.getLong("long", 0) == 7L && merged.getFloat("float", 0) == 2f
                && merged.getStringSet("set", java.util.Set.of()).equals(java.util.Set.of("a")), "all preference types copied");
        merged.edit().remove("ai_reply_credential_v1").commit();
        check(!PluginPreferences.get(upgrading).contains("ai_reply_credential_v1"), "removed values stay removed after migration");
        check(upgrading.legacy.values.containsKey("ai_reply_credential_v1"), "legacy store preserved");
        App retry = new App();
        retry.legacy.values.put("ai_reply_model_v1", "test-model");
        retry.disk.save = false;
        check(!PluginPreferences.get(retry).contains("ai_reply_model_v1"), "failed migration stays inactive");
        retry.disk.save = true;
        check("test-model".equals(PluginPreferences.get(retry).getString("ai_reply_model_v1", "")), "failed migration retried");
        System.out.println("PluginPreferences: configuration and unified migration checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
