package dev.local.newsoul;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import dev.local.newsoul.SettingsUi.MenuSwitch;
import android.widget.TextView;

final class ChatTimeUi {
    private static final String FORMAT_HELP = "yyyy 年、MM 月、dd 日、HH 24 小时、hh 12 小时、mm 分、ss 秒、E 星期、a 上午/下午；英文原样显示时用单引号包住";

    static void addSettings(SettingsUi.MenuPanel parent) {
        SharedPreferences prefs = PluginPreferences.get(parent.activity);
        LinearLayout body = SettingsUi.section(parent, "消息时间");
        MenuSwitch every = SettingsUi.addSwitch(body, prefs, ChatTimeControls.EVERY, "每条消息显示时间",
                ChatTimeControls.ready ? "" : "当前版本未就绪", parent.foreground, parent.secondary);
        SettingsUi.bindAvailability(parent, every, () -> ChatTimeControls.ready);
        MenuSwitch toggle = SettingsUi.addSwitch(body, prefs, ChatTimeControls.ENABLED, "自定义时间格式",
                FORMAT_HELP + (ChatTimeControls.ready ? "" : "\n当前版本未就绪"),
                parent.foreground, parent.secondary, true);
        SettingsUi.bindAvailability(parent, toggle, () -> ChatTimeControls.ready);
        addEditor(parent, prefs, toggle, every);
    }

