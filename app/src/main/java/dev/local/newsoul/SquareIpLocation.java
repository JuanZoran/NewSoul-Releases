package dev.local.newsoul;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Adds a passive label to Soul's existing horizontal author-label strip. */
final class SquareIpLocation {
    static final String ENABLED = "square_show_ip_location_v1";
    static boolean ready;
    private static Field location;
    private static Class<?> postType;
    // Values must not retain the weak key through label -> parent -> itemView.
    private static final WeakHashMap<View, WeakReference<Binding>> rows = new WeakHashMap<>();

    static void install(ClassLoader loader) {
        try {
            postType = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
            if (HostBindings.legacyChat(loader)) {
                postType.getField("authorIdEcpt");
                location = null;
                ready = HostAccountHttp.ready;
            } else {
                location = postType.getField("showIpLocationInfo");
                ready = location.getType() == String.class;
            }
            DebugDiagnostics.registration("feed.ip_location", ready ? null : new NoSuchFieldException("showIpLocationInfo"));
        } catch (ReflectiveOperationException error) {
            ready = false;
            DebugDiagnostics.registration("feed.ip_location", error);
        }
    }

    static void clear(View root) {
        WeakReference<Binding> ref = rows.remove(root);
        Binding binding = ref == null ? null : ref.get();
        if (binding != null) binding.remove();
    }

    static void update(View root, Object post, boolean square) {
        boolean enabled = PluginPreferences.get(root.getContext()).getBoolean(ENABLED, true);
        if (!square || !ready || postType == null || !postType.isInstance(post)
                || !enabled) {
            clear(root);
            return;
        }
        try {
            String text = location == null ? "" : SquareIpLocationRules.label((String) location.get(post));
            if (text.isEmpty()) text = SquareIpLocationRules.label(SquareIpLocationLookup.resolve(root.getContext(), post));
            if (text.isEmpty()) { clear(root); return; }
            WeakReference<Binding> ref = rows.get(root);
            Binding binding = ref == null ? null : ref.get();
            if (binding != null && binding.label.getParent() == binding.names) {
                binding.label.setText(text);
                binding.layout();
                return;
            }
            clear(root);
            View nameStrip = FeedHeaderViews.names(root);
            View timeStrip = FeedHeaderViews.time(root);
            if (!(nameStrip instanceof ViewGroup)) return;
            TextView name = FeedHeaderViews.nameAnchor(root, nameStrip), time = FeedHeaderViews.firstText(timeStrip);
            View anchor = FeedHeaderViews.layoutAnchor((ViewGroup) nameStrip, name);
            if (name == null || anchor == null) return;
            // Use the host strip's own LayoutParams class (LinearLayoutCompat on 6.38).
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) anchor.getLayoutParams().getClass()
                    .getConstructor(int.class, int.class).newInstance(-2, -2);
            if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
            ((ViewGroup.MarginLayoutParams) params).setMarginStart(SettingsUi.dp(root.getContext(), 8));
            try { params.getClass().getField("gravity").setInt(params, Gravity.CENTER_VERTICAL); }
            catch (NoSuchFieldException ignored) { return; }
            binding = new Binding((ViewGroup) nameStrip, name, time, text);
            binding.names.addView(binding.label, params);
            rows.put(root, new WeakReference<>(binding));
            binding.watch();
            binding.layout();
        } catch (ReflectiveOperationException | RuntimeException error) {
            clear(root);
            DebugDiagnostics.failure("feed.ip_location.bind", error);
        }
    }

    private static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "cn.soulapp.android");
        return id == 0 ? null : root.findViewById(id);
    }

    private static final class Binding {
        final ViewGroup names;
        final TextView name, time, label;
        ViewTreeObserver observer;
        boolean removed;
        final ViewTreeObserver.OnGlobalLayoutListener listener = this::layout;
        final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                if (removed) return;
                observe();
                layout();
            }
            @Override public void onViewDetachedFromWindow(View view) { stopObserving(); }
        };
        Binding(ViewGroup names, TextView name, TextView time, String text) {
            this.names = names; this.name = name; this.time = time;
            label = new TextView(names.getContext());
            label.setText(text);
            label.setTag(FeedHeaderViews.METADATA_TAG);
            label.setSingleLine(true);
            label.setIncludeFontPadding(name.getIncludeFontPadding());
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setClickable(false);
            label.setLongClickable(false);
            label.setFocusable(false);
            label.setVisibility(View.GONE);
        }
        void watch() {
            names.addOnAttachStateChangeListener(attachment);
            if (names.isAttachedToWindow()) observe();
        }
        void observe() {
            if (removed) return;
            stopObserving();
            ViewTreeObserver current = names.getViewTreeObserver();
            if (current.isAlive()) {
                current.addOnGlobalLayoutListener(listener);
                observer = current;
            }
        }
        void stopObserving() {
            ViewTreeObserver previous = observer;
            observer = null;
            if (previous != null && previous.isAlive()) previous.removeOnGlobalLayoutListener(listener);
            // An observer can change when a detached view joins another window.
            ViewTreeObserver current = names.getViewTreeObserver();
            if (current != previous && current.isAlive()) current.removeOnGlobalLayoutListener(listener);
        }
        void layout() {
            if (removed || label.getParent() != names) return;
            float size = name == time ? time.getTextSize() : name.getTextSize() * .75f;
            int defaultColor;
            // Party-host badges replace the time text with an image. Styling must
            // not require that optional text, or the author loses the IP label.
            if (time != null && time.getParent() != null && time.getVisibility() == View.VISIBLE) {
                defaultColor = time.getCurrentTextColor();
                label.setTypeface(time.getTypeface());
            } else {
                int color = names.getResources().getIdentifier("color_s_06", "color", "cn.soulapp.android");
                defaultColor = color != 0 ? names.getResources().getColor(color, names.getContext().getTheme())
                        : name.getCurrentTextColor();
                label.setTypeface(android.graphics.Typeface.DEFAULT);
            }
            FeedLabelStyle.apply(label, FeedLabelStyle.IP, size, defaultColor);
            int available = FeedHeaderViews.availableWidth(names);
            int used = 0;
            for (int i = 0; i < names.getChildCount(); i++) {
                View child = names.getChildAt(i);
                if (child == label || child.getVisibility() == View.GONE) continue;
                used += child.getMeasuredWidth();
                if (child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                    used += p.leftMargin + p.rightMargin;
                }
            }
            int needed = (int) Math.ceil(label.getPaint().measureText(label.getText().toString()))
                    + label.getPaddingLeft() + label.getPaddingRight()
                    + SettingsUi.dp(names.getContext(), 8);
            // Preserve host names, badges and follow controls; never squeeze or clip the region.
            int visibility = name.getVisibility() == View.VISIBLE
                    && SquareIpLocationRules.fits(available, used, needed) ? View.VISIBLE : View.GONE;
            if (label.getVisibility() != visibility) label.setVisibility(visibility);
        }
        void remove() {
            if (removed) return;
            removed = true;
            names.removeOnAttachStateChangeListener(attachment);
            stopObserving();
            if (label.getParent() instanceof ViewGroup) ((ViewGroup) label.getParent()).removeView(label);
        }
    }
}
