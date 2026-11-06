package dev.local.newsoul;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Native share-menu disc with a rounded bookmark outline; branding is a separate shared overlay. */
final class ProfileBookmarkIcon extends Drawable {
    private final Drawable host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bookmark = new Path();
    private final int disc, ink;
    private int alpha=255;
    ProfileBookmarkIcon(BitmapDrawable host,int ink) {
        this.host=host; this.ink=ink;
        Bitmap bitmap=host.getBitmap();
        disc=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);
        bookmark.moveTo(18,13); bookmark.lineTo(30,13);
        bookmark.quadTo(32,13,32,15); bookmark.lineTo(32,35);
        bookmark.lineTo(24,30); bookmark.lineTo(16,35);
        bookmark.lineTo(16,15); bookmark.quadTo(16,13,18,13); bookmark.close();
        paint.setStrokeWidth(2); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
    }
    @Override public void draw(Canvas canvas) {
        host.setBounds(getBounds()); host.draw(canvas);
        float size=Math.min(getBounds().width(),getBounds().height());
        if(size<=0)return;
        int save=canvas.save();
        canvas.translate(getBounds().left+(getBounds().width()-size)/2,getBounds().top+(getBounds().height()-size)/2);
        canvas.scale(size/48,size/48);
        // Cover only the old inner artwork; retain the exact host disc, edge and theme treatment.
        paint.setStyle(Paint.Style.FILL); paint.setColor(disc); paint.setAlpha(alpha);
        canvas.drawCircle(24,24,16,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(ink); paint.setAlpha(alpha);
        canvas.drawPath(bookmark,paint); canvas.restoreToCount(save);
    }
    @Override public int getIntrinsicWidth(){return host.getIntrinsicWidth();}
    @Override public int getIntrinsicHeight(){return host.getIntrinsicHeight();}
    @Override public void setAlpha(int value){alpha=value;host.setAlpha(value);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){host.setColorFilter(filter);paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