    private static void addEditor(SettingsUi.MenuPanel panel, SharedPreferences prefs,
            MenuSwitch toggle, MenuSwitch every) {
        LinearLayout editor = new LinearLayout(panel.context);
        editor.setOrientation(LinearLayout.VERTICAL);
        SettingsUi.attachSwitchContentBelow(toggle, editor);
        String[] saved = {prefs.getString(ChatTimeControls.FORMAT, ChatTimeRules.DEFAULT)};
        EditText input = new EditText(panel.context);
        input.setTextSize(16); input.setTextColor(panel.foreground); input.setHintTextColor(panel.secondary);
        panel.appearance.applyInlineInput(input);
        input.setPadding(SettingsUi.dp(panel.context, 14), SettingsUi.dp(panel.context, 12),
                SettingsUi.dp(panel.context, 14), SettingsUi.dp(panel.context, 12));
        input.setMinHeight(SettingsUi.dp(panel.context, 48));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setHint(ChatTimeRules.DEFAULT);
        input.setContentDescription("时间格式");
        input.setText(saved[0]);
        TextView shortFormat = preset(panel, input, "时分秒", "HH:mm:ss");
        TextView fullFormat = preset(panel, input, "完整日期", ChatTimeRules.DEFAULT);
        SettingsUi.addInfoActionPair(panel, editor, shortFormat, fullFormat);
        editor.addView(input, new LinearLayout.LayoutParams(-1, -2));
        TextView status = SettingsUi.label(panel.context, "", 14, panel.appearance.errorColor());
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        editor.addView(status, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = SettingsUi.label(panel.context, "预览", 14, panel.secondary);
        heading.setPadding(0, SettingsUi.dp(panel.context, 16), 0, SettingsUi.dp(panel.context, 8));
        editor.addView(heading);
        LinearLayout sample = new LinearLayout(panel.context);
        sample.setOrientation(LinearLayout.VERTICAL);
        editor.addView(sample, new LinearLayout.LayoutParams(-1, -2));
        // Compare formats at one instant; rebuild native anchors only when the time placement changes.
        final long instant = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>> display =
                new java.util.concurrent.atomic.AtomicReference<>();
        Boolean[] renderedEvery = {null};
        boolean[] invalidBefore = {false};
        android.animation.ObjectAnimator[] shake = {null};
        Runnable nudge = () -> {
            if (!input.isAttachedToWindow() || !input.isShown() || !invalidBefore[0]
                    || !toggle.isChecked() || !toggle.isEnabled()
                    || !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
            if (shake[0] != null) shake[0].cancel();
            float distance = SettingsUi.dp(panel.context, 3);
            shake[0] = android.animation.ObjectAnimator.ofFloat(input, View.TRANSLATION_X,
                    0, -distance, distance, -distance * .6f, distance * .6f, 0);
            shake[0].setDuration(280);
            shake[0].start();
        };
        Runnable stopShake = () -> {
            input.removeCallbacks(nudge);
            if (shake[0] != null) { shake[0].cancel(); shake[0] = null; }
            input.setTranslationX(0);
        };
        Runnable refresh = () -> {
            if (toggle.isChecked() && toggle.isEnabled()
                    && (display.get() == null || renderedEvery[0] != every.isChecked())) {
                sample.removeAllViews();
                display.set(addPreview(panel, sample, every.isChecked()));
                renderedEvery[0] = every.isChecked();
            }
            boolean valid;
            try {
                String text = ChatTimeRules.format(input.getText().toString(), instant);
                if (display.get() != null) display.get().accept(text);
                status.setText(""); status.setVisibility(View.GONE); valid = true;
            } catch (IllegalArgumentException error) {
                if (display.get() != null) display.get().accept("—");
                status.setText(error.getMessage()); status.setTextColor(panel.appearance.errorColor());
                status.setVisibility(View.VISIBLE); valid = false;
            }
            if (invalidBefore[0] != !valid) panel.appearance.inputError(input, !valid);
            if (!valid && !invalidBefore[0]) input.postDelayed(nudge, 220);
            if (valid) stopShake.run();
            invalidBefore[0] = !valid;
        };
        Runnable save = () -> {
            String value = input.getText().toString();
            if (value.equals(saved[0])) return;
            if (!ChatTimeRules.valid(value)) {
                refresh.run();
                status.append("\n未保存，请修改格式");
                return;
            }
            if (!PreferenceChanges.putString(prefs, ChatTimeControls.FORMAT, value)) {
                status.setText("保存失败，请重试");
                status.setTextColor(panel.appearance.errorColor()); status.setVisibility(View.VISIBLE);
                return;
            }
            saved[0] = value;
            refresh.run();
        };
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable value) {
                refresh.run();
                // Presets commit immediately when the user is not editing a draft.
                if (!input.hasFocus()) save.run();
            }
        });
        input.setOnFocusChangeListener((view, focused) -> { if (!focused) save.run(); });
        panel.saveInputOnExit(input);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            input.clearFocus();
            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager) panel.context.getSystemService(
                            android.content.Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            return true;
        });
        input.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) {}
            public void onViewDetachedFromWindow(View view) { save.run(); stopShake.run(); }
        });
        toggle.observe(() -> {
            if (!toggle.isChecked() || !toggle.isEnabled()) {
                stopShake.run();
                if (input.hasFocus()) {
                    android.view.inputmethod.InputMethodManager keyboard =
                            (android.view.inputmethod.InputMethodManager) panel.context.getSystemService(
                                    android.content.Context.INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    input.clearFocus();
                }
            }
            refresh.run();
        });
        every.observe(refresh);
        panel.addPreferenceRefresher(() -> {
            String current = prefs.getString(ChatTimeControls.FORMAT, ChatTimeRules.DEFAULT);
            // Focus/readiness refreshes retain the draft; configuration imports replace its saved baseline.
            if (!current.equals(saved[0])) {
                saved[0] = current;
                input.setText(current);
            }
            refresh.run();
        });
        refresh.run();
    }

    private static java.util.function.Consumer<String> addPreview(SettingsUi.MenuPanel panel,
            LinearLayout container, boolean every) {
        View sample;
        java.util.function.Consumer<String> display;
        try {
            View bubble = RecallMessageStyle.sample(panel.context, "这是一条示例消息");
            int id = panel.context.getResources().getIdentifier("timestamp", "id", "cn.soulapp.android");
            TextView nativeTime = bubble.findViewById(id);
            if (nativeTime == null) throw new IllegalStateException("Native timestamp missing");
            if (every) {
                if (!ChatMessageFootnote.time(bubble, "—", nativeTime.getTextColors(), false))
                    throw new IllegalStateException("Native bubble missing");
                nativeTime.setVisibility(View.GONE);
                display = text -> ChatMessageFootnote.time(bubble, text, nativeTime.getTextColors(), false);
                sample = bubble;
            } else {
                ((ViewGroup) nativeTime.getParent()).removeView(nativeTime);
                nativeTime.setVisibility(View.VISIBLE);
                nativeTime.setGravity(Gravity.CENTER);
                display = nativeTime::setText;
                sample = nativeTime;
            }
        } catch (Exception error) {
            TextView fallback = SettingsUi.label(panel.context, "", 10, panel.secondary);
            display = fallback::setText;
            sample = fallback;
            container.addView(SettingsUi.label(panel.context, "当前版本仅支持文字预览", 13, panel.secondary));
        }
        container.addView(sample, new LinearLayout.LayoutParams(-1, -2));
        return display;
    }
    private static TextView preset(SettingsUi.MenuPanel panel, EditText input, String title, String value) {
        TextView action = SettingsUi.label(panel.context, title, 15, panel.accentText);
        SettingsUi.buttonSemantics(action);
        action.setOnClickListener(v -> { input.setText(value); input.setSelection(value.length()); });
        return action;
    }
}
