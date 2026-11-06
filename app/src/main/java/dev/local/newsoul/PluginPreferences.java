package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;
import java.util.Set;

/** Shared configuration; existing public choices win over legacy internal values. */
final class PluginPreferences {
    static final String LEGACY_INTERNAL = "dev.local.soulhookdemo.settings.v1";
    private static final String MERGED = "newsoul_unified_preferences_v1";
    private PluginPreferences() {}

    static synchronized SharedPreferences get(Context context) {
        SharedPreferences target = TransactionalPreferences.wrap(context.getSharedPreferences(
                VariantFeatures.PREFERENCES, Context.MODE_PRIVATE));
        if (!target.getBoolean(MERGED, false)) {
            SharedPreferences legacy = context.getSharedPreferences(LEGACY_INTERNAL, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = target.edit();
            for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                if (!target.contains(entry.getKey()) && !MERGED.equals(entry.getKey()))
                    copy(editor, entry.getKey(), entry.getValue());
            }
            // Failed commits restore the accepted snapshot and leave migration retryable.
            editor.putBoolean(MERGED, true).commit();
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private static void copy(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }
}
