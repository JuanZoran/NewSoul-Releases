package dev.local.newsoul;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Visual picker; tapping a tile selects only. Sending always needs confirmation. */
final class GamePicker {
    interface Callback { void select(int result); }
    private static final int ACCENT=Color.rgb(0,198,184);
    static Dialog show(Activity activity,boolean dice,boolean preview,Callback callback,Runnable closed) {
        boolean dark=(activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        UiAppearance appearance=UiAppearance.of(activity,dark);
        int text=dark?0xffe5e5ee:0xff252530;
        int muted=dark?0xff9292a8:0xff737383;
        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity,20),dp(activity,10),dp(activity,20),dp(activity,20));
        appearance.apply(body,UiAppearance.Role.PANEL,24);
        View handle=new View(activity);
        handle.setBackground(bg(dark?0xff454557:0xffd7d7dd,0,0,activity,3));
        LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(activity,32),dp(activity,4));
        handleParams.gravity=Gravity.CENTER_HORIZONTAL;handleParams.bottomMargin=dp(activity,8);
        body.addView(handle,handleParams);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(activity,dice?"骰子":"猜拳",20,text);
        title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(activity,48),1));
        if(preview) {
            TextView badge=label(activity,"仅预览",12,muted);
            badge.setPadding(dp(activity,8),0,dp(activity,8),0);
            header.addView(badge,new LinearLayout.LayoutParams(-2,dp(activity,32)));
        }
        TextView close=label(activity,"×",28,muted);close.setGravity(Gravity.CENTER);
        appearance.apply(close,UiAppearance.Role.ICON);
        close.setContentDescription("取消");close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));
        body.addView(header);
        ArrayList<View> tiles=new ArrayList<>();ArrayList<Integer> values=new ArrayList<>();
        final int[] selected={-1};
        TextView confirm=label(activity,preview?"预览":"发送",17,0xff063a35);
        confirm.setGravity(Gravity.CENTER);confirm.setTypeface(null,Typeface.BOLD);
        confirm.setEnabled(false);confirm.setAlpha(.35f);
        appearance.apply(confirm,UiAppearance.Role.PRIMARY,16);
        Runnable refresh=()->{
            for(int i=0;i<tiles.size();i++) {
                View tile=tiles.get(i);boolean checked=values.get(i)==selected[0];
                tile.setSelected(checked);
                if(tile instanceof FrameLayout) {
                    View tick=((FrameLayout)tile).findViewWithTag("tick");
                    if(tick!=null)tick.setVisibility(checked?View.VISIBLE:View.INVISIBLE);
                }
            }
            confirm.setEnabled(selected[0]>=0);confirm.setAlpha(selected[0]>=0?1f:.35f);
        };
        int[] ordering=dice?new int[]{1,2,3,4,5,6}:new int[]{3,2,1};
        for(int row=0;row<(dice?2:1);row++) {
            LinearLayout line=new LinearLayout(activity);
            for(int col=0;col<3;col++) {
                int value=ordering[row*3+col];
                FrameLayout card=new FrameLayout(activity);
                card.setContentDescription(dice?value+" 点":handLabel(value));
                View picture;
                if(dice)picture=new DiceFace(activity,value);
                else {
                    String resource=value==3?"finger_stone":value==2?"finger_scissor":"finger_cloth";
                    int id=activity.getResources().getIdentifier(resource,"drawable",activity.getPackageName());
                    if(id!=0) {ImageView image=new ImageView(activity);image.setImageResource(id);image.setScaleType(ImageView.ScaleType.FIT_CENTER);picture=image;}
                    else {TextView fallback=label(activity,value==3?"✊":value==2?"✌":"✋",36,text);fallback.setGravity(Gravity.CENTER);picture=fallback;}
                }
                picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(dp(activity,60),dp(activity,60),Gravity.CENTER);
                card.addView(picture,iconParams);
                TextView tick=label(activity,"✓",12,0xff063a35);tick.setGravity(Gravity.CENTER);tick.setTag("tick");
                tick.setBackground(bg(ACCENT,0,0,activity,10));tick.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams tickParams=new FrameLayout.LayoutParams(dp(activity,20),dp(activity,20),Gravity.TOP|Gravity.RIGHT);
                tickParams.topMargin=tickParams.rightMargin=dp(activity,7);card.addView(tick,tickParams);
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(activity,92),1);
                if(col!=2)cell.rightMargin=dp(activity,10);
                line.addView(card,cell);tiles.add(card);values.add(value);
                card.setFocusable(true);card.setOnClickListener(v->{selected[0]=value;refresh.run();v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);});
            }
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(activity,10);
            body.addView(line,rowParams);
        }
        LinearLayout random=new LinearLayout(activity);random.setGravity(Gravity.CENTER);
        TextView shuffle=label(activity,"⤨",25,muted);shuffle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        random.addView(shuffle,new LinearLayout.LayoutParams(dp(activity,30),dp(activity,44)));
        random.addView(label(activity,"随机",15,text));random.setContentDescription("随机结果");random.setFocusable(true);
        tiles.add(random);values.add(0);random.setOnClickListener(v->{selected[0]=0;refresh.run();});
        LinearLayout.LayoutParams randomParams=new LinearLayout.LayoutParams(-1,dp(activity,48));randomParams.topMargin=dp(activity,12);
        body.addView(random,randomParams);
        LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(-1,dp(activity,52));sendParams.topMargin=dp(activity,16);
        body.addView(confirm,sendParams);
        confirm.setOnClickListener(v->{if(selected[0]<0)return;confirm.setEnabled(false);dialog.dismiss();callback.select(selected[0]);});
        for(View tile:tiles)appearance.apply(tile,UiAppearance.Role.CHOICE,16);
        refresh.run();dialog.setContentView(body);
        dialog.setOnDismissListener(d->closed.run());dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.5f;window.setAttributes(params);
        window.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        dialog.show();
        int width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,24),dp(activity,420));
        window.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams placed=window.getAttributes();placed.y=dp(activity,12);window.setAttributes(placed);
        return dialog;
    }
    private static String handLabel(int v){return v==3?"石头":v==2?"剪刀":"布";}
    private static TextView label(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private static GradientDrawable bg(int color,int stroke,int weight,Context c,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(weight>0)d.setStroke(dp(c,weight),stroke);return d;}
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private static final class DiceFace extends View {
        final int value;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        DiceFace(Context c,int value){super(c);this.value=value;}
        protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/60f,getHeight()/60f);
            p.setColor(0xfffafafc);canvas.drawRoundRect(5,5,55,55,11,11,p);
            p.setColor(0xff343440);
            if(value%2==1)dot(canvas,30,30);
            if(value>=2){dot(canvas,18,18);dot(canvas,42,42);}
            if(value>=4){dot(canvas,42,18);dot(canvas,18,42);}
            if(value==6){dot(canvas,18,30);dot(canvas,42,30);}
            canvas.restore();
        }
        private void dot(Canvas c,float x,float y){c.drawCircle(x,y,3.7f,p);}
    }
}
