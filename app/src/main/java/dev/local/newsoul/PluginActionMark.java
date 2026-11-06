package dev.local.newsoul;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.WeakHashMap;

/** Decoration only: never replaces host artwork, tint, listeners or hit targets. */
final class PluginActionMark {
    private static final WeakHashMap<View, Mark> marks = new WeakHashMap<>();

    static void set(View view, boolean enabled) {
        if (view == null) return;
        Mark old = marks.get(view);
        if (!enabled) {
            if (old != null) { view.getOverlay().remove(old); old.attached = false; }
            return;
        }
        if (old != null) {
            if (!old.attached) { view.getOverlay().add(old); old.attached = true; }
            return;
        }
        Mark mark = new Mark(view.getResources().getDisplayMetrics().density);
        marks.put(view, mark);
        view.getOverlay().add(mark);
        mark.setBounds(0, 0, view.getWidth(), view.getHeight());
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            Mark current = marks.get(v);
            if (current != null) current.setBounds(0, 0, r - l, b - t);
        });
        view.invalidate();
    }

    static ImageView icon(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView found = icon(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Mark extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density;
        int alpha = 255;
        boolean attached = true;
        Mark(float density) { this.density = density; }
        @Override public void draw(Canvas canvas) {
            float size = Math.min(10 * density, Math.min(getBounds().width(), getBounds().height()) * .26f);
            if (size <= 0) return;
            float x = getBounds().right - size * .65f - density;
            float y = getBounds().top + size * .65f + density;
            float r = size / 2;
            Path star = new Path();
            star.moveTo(x, y-r);
            star.quadTo(x+r*.17f,y-r*.17f,x+r,y);
            star.quadTo(x+r*.17f,y+r*.17f,x,y+r);
            star.quadTo(x-r*.17f,y+r*.17f,x-r,y);
            star.quadTo(x-r*.17f,y-r*.17f,x,y-r);
            star.close();
            paint.setAlpha(alpha); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.5f*density); paint.setColor(0xffe0fff8);
            canvas.drawPath(star, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xff38dfc4);
            canvas.drawPath(star, paint);
        }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter value) { paint.setColorFilter(value); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
