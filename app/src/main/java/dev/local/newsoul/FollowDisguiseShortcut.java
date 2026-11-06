package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Add long-press to the existing click container so a short tap keeps Soul's native route. */
final class FollowDisguiseShortcut {
    private static final WeakHashMap<TextView, Binding> BOUND = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Object> PENDING = new WeakHashMap<>();

    private static final class Binding implements View.OnLongClickListener {
        final WeakReference<TextView> number;
        final WeakReference<View> target;
        final boolean wasLongClickable;
        Binding(TextView number, View target) {
            this.number = new WeakReference<>(number);
            this.target = new WeakReference<>(target);
            wasLongClickable = target.isLongClickable();
        }
        @Override public boolean onLongClick(View view) {
            TextView current = number.get();
            if (current == null || BOUND.get(current) != this || !current.isShown()
                    || !current.isAttachedToWindow() || !view.isEnabled()) return false;
            Activity activity = activity(view.getContext());
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
            FollowDisguiseUi.show(activity, BottomSettingsEntry.dark(activity));
            return true; // Consume the long press: do not also open the native follow list.
        }
        void remove() {
            View view = target.get();
            if (view != null) {
                view.setOnLongClickListener(null);
                view.setLongClickable(wasLongClickable);
            }
        }
    }

    static void bind(TextView number, boolean own) {
        Object token = new Object();
        PENDING.put(number, token);
        if (!own) {
            PENDING.remove(number);
            Binding previous = BOUND.remove(number);
            if (previous != null) previous.remove();
            return;
        }
        // RevisionProfileProvider attaches native cell clicks after its count-render helper.
        // Wait for that bind stack to finish; an intervening rebind cancels this request.
        number.post(() -> {
            if (PENDING.get(number) != token) return;
            PENDING.remove(number);
            attach(number);
        });
    }

    private static void attach(TextView number) {
        View target = clickTarget(number);
        Binding previous = BOUND.get(number);
        if (previous != null && previous.target.get() == target && target != null) return;
        if (previous != null) {
            BOUND.remove(number);
            previous.remove();
        }
        // Preserve any host-provided long-press action, as the shared bottom shortcut does.
        if (target == null || target.hasOnLongClickListeners()) return;
        Binding binding = new Binding(number, target);
        BOUND.put(number, binding);
        target.setOnLongClickListener(binding);
    }

    private static View clickTarget(View number) {
        View current = number;
        // The inspected host layouts attach clicks to the numeric cell's parent container.
        // Do not make a previously inert TextView consume short taps meant for that container.
        for (int depth = 0; depth < 12; depth++) {
            if (current.isClickable()) return current;
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return number;
            current = (View) parent;
        }
        // A display-only day label has no native tap action to preserve.
        return number;
    }

    private static Activity activity(Context context) {
        for (int depth = 0; depth < 12 && context != null; depth++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return null;
            context = base;
        }
        return null;
    }
}
