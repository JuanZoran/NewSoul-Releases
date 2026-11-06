package dev.local.newsoul;

import android.text.Editable;
import android.text.SpannedString;
import android.text.TextWatcher;
import android.view.View;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Appends registration age to the host time text without adding or moving views. */
final class SquareRegistrationDays {
    private static final Object BADGE_TAG = FeedHeaderViews.METADATA_TAG;
    private static final WeakHashMap<View, WeakReference<TextView>> badges = new WeakHashMap<>();
    private static final WeakHashMap<View, WeakReference<Binding>> rows = new WeakHashMap<>();

    static void clear(View root) {
        WeakReference<Binding> ref = rows.remove(root);
        Binding binding = ref == null ? null : ref.get();
        if (binding != null) binding.remove();
        clearBadge(root);
    }

    private static void clearBadge(View root) {
        WeakReference<TextView> ref = badges.remove(root);
        TextView label = ref == null ? null : ref.get();
        if (label != null && label.getParent() instanceof ViewGroup)
            ((ViewGroup) label.getParent()).removeView(label);
    }

    static void update(View root, Object post, boolean square) {
        if (!square || !PluginPreferences.get(root.getContext()).getBoolean(RegistrationDaysFilter.DISPLAY, false)) {
            clear(root);
            return;
        }
        try {
            Integer days = RegistrationDaysFilter.resolve(root.getContext(), post);
            if (days == null) { clear(root); return; }
            View strip = FeedHeaderViews.time(root);
            TextView time = FeedHeaderViews.firstText(strip);
            if (time == null) {
                WeakReference<Binding> old = rows.remove(root);
                Binding previous = old == null ? null : old.get();
                if (previous != null) previous.remove();
                if (strip instanceof ViewGroup && strip.getVisibility() == View.VISIBLE)
                    badge(root, (ViewGroup) strip, days);
                else clearBadge(root);
                return;
            }
            clearBadge(root);
            WeakReference<Binding> ref = rows.get(root);
            Binding binding = ref == null ? null : ref.get();
            if (binding == null || binding.time != time) {
                clear(root);
                binding = new Binding(time);
                rows.put(root, new WeakReference<>(binding));
            }
            binding.days = days;
            binding.render();
        } catch (ReflectiveOperationException | RuntimeException error) {
            clear(root);
            DebugDiagnostics.failure("feed.registration.display.bind", error);
        }
    }

    private static void badge(View root, ViewGroup strip, int days) throws ReflectiveOperationException {
        WeakReference<TextView> ref = badges.get(root);
        TextView label = ref == null ? null : ref.get();
        if (label == null || label.getParent() != strip) {
            clearBadge(root);
            View anchor = null;
            for (int i = 0; i < strip.getChildCount(); i++) {
                View child = strip.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && child.getTag() != BADGE_TAG) anchor = child;
            }
            if (anchor == null) return;
            int color = root.getResources().getIdentifier("color_s_06", "color", "cn.soulapp.android");
            if (color == 0) return;
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) anchor.getLayoutParams().getClass()
                    .getConstructor(int.class, int.class).newInstance(-2, -2);
            if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
            ((ViewGroup.MarginLayoutParams) params).setMarginStart(SettingsUi.dp(root.getContext(), 6));
            params.getClass().getField("gravity").setInt(params, Gravity.CENTER_VERTICAL);
            label = new TextView(strip.getContext());
            label.setTag(BADGE_TAG);
            // Soul's PostTextLabel creates its time label at 12dp (unit DIP).
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            label.setTextColor(root.getResources().getColor(color, root.getContext().getTheme()));
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setClickable(false); label.setLongClickable(false); label.setFocusable(false);
            strip.addView(label, params);
            badges.put(root, new WeakReference<>(label));
        }
        int color = root.getResources().getIdentifier("color_s_06", "color", "cn.soulapp.android");
        FeedLabelStyle.apply(label, FeedLabelStyle.DAYS, SettingsUi.dp(root.getContext(), 12),
                root.getResources().getColor(color, root.getContext().getTheme()));
        label.setText("· 注册" + days + "天");
    }

    private static final class Binding implements TextWatcher {
        final TextView time;
        CharSequence original, applied;
        int days;
        boolean writing;
        Binding(TextView time) {
            this.time = time;
            original = new SpannedString(time.getText());
            time.addTextChangedListener(this);
        }
        void render() {
            if (writing) return;
            CharSequence value = FeedLabelStyle.appendDays(time, original, days);
            writing = true;
            try { applied = value; time.setText(value); }
            finally { writing = false; }
        }
        void remove() {
            time.removeTextChangedListener(this);
            // Restore only our last write, never overwrite a newer host binding.
            if (applied != null && applied.toString().contentEquals(time.getText())) time.setText(original);
        }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable text) {
            if (writing) return;
            original = new SpannedString(text);
            render();
        }
    }
}
