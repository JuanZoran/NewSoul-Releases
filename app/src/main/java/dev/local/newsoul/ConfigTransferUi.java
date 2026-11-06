package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** SAF grants access only to the user's chosen document; no storage permission needed. */
final class ConfigTransferUi {
    private static XposedModule module;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final WeakHashMap<Activity,Pending> PENDING = new WeakHashMap<>();
    private static final Set<Method> HOOKED = new HashSet<>();
    private static final java.util.Random REQUESTS = new java.security.SecureRandom();
    private static final class Pending {
        final int code; final boolean dark; final String export;
        final WeakReference<SettingsUi.MenuPanel> source;
        final WeakReference<TransferStatus> status;
        boolean choosing = true;
        Pending(SettingsUi.MenuPanel panel,TransferStatus status,String export){
            this.code=0x5000+REQUESTS.nextInt(0x9000);this.dark=panel.dark;this.export=export;
            source=new WeakReference<>(panel);
            this.status=new WeakReference<>(status);
        }
    }
    /** Scoped to the existing section; no preference keys or persisted transient state. */
    private static final class TransferStatus {
        final TextView export, importConfig, message;
        TransferStatus(LinearLayout section,
                TextView export, TextView importConfig) {
            this.export=export;this.importConfig=importConfig;
            message=SettingsUi.addSummary(section, "");
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            message.setVisibility(View.GONE);
        }
        void show(String text,boolean busy) {
            message.setText(text);
            message.setVisibility(View.VISIBLE);
            export.setEnabled(!busy);
            importConfig.setEnabled(!busy);
            export.setAlpha(busy?0.5f:1f);
            importConfig.setAlpha(busy?0.5f:1f);
        }
    }
    static void install(XposedModule value){module=value;}
    static void addRows(SettingsUi.MenuPanel panel) {
        LinearLayout section=SettingsUi.section(panel,"配置迁移",
                "导出不含密钥、聊天记录或账号凭据");
        TextView export=SettingsUi.label(panel.context,"导出配置",15,panel.accentText);
        TextView importConfig=SettingsUi.label(panel.context,"导入配置",15,panel.accentText);
        SettingsUi.buttonSemantics(export);
        SettingsUi.buttonSemantics(importConfig);
        SettingsUi.addInfoActionPair(panel,section,export,importConfig);
        TransferStatus status=new TransferStatus(section,export,importConfig);
        export.setOnClickListener(v->launch(panel,status,true));
        importConfig.setOnClickListener(v->launch(panel,status,false));
    }
    private static void launch(SettingsUi.MenuPanel panel,TransferStatus status,boolean export){
        Activity activity=panel.activity;
        try {
            Pending existing=PENDING.get(activity);
            if(existing!=null){
                // The original section stays disabled until completion. A newly
                // opened menu cannot submit a second operation either.
                if(existing.status.get()!=status)
                    status.show("配置操作进行中，请先完成或取消",false);
                return;
            }
            ensureResultHook(activity);
            String data=export?ConfigTransferRules.exportConfig(PluginPreferences.get(activity).getAll()):null;
            Pending pending=new Pending(panel,status,data);
            Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(export?"application/json":"*/*");
            if(export)intent.putExtra(Intent.EXTRA_TITLE,"NewSoul-config-"+
                    new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(new java.util.Date())+".json");
            PENDING.put(activity,pending);
            status.show(export?"请选择保存位置":"请选择配置文件",true);
            try {activity.startActivityForResult(intent,pending.code);}
            catch(RuntimeException failure){PENDING.remove(activity);throw failure;}
            // SAF covers this window. Keeping the same dialog preserves the page,
            // scroll and focus for every result without reopening a dismissed menu.
        } catch(Exception error){status.show("无法打开配置文件操作，请重试",false);}
    }
    private static void ensureResultHook(Activity activity) throws Exception {
        if(module==null)throw new IllegalStateException();
        Class<?> type=activity.getClass();
        Method callback=null;
        while(type!=null){
            try{callback=type.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);break;}
            catch(NoSuchMethodException ignored){type=type.getSuperclass();}
        }
        if(callback==null)throw new NoSuchMethodException();
        if(HOOKED.contains(callback))return;
        module.hook(callback).setId("new-soul/config-file-result")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain->{
                    Activity owner=(Activity)chain.getThisObject();
                    Pending pending=PENDING.get(owner);
                    if(pending==null||pending.code!=(int)chain.getArg(0))return chain.proceed();
                    if(!pending.choosing)return null;
                    pending.choosing=false;
                    if((int)chain.getArg(1)==Activity.RESULT_OK){
                        Intent data=(Intent)chain.getArg(2);
                        if(data!=null&&data.getData()!=null)process(owner,pending,data.getData());
                        else finish(owner,pending,"未取得配置文件，请重新选择");
                    } else finish(owner,pending,"已取消配置操作");
                    // Consume only our own live request, leaving host callbacks untouched.
                    return null;
                });
        HOOKED.add(callback);
    }
    private static void progress(Pending pending,String text) {
        TransferStatus status=pending.status.get();
        if(status!=null)status.show(text,true);
    }
    private static void finish(Activity activity,Pending pending,String text) {
        if(PENDING.get(activity)!=pending)return;
        PENDING.remove(activity);
        TransferStatus status=pending.status.get();
        if(status!=null)status.show(text,false);
        else if(!activity.isDestroyed())toast(activity,text);
    }
    private static void process(Activity activity,Pending pending,Uri uri){
        progress(pending,pending.export!=null?"正在导出配置…":"正在读取配置…");
        IO.execute(()->{
            try {
                if(!"content".equals(uri.getScheme()))throw new IllegalArgumentException();
                if(pending.export!=null){
                    try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){
                        if(out==null)throw new java.io.IOException();
                        out.write(pending.export.getBytes(StandardCharsets.UTF_8));out.flush();
                    }
                    MAIN.post(()->finish(activity,pending,"配置已导出"));
                } else {
                    byte[] bytes;
                    try(InputStream in=activity.getContentResolver().openInputStream(uri)){
                        if(in==null)throw new java.io.IOException();
                        bytes=ConfigTransferRules.readBounded(in);
                    }
                    String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                    Map<String,Object> values=ConfigTransferRules.parse(text,PluginPreferences.get(activity).getAll());
                    MAIN.post(()->confirm(activity,pending,values));
                }
            } catch(ConfigTransferRules.InvalidRangeException error){
                MAIN.post(()->finish(activity,pending,"导入失败："+error.getMessage()));
            } catch(Exception error){
                MAIN.post(()->finish(activity,pending,pending.export!=null?"导出失败，请重新选择保存位置":"导入失败：文件无法读取、过大或格式无效，请重新选择"));
            }
        });
    }
    private static boolean sourceStillOpen(Activity activity,Pending pending){
        SettingsUi.MenuPanel source=pending.source.get();
        if(activity.isFinishing()||activity.isDestroyed()||source==null
                ||source.activity!=activity||source.dialog==null||!source.dialog.isShowing())return false;
        android.view.Window window=source.dialog.getWindow();
        return window!=null&&window.getDecorView().isAttachedToWindow();
    }
    private static void confirm(Activity activity,Pending pending,Map<String,Object> values){
        // Reading is asynchronous: a closed menu or destroyed Activity must not
        // regain an import prompt when the old read completes.
        if(!sourceStillOpen(activity,pending)){
            finish(activity,pending,"已取消导入");
            return;
        }
        progress(pending,"配置已读取，等待确认导入");
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(activity,pending.dark);
        panel.body.addView(SettingsUi.label(panel.context,"将导入 "+values.size()+" 项设置\n文件中未包含的设置和现有密钥保持不变",15,panel.foreground));
        if(Boolean.TRUE.equals(values.get("square_auto_like")))
            panel.body.addView(SettingsUi.label(panel.context,"此配置会开启自动点赞",14,panel.foreground));
        panel.body.addView(SettingsUi.label(panel.context,"当前不可用的配置项仅保存，不启用对应功能",13,panel.secondary));
        panel.builder.setNegativeButton("取消",(dialog,which)->{});
        panel.show("导入配置","确认导入");
        final boolean[] saving={false};
        panel.dialog.setOnDismissListener(dialog->{
            if(!saving[0])finish(activity,pending,"已取消导入");
        });
        panel.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            if(saving[0])return;
            saving[0]=true;
            progress(pending,"正在导入配置…");
            panel.dialog.dismiss();
            IO.execute(()->{
                boolean saved=false;
                String failure="配置保存失败，请重新导入";
                try{
                    SharedPreferences prefs=PluginPreferences.get(activity);
                    // Validate and persist against the same snapshot as other coordinated edits.
                    synchronized (prefs) {
                        saved=ConfigTransferRules.writeImport(values,prefs.getAll(),updates->
                                PreferenceChanges.write(prefs, updates));
                    }
                }catch(ConfigTransferRules.InvalidRangeException error){
                    failure="导入失败："+error.getMessage();
                }catch(Exception ignored){}
                final boolean ok=saved;
                final String errorMessage=failure;
                MAIN.post(()->{
                    SettingsUi.MenuPanel source=pending.source.get();
                    if (ok) {
                        if (source != null && sourceStillOpen(activity, pending)) source.refreshPreferenceViews();
                    }
                    finish(activity,pending,ok?"配置已导入\n部分功能重启 Soul 后生效":errorMessage);
                });
            });
        });
    }
    private static void toast(Activity activity,String message){
        if(!activity.isDestroyed())Toast.makeText(activity,message,Toast.LENGTH_LONG).show();
    }
}
