package dev.local.newsoul;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Shared 24-unit outline family for internal tabs and setting rows; no host-action brand mark. */
final class SettingsCardIcon extends View {
    private final Glyph glyph;
    SettingsCardIcon(Context context, String title, int color) {
        super(context);
        glyph = new Glyph(title, color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    static android.graphics.drawable.Drawable drawable(Context context, String title, int color) {
        Glyph glyph = new Glyph(title, color);
        int size = SettingsUi.dp(context,22);
        glyph.setBounds(0,0,size,size);
        return glyph;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        glyph.setBounds(0,0,getWidth(),getHeight());
        glyph.draw(canvas);
    }
    private static final class Glyph extends android.graphics.drawable.Drawable {
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String title;
        Glyph(String title, int color) {
            this.title=title;
            pen.setColor(color); pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(1.65f); pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void setTint(int color) { pen.setColor(color); invalidateSelf(); }
        @Override public void setAlpha(int alpha) { pen.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { pen.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        private void line(Canvas c, float... points) {
            Path p = new Path(); p.moveTo(points[0], points[1]);
            for(int i=2;i<points.length;i+=2) p.lineTo(points[i],points[i+1]);
            c.drawPath(p,pen);
        }
        @Override public void draw(Canvas c) {
            c.save(); c.translate(getBounds().left,getBounds().top); c.scale(getBounds().width()/24f,getBounds().height()/24f);
            if(title.equals("主页")) {
                line(c,3,11,12,3,21,11); line(c,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);
            } else if(title.equals("广场")) {
                c.drawCircle(12,12,7,pen); c.save(); c.rotate(-30,12,12); c.drawOval(1,8,23,16,pen); c.restore();
            } else if(title.equals("其他")) {
                line(c,12,2,21,7,21,17,12,22,3,17,3,7,12,2); c.drawCircle(12,12,3.5f,pen);
            } else if(title.contains("骰子") || title.contains("猜拳")) {
                c.drawRoundRect(4,4,20,20,4,4,pen); c.drawCircle(8,8,0.8f,pen); c.drawCircle(12,12,0.8f,pen); c.drawCircle(16,16,0.8f,pen);
            } else if(title.contains("时间") || title.contains("运行")) {
                c.drawCircle(12,12,8,pen); line(c,12,7,12,12,16,14);
            } else if(title.contains("撤回")) {
                line(c,8,5,3,10,8,15); line(c,3,10,14,10);
                c.drawArc(9,10,21,20,-90,180,false,pen); line(c,15,20,10,20);
            } else if(title.contains("已读")) {
                line(c,3,12,7,16,15,7); line(c,12,15,15,17,22,8);
            } else if(title.contains("限制") || title.contains("广告")) {
                c.drawCircle(12,12,8,pen); line(c,6.5f,6.5f,17.5f,17.5f);
            } else if(title.contains("筛选")) {
                line(c,3,5,21,5,14,13,14,20,10,18,10,13,3,5);
            } else if(title.contains("点赞") || title.contains("支持")) {
                Path p=new Path(); p.moveTo(12,20); p.cubicTo(0,12,2,3,8,5);
                p.quadTo(11,5,12,8); p.cubicTo(17,-1,28,8,12,20); c.drawPath(p,pen);
            } else if(title.contains("下载") || title.contains("迁移")) {
                line(c,12,3,12,15); line(c,7,10,12,15,17,10); line(c,4,16,4,21,20,21,20,16);
            } else if(title.contains("主页") || title.contains("资料")) {
                c.drawCircle(12,8,4,pen); c.drawArc(4,13,20,27,180,180,false,pen);
            } else if(title.contains("定位")) {
                Path p=new Path(); p.moveTo(12,22); p.cubicTo(-4,7,8,-3,18,6);
                p.cubicTo(23,12,17,17,12,22); c.drawPath(p,pen); c.drawCircle(12,9,2.5f,pen);
            } else if(title.contains("隐私")) {
                c.drawRoundRect(5,10,19,21,3,3,pen); c.drawArc(8,3,16,16,180,180,false,pen);
                line(c,12,14,12,17);
            } else if(title.contains("版本") || title.contains("检测")) {
                c.drawCircle(12,12,8,pen); line(c,8,12,11,15,16,9);
            } else if(title.contains("聊天") || title.contains("回复") || title.contains("交流")) {
                Path p=new Path(); p.moveTo(6,18); p.cubicTo(-4,9,7,-2,18,5);
                p.cubicTo(27,13,17,22,10,19); p.lineTo(4,21); p.close(); c.drawPath(p,pen);
            } else {
                c.drawRoundRect(4,4,20,20,4,4,pen); line(c,8,9,16,9); line(c,8,14,14,14);
            }
            c.restore();
        }
    }
}
