package dev.local.newsoul;

import android.app.AlertDialog;
import android.text.InputFilter;
import android.widget.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Only manual editing. No model, composer, sending, clipboard or promotion capability. */
final class AiMaterialUi {
    static void show(SettingsUi.MenuPanel owner,AiMaterialRules.Kind kind,AiMaterialRules.Selection selection,
                     Runnable changed,Consumer<AlertDialog> track,BooleanSupplier alive) {
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(owner.activity,owner.dark);
        boolean real=kind==AiMaterialRules.Kind.REAL;
        AiMaterialRules.Selection staged=selection.copy();
        panel.help=real?"每轮最多选 3 条，用于“帮我回复”，改写不使用\n关闭回复面板或更新聊天资料后需重新选择，编辑过的素材需重新选用\n素材保存在本机，不随插件配置导出"
                :"记录虚构或待核实的想法，不用于回复生成或发送\n真实经历请另存到“真实素材”\n草稿保存在本机，不随插件配置导出";
        Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            if(!alive.getAsBoolean())return;
            panel.body.removeAllViews();
            if(!real)AiReplyUi.infoCard(panel,"虚构/待核实","");
            LinearLayout actions=new LinearLayout(panel.context);actions.setOrientation(LinearLayout.VERTICAL);panel.body.addView(actions);
            TextView add=AiReplyUi.chip(panel,real?"新增素材":"新增草稿",()->edit(panel,kind,null,()->{refresh[0].run();changed.run();},track,alive));
            if(real)SettingsUi.addInfoActionPair(panel,actions,add,AiReplyUi.chip(panel,"清空选择",()->{staged.clear();refresh[0].run();}));
            else actions.addView(add);
            try {
                AiMaterialRules.Library library=AiMaterialStore.read(owner.activity,kind);
                if(real&&staged.reconcile(library))Toast.makeText(owner.activity,"变更的素材已取消选用",Toast.LENGTH_SHORT).show();
                if(library.entries.isEmpty())AiReplyUi.infoCard(panel,real?"还没有真实素材":"还没有灵感草稿","");
                for(AiMaterialRules.Entry entry:library.entries) {
                    LinearLayout card=AiReplyUi.infoCard(panel,entry.title,(entry.tags.isEmpty()?"":entry.tags+"\n")+entry.text);
                    TextView edit=AiReplyUi.chip(panel,"编辑",()->edit(panel,kind,entry,()->{refresh[0].run();changed.run();},track,alive));
                    if(real) {
                        CheckBox pick=new CheckBox(panel.context);pick.setText("本轮选用");pick.setTextColor(panel.foreground);pick.setMinHeight(SettingsUi.dp(panel.context,48));
                        pick.setTextSize(13);panel.appearance.checkControl(pick);
                        pick.setChecked(staged.contains(entry.id));
                        pick.setOnClickListener(v->{try{staged.toggle(entry);}catch(IllegalArgumentException error){Toast.makeText(owner.activity,error.getMessage(),Toast.LENGTH_SHORT).show();}pick.setChecked(staged.contains(entry.id));});
                        SettingsUi.addInfoActionPair(panel,card,pick,edit);
                    } else card.addView(edit);
                }
            } catch(Exception error) {AiReplyUi.infoCard(panel,"素材读取失败","原数据未改动，请关闭后重试");}
        };
        refresh[0].run();
        if(real)panel.protectDraft(()->!sameSelection(selection,staged));
        panel.show(real?"真实素材":"灵感草稿",real?"选用":"关闭");track.accept(panel.dialog);
        if(real)panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!alive.getAsBoolean())return;
            try {
                if(staged.size()>0&&staged.reconcile(AiMaterialStore.read(owner.activity,kind))){refresh[0].run();Toast.makeText(owner.activity,"素材已变更，请重新选用",Toast.LENGTH_SHORT).show();return;}
                selection.replace(staged);changed.run();panel.dialog.dismiss();
            }catch(Exception error){Toast.makeText(owner.activity,"选用失败，原选择未改变",Toast.LENGTH_SHORT).show();}
        });
    }
    private static boolean sameSelection(AiMaterialRules.Selection a,AiMaterialRules.Selection b){return a.sameIds(b);}
    private static void edit(SettingsUi.MenuPanel owner,AiMaterialRules.Kind kind,AiMaterialRules.Entry entry,
                             Runnable saved,Consumer<AlertDialog> track,BooleanSupplier alive) {
        if(!alive.getAsBoolean())return;
        boolean real=kind==AiMaterialRules.Kind.REAL;
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(owner.activity,owner.dark);
        panel.help=real?"只记录自己确认发生的经历，修改后需重新确认\n名称最多 40 字，标签 120 字，事件 1000 字"
                :"名称最多 40 字，标签 120 字，内容 1000 字";
        if(!real)AiReplyUi.infoCard(panel,"虚构/待核实","");
        EditText title=AiReplyUi.field(panel,"名称",true),tags=AiReplyUi.field(panel,"标签（可留空）",true),body=AiReplyUi.field(panel,real?"真实事件":"手动写下灵感",false);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});tags.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});body.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});body.setMinLines(4);body.setMaxLines(8);
        String initialTitle=entry==null?"":entry.title,initialTags=entry==null?"":entry.tags,initialBody=entry==null?"":entry.text;
        title.setText(initialTitle);tags.setText(initialTags);body.setText(initialBody);
        CheckBox confirmed=new CheckBox(panel.context);
        if(real){confirmed.setText("我确认这是真实经历");confirmed.setTextColor(panel.foreground);panel.appearance.checkControl(confirmed);confirmed.setMinHeight(SettingsUi.dp(panel.context,48));panel.body.addView(confirmed);}
        if(real) {
            android.text.TextWatcher edited=new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){confirmed.setChecked(false);}
                public void afterTextChanged(android.text.Editable text){}
            };
            title.addTextChangedListener(edited);tags.addTextChangedListener(edited);body.addTextChangedListener(edited);
        }
        TextView error=AiReplyUi.label(panel,"",12);error.setVisibility(android.view.View.GONE);panel.body.addView(error);
        if(entry!=null) {
            LinearLayout actions=new LinearLayout(panel.context);panel.body.addView(actions);
            actions.addView(AiReplyUi.chip(panel,real?"删除素材":"删除草稿",()->{
                SettingsUi.MenuPanel confirm=new SettingsUi.MenuPanel(owner.activity,owner.dark);
                AiReplyUi.infoCard(confirm,"删除后无法恢复",entry.title);
                confirm.builder.setNegativeButton("取消",(d,w)->{});confirm.show("删除这条内容？","删除");track.accept(confirm.dialog);
                confirm.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(!alive.getAsBoolean())return;
                    try{AiMaterialStore.delete(owner.activity,kind,entry.id,entry.revision);confirm.dialog.dismiss();panel.dialog.dismiss();saved.run();}
                    catch(Exception failure){Toast.makeText(owner.activity,"删除失败或内容已变更，请重新打开",Toast.LENGTH_SHORT).show();}
                });
            }));
        }
        panel.protectDraft(()->!initialTitle.equals(title.getText().toString())||!initialTags.equals(tags.getText().toString())||!initialBody.equals(body.getText().toString())||confirmed.isChecked());
        panel.show(real?"编辑真实素材":"编辑灵感草稿","保存");track.accept(panel.dialog);
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!alive.getAsBoolean())return;
            v.setEnabled(false);
            try {
                AiMaterialStore.save(owner.activity,kind,entry==null?null:entry.id,entry==null?0:entry.revision,title.getText().toString(),tags.getText().toString(),body.getText().toString(),real&&confirmed.isChecked());
                panel.dialog.dismiss();saved.run();
            }catch(Exception failure){error.setVisibility(android.view.View.VISIBLE);error.setText(failure instanceof IllegalArgumentException?failure.getMessage():"保存失败，请重试");v.setEnabled(true);}
        });
    }
}
