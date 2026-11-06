package dev.local.newsoul;

import android.content.SharedPreferences;
import android.util.Log;
import android.widget.TextView;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Each bound message gets a small timestamp; original presentation restores on disable/rebind. */
final class ChatTimeControls {
    static final String ENABLED = "chat_time_format_enabled_v1";
    static final String EVERY = "chat_time_every_message_v1";
    static final String FORMAT = "chat_time_format_v1";
    static boolean ready;
    private static final WeakHashMap<TextView, Stamp> STAMPS = new WeakHashMap<>();
    private static final class Stamp {
        final long millis;
        final CharSequence original;
        final int visibility;
        final float size;
        final WeakReference<View> root;
        final boolean outgoing;
        Stamp(long millis, TextView view, View root, boolean outgoing) {
            this.millis = millis; this.original = view.getText(); this.visibility = view.getVisibility();
            this.size = view.getTextSize(); this.root = new WeakReference<>(root); this.outgoing = outgoing;
        }
    }
    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> im = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Method time = HostBindings.messageMethod(im, "getServerTime");
            Method localTime = HostBindings.messageMethod(im, "getLocalTime");
            Class<?> dual = loader.loadClass("cn.soulapp.android.component.chat.widget.AbsChatDualItem");
            boolean legacy = HostBindings.legacyChat(loader);
            Class<?> holder = loader.loadClass(dual.getName() + (legacy ? "$e" : "$ViewHolder"));
            Field itemView = holder.getField("itemView");
            Class<?> sendHolder = loader.loadClass(dual.getName() + (legacy ? "$d" : "$SendViewHolder"));
            Field label = holder.getDeclaredField("a"); label.setAccessible(true);
            if (label.getType() != TextView.class) throw new IllegalStateException("Unknown timestamp field");
            Method bind = unique(dual, holder, im, int.class, List.class);
            // This class is shared by native single-column message rows on Soul 6.37/6.38.
            Class<?> single = loader.loadClass("cn.soulapp.android.component.chat.widget." + (legacy ? "j" : "m"));
            Method singleBind = unique(single, int.class, im, TextView.class);
            for (Method method : new Method[]{bind, singleBind}) {
                boolean dualRow = method == bind;
                module.hook(method).setId("new-soul/chat-time-" + (dualRow ? "dual" : "single"))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            TextView view = dualRow ? (TextView) label.get(chain.getArg(0)) : (TextView) chain.getArg(2);
                            // Restore before binding: a recycled hidden row may skip the host setText call.
                            if (view != null) {
                                Stamp old = STAMPS.remove(view);
                                if (old != null) restore(view, old);
                            }
                            Object result = chain.proceed();
                            try {
                                if (view != null) {
                                    long millis = ((Number) time.invoke(chain.getArg(1))).longValue();
                                    if (millis <= 0) millis = ((Number) localTime.invoke(chain.getArg(1))).longValue();
                                    View root = dualRow ? (View) itemView.get(chain.getArg(0)) : null;
                                    Stamp stamp = new Stamp(millis, view, root, dualRow && sendHolder.isInstance(chain.getArg(0)));
                                    STAMPS.put(view, stamp);
                                    apply(view, stamp);
                                }
                            } catch (Throwable error) { Log.w("SoulHook102", "chat timestamp left native", error); }
                            return result;
                        });
                module.deoptimize(method);
            }
            ready = true;
            module.log(Log.INFO, "SoulHook102", "chat time format installed; dual/single native rows");
        } catch (Throwable error) { module.log(Log.ERROR, "SoulHook102", "chat time format unavailable", error); }
    }
    private static Method unique(Class<?> type, Class<?>... parameters) throws Exception {
        Method found = null;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getReturnType() == void.class && !method.isBridge()
                    && java.util.Arrays.equals(method.getParameterTypes(), parameters)) {
                if (found != null) throw new IllegalStateException("Ambiguous timestamp binder");
                found = method;
            }
        }
        if (found == null) throw new NoSuchMethodException(type.getName());
        found.setAccessible(true);
        return found;
    }
    private static void restore(TextView view, Stamp stamp) {
        ChatMessageFootnote.time(stamp.root.get(), null, null, stamp.outgoing);
        view.setText(stamp.original); view.setVisibility(stamp.visibility);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, stamp.size);
    }
    private static void apply(TextView view, Stamp stamp) {
        SharedPreferences prefs = PluginPreferences.get(view.getContext());
        boolean custom = prefs.getBoolean(ENABLED, false), every = prefs.getBoolean(EVERY, false);
        if (!ready || (!custom && !every) || stamp.millis <= 0) { restore(view, stamp); return; }
        try {
            String text = ChatTimeRules.format(custom ? prefs.getString(FORMAT, ChatTimeRules.DEFAULT) : "HH:mm", stamp.millis);
            if (!every) {
                restore(view, stamp);
                view.setText(text);
                return;
            }
            if (ChatMessageFootnote.time(stamp.root.get(), text, view.getTextColors(), stamp.outgoing)) {
                view.setVisibility(View.GONE);
            } else {
                // Single-column notices have no bubble: retain their anchor, but show every timestamp.
                view.setText(text); view.setTextSize(10); view.setVisibility(View.VISIBLE);
            }
        } catch (IllegalArgumentException invalid) { restore(view, stamp); }
    }
    static void refreshUi() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            for (java.util.Map.Entry<TextView, Stamp> entry : STAMPS.entrySet()) {
                TextView view = entry.getKey();
                if (view != null) apply(view, entry.getValue());
            }
        });
    }
}
