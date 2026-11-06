package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One explicit test per model row; no result or credential is persisted. */
final class AiKeyTestControl implements View.OnAttachStateChangeListener {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2);
    private static final Set<AiKeyTestControl> ATTACHED=
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Activity activity;
    private final Context context;
    private final View row;
    private final AiModelConfig model;
    private final Runnable changed;
    private final SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(prefs,key)->{
        if(Looper.myLooper()==Looper.getMainLooper()) configurationChanged();
        else MAIN.post(this::configurationChanged);
    };
    private String fingerprint;
    private long revision;
    private boolean connected;
    private AiReplyClient.Cancellation cancellation;
    private AiApiKeyProbe.Result result;

    AiKeyTestControl(Activity activity,View row,AiModelConfig model,Runnable changed) {
        this.activity=activity;this.context=activity.getApplicationContext();
        this.row=row;this.model=model;this.changed=changed;
        preferences=PluginPreferences.get(context);
        fingerprint=fingerprint();
        row.addOnAttachStateChangeListener(this);
        if(row.isAttachedToWindow())onViewAttachedToWindow(row);
    }

    boolean running(){return cancellation!=null;}
    boolean passed(){return result!=null&&result.passed();}
    boolean failed(){return result!=null&&!result.passed();}
    String status(){
        if(running())return "正在测试";
        if(result!=null)return result.message;
        if (model.isCustom()) return !AiReplyConfig.ready(context,model) ? "接口未配置"
                : AiReplyConfig.hasKey(context,model) ? "接口与密钥已配置" : "接口已配置 · 无密钥";
        return AiReplyConfig.hasKey(context,model)?"密钥已配置":"密钥未配置";
    }

    /** Also called by the existing configuration-import and key-editor refresh paths. */
    void refresh(){
        String current=fingerprint();
        if(!java.util.Objects.equals(current,fingerprint)) {
            cancel();result=null;fingerprint=current;
        }
    }

    void start(){
        refresh();
        if(running()||!usable()||!AiReplyConfig.ready(context,model))return;
        if(fingerprint==null){
            result=unavailable();changed.run();return;
        }
        final String expected=fingerprint;
        final long request=++revision;
        final AiReplyClient.Cancellation token=new AiReplyClient.Cancellation();
        cancellation=token;result=null;changed.run();
        WORK.execute(()->{
            AiApiKeyProbe.Result answer;
            try {
                token.check();
                if(!expected.equals(AiReplyConfig.credentialFingerprint(context,model))){token.cancel();return;}
                AiModelConfig current=AiReplyConfig.resolveModel(context,model);
                String key=AiReplyConfig.readKey(context,current);
                token.check();
                if(!expected.equals(AiReplyConfig.credentialFingerprint(context,model))){token.cancel();return;}
                answer=AiApiKeyProbe.run(current,key,token);
            } catch(Exception error) {
                answer=unavailable();
            } finally {
                // A revision mismatch or cancelled worker must still release its busy state.
                MAIN.post(()->{
                    if(request==revision&&cancellation==token&&token.isCancelled()) {
                        cancel();changed.run();
                    }
                });
            }
            AiApiKeyProbe.Result completed=answer;
            MAIN.post(()->{
                if(request!=revision||cancellation!=token)return;
                cancellation=null;
                if(!usable()||token.isCancelled()||!expected.equals(fingerprint())) {
                    result=null;refresh();changed.run();return;
                }
                result=completed.status==AiApiKeyProbe.Status.CANCELLED?null:completed;
                changed.run();
            });
        });
    }

    private AiApiKeyProbe.Result unavailable(){
        return new AiApiKeyProbe.Result(AiApiKeyProbe.Status.UNCONFIRMED,
                "密钥读取失败，请重新配置",0,"");
    }

    private String fingerprint(){
        try{return AiReplyConfig.credentialFingerprint(context,model);}
        catch(Exception ignored){return null;}
    }

    private void configurationChanged(){
        if(!connected)return;
        refresh();changed.run();
    }

    private boolean usable(){
        return connected&&row.isAttachedToWindow()&&row.isShown()
                &&!activity.isFinishing()&&!activity.isDestroyed();
    }

    private void cancel(){
        revision++;
        if(cancellation!=null)cancellation.cancel();
        cancellation=null;
    }

    public void onViewAttachedToWindow(View view){
        if(connected)return;
        connected=true;ATTACHED.add(this);
        preferences.registerOnSharedPreferenceChangeListener(listener);
        configurationChanged();
    }

    public void onViewDetachedFromWindow(View view){
        if(!connected)return;
        connected=false;ATTACHED.remove(this);
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        cancel();result=null;
    }

    static void cancelFor(Activity activity){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            MAIN.post(()->cancelFor(activity));return;
        }
        for(AiKeyTestControl control:new ArrayList<>(ATTACHED)){
            if(control.activity==activity&&control.running()){
                control.cancel();control.changed.run();
            }
        }
    }
}
