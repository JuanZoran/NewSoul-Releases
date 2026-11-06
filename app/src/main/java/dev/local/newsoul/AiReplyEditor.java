package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One editable card is exactly one outgoing message. Opening or dismissing never sends. */
final class AiReplyEditor {
    private final SettingsUi.MenuPanel panel;
    private final List<Row> rows = new ArrayList<>();
    private final TextView count;

    static AlertDialog show(Activity activity, boolean dark, String reply, boolean preview,
                            Consumer<String> submit) {
        return new AiReplyEditor(activity, dark, reply, preview, submit).panel.dialog;
    }

    private AiReplyEditor(Activity activity, boolean dark, String reply, boolean preview,
                          Consumer<String> submit) {
        panel = new SettingsUi.MenuPanel(activity, dark);
        count = text("", 12, panel.secondary);
        for (String part : AiSendRules.parts(reply)) add(part, false);
        LinearLayout footer = new LinearLayout(panel.context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(16), 0, dp(16), dp(8));
        footer.addView(count);
        LinearLayout actions = new LinearLayout(panel.context);
        TextView add = action("添加一条", () -> add("", true));
        TextView send = action(preview ? "完成预览" : "发送", () -> {
            StringBuilder joined = new StringBuilder();
            for (Row row : rows) {
                String value = row.input.getText().toString().trim();
                if (value.isEmpty()) {
                    row.input.setError("填写这条消息，或删除此卡片");
                    row.input.requestFocus();
                    return;
                }
                if (joined.length() > 0) joined.append('\n');
                joined.append(value);
            }
            if (joined.length() > AiReplyRules.MAX_TEXT) {
                count.setText("总字数超过 " + AiReplyRules.MAX_TEXT + "，请精简后再发送");
                return;
            }
            panel.dialog.dismiss();
            submit.accept(joined.toString());
        });
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(send, new LinearLayout.LayoutParams(0, dp(48), 1));
        footer.addView(actions);
        panel.showAiBottom(preview ? "编辑回复 · 预览" : "编辑回复", footer);
        // A tap on a card opens the keyboard; showing the editor itself stays compact.
        panel.body.setFocusableInTouchMode(true);
        panel.body.requestFocus();
    }

    private void add(String value, boolean focus) {
        Row row = new Row();
        rows.add(row);
        panel.body.addView(row.card);
        row.input.setText(value);
        update();
        if (focus) {
            row.input.requestFocus();
            row.input.post(() -> {
                row.input.requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                        row.input.getWidth(), row.input.getHeight()));
                android.view.inputmethod.InputMethodManager keyboard =
                        (android.view.inputmethod.InputMethodManager) panel.context.getSystemService(
                                android.content.Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(row.input, 0);
            });
        }
    }

    private void update() {
        int length = Math.max(0, rows.size() - 1);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.title.setText("第 " + (i + 1) + " 条");
            row.input.setContentDescription("第 " + (i + 1) + " 条消息正文");
            row.remove.setContentDescription("删除第 " + (i + 1) + " 条消息");
            row.remove.setVisibility(rows.size() > 1 ? View.VISIBLE : View.INVISIBLE);
            length += row.input.getText().toString().trim().length();
        }
        count.setText("共 " + rows.size() + " 条 · " + length + "/" + AiReplyRules.MAX_TEXT
                + " 字\n每张卡片发送一条消息，按上到下的顺序发送");
    }

    private final class Row {
        final LinearLayout card = new LinearLayout(panel.context);
        final TextView title = text("", 12, panel.secondary);
        final EditText input = new EditText(panel.context);
        final TextView remove = action("删除", () -> {
            if (rows.size() <= 1) return;
            rows.remove(this);
            panel.body.removeView(card);
            update();
        });

        Row() {
            card.setOrientation(LinearLayout.VERTICAL);
            UiAppearance.bind(card, AiReplyComponents.cardBackground(panel));
            card.setPadding(dp(12), 0, dp(12), dp(8));
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, -2);
            bounds.bottomMargin = dp(8);
            card.setLayoutParams(bounds);
            LinearLayout header = new LinearLayout(panel.context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
            title.setGravity(Gravity.CENTER_VERTICAL);
            remove.setTextSize(12);
            remove.setTextColor(panel.secondary);
            header.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(header);
            input.setTextSize(14);
            input.setTextColor(AiReplyComponents.hostColor(panel, "color_s_03", panel.foreground));
            input.setHintTextColor(panel.secondary);
            input.setHint("输入消息");
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMinLines(2);
            input.setMaxLines(5);
            panel.appearance.apply(input, UiAppearance.Role.CONTENT);
            input.setPadding(0, 0, 0, dp(4));
            // Paragraph boundaries are controlled by cards, including pasted multiline text.
            input.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
                String original = source.subSequence(start, end).toString();
                String flat = original.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
                return original.equals(flat) ? null : flat;
            }, new InputFilter.LengthFilter(AiReplyRules.MAX_TEXT)});
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(Editable value) { update(); }
            });
            card.addView(input, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private TextView action(String value, Runnable action) {
        TextView view = text(value, 15, panel.accentText);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        panel.appearance.apply(view, UiAppearance.Role.ACTION);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(panel.context);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * panel.context.getResources().getDisplayMetrics().density); }
}
