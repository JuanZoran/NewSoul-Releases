package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import static dev.local.newsoul.SettingsUi.*;

/** Shared switch, count and word-list editor for independently scoped keyword filters. */
final class KeywordFilterUi {
    static void add(MenuPanel panel, LinearLayout body, String title, String enabledKey, String wordsKey,
            java.util.function.BooleanSupplier ready, String help) {
        SharedPreferences prefs = PluginPreferences.get(panel.activity);
        MenuSwitch toggle = addSwitch(body, prefs, enabledKey, title, "", panel.foreground, panel.secondary);
        bindAvailability(panel, toggle, ready);
        final Runnable[] refresh = {() -> {}};
        TextView edit = attachSwitchAction(toggle, panel, "编辑屏蔽词", "编辑" + title,
                () -> showKeywordEditor(panel.activity, panel.dark, title, wordsKey, help, refresh[0]));
        refresh[0] = () -> {
            int count = 0;
            for (String line : prefs.getString(wordsKey, "").split("\\R")) if (!line.trim().isEmpty()) count++;
            edit.setText("编辑屏蔽词（" + count + "）");
        };
        refresh[0].run();
        panel.addPreferenceRefresher(refresh[0]);
    }

    private static void showKeywordEditor(Activity activity, boolean dark, String title, String key, String help, Runnable onSaved) {
        MenuPanel panel = new MenuPanel(activity, dark);
        SharedPreferences prefs = PluginPreferences.get(activity);
        panel.help = "输入可查找已有词，也可粘贴多行\n" + help;
        java.util.ArrayList<String> words = new java.util.ArrayList<>();
        for (String line : prefs.getString(key, "").split("\\R")) {
            if (!line.trim().isEmpty()) words.add(line.trim());
        }
        java.util.List<String> initialWords = new java.util.ArrayList<>(words);
        android.widget.EditText input = new android.widget.EditText(panel.context);
        input.setTextSize(16); input.setTextColor(panel.foreground); input.setHintTextColor(panel.secondary);
        panel.appearance.apply(input, UiAppearance.Role.INPUT);
        input.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(1); input.setMaxLines(4);
        input.setMinHeight(dp(activity, 48));
        input.setHint("查找或添加屏蔽词");
        input.setContentDescription("查找或添加屏蔽词");
        TextView add = keywordAction(panel, "添加", "添加到屏蔽词列表");
        add.setMinHeight(dp(activity, 48));
        add.setMinWidth(dp(activity, 64));
        add.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 8));
        add.setSelected(true);
        applyCapsuleStyle(add, panel);
        buttonSemantics(add);
        LinearLayout inputRow = new LinearLayout(panel.context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = View.MeasureSpec.getSize(widthSpec);
                int actionWidth = Math.max(dp(activity, 64), (int) Math.ceil(add.getPaint().measureText(add.getText().toString()))
                        + add.getPaddingLeft() + add.getPaddingRight());
                boolean stacked = View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.UNSPECIFIED
                        && available < actionWidth + dp(activity, 128);
                setOrientation(stacked ? VERTICAL : HORIZONTAL);
                LinearLayout.LayoutParams field = (LinearLayout.LayoutParams) input.getLayoutParams();
                field.width = stacked ? -1 : 0;
                field.weight = stacked ? 0 : 1;
                LinearLayout.LayoutParams action = (LinearLayout.LayoutParams) add.getLayoutParams();
                action.setMarginStart(stacked ? 0 : dp(activity, 8));
                action.leftMargin = stacked ? 0 : dp(activity, 8);
                action.topMargin = stacked ? dp(activity, 8) : 0;
                action.gravity = stacked ? Gravity.END : Gravity.CENTER_VERTICAL;
                field.height = -2; action.height = -2;
                super.onMeasure(widthSpec, heightSpec);
                if (!stacked) {
                    int equalHeight = Math.max(input.getMeasuredHeight(), add.getMeasuredHeight());
                    field.height = equalHeight; action.height = equalHeight;
                    super.onMeasure(widthSpec, heightSpec);
                }
            }
        };
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        inputRow.addView(add, new LinearLayout.LayoutParams(-2, -2));
        panel.body.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout heading = new LinearLayout(panel.context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, dp(activity, 18), 0, dp(activity, 8));
        heading.addView(label(panel.context, "屏蔽词", 16, panel.foreground), new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = label(panel.context, "", 13, panel.secondary);
        heading.addView(count);
        panel.body.addView(heading);
        String[] highlighted = {null};
        KeywordFlowLayout list = new KeywordFlowLayout(panel.context);
        panel.body.addView(list, new LinearLayout.LayoutParams(-1, -2));
        TextView undo = keywordAction(panel, "", "撤销上一次删除屏蔽词");
        undo.setMinHeight(dp(activity, 48));
        undo.setVisibility(View.GONE);
        panel.body.addView(undo, new LinearLayout.LayoutParams(-1, -2));
        String[] removed = {null};
        int[] removedIndex = {0};
        Runnable render = new Runnable() {
            @Override public void run() {
                String pending = input.getText().toString().trim();
                boolean batch = pending.matches("(?s).*\\R.*");
                String query = batch ? "" : pending.toLowerCase(java.util.Locale.ROOT);
                boolean exists = false;
                for (String word : words) exists |= word.equalsIgnoreCase(pending);
                add.setText(exists ? "已添加" : "添加");
                add.setEnabled(!pending.isEmpty() && !exists);
                add.setSelected(add.isEnabled());
                list.removeAllViews();
                int matched = 0;
                for (int i = 0; i < words.size(); i++) {
                    final int index = i;
                    String word = words.get(i);
                    if (!word.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                    matched++;
                    LinearLayout row = new LinearLayout(panel.context);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(dp(activity, 48));
                    boolean shortWord = word.codePointCount(0, word.length()) <= 8;
                    TextView text = label(panel.context, word, 16, panel.foreground);
                    text.setPadding(dp(activity, 10), dp(activity, 8), 0, dp(activity, 8));
                    row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView delete = keywordAction(panel, shortWord ? "×" : "删除", "删除屏蔽词：" + word);
                    row.addView(delete, new LinearLayout.LayoutParams(dp(activity, shortWord ? 48 : 64), -1));
                    delete.setMinHeight(dp(activity, 48));
                    if (shortWord) {
                        panel.appearance.apply(row, UiAppearance.Role.CHOICE, 16);
                        row.setSelected(word.equals(highlighted[0]));
                    }
                    int naturalWidth = (int) Math.ceil(text.getPaint().measureText(word)) + dp(activity, 64);
                    list.addView(row, new android.view.ViewGroup.LayoutParams(shortWord ? naturalWidth : -1, -2));
                    delete.setOnClickListener(v -> {
                        removed[0] = words.remove(index); removedIndex[0] = index;
                        undo.setText("已删除“" + removed[0] + "” · 撤销");
                        undo.setVisibility(View.VISIBLE); run();
                    });
                }
                count.setText(query.isEmpty() ? words.size() + "/100"
                        : "匹配 " + matched + " 个 · 共 " + words.size() + " 个");
                if (matched == 0) {
                    TextView empty = label(panel.context, query.isEmpty() ? "暂无屏蔽词" : "列表中没有这个词，可点击添加", 14, panel.secondary);
                    empty.setPadding(0, dp(activity, 16), 0, dp(activity, 16));
                    list.addView(empty, new android.view.ViewGroup.LayoutParams(-1, -2));
                }
            }
        };
        undo.setOnClickListener(v -> {
            if (removed[0] == null) return;
            boolean exists = false;
            for (String word : words) exists |= word.equalsIgnoreCase(removed[0]);
            if (!exists) {
                try { new FeedKeywordRules(String.join("\n", words) + "\n" + removed[0]); }
                catch (IllegalArgumentException error) { input.setError(error.getMessage()); return; }
                words.add(Math.min(removedIndex[0], words.size()), removed[0]);
            }
            removed[0] = null; undo.setVisibility(View.GONE); render.run();
        });
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { render.run(); }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });
        // Validate the whole draft before changing it, so invalid bulk input is never partly added.
        java.util.function.BooleanSupplier append = () -> {
            String pending = input.getText().toString().trim();
            if (pending.isEmpty()) return true;
            try {
                new FeedKeywordRules(String.join("\n", words) + "\n" + pending);
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                for (String word : words) seen.add(word.toLowerCase(java.util.Locale.ROOT));
                for (String line : pending.split("\\R")) {
                    String word = line.trim();
                    if (!word.isEmpty() && seen.add(word.toLowerCase(java.util.Locale.ROOT))) { words.add(word); highlighted[0] = word; }
                }
                input.setText(""); input.setError(null); render.run();
                input.postDelayed(() -> { highlighted[0] = null; render.run(); }, 1800);
                return true;
            } catch (IllegalArgumentException error) { input.setError(error.getMessage()); return false; }
        };
        add.setOnClickListener(v -> append.getAsBoolean());
        render.run();
        panel.protectDraft(() -> !words.equals(initialWords));
        panel.show(title, "保存");
        panel.dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        panel.body.setFocusableInTouchMode(true); panel.body.requestFocus();
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = String.join("\n", words);
            try {
                FeedKeywordRules rules = new FeedKeywordRules(raw);
                PreferenceChanges.putString(prefs, key, raw);
                onSaved.run();
                Toast.makeText(activity, "已保存 " + rules.words.size() + " 个屏蔽词", Toast.LENGTH_SHORT).show();
                panel.dialog.dismiss();
            } catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
        });
    }

    private static TextView keywordAction(MenuPanel panel, String text, String description) {
        TextView action = label(panel.context, text, 15, panel.accentText);
        action.setGravity(Gravity.CENTER);
        panel.appearance.apply(action, UiAppearance.Role.ICON);
        action.setContentDescription(description);
        buttonSemantics(action);
        return action;
    }

    private KeywordFilterUi() {}
}
