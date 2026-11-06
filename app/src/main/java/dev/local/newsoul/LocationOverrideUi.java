package dev.local.newsoul;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import dev.local.newsoul.SettingsUi.MenuSwitch;
import android.widget.TextView;

final class LocationOverrideUi {
    static void addEntry(SettingsUi.MenuPanel panel, LinearLayout group) {
        TextView entry = SettingsUi.label(panel.context, "修改定位", 16, panel.accentText);
        SettingsUi.buttonSemantics(entry);
        entry.setOnClickListener(v -> show(panel.activity, panel.dark));
        SettingsUi.styleInfoAction(panel, entry);
        group.addView(entry, new LinearLayout.LayoutParams(-1, -2));
    }
    static void show(Activity activity, boolean dark) {
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(activity, dark);
        panel.help = "经测试，IP属地会自动更新";
        SharedPreferences prefs = PluginPreferences.get(activity);
        MenuSwitch enabled = SettingsUi.addSwitch(panel.body, null, LocationOverridePoint.ENABLED, "修改定位", "",
                panel.foreground, panel.secondary);
        TextView selected = SettingsUi.label(panel.context, "", 15, panel.foreground);
        panel.body.addView(selected);
        TextView choose = SettingsUi.label(panel.context, "选择位置", 16, panel.accentText);
        SettingsUi.buttonSemantics(choose);
        SettingsUi.styleInfoAction(panel, choose);
        panel.body.addView(choose, new LinearLayout.LayoutParams(-1, -2));
        choose.setOnClickListener(v -> LocationOverridePicker.open(activity));
        boolean[] refreshing = {false};
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (refreshing[0]) return;
            PreferenceChanges.putBoolean(prefs, LocationOverridePoint.ENABLED, checked);
        });
        Runnable refresh = () -> {
            LocationOverridePoint point = LocationOverrideControls.saved(prefs);
            boolean available = LocationOverrideControls.ready && LocationOverridePicker.ready;
            refreshing[0] = true;
            enabled.setChecked(prefs.getBoolean(LocationOverridePoint.ENABLED, false));
            refreshing[0] = false;
            // Always allow an existing override to be disabled, even after a host update.
            enabled.setEnabled(enabled.isChecked() || available && point != null);
            choose.setEnabled(LocationOverridePicker.ready);
            selected.setText(!available ? "当前 Soul 版本的定位功能未就绪" : point == null ? "请先选择位置"
                    : point.title.equals(point.city) ? point.city : point.city + " · " + point.title);
        };
        SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
            if (LocationOverridePoint.POINT.equals(key) || LocationOverridePoint.ENABLED.equals(key))
                panel.body.post(() -> { panel.refreshPreferenceViews(); refresh.run(); });
        };
        panel.body.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { prefs.registerOnSharedPreferenceChangeListener(listener); refresh.run(); }
            public void onViewDetachedFromWindow(View view) { prefs.unregisterOnSharedPreferenceChangeListener(listener); }
        });
        panel.addPreferenceRefresher(refresh);
        refresh.run();
        panel.show("修改定位", "返回");
    }
}
