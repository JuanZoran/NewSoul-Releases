package dev.local.newsoul;
import android.content.Context;
import android.content.SharedPreferences;
final class SettingsUi {
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("test", Context.MODE_PRIVATE);
    }
}
