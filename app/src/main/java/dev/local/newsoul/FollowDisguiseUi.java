package dev.local.newsoul;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import dev.local.newsoul.SettingsUi.MenuSwitch;
import android.widget.TextView;
import android.widget.Toast;

final class FollowDisguiseUi {
    private static final java.util.WeakHashMap<Activity, java.lang.ref.WeakReference<AlertDialog>> OPEN = new java.util.WeakHashMap<>();
    static void addSettings(SettingsUi.MenuPanel parent) {
        LinearLayout body = SettingsUi.section(parent, "个人主页");
        TextView entry = SettingsUi.label(parent.context, "信息伪装", 16, parent.foreground);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setMinHeight(SettingsUi.dp(parent.context, 48));
        entry.setPadding(SettingsUi.dp(parent.context, 12), SettingsUi.dp(parent.context, 10),
                SettingsUi.dp(parent.context, 12), SettingsUi.dp(parent.context, 10));
        parent.appearance.apply(entry, UiAppearance.Role.ICON);
        SettingsUi.buttonSemantics(entry);
        entry.setOnClickListener(v -> show(parent.activity, parent.dark));
        body.addView(entry, new LinearLayout.LayoutParams(-1, -2));
    }
    static void show(Activity activity, boolean dark) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        java.lang.ref.WeakReference<AlertDialog> reference = OPEN.get(activity);
        AlertDialog existing = reference == null ? null : reference.get();
        if (existing != null && existing.isShowing()) return;
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(activity, dark);
        SharedPreferences prefs = PluginPreferences.get(activity);
        String explanation = "仅改变本地显示, 不会改变他人看到的数据\n\n";
        android.text.SpannableString help = new android.text.SpannableString(explanation
                + "长按自己主页的对应区域，也可以快速打开此页面");
        help.setSpan(new android.text.style.ForegroundColorSpan(panel.accentText), explanation.length(),
                help.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        help.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), explanation.length(),
                help.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        panel.help = help;
        if (!FollowDisguiseControls.ready()) panel.body.addView(SettingsUi.label(panel.context,
                "当前版本的主页数量显示入口未就绪", 14, panel.secondary));
        Editor first = new Editor(panel, prefs, "关注", FollowDisguiseControls.FOLLOW);
        Editor second = new Editor(panel, prefs, "被关注", FollowDisguiseControls.FOLLOWED);
        Editor visitor = new Editor(panel, prefs, "访客", FollowDisguiseControls.VISITOR);
        Editor days = new Editor(panel, prefs, "天数", FollowDisguiseControls.DAYS);
        if (!FollowDisguiseControls.daysReady()) panel.body.addView(SettingsUi.label(panel.context,
                "当前版本的天数显示入口未就绪", 14, panel.secondary));
        panel.protectDraft(() -> first.dirty() || second.dirty() || visitor.dirty() || days.dirty());
        panel.show("信息伪装", "保存");
        OPEN.put(activity, new java.lang.ref.WeakReference<>(panel.dialog));
        panel.dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        panel.body.setFocusableInTouchMode(true); panel.body.requestFocus();
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean validFirst = first.validateForSave(), validSecond = second.validateForSave();
            boolean validVisitor = visitor.validateForSave();
            boolean validDays = days.validateForSave();
            if (!validFirst || !validSecond || !validVisitor || !validDays) return;
            java.util.Map<String, Object> updates = new java.util.LinkedHashMap<>();
            first.save(updates); second.save(updates); visitor.save(updates); days.save(updates);
            PreferenceChanges.applyUpdates(prefs, updates);
            panel.dialog.dismiss();
            Toast.makeText(activity, "已保存", Toast.LENGTH_SHORT).show();
        });
    }
    private static final class Editor {
        private static final String[] MODES = {"absolute", "delta"};
        final String key;
        final MenuSwitch enabled;
        final EditText input;
        final String name;
        final String[] values = new String[2];
        final String initial;
        int selected;
        Editor(SettingsUi.MenuPanel panel, SharedPreferences prefs, String name, String key) {
            this.key = key;
            this.name = name;
            LinearLayout body = SettingsUi.section(panel, null);
            enabled = SettingsUi.addSwitch(body, null, key + "enabled", name, "", panel.foreground, panel.secondary);
            enabled.setChecked(prefs.getBoolean(key + "enabled", false));
            LinearLayout controls = new LinearLayout(panel.context);
            controls.setOrientation(LinearLayout.VERTICAL);
            controls.setPadding(0, SettingsUi.dp(panel.context, 8), 0, 0);
            body.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            SegmentedChoice choices = new SegmentedChoice(panel, null, new String[]{"直接修改", "原有增减"}, 13);
            choices.setContentDescription(name + "修改方式");
            controls.addView(choices, new LinearLayout.LayoutParams(-1, -2));
            selected = "delta".equals(prefs.getString(key + "mode", "absolute")) ? 1 : 0;
            for (int i = 0; i < 2; i++) values[i] = String.valueOf(prefs.getInt(key + MODES[i], 0));
            choices.selectValue(MODES[selected], MODES, false);
            input = new EditText(panel.context);
            input.setTextColor(panel.foreground); input.setHintTextColor(panel.secondary); input.setTextSize(16);
            input.setMinHeight(SettingsUi.dp(panel.context, 48)); input.setSingleLine(true);
            input.setPadding(SettingsUi.dp(panel.context, 14), SettingsUi.dp(panel.context, 10),
                    SettingsUi.dp(panel.context, 14), SettingsUi.dp(panel.context, 10));
            panel.appearance.applyOutlinedInput(input);
            LinearLayout numberRow = new LinearLayout(panel.context);
            numberRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView minus = stepButton(panel, "−", "减少" + name + "数量", -1);
            TextView plus = stepButton(panel, "+", "增加" + name + "数量", 1);
            numberRow.addView(minus, new LinearLayout.LayoutParams(SettingsUi.dp(panel.context, 48), -2));
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -2, 1);
            inputParams.leftMargin = inputParams.rightMargin = SettingsUi.dp(panel.context, 8);
            numberRow.addView(input, inputParams);
            numberRow.addView(plus, new LinearLayout.LayoutParams(SettingsUi.dp(panel.context, 48), -2));
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(-1, -2);
            numberParams.topMargin = SettingsUi.dp(panel.context, 12);
            controls.addView(numberRow, numberParams);
            updateInput();
            SettingsUi.bindSwitchContent(enabled, controls);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                if (!checked) input.setError(null);
                if (!checked && input.hasFocus()) {
                    android.view.inputmethod.InputMethodManager keyboard =
                            (android.view.inputmethod.InputMethodManager) panel.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    input.clearFocus();
                }
            });
            choices.onSelection(index -> {
                if (index == selected) return;
                // Validate before changing mode so invalid drafts cannot be hidden and saved accidentally.
                if (!validate()) return;
                selected = index;
                choices.selectValue(MODES[selected], MODES, true);
                updateInput();
            });
            initial = snapshot();
        }
        void updateInput() {
            input.setContentDescription(name + (selected == 0 ? "显示数量" : "增减数量"));
            input.setInputType(InputType.TYPE_CLASS_NUMBER | (selected == 1 ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
            input.setGravity(Gravity.CENTER);
            input.setText(values[selected]); input.setSelection(input.length()); input.setError(null);
        }
        TextView stepButton(SettingsUi.MenuPanel panel, String text, String description, int direction) {
            TextView button = SettingsUi.label(panel.context, text, 22, panel.foreground);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(SettingsUi.dp(panel.context, 48));
            button.setContentDescription(description);
            panel.appearance.apply(button, UiAppearance.Role.INLINE_ACTION);
            SettingsUi.buttonSemantics(button);
            button.setOnClickListener(v -> {
                if (!validate()) return;
                long current = Integer.parseInt(values[selected]);
                long minimum = selected == 0 ? 0L : Integer.MIN_VALUE;
                values[selected] = String.valueOf(Math.max(minimum, Math.min(Integer.MAX_VALUE, current + direction)));
                input.setText(values[selected]); input.setSelection(input.length());
            });
            return button;
        }
        boolean validateForSave() {
            if (enabled.isChecked()) return validate();
            // Preserve valid edits; an incomplete hidden number must not block turning the feature off.
            try {
                values[selected] = String.valueOf(FollowDisguiseRules.parse(input.getText().toString(), selected == 1));
            } catch (IllegalArgumentException ignored) { }
            return true;
        }
        boolean validate() {
            try {
                values[selected] = String.valueOf(FollowDisguiseRules.parse(input.getText().toString(), selected == 1));
                input.setError(null); return true;
            } catch (IllegalArgumentException error) { input.setError(error.getMessage()); return false; }
        }
        String snapshot() {
            return enabled.isChecked() + ":" + selected + ":" + (selected == 0 ? input.getText() : values[0])
                    + ":" + (selected == 1 ? input.getText() : values[1]);
        }
        boolean dirty() { return !initial.equals(snapshot()); }
        void save(java.util.Map<String, Object> updates) {
            updates.put(key + "enabled", enabled.isChecked());
            updates.put(key + "mode", MODES[selected]);
            for (int i = 0; i < 2; i++) updates.put(key + MODES[i], Integer.parseInt(values[i]));
        }
    }
}
