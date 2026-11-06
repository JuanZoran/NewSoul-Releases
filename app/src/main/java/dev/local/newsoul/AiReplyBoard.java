package dev.local.newsoul;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Presentation counterpart of Soul's LoveBoard; all actions belong to the plugin session. */
final class AiReplyBoard extends LinearLayout {
    interface Actions {
        void mode(AiWritingRules.Mode mode);
        void style(AiWritingRules.Style style);
        void settings(); void details(); void restore(); void clear(); void send();
    }
    final SettingsUi.MenuPanel panel;
    final Actions actions;
    final TextView replyTab, rewriteTab, style, insight, status, restore, clear, send;
    final LinearLayout tabs, body, grid, rewriteActions;
    final ScrollView scroller;
    final LinearLayout results;
    final List<View> interactive=new ArrayList<>();
    AiWritingRules.Mode mode=AiWritingRules.Mode.REPLY;
    AiWritingRules.Style selected=AiWritingRules.Style.DEFAULT;
    boolean stylesOpen;
    final int accent=0xff884dff;
    final int background, card;

    AiReplyBoard(SettingsUi.MenuPanel panel,LinearLayout results,TextView status,TextView generate,Actions actions) {
        super(panel.context);this.panel=panel;this.results=results;this.status=status;this.actions=actions;
        background=AiReplyComponents.hostColor(panel,panel.dark?"color_s_09_dark":"color_s_05_light",panel.dark?0xff12121f:0xfffefefe);
        card=AiReplyComponents.hostColor(panel,"color_s_04",panel.dark?0xff282838:0xffededed);
        setOrientation(VERTICAL);setPadding(dp(16),dp(4),dp(16),dp(4));panel.appearance.hostBoardSurface(this,background,0,0);
        LinearLayout header=new LinearLayout(getContext());header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("灵感回复",14);header.addView(title,new LayoutParams(-2,dp(44)));
        insight=text("回复思路",11);insight.setTextColor(accent);insight.setPadding(dp(8),0,dp(8),0);
        surface(insight,background,18,accent);insight.setOnClickListener(v->actions.details());insight.setContentDescription("查看回复思路");
        LayoutParams cp=new LayoutParams(-2,dp(28));cp.leftMargin=dp(6);header.addView(insight,cp);interactive.add(insight);
        header.addView(new View(getContext()),new LayoutParams(0,1,1));
        TextView settings=icon("设置","c_ct_aigc_board_setting_icon",actions::settings);
        header.addView(settings,new LayoutParams(dp(40),dp(44)));
        decorateIcon(generate,"换一组","c_ct_aigc_board_refresh_icon");
        header.addView(generate,new LayoutParams(dp(40),dp(44)));addView(header,new LayoutParams(-1,-2));
        LinearLayout navigation=new LinearLayout(getContext());navigation.setGravity(Gravity.CENTER_VERTICAL);
        tabs=new LinearLayout(getContext());tabs.setPadding(dp(2),dp(2),dp(2),dp(2));surface(tabs,card,24,0);
        replyTab=text("帮我回复",13);rewriteTab=text("帮我改写",13);
        SettingsUi.singleChoiceGroup(tabs,title,2);
        TextView[] options={replyTab,rewriteTab};
        for(int i=0;i<2;i++) {final int index=i;TextView tab=options[i];
            tab.setPadding(dp(8),0,dp(8),0);SettingsUi.singleChoiceOption(tab,i);
            tab.setOnClickListener(v->actions.mode(index==0?AiWritingRules.Mode.REPLY:AiWritingRules.Mode.REWRITE));
            tabs.addView(tab,new LayoutParams(0,dp(40),1));interactive.add(tab);
        }
        navigation.addView(tabs,new LayoutParams(0,-2,1.4f));
        style=text("默认风格",12);style.setTextColor(accent);style.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        style.setPadding(dp(8),0,0,0);style.setOnClickListener(v->{stylesOpen=!stylesOpen;renderMode();});interactive.add(style);
        navigation.addView(style,new LayoutParams(0,dp(48),1));
        addView(navigation,new LayoutParams(-1,-2));
        body=new LinearLayout(getContext());body.setOrientation(HORIZONTAL);
        scroller=new ScrollView(getContext());scroller.setFillViewport(false);scroller.setClipToPadding(false);
        scroller.setPadding(0,dp(8),0,dp(4));body.addView(scroller,new LayoutParams(0,-1,1));
        grid=new LinearLayout(getContext());grid.setOrientation(VERTICAL);
        rewriteActions=new LinearLayout(getContext());rewriteActions.setOrientation(VERTICAL);rewriteActions.setPadding(dp(8),dp(8),0,dp(4));
        restore=side("还原",actions::restore);clear=side("清空",actions::clear);send=side("发送",actions::send);
        rewriteActions.addView(restore,new LayoutParams(-1,0,1));
        LayoutParams gap=new LayoutParams(-1,0,1);gap.topMargin=dp(8);rewriteActions.addView(clear,gap);
        LayoutParams sp=new LayoutParams(-1,0,2);sp.topMargin=dp(8);rewriteActions.addView(send,sp);
        body.addView(rewriteActions,new LayoutParams(dp(78),-1));addView(body,new LayoutParams(-1,dp(210)));
        status.setPadding(0,dp(4),0,0);status.setTextSize(11);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(28));status.setMaxLines(2);
        addView(status,new LayoutParams(-1,-2));renderMode();
    }
    void mode(AiWritingRules.Mode mode,AiWritingRules.Style selected) {
        this.mode=mode;this.selected=selected;stylesOpen=false;renderMode();
    }
    void selected(AiWritingRules.Style style) {selected=style;stylesOpen=false;renderMode();}
    void result(AiReplyRules.Result result) {
        insight.setContentDescription(result==null?"查看回复思路":result.interestLabel()+"，查看回复思路");
    }
    void busy(boolean busy) {
        for(View view:interactive)view.setEnabled(!busy);
        setGridEnabled(grid,!busy);
        restore.setEnabled(!busy&&restore.isActivated());clear.setEnabled(!busy&&clear.isActivated());send.setEnabled(!busy&&send.isActivated());
        restore.setAlpha(restore.isEnabled()?1f:.35f);clear.setAlpha(clear.isEnabled()?1f:.35f);send.setAlpha(send.isEnabled()?1f:.35f);
    }
    void draftActions(boolean canRestore,boolean hasText,boolean canSend,boolean busy) {
        restore.setActivated(canRestore);clear.setActivated(hasText);send.setActivated(canSend);busy(busy);
    }
    private void renderMode() {
        replyTab.setSelected(mode==AiWritingRules.Mode.REPLY);rewriteTab.setSelected(mode==AiWritingRules.Mode.REWRITE);
        surface(replyTab,mode==AiWritingRules.Mode.REPLY?background:card,24,0);
        surface(rewriteTab,mode==AiWritingRules.Mode.REWRITE?background:card,24,0);
        replyTab.setAlpha(mode==AiWritingRules.Mode.REPLY?1f:.5f);rewriteTab.setAlpha(mode==AiWritingRules.Mode.REWRITE?1f:.5f);
        boolean rewrite=mode==AiWritingRules.Mode.REWRITE;
        style.setVisibility(rewrite?INVISIBLE:VISIBLE);style.setText(selected.label);
        Drawable arrow=AiReplyComponents.hostDrawable(panel,"c_ct_down_arrow_day");
        if(arrow!=null){arrow.setTint(accent);arrow.setBounds(0,0,dp(12),dp(12));style.setCompoundDrawables(null,null,arrow,null);style.setCompoundDrawablePadding(dp(5));}
        rewriteActions.setVisibility(rewrite?VISIBLE:GONE);
        scroller.removeAllViews();
        if(rewrite||stylesOpen){populateGrid();scroller.addView(grid,new ScrollView.LayoutParams(-1,-2));}
        else scroller.addView(results,new ScrollView.LayoutParams(-1,-2));
    }
    private void populateGrid() {
        grid.removeAllViews();AiWritingRules.Style[] values=AiWritingRules.styles(mode);
        for(int start=0;start<values.length;start+=3){
            LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
            for(int i=start;i<Math.min(start+3,values.length);i++){
                AiWritingRules.Style value=values[i];TextView cell=text(value.emoji+value.label,12);
                cell.setMinHeight(dp(58));cell.setPadding(dp(3),dp(8),dp(3),dp(8));
                surface(cell,card,8,value==selected?accent:0);cell.setSelected(value==selected);
                if(value==AiWritingRules.Style.DEFAULT){Drawable icon=AiReplyComponents.hostDrawable(panel,"c_ct_loveboard_default_character_icon");
                    if(icon!=null){icon.setBounds(0,0,dp(16),dp(16));cell.setCompoundDrawables(icon,null,null,null);}}
                cell.setOnClickListener(v->actions.style(value));cell.setContentDescription(value.label);
                LayoutParams lp=new LayoutParams(0,-2,1);if(i>start)lp.leftMargin=dp(8);row.addView(cell,lp);
            }
            LayoutParams rp=new LayoutParams(-1,-2);rp.bottomMargin=dp(8);grid.addView(row,rp);
        }
    }
    private void setGridEnabled(android.view.ViewGroup group,boolean enabled){
        for(int i=0;i<group.getChildCount();i++){View view=group.getChildAt(i);view.setEnabled(enabled);if(view instanceof android.view.ViewGroup)setGridEnabled((android.view.ViewGroup)view,enabled);}
    }
    private TextView side(String label,Runnable action){TextView view=text(label,13);view.setTextColor(accent);surface(view,card,8,0);view.setOnClickListener(v->action.run());interactive.add(view);return view;}
    private TextView icon(String label,String name,Runnable action){TextView view=text("",12);decorateIcon(view,label,name);view.setOnClickListener(v->action.run());interactive.add(view);return view;}
    private void decorateIcon(TextView view,String label,String name){
        view.setText("");panel.appearance.apply(view,UiAppearance.Role.CONTENT);view.setPadding(dp(8),dp(10),dp(8),dp(10));
        view.setGravity(Gravity.CENTER);view.setMinWidth(0);view.setContentDescription(label);
        Drawable icon=AiReplyComponents.hostDrawable(panel,name);
        if(icon!=null){icon.setBounds(0,0,dp(24),dp(24));view.setCompoundDrawables(icon,null,null,null);}else view.setText(label);
    }
    private TextView text(String value,int size){TextView view=SettingsUi.label(getContext(),value,size,panel.foreground);view.setGravity(Gravity.CENTER);view.setPadding(0,0,0,0);view.setFocusable(true);return view;}
    private void surface(View view,int color,int radius,int stroke){panel.appearance.hostBoardSurface(view,color,radius,stroke);}
    private int dp(int value){return SettingsUi.dp(getContext(),value);}
}
