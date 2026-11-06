package dev.local.newsoul;
import android.view.View;
final class SquareRegistrationDays {
    static final java.util.Map<View, Object> visible = new java.util.IdentityHashMap<>();
    static void clear(View view) { visible.remove(view); }
    static void update(View view, Object post, boolean square) {
        if (square) visible.put(view, post); else visible.remove(view);
    }
}
