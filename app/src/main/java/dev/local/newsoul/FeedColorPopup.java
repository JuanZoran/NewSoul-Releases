package dev.local.newsoul;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.function.BiConsumer;
import static dev.local.newsoul.SettingsUi.*;

/** Anchored HSV editor using MenuPanel's existing bubble surface and popup lifecycle. */
final class FeedColorPopup {
    static void show(MenuPanel panel, View anchor, int color, BiConsumer<String, Boolean> changed) {
        show(panel,anchor,color,changed,true);
    }
    private static void show(MenuPanel panel, View anchor, int color,
            BiConsumer<String, Boolean> changed, boolean reposition) {
        if (!anchor.isAttachedToWindow()) return;
        LinearLayout content=new LinearLayout(panel.context); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(panel.context,12),dp(panel.context,16),dp(panel.context,12),dp(panel.context,8));
        float[] hsv=new float[3]; Color.colorToHSV(color,hsv);
        Picker field=new Picker(panel,hsv,false,changed), hue=new Picker(panel,hsv,true,changed);
        field.other=hue; hue.other=field;
        content.addView(field,new LinearLayout.LayoutParams(-1,dp(panel.context,160)));
        content.addView(hue,new LinearLayout.LayoutParams(-1,dp(panel.context,48)));
        TextView reset=label(panel.context,"默认",14,panel.accentText);
        styleInfoAction(panel,reset); buttonSemantics(reset); content.addView(reset,new LinearLayout.LayoutParams(-1,-2));
        Rect frame=new Rect(); anchor.getWindowVisibleDisplayFrame(frame);
        int width=Math.min(dp(panel.context,280),frame.width()-dp(panel.context,24));
        View root=anchor.getRootView();
        int[] screen=new int[2],window=new int[2],position=new int[2];
        root.getLocationOnScreen(screen);root.getLocationInWindow(window);anchor.getLocationOnScreen(position);
        // Popup windows can use the visible screen above the dialog, not just its content bounds.
        frame.inset(dp(panel.context,8),dp(panel.context,8));
        width=Math.min(width,frame.width());
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int center=position[0]+anchor.getWidth()/2,aboveY=position[1]-dp(panel.context,4);
        int available=aboveY-frame.top;
        int fixedHeight=content.getMeasuredHeight()-field.getMeasuredHeight();
        int minimumHeight=fixedHeight+dp(panel.context,64);
        if(available<minimumHeight || width<dp(panel.context,80)) {
            // Move the anchor down by scrolling its settings page toward the top, then remeasure.
            if(reposition && available<minimumHeight) {
                for(ViewParent parent=anchor.getParent();parent!=null;parent=parent.getParent()) {
                    if(parent instanceof ScrollView) {
                        ScrollView page=(ScrollView)parent;
                        int before=page.getScrollY();
                        page.scrollBy(0,-(minimumHeight-available));
                        if(page.getScrollY()!=before) {
                            anchor.post(()->show(panel,anchor,color,changed,false));
                            return;
                        }
                    }
                }
            }
            Toast.makeText(panel.context,"请将颜色按钮移到页面下方后重试",Toast.LENGTH_SHORT).show();
            return;
        }
        // Fit the entire editor above the anchor. No scrolling competes with the two-axis picker.
        field.getLayoutParams().height=Math.min(dp(panel.context,160),available-fixedHeight);
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=content.getMeasuredHeight();
        int x=Math.max(frame.left,Math.min(center-dp(panel.context,24),frame.right-width));
        int y=aboveY-height;
        panel.appearance.helpBubble(content,false,center-x);
        PopupWindow popup=new PopupWindow(content,width,height,true);
        panel.appearance.transparentPopup(popup); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setElevation(dp(panel.context,8));
        reset.setOnClickListener(v->{changed.accept("default",true);popup.dismiss();});
        View.OnAttachStateChangeListener lifetime=new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { popup.dismiss(); }
        };
        anchor.addOnAttachStateChangeListener(lifetime);
        popup.setOnDismissListener(()->anchor.removeOnAttachStateChangeListener(lifetime));
        try { popup.showAtLocation(root,Gravity.TOP|Gravity.LEFT,x-(screen[0]-window[0]),y-(screen[1]-window[1])); }
        catch(RuntimeException e) { anchor.removeOnAttachStateChangeListener(lifetime); throw e; }
    }
    private static final class Picker extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[] hsv;
        final boolean hue;
        final BiConsumer<String,Boolean> changed;
        Picker other;
        Picker(MenuPanel panel,float[] hsv,boolean hue,BiConsumer<String,Boolean> changed) {
            super(panel.context);this.hsv=hsv;this.hue=hue;this.changed=changed;
            setContentDescription(hue?"色相":"颜色饱和度与明度");
            setClickable(true);
        }
        float inset() { return 8*getResources().getDisplayMetrics().density; }
        @Override protected void onDraw(Canvas c) {
            float m=inset(),w=getWidth()-2*m,h=hue?12*getResources().getDisplayMetrics().density:getHeight()-2*m;
            float y=hue?(getHeight()-h)/2:m;
            if(hue) p.setShader(new LinearGradient(m,0,m+w,0,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null,Shader.TileMode.CLAMP));
            else p.setShader(new LinearGradient(m,0,m+w,0,Color.WHITE,Color.HSVToColor(new float[]{hsv[0],1,1}),Shader.TileMode.CLAMP));
            c.drawRect(m,y,m+w,y+h,p);p.setShader(null);
            if(!hue) {
                p.setShader(new LinearGradient(0,y,0,y+h,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));
                c.drawRect(m,y,m+w,y+h,p);p.setShader(null);
            }
            float cx=m+w*(hue?hsv[0]/360:hsv[1]),cy=hue?getHeight()/2f:y+h*(1-hsv[2]);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.BLACK);c.drawCircle(cx,cy,m*.7f+2,p);
            p.setColor(Color.WHITE);c.drawCircle(cx,cy,m*.7f,p);p.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN || a==MotionEvent.ACTION_MOVE || a==MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(a!=MotionEvent.ACTION_UP);
                float x=Math.max(0,Math.min(1,(e.getX()-inset())/(getWidth()-2*inset())));
                if(hue) hsv[0]=x*360;
                else {hsv[1]=x;hsv[2]=1-Math.max(0,Math.min(1,(e.getY()-inset())/(getHeight()-2*inset())));}
                changed.accept(String.format(java.util.Locale.ROOT,"#%06X",Color.HSVToColor(hsv)&0xffffff),a==MotionEvent.ACTION_UP);
                invalidate();other.invalidate();if(a==MotionEvent.ACTION_UP)performClick();return true;
            }
            if(a==MotionEvent.ACTION_CANCEL) {
                changed.accept(String.format(java.util.Locale.ROOT,"#%06X",Color.HSVToColor(hsv)&0xffffff),true);return true;
            }
            return super.onTouchEvent(e);
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
