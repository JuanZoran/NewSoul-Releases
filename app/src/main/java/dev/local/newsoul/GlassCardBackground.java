package dev.local.newsoul;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A glass background only: child text and controls are drawn normally above this Drawable. */
final class GlassCardBackground extends Drawable {
    private final GlassLensRenderer renderer = new GlassLensRenderer();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF surface = new RectF();
    private final Matrix gradientMatrix = new Matrix();
    private final Shader transmission;
    private final boolean dark, squareBottom;
    private final int panelColor;
    private final float radius, density;
    private final GlassLensRenderer.Source source = this::drawSurface;
    private int alpha = 255;
    private ColorFilter colorFilter;

    GlassCardBackground(boolean dark, int panelColor, float radius, float density) {
        this(dark, panelColor, radius, density, false);
    }

    GlassCardBackground(boolean dark, int panelColor, float radius, float density, boolean squareBottom) {
        this(dark, panelColor, radius, density, squareBottom, false);
    }

    GlassCardBackground(boolean dark, int panelColor, float radius, float density,
            boolean squareBottom, boolean clearSurface) {
        this.squareBottom = squareBottom;
        this.dark = dark;
        this.panelColor = clearSurface ? Color.TRANSPARENT : panelColor;
        this.radius = radius;
        this.density = density;
        // A thin neutral surface inherits the menu palette, without introducing a solid grey card.
        transmission = new LinearGradient(0, 0, 0, 1,
                clearSurface ? (dark ? 0x0fffffff : 0x38ffffff) : (dark ? 0x12ffffff : 0x80ffffff),
                clearSurface ? (dark ? 0x048e99cc : 0x12ffffff) : (dark ? 0x038e99cc : 0x20ffffff), Shader.TileMode.CLAMP);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float inset = density * .5f;
        surface.set(inset, inset, bounds.width() - inset, bounds.height() - inset + (squareBottom ? radius : 0));
        gradientMatrix.setScale(1, Math.max(1, bounds.height()));
        transmission.setLocalMatrix(gradientMatrix);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || alpha == 0) return;
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.clipRect(0, 0, bounds.width(), bounds.height());
        if (alpha != 255 || colorFilter != null) {
            paint.setAlpha(alpha);
            paint.setColorFilter(colorFilter);
            canvas.saveLayer(0, 0, bounds.width(), bounds.height(), paint);
            paint.setAlpha(255);
            paint.setColorFilter(null);
        }
        // No press animation or child capture: a settings group is not itself an action button.
        renderer.draw(canvas, bounds.width(), bounds.height() + (squareBottom ? (int)Math.ceil(radius) : 0), surface, radius,
                0, density, dark, source);
        canvas.restoreToCount(save);
    }

    private void drawSurface(Canvas canvas) {
        canvas.drawColor(panelColor);
        paint.setShader(transmission);
        canvas.drawRect(0, 0, getBounds().width(), getBounds().height() + (squareBottom ? radius : 0), paint);
        paint.setShader(null);
    }

    @Override public void getOutline(Outline outline) {
        if (!squareBottom) { outline.setRoundRect(getBounds(), radius); return; }
        Path path = new Path();
        path.addRoundRect(new RectF(getBounds()), new float[]{radius,radius,radius,radius,0,0,0,0}, Path.Direction.CW);
        outline.setConvexPath(path);
    }
    @Override public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { colorFilter = filter; invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    void release() { renderer.release(); }
}
