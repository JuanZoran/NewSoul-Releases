package dev.local.newsoul;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One below-bubble label owns both time and recall text, independent of hook order. */
final class ChatMessageFootnote {
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private static final class State {
        WeakReference<View> bubble;
        WeakReference<TextView> label;
        int bottom;
        String time, recall;
        boolean outgoing;
    }
    static boolean time(View root, String value, ColorStateList colors, boolean outgoing) {
        return update(root, value, colors, outgoing, true);
    }
    static boolean recall(View root, String value, ColorStateList colors) {
        return update(root, value, colors, false, false);
    }
    private static boolean update(View root, String value, ColorStateList colors, boolean outgoing, boolean time) {
        if (root == null) return false;
        State state = STATES.get(root);
        if (state == null && value == null) return true;
        try {
            if (state == null) {
                int id = root.getResources().getIdentifier("container", "id", "cn.soulapp.android");
                View bubble = root.findViewById(id);
                if (bubble == null || !(bubble.getParent() instanceof ViewGroup)) return false;
                ViewGroup.LayoutParams old = bubble.getLayoutParams();
                Class<?> type = old.getClass();
                int bottom = type.getField("bottomToBottom").getInt(old);
                type.getField("startToStart"); type.getField("endToEnd");
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) type
                        .getConstructor(int.class, int.class).newInstance(0, -2);
                type.getField("topToBottom").setInt(params, bubble.getId());
                type.getField("bottomToBottom").setInt(params, 0);
                params.topMargin = dp(root, 2); params.bottomMargin = dp(root, 8);
                TextView label = new TextView(root.getContext());
                label.setId(View.generateViewId()); label.setTextSize(10);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                label.setClickable(false); label.setLongClickable(false); label.setFocusable(false);
                state = new State(); state.bottom = bottom;
                state.bubble = new WeakReference<>(bubble); state.label = new WeakReference<>(label);
                ((ViewGroup) bubble.getParent()).addView(label, params);
                STATES.put(root, state);
            }
            if (time) { state.time = value; state.outgoing = outgoing; }
            else state.recall = value;
            TextView label = state.label.get(); View bubble = state.bubble.get();
            if (label == null || bubble == null) { STATES.remove(root); return false; }
            if (state.time == null && state.recall == null) {
                if (label.getParent() instanceof ViewGroup) ((ViewGroup) label.getParent()).removeView(label);
                ViewGroup.LayoutParams old = bubble.getLayoutParams();
                old.getClass().getField("bottomToBottom").setInt(old, state.bottom);
                bubble.setLayoutParams(old); STATES.remove(root); return true;
            }
            if (colors != null) label.setTextColor(colors);
            label.setText(state.time == null ? state.recall : state.recall == null ? state.time : state.time + "\n" + state.recall);
            label.setGravity(state.outgoing ? android.view.Gravity.END : android.view.Gravity.START);
            ViewGroup.LayoutParams params = label.getLayoutParams(); Class<?> type = params.getClass();
            type.getField("startToStart").setInt(params, state.outgoing ? 0 : bubble.getId());
            type.getField("endToEnd").setInt(params, state.outgoing ? bubble.getId() : 0);
            label.setLayoutParams(params);
            ViewGroup.LayoutParams old = bubble.getLayoutParams();
            old.getClass().getField("bottomToBottom").setInt(old, -1);
            bubble.setLayoutParams(old);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("SoulHook102", "chat footnote unavailable", error);
            return false;
        }
    }
    private static int dp(View root, int value) { return Math.round(value * root.getResources().getDisplayMetrics().density); }
}
