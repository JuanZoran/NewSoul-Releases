package dev.local.newsoul;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Raised soft keys with C-style recessed flat selected faces. Shared by the actual control and offscreen rendering probe. */
final class SoftFilterBackground extends Drawable {
    private final boolean dark;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean selected, error;
    SoftFilterBackground(boolean dark, float density) { this.dark = dark; this.density = density; }
    void state(boolean selected, boolean error) {
        this.selected = selected; this.error = error; invalidateSelf();
    }
    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        float pad = 5 * density;
        RectF r = new RectF(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad);
        float radius = Math.min((selected ? 12 : 20) * density, r.height() / 2);
        Path shape = new Path(); shape.addRoundRect(r, radius, radius, Path.Direction.CW);
        paint.reset(); paint.setAntiAlias(true);
        int top = dark ? (selected ? 0xff10151c : 0xff424859) : (selected ? 0xff536263 : 0xffffffff);
        int bottom = dark ? (selected ? 0xff141a22 : 0xff2b3040) : (selected ? 0xff596869 : 0xffe5e8ed);
        if (error) { top = dark ? 0xff291b23 : 0xff634c50; bottom = dark ? 0xff2c1e26 : 0xff695256; }
        if (!selected) {
            paint.setColor(bottom);
            paint.setShadowLayer(3 * density, 1 * density, 2 * density, dark ? 0x90000000 : 0x400e1c30);
            canvas.drawPath(shape, paint); paint.clearShadowLayer();
        }
        paint.setShader(new LinearGradient(r.left, r.top, selected ? r.left : r.right, r.bottom,
                top, bottom, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint); paint.setShader(null);
        if (selected) {
            int save = canvas.save(); canvas.clipPath(shape);
            // A shallow, crisp inner wall surrounds the flat lowered face.
            // Its shadow falls inside only; the selected key has no exterior elevation.
            Path outside = new Path(); outside.setFillType(Path.FillType.EVEN_ODD);
            outside.addRect(r.left - 40*density, r.top - 40*density,
                    r.right + 40*density, r.bottom + 40*density, Path.Direction.CW);
            outside.addRoundRect(r, radius, radius, Path.Direction.CW);
            paint.setColor(dark ? 0xff06090e : 0xff253031);
            paint.setShadowLayer(2*density, 1.2f*density, 3*density, dark ? 0xf0000000 : 0xd0182426);
            canvas.drawPath(outside, paint); paint.clearShadowLayer(); canvas.restoreToCount(save);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density * .7f);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                error ? 0x99ef777d : selected ? (dark ? 0xe006090e : 0xc0253031) : (dark ? 0x608c97ad : 0xeaffffff),
                error ? 0x66ef777d : selected ? (dark ? 0x90636d7d : 0xeeffffff) : 0x10222a3a,
                Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint); paint.setShader(null); paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
