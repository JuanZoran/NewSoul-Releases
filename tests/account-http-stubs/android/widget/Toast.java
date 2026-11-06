package android.widget;
import android.content.Context;
import java.util.concurrent.CopyOnWriteArrayList;
public final class Toast {
    public static final int LENGTH_LONG = 1;
    public static final CopyOnWriteArrayList<String> shown = new CopyOnWriteArrayList<>();
    private final String text;
    private Toast(String text) { this.text = text; }
    public static Toast makeText(Context context, CharSequence text, int duration) { return new Toast(text.toString()); }
    public void show() { shown.add(text); }
}
