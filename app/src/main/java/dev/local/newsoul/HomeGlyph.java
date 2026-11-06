package dev.local.newsoul;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Shared 24-unit canvas for home brand icons and status marks. */
final class HomeGlyph extends Drawable {
    static final int CHECK = 0, HELP = 1, REFRESH = 2, GITHUB = 3, TELEGRAM = 4;
    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    HomeGlyph(int kind, int color) { this.kind = kind; paint.setColor(color); baseColor = color; }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();
        if (kind == CHECK || kind == HELP) {
            canvas.drawCircle(12, 12, 10, paint);
            if (kind == CHECK) {
                path.moveTo(6.8f,12); path.lineTo(10.3f,15.4f); path.lineTo(17.2f,8.5f);
                canvas.drawPath(path,paint);
            } else {
                path.moveTo(9.3f,8); path.cubicTo(9.3f,4.8f,15.5f,4.8f,15.1f,8.7f);
                path.cubicTo(15,10.5f,12,10.8f,12,13); canvas.drawPath(path,paint);
                canvas.drawPoint(12,16.5f,paint);
            }
        } else if (kind == REFRESH) {
            // One clockwise arc with a connected, open arrowhead; no overlapping inner ring.
            path.addArc(4, 4, 20, 20, -20, 290);
            path.lineTo(16, 4);
            path.moveTo(13, 1);
            path.lineTo(16, 4);
            path.lineTo(13, 7);
            canvas.drawPath(path, paint);
        } else if (kind == TELEGRAM) {
            paint.setStyle(Paint.Style.FILL);
            path.moveTo(1,10);path.lineTo(22,2);path.lineTo(18.3f,22);path.lineTo(11.3f,16.2f);
            path.lineTo(7.8f,20);path.lineTo(8.3f,13);path.lineTo(18.2f,6.3f);
            path.lineTo(5.8f,11.8f);path.close();canvas.drawPath(path,paint);
        } else {
            // Octocat silhouette on the same canvas as the paper plane.
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(12,12,11,paint);
            paint.setColor(baseColor == 0xfff1f4fa ? 0xff171b26 : 0xfff8fafc);
            path.moveTo(7,8);path.lineTo(6.5f,4.5f);path.lineTo(10,6);
            path.cubicTo(11,5.7f,13,5.7f,14,6);path.lineTo(17.5f,4.5f);path.lineTo(17,8);
            path.cubicTo(20,12,18,15,14,15.5f);path.cubicTo(15,17,14.8f,19,14.8f,23);
            path.lineTo(9.2f,23);path.lineTo(9.2f,18);path.cubicTo(5,19,3.5f,15,4,14.5f);
            path.cubicTo(6,14,6.3f,17.2f,9.2f,16.5f);path.lineTo(10,15.5f);
            path.cubicTo(6,15,4,12,7,8);path.close();canvas.drawPath(path,paint);
            paint.setColor(baseColor);
        }
        canvas.restoreToCount(save);
    }
    private int baseColor;
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
