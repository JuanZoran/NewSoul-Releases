package dev.local.newsoul;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** 24dp host-menu geometry: white rounded strokes, with no backing disc. */
final class VoiceLibraryMenuIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Path tray = new Path();
    private final int intrinsicSize;

    VoiceLibraryMenuIcon(float density) {
        intrinsicSize = Math.round(24 * density);
        paint.setColor(0xffFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.65f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        arrow.moveTo(12, 4); arrow.lineTo(12, 14);
        arrow.moveTo(8, 10); arrow.lineTo(12, 14); arrow.lineTo(16, 10);
        tray.moveTo(4, 14); tray.lineTo(4, 18);
        tray.quadTo(4, 20, 6, 20); tray.lineTo(18, 20);
        tray.quadTo(20, 20, 20, 18); tray.lineTo(20, 14);
    }

    @Override public void draw(Canvas canvas) {
        float size = Math.min(getBounds().width(), getBounds().height());
        if (size <= 0) return;
        int save = canvas.save();
        canvas.translate(getBounds().left + (getBounds().width() - size) / 2,
                getBounds().top + (getBounds().height() - size) / 2);
        canvas.scale(size / 24, size / 24);
        canvas.drawPath(arrow, paint);
        canvas.drawPath(tray, paint);
        canvas.restoreToCount(save);
    }
    @Override public int getIntrinsicWidth() { return intrinsicSize; }
    @Override public int getIntrinsicHeight() { return intrinsicSize; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
