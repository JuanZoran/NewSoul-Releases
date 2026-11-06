package dev.local.newsoul;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Inline two-thumb range, with discrete day values and commit-on-release. */
final class RegistrationRangeView extends View {
    interface Listener { void changed(int low, int high, boolean commit); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int accent, track;
    private int low, high, ceiling = 3650, thumb, savedLow, savedHigh;
    private boolean dragging;
    private Listener listener;
    static int valueColor(SettingsUi.MenuPanel panel) { return panel.dark ? 0xff35dfce : 0xff007f73; }
    RegistrationRangeView(SettingsUi.MenuPanel panel) {
        super(panel.context);
        density = getResources().getDisplayMetrics().density;
        accent = valueColor(panel); track = panel.dark ? 0xff626b89 : 0xffbdc6d2;
        setMinimumHeight((int)(48*density)); setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("注册天数范围；左侧为下限，右侧为上限");
    }
    void onChange(Listener value) { listener = value; }
    void setRange(int min, int max) {
        if (dragging) return;
        low = Math.max(1,min); high = max; ceiling = RegistrationRangeRules.ceiling(min, max); invalidate();
    }
    private float x(int value, boolean upper) {
        return 12*density + RegistrationRangeRules.position(value, ceiling, upper) * Math.max(1,getWidth()-24*density);
    }
    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(resolveSize((int)(240*density),width), resolveSize((int)(48*density),height));
    }
    @Override protected void onDraw(Canvas canvas) {
        float y = 24*density, left = 12*density, right = getWidth()-12*density;
        paint.setStrokeWidth(3*density); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(track); canvas.drawLine(left,y,right,y,paint);
        paint.setColor(accent); canvas.drawLine(x(low,false),y,x(high,true),y,paint);
        for (int i=0;i<2;i++) {
            float cx = x(i==0?low:high,i==1);
            if ((dragging || isFocused()) && thumb==i) {
                paint.setColor((accent & 0x00ffffff)|0x30000000); canvas.drawCircle(cx,y,12*density,paint);
            }
            paint.setColor(0x24000000); canvas.drawCircle(cx,y + density,8*density,paint);
            paint.setColor(0xfffbfffe); canvas.drawCircle(cx,y,7*density,paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                savedLow=low; savedHigh=high;
                float dl=Math.abs(event.getX()-x(low,false)), dh=Math.abs(event.getX()-x(high,true));
                thumb = Math.abs(dl-dh)<density ? (event.getX()<x(low,false)?0:1) : dl<dh?0:1;
                dragging=true; getParent().requestDisallowInterceptTouchEvent(true);
                move(event.getX(),false); return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) move(event.getX(),false); return true;
            case MotionEvent.ACTION_UP:
                if (dragging) { move(event.getX(),true); dragging=false; performClick(); }
                getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) { low=savedLow; high=savedHigh; dragging=false; emit(false); }
                getParent().requestDisallowInterceptTouchEvent(false); invalidate(); return true;
            default:return super.onTouchEvent(event);
        }
    }
    private void move(float x, boolean commit) {
        float position=(x-12*density)/Math.max(1,getWidth()-24*density);
        int value=RegistrationRangeRules.value(position,ceiling,thumb==1);
        if (thumb==0) low=high==0?value:Math.min(value,high);
        else high=value==0?0:Math.max(low,value);
        emit(commit);
    }
    private void emit(boolean commit) { invalidate(); if(listener!=null)listener.changed(low,high,commit); }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) {
            thumb=1-thumb; invalidate(); return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) {
            step(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);return true;
        }
        return super.onKeyDown(key,event);
    }
    private void step(int delta) {
        if(thumb==0) low=(int)Math.max(1,Math.min(high==0?Integer.MAX_VALUE:high,(long)low+delta));
        else if(high==0) { if(delta<0)high=ceiling; }
        else if(delta>0 && high>=ceiling)high=0;
        else high=(int)Math.max(Math.max(1,low),Math.min(Integer.MAX_VALUE,(long)high+delta));
        ceiling=RegistrationRangeRules.ceiling(low,high);emit(true);
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setContentDescription((thumb==0?"最少天数 "+low:"最多天数 "+(high==0?"不限":high))+"；点击切换上下限");
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if(action==AccessibilityNodeInfo.ACTION_CLICK) {thumb=1-thumb;invalidate();sendAccessibilityEvent(4);return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            step(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1); return true;
        }
        return super.performAccessibilityAction(action,args);
    }
}
