package dev.local.newsoul;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Both previews and live rows use the same native nickname and bubble anchors. */
final class RecallMessageStyle {
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private static final class State {
        WeakReference<TextView> name, flag;
        int nameVisibility, flagVisibility;
        CharSequence nameText, flagText;
    }

    static void clear(View root) {
        ChatMessageFootnote.recall(root, null, null);
        State state = STATES.remove(root);
        if (state == null) return;
        try {
            if (state.name != null) {
                TextView name = state.name.get(), flag = state.flag.get();
                if (name != null) { name.setText(state.nameText); name.setVisibility(state.nameVisibility); }
                if (flag != null) { flag.setText(state.flagText); flag.setVisibility(state.flagVisibility); }
            }
        } catch (Throwable error) { Log.w("SoulHook102", "recall style reset failed", error); }
    }

    static void apply(View root, String style, String author) throws Exception {
        clear(root);
        TextView name = (TextView) find(root, "tv_nikename");
        TextView flag = (TextView) find(root, "tv_nikename_flag");
        View bubble = find(root, "container");
        if (bubble == null || name == null || flag == null) return;
        State state = new State();
        STATES.put(root, state);
        if ("nickname".equals(style)) {
            state.name = new WeakReference<>(name); state.flag = new WeakReference<>(flag);
            state.nameText = name.getText(); state.flagText = flag.getText();
            state.nameVisibility = name.getVisibility(); state.flagVisibility = flag.getVisibility();
            if (name.getVisibility() != View.VISIBLE || name.getText().length() == 0) name.setText(author);
            name.setVisibility(View.VISIBLE);
            flag.setText(state.flagVisibility == View.VISIBLE && state.flagText.length() > 0
                    ? state.flagText + " · 已撤回" : "已撤回");
            flag.setVisibility(View.VISIBLE);
        } else {
            ChatMessageFootnote.recall(root, "对方已撤回 · 已保留", name.getTextColors());
        }
    }

    static View preview(Context context, String style) throws Exception {
        View root = sample(context, "这是一条已撤回的消息");
        apply(root, style, "示例好友");
        return root;
    }

    static View sample(Context context, String text) throws Exception {
        int layout = resource(context, "item_chat_message_receive", "layout");
        View root = LayoutInflater.from(context).inflate(layout, new FrameLayout(context), false);
        FrameLayout bubble = (FrameLayout) find(root, "container");
        TextView name = (TextView) find(root, "tv_nikename");
        TextView flag = (TextView) find(root, "tv_nikename_flag");
        name.setText("示例好友"); name.setVisibility(View.VISIBLE); flag.setVisibility(View.GONE);
        View avatar = find(root, "chat_avatar");
        if (avatar != null) {
            android.graphics.drawable.Drawable image = context.getDrawable(resource(context, "c_user_ic_default_avatar", "drawable"));
            if (avatar instanceof ViewGroup) {
                android.widget.ImageView portrait = new android.widget.ImageView(context);
                portrait.setImageDrawable(image);
                portrait.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                portrait.setBackground(context.getDrawable(resource(context, "c_ic_avatar_default_placeholder", "drawable")));
                portrait.setClipToOutline(true);
                FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(dp(context,40),dp(context,40),android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL);
                bounds.topMargin=dp(context,4);
                ((ViewGroup)avatar).addView(portrait,bounds);
            } else if (avatar instanceof android.widget.ImageView) {
                ((android.widget.ImageView)avatar).setImageDrawable(image);
            }
            avatar.setVisibility(View.VISIBLE);
        }
        TextView message = new TextView(context);
        message.setText(text);
        message.setTextSize(14);
        message.setTextColor(context.getResources().getColor(resource(context, "color_s_02", "color"), context.getTheme()));
        message.setBackgroundResource(resource(context, "icon_bubble_white", "drawable"));
        message.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        bubble.addView(message, new FrameLayout.LayoutParams(-2, -2));
        // A layout-only sample: no ImMessage, adapter, database, or send callback.
        disableInteraction(root);
        return root;
    }

    private static void disableInteraction(View view) {
        view.setClickable(false); view.setLongClickable(false); view.setFocusable(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) disableInteraction(group.getChildAt(i));
        }
    }
    private static int resource(Context context, String name, String type) {
        int id = context.getResources().getIdentifier(name, type, "cn.soulapp.android");
        if (id == 0) throw new IllegalStateException("Missing Soul resource: " + name);
        return id;
    }
    private static View find(View root, String name) { return root.findViewById(resource(root.getContext(), name, "id")); }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
