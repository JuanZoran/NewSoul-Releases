package dev.local.newsoul;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import java.util.List;
import java.util.function.BooleanSupplier;
import static dev.local.newsoul.SettingsUi.*;

/** Shared native multi-select rows and ready-only batch operations. */
final class SettingsMultiChoice {
    static final class Choice {
        final String key;
        final BooleanSupplier ready;
        boolean readingPreferences;
        Choice(String key, BooleanSupplier ready) { this.key = key; this.ready = ready; }
    }
    static Choice add(MenuPanel panel, LinearLayout body, SharedPreferences prefs,
            String key, String title, String help, String selectedMeaning, BooleanSupplier ready) {
        LinearLayout row = new LinearLayout(panel.context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(panel.context, 52));
        CheckBox checkbox = new CheckBox(panel.context);
        checkbox.setTextSize(16);
        checkbox.setTextColor(new ColorStateList(
                new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
                new int[] {panel.secondary, panel.foreground}));
        panel.appearance.checkControl(checkbox);
        checkbox.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        checkbox.setMinHeight(dp(panel.context, 52));
        checkbox.setPadding(dp(panel.context, 4), dp(panel.context, 4), dp(panel.context, 8), dp(panel.context, 4));
        row.addView(checkbox, new LinearLayout.LayoutParams(0, -2, 1));
        if (!help.isEmpty()) row.addView(helpButton(panel, title, help));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        Choice choice = new Choice(key, ready);
        AvailabilityGroup availability = panel.availability.get(body);
        if (availability == null) {
            availability = new AvailabilityGroup(panel, body);
            panel.availability.put(body, availability);
        }
        availability.toggles.add(checkbox);
        final AvailabilityGroup group = availability;
        Runnable refresh = () -> {
            choice.readingPreferences = true;
            try {
                boolean available = ready.getAsBoolean();
                checkbox.setEnabled(available);
                checkbox.setChecked(prefs.getBoolean(key, ConfigDefaults.booleanValue(key)));
                checkbox.setText(title + (available ? "" : "（未就绪）"));
                checkbox.setContentDescription(title + (available ? "，" + selectedMeaning : "，未就绪，原因尚未确认"));
            } finally { choice.readingPreferences = false; }
            group.refresh();
        };
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            if (choice.readingPreferences) return;
            if (!ready.getAsBoolean()) { refresh.run(); return; }
            PreferenceChanges.putBoolean(prefs, key, checked);
            android.util.Log.i("SoulHook102", "setting changed: " + key + "=" + checked);
        });
        panel.addPreferenceRefresher(refresh);
        refresh.run();
        return choice;
    }
    static void finish(MenuPanel panel, LinearLayout body) {
        AvailabilityGroup group = panel.availability.get(body);
        if (group == null) return;
        body.removeView(group.status); body.removeView(group.action);
        body.addView(group.status); body.addView(group.action);
    }
    static void showMenu(MenuPanel panel, View anchor, SharedPreferences prefs,
            List<Choice> choices, String enable, String disable) {
        if (!attached(panel, anchor)) return;
        boolean anyReady = false;
        for (Choice choice : choices) anyReady |= choice.ready.getAsBoolean();
        panel.showActionMenu(anchor, new String[]{enable, disable}, anyReady, index -> {
            if (!attached(panel, anchor)) return;
            java.util.Map<String, Object> updates = new java.util.LinkedHashMap<>();
            for (Choice choice : choices) if (choice.ready.getAsBoolean()) updates.put(choice.key, index == 0);
            if (!updates.isEmpty()) PreferenceChanges.applyUpdates(prefs, updates);
            panel.refreshPreferenceViews();
        });
    }
    private static boolean attached(MenuPanel panel, View anchor) {
        return anchor.isAttachedToWindow() && anchor.isShown()
                && !panel.activity.isFinishing() && !panel.activity.isDestroyed();
    }
    private SettingsMultiChoice() {}
}
