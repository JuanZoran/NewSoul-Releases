package dev.local.newsoul;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import static dev.local.newsoul.SettingsUi.*;

/** Two independent inline editors share one passive text-post sample. */
final class FeedLabelStyleUi {
    static void attach(MenuPanel panel, LinearLayout body, MenuSwitch ipToggle, MenuSwitch daysToggle) {
        SharedPreferences prefs=PluginPreferences.get(panel.activity);
        LinearLayout preview=new LinearLayout(panel.context);preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(panel.context,12),dp(panel.context,12),dp(panel.context,12),dp(panel.context,12));
        panel.appearance.apply(preview,UiAppearance.Role.CONTENT,12);
        TextView title=label(panel.context,"预览",12,panel.secondary);
        preview.addView(title);
        LinearLayout names=new LinearLayout(panel.context);names.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=label(panel.context,"示例用户",16,hostColor(panel,"color_s_02",panel.foreground));
        names.addView(name,new LinearLayout.LayoutParams(-2,-2));
        TextView ip=label(panel.context,"IP属地：上海",12,panel.secondary);
        LinearLayout.LayoutParams ipBounds=new LinearLayout.LayoutParams(-2,-2);ipBounds.leftMargin=dp(panel.context,8);
        names.addView(ip,ipBounds);
        LinearLayout.LayoutParams nameBounds=new LinearLayout.LayoutParams(-1,-2);nameBounds.topMargin=dp(panel.context,12);
        preview.addView(names,nameBounds);
        TextView time=label(panel.context,"11分钟前",12,panel.secondary);
        TextView post=label(panel.context,"今天的风很舒服，想把这份好心情分享给你",16,hostColor(panel,"color_s_02",panel.foreground));
        post.setPadding(0,dp(panel.context,12),0,dp(panel.context,4));
        preview.addView(time,new LinearLayout.LayoutParams(-1,-2));
        preview.addView(post,new LinearLayout.LayoutParams(-1,-2));
        String[][] draft={{prefs.getString(FeedLabelStyle.IP+"color","default"),prefs.getString(FeedLabelStyle.IP+"size","default")},
                {prefs.getString(FeedLabelStyle.DAYS+"color","default"),prefs.getString(FeedLabelStyle.DAYS+"size","default")}};
        Runnable render=()->{
            boolean showIp=ipToggle.isChecked()&&ipToggle.isEnabled(),showDays=daysToggle.isChecked()&&daysToggle.isEnabled();
            preview.setVisibility(showIp||showDays?View.VISIBLE:View.GONE);
            ip.setVisibility(showIp?View.VISIBLE:View.GONE);
            int secondary=hostColor(panel,"color_s_06",panel.secondary);
            FeedLabelStyle.apply(ip,draft[0][0],draft[0][1],name.getTextSize()*.75f,secondary);
            time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,12);time.setTextColor(secondary);
            time.setText(showDays ? FeedLabelStyle.appendDays(time,"11分钟前",365,draft[1][0],draft[1][1]) : "11分钟前");
        };
        editor(panel,ipToggle,FeedLabelStyle.IP,draft[0],prefs,render);
        editor(panel,daysToggle,FeedLabelStyle.DAYS,draft[1],prefs,render);
        body.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        ipToggle.observe(render);daysToggle.observe(render);
        panel.addPreferenceRefresher(render);render.run();
    }

    private static void editor(MenuPanel panel,MenuSwitch toggle,String prefix,String[] draft,
            SharedPreferences prefs,Runnable render) {
        LinearLayout row=new LinearLayout(panel.context);row.setGravity(Gravity.CENTER_VERTICAL);
        ColorDot dot=new ColorDot(panel);
        Runnable tint=()->{
            Integer color=FeedLabelStyle.color(draft[0]);dot.color=color==null?hostColor(panel,"color_s_06",panel.secondary):color;
            dot.setContentDescription("字体颜色，"+(color==null?"默认":draft[0])+"，点击选择");dot.invalidate();
        };
        FeedFontRuler ruler=new FeedFontRuler(panel,(value,commit)->{
            draft[1]=value;render.run();
            if(commit)PreferenceChanges.putString(prefs,prefix+"size",value);
        });
        row.addView(dot,new LinearLayout.LayoutParams(dp(panel.context,48),dp(panel.context,48)));
        row.addView(ruler,new LinearLayout.LayoutParams(dp(panel.context,104),dp(panel.context,52)));
        dot.setOnClickListener(v->FeedColorPopup.show(panel,dot,dot.color,(value,commit)->{
            draft[0]=value;tint.run();render.run();
            if(commit)PreferenceChanges.putString(prefs,prefix+"color",value);
        }));
        Runnable refresh=()->{
            draft[0]=prefs.getString(prefix+"color","default");draft[1]=prefs.getString(prefix+"size","default");
            ruler.select(draft[1]);tint.run();
        };
        attachSwitchEditor(toggle,panel,row);panel.addPreferenceRefresher(refresh);refresh.run();
    }
    private static int hostColor(MenuPanel panel,String name,int fallback) {
        int id=panel.context.getResources().getIdentifier(name,"color","cn.soulapp.android");
        return id==0?fallback:panel.context.getResources().getColor(id,panel.context.getTheme());
    }
    private static final class ColorDot extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final MenuPanel panel;int color;
        ColorDot(MenuPanel panel){super(panel.context);this.panel=panel;setFocusable(true);setClickable(true);buttonSemantics(this);}
        @Override protected void onDraw(Canvas canvas){
            float radius=dp(getContext(),11);paint.setColor(color);paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(getWidth()/2f,getHeight()/2f,radius,paint);
            paint.setColor(panel.secondary);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(getContext(),1));
            canvas.drawCircle(getWidth()/2f,getHeight()/2f,radius,paint);
        }
    }
    private FeedLabelStyleUi() {}
}
