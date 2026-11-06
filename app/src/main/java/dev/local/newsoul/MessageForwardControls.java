package dev.local.newsoul;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Native recipient checkboxes; sends only a confirmed, one-shot batch from a marked picker. */
final class MessageForwardControls {
    static MessageForwardControls active;
    private static final String EXTRA = "dev.local.newsoul.message_forward_token";
    private Class<?> selector, confirmListener, choiceListener;
    private Constructor<?> confirm;
    private Method bindChoice, bindConfirm, account, createChat, setContent, setType, setExtras;
    private Method createMessage, getImManager, getChatManager, deliver, toggleRecipient;
    private HostMessageDecoration decoration;
    private boolean legacy;
    private Method selectedRecipients;
    private Field presenterRows;
    private String token;
    private MessageForwardRequest pending;
    private Dialog confirmation;
    private boolean ready;

    static MessageForwardControls install(XposedModule module, ClassLoader loader) {
        MessageForwardControls controls = new MessageForwardControls();
        try { controls.installHooks(module, loader); controls.ready=true; active=controls; DebugDiagnostics.registration("message.forward", null); }
        catch(Throwable error) {
            DebugDiagnostics.registration("message.forward", error);
            Log.e("SoulHook102", "message forward unavailable");
        }
        return controls;
    }
    boolean ready() { return ready; }
    private boolean marked(Activity activity) { return activity.getIntent().hasExtra(EXTRA); }
    private MessageForwardRequest request(Activity activity) throws Exception {
        return pending!=null && token!=null && token.equals(activity.getIntent().getStringExtra(EXTRA))
                && pending.live((String)account.invoke(null), SystemClock.elapsedRealtime()) ? pending : null;
    }
    void open(Activity activity, String own, String source, int type, Object body) throws Exception {
        if(!ready || activity.isFinishing() || activity.isDestroyed() || !own.equals(account.invoke(null)))
            throw new IllegalStateException("Forward unavailable");
        // Snapshot once at the menu click; each recipient receives another independent copy.
        Object snapshot = type == MessageForwardRequest.POST ? body : ChatRepeatControls.copyBody(type,body);
        pending=new MessageForwardRequest(own,source,type,snapshot,SystemClock.elapsedRealtime());
        token=UUID.randomUUID().toString();
        Intent intent=new Intent(activity,selector).putExtra(EXTRA,token)
                .putExtra("isChoice",false).putExtra("isFromPreview",true)
                .putExtra("onlyFinishAfterShare",true);
        try { activity.startActivity(intent); }
        catch(RuntimeException error) { pending=null;token=null;throw error; }
        DebugDiagnostics.event("message.forward.picker_open","type="+type);
    }

    private void installHooks(XposedModule module,ClassLoader loader)throws Exception {
        legacy = HostBindings.legacyChat(loader);
        selector=loader.loadClass("cn.soulapp.android.component.chat.SelectConversationActivity");
        Class<?> dialog=loader.loadClass(legacy ? "cn.soulapp.android.component.chat.dialog.s0" : "cn.soulapp.android.component.chat.dialog.j1");
        confirmListener=loader.loadClass("cn.soulapp.android.chat.listener.OnClickConfirmListener");
        confirm=dialog.getConstructor(Context.class,String.class); bindConfirm=dialog.getMethod("d",confirmListener);
        account=HostBindings.account(loader);
        Class<?> chat=loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage");
        Class<?> message=loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
        createChat=HostBindings.messageMethod(chat,"create",String.class);setContent=HostBindings.messageMethod(chat,"setMsgContent",Serializable.class);
        setType=HostBindings.messageMethod(chat,"setMsgType",int.class);setExtras=HostBindings.messageMethod(chat,"setTransExtMap",Map.class);
        decoration=new HostMessageDecoration(loader);
        createMessage=HostBindings.messageMethod(message,"createChatSendMsg",chat,String.class);
        Class<?> imManager=loader.loadClass(legacy ? "cn.soulapp.imlib.b" : "cn.soulapp.imlib.i0");
        getImManager=imManager.getMethod(legacy ? "t" : "v");getChatManager=imManager.getMethod(legacy ? "n" : "o");
        deliver=loader.loadClass("cn.soulapp.imlib.ChatManager").getMethod(legacy ? "b0" : "q0",message);
        Class<?> presenter=HostSymbols.recipientPresenter(loader);
        toggleRecipient=legacy ? presenter.getMethod("s1",int.class)
                : presenter.getMethod("g2",loader.loadClass("cn.soulapp.android.chat.bean.UserConversation"));
        selectedRecipients=presenter.getMethod(legacy ? "Z" : "m0");
        if (legacy) { presenterRows=presenter.getDeclaredField("p"); presenterRows.setAccessible(true); }
        Class<?> adapterClass=loader.loadClass("cn.soulapp.android.component.chat.adapter.ConversationAdapter");
        adapterClass.getMethod("k",boolean.class);
        choiceListener=loader.loadClass("com.jude.easyrecyclerview.adapter.RecyclerArrayAdapter$OnItemClickListener");
        bindChoice=adapterClass.getMethod("setOnItemClickListener",choiceListener);
        selector.getDeclaredField("b");selector.getDeclaredField("e");
        Method click=selector.getDeclaredMethod("F",int.class);
        module.hook(click).setId("new-soul/message-forward-select")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Activity activity=(Activity)chain.getThisObject();
                    if(!marked(activity))return chain.proceed();
                    try{toggle(activity,(int)chain.getArg(0));}catch(Throwable error){fail(activity,error);}
                    return null;
                });
        module.deoptimize(click);
        Method list=selector.getDeclaredMethod("loadConversationList",List.class,List.class,boolean.class);
        module.hook(list).setId("new-soul/message-forward-list")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Activity activity=(Activity)chain.getThisObject();
                    if(!marked(activity))return chain.proceed();
                    List<Object> filtered=new ArrayList<>();
                    try{
                        MessageForwardRequest request=request(activity);
                        if(request!=null && chain.getArg(1) instanceof List<?>) {
                            for(Object row:(List<?>)chain.getArg(1)) {
                                Object conversation=field(row,"conversation");
                                if(field(row,"user")!=null && conversation!=null && request.targetAllowed(
                                        (String)call(conversation,"getToUserId"),(int)call(conversation,"getChatType"))) filtered.add(row);
                            }
                        }
                    }catch(Throwable error){fail(activity,error);}
                    Object[] args=chain.getArgs().toArray();args[1]=filtered;
                    return chain.proceed(args);
                });
        module.deoptimize(list);
        Method init=selector.getDeclaredMethod("init",Bundle.class);
        module.hook(init).setId("new-soul/message-forward-ui")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();Activity activity=(Activity)chain.getThisObject();
                    if(marked(activity))try{
                        if(request(activity)==null){activity.finish();return result;}
                        Object adapter=field(activity,"b");adapter.getClass().getMethod("k",boolean.class).invoke(adapter,true);
                        // Own only this marked picker's callback; do not depend on native F being interceptable.
                        Object choices=Proxy.newProxyInstance(choiceListener.getClassLoader(),new Class<?>[]{choiceListener},(proxy,method,args)->{
                            if(method.getDeclaringClass()==Object.class){
                                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                                if(method.getName().equals("equals"))return proxy==args[0];return "NewSoulForwardRecipients";
                            }
                            if(method.getName().equals("onItemClick"))try{toggle(activity,(int)args[0]);}catch(Throwable error){fail(activity,error);}
                            return null;
                        });
                        bindChoice.invoke(adapter,choices);
                        for(String id:new String[]{"ic_user_follow","tv_mutily_choose","tv_share"}) {
                            View view=find(activity,id);if(view!=null)view.setVisibility(View.GONE);
                        }
                        View complete=(View)field(activity,"e");complete.setVisibility(View.VISIBLE);
                        complete.setOnClickListener(v->confirmSafely(activity));
                        updateSelection(activity);
                    }catch(Throwable error){fail(activity,error);activity.finish();}
                    return result;
                });
        module.deoptimize(init);
        // Every native completion path of this marked picker is redirected; ordinary Soul shares are untouched.
        for(Method method:new Method[]{selector.getDeclaredMethod("E",View.class),selector.getDeclaredMethod("S")}) {
            module.hook(method).setId("new-soul/message-forward-complete-"+method.getName())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        Activity activity=(Activity)chain.getThisObject();
                        if(!marked(activity))return chain.proceed();confirmSafely(activity);return null;
                    });
            module.deoptimize(method);
        }
        for(Method method:new Method[]{selector.getDeclaredMethod("D",Object.class),selector.getDeclaredMethod("onClick",View.class)}) {
            module.hook(method).setId("new-soul/message-forward-navigation-"+method.getName())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain->marked((Activity)chain.getThisObject())?null:chain.proceed());
            module.deoptimize(method);
        }
        module.hook(Activity.class.getDeclaredMethod("onDestroy")).setId("new-soul/message-forward-cleanup")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Activity activity=(Activity)chain.getThisObject();
                    if(selector.isInstance(activity) && marked(activity) && !activity.isChangingConfigurations()
                            && token!=null && token.equals(activity.getIntent().getStringExtra(EXTRA))) {pending=null;token=null;}
                    if(confirmation!=null && confirmation.getContext()==activity){confirmation.dismiss();confirmation=null;}
                    return chain.proceed();
                });
    }
    private Map<?,?> selected(Activity activity)throws Exception {return (Map<?,?>)selectedRecipients.invoke(field(activity,"presenter"));}
    private List<?> rows(Activity activity)throws Exception {return (List<?>)call(field(activity,"b"),"getAllData");}
    private void toggle(Activity activity,int index)throws Exception {
        MessageForwardRequest request=request(activity);
        if(request==null){toast(activity,"转发已过期，请重新选择消息");activity.finish();return;}
        List<?> rows=rows(activity);if(index<0 || index>=rows.size())return;
        Object row=rows.get(index),conversation=field(row,"conversation");if(conversation==null || field(row,"user")==null)return;
        String target=(String)call(conversation,"getToUserId");
        if(!request.targetAllowed(target,(int)call(conversation,"getChatType")))return;
        Map<?,?> selected=selected(activity);
        if(!selected.containsValue(target) && selected.size()>=MessageForwardRequest.MAX_TARGETS){toast(activity,"最多选择9人");return;}
        // Use the actual displayed row, never a filtered-list index into the presenter's unfiltered data.
        Object presenter = field(activity,"presenter");
        if (legacy) {
            // The visible list is filtered. Resolve identity in the presenter's source list before toggling.
            List<?> originals = (List<?>) presenterRows.get(presenter);
            int position = -1;
            for (int i=0;i<originals.size();i++) if (originals.get(i)==row) { position=i; break; }
            if (position<0) throw new IllegalStateException("Recipient list changed");
            toggleRecipient.invoke(presenter,position);
        } else toggleRecipient.invoke(presenter,row);
        call(field(activity,"b"),"notifyDataSetChanged");updateSelection(activity);
        DebugDiagnostics.event("message.forward.selection","count="+selected(activity).size());
    }
    private void updateSelection(Activity activity)throws Exception {
        int count=selected(activity).size();TextView complete=(TextView)field(activity,"e");
        complete.setText(count==0?"完成":"完成("+count+")");complete.setEnabled(count>0);complete.setAlpha(count>0?1f:0.5f);
    }
    private LinkedHashMap<String,String> recipients(Activity activity,MessageForwardRequest request)throws Exception {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        for(Object value:selected(activity).values()) {
            if(!(value instanceof String) || result.containsKey(value))throw new IllegalStateException("Invalid selection");
            String target=(String)value;Object matched=null;
            for(Object row:rows(activity)) {
                Object conversation=field(row,"conversation");
                if(conversation!=null && target.equals(call(conversation,"getToUserId"))
                        && request.targetAllowed(target,(int)call(conversation,"getChatType")) && field(row,"user")!=null){matched=row;break;}
            }
            if(matched==null)throw new IllegalStateException("Recipient changed");
            Object user=field(matched,"user");String name=(String)field(user,"alias");
            if(name==null || name.isEmpty())name=(String)field(user,"signature");
            result.put(target,name==null || name.isEmpty()?"所选用户":name);
        }
        return result;
    }
    private void confirmSafely(Activity activity){try{confirmBatch(activity);}catch(Throwable error){fail(activity,error);}}
    private void confirmBatch(Activity activity)throws Exception {
        if(activity.isFinishing() || activity.isDestroyed() || (confirmation!=null && confirmation.isShowing()))return;
        MessageForwardRequest request=request(activity);
        if(request==null){toast(activity,"转发已过期，请重新选择消息");activity.finish();return;}
        LinkedHashMap<String,String> recipients=recipients(activity,request);
        if(recipients.isEmpty() || recipients.size()>MessageForwardRequest.MAX_TARGETS)return;
        List<String> targets=new ArrayList<>(recipients.keySet());String requestToken=token;
        String kind=request.type==1?"文字消息":request.type==5?"语音":request.type==4?"视频":request.type==MessageForwardRequest.POST?"帖子":"照片";
        String verb=request.type==MessageForwardRequest.POST?"分享":"转发";
        Dialog dialog=(Dialog)confirm.newInstance(activity,"将这条"+kind+verb+"给以下 "+targets.size()+" 人？\n\n"+String.join("、",recipients.values()));
        Object listener=Proxy.newProxyInstance(confirmListener.getClassLoader(),new Class<?>[]{confirmListener},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class){
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];return "NewSoulMessageForwardConfirm";
            }
            if(method.getName().equals("onClickConfirm")) {
                int submitted=0;
                try{
                    if(activity.isFinishing() || activity.isDestroyed() || !requestToken.equals(token)
                            || request(activity)!=request || !recipients(activity,request).keySet().equals(recipients.keySet()))
                        throw new IllegalStateException("Selection changed");
                    List<String> claimed=request.claim((String)account.invoke(null),targets,SystemClock.elapsedRealtime());
                    if(claimed==null)throw new IllegalStateException("Expired confirmation");
                    for(String target:claimed){
                        if(!request.account.equals(account.invoke(null)))throw new IllegalStateException("Account changed");
                        send(request,target);submitted++;
                    }
                    DebugDiagnostics.event("message.forward.dispatched","type="+request.type+"; targets="+submitted);
                    toast(activity,"已提交转发给 "+submitted+" 人，请在目标会话查看");
                }catch(Throwable error){
                    DebugDiagnostics.failure("message.forward.failed","submitted="+submitted+"; total="+targets.size(),error);
                    toast(activity,"转发中断，已提交 "+submitted+"/"+targets.size()+"，请先核对目标会话");
                }finally{dialog.dismiss();activity.finish();}
            }
            return null;
        });
        bindConfirm.invoke(dialog,listener);confirmation=dialog;
        dialog.setOnDismissListener(d->{if(confirmation==dialog)confirmation=null;});dialog.show();
        DebugDiagnostics.event("message.forward.confirmation","type="+request.type+"; targets="+targets.size());
    }
    private void send(MessageForwardRequest request,String target)throws Exception {
        if (request.type == MessageForwardRequest.POST) {
            ((PostSharePayload) request.body).send(target);
            return;
        }
        Object chat=createChat.invoke(null,target);
        setContent.invoke(chat,ChatRepeatControls.copyBody(request.type,request.body));
        setExtras.invoke(chat,new java.util.HashMap<>());setType.invoke(chat,request.type);decoration.apply(chat);
        Object message=createMessage.invoke(null,chat,target);
        deliver.invoke(getChatManager.invoke(getImManager.invoke(null)),message);
    }
    private static View find(Activity activity,String id){int r=activity.getResources().getIdentifier(id,"id",activity.getPackageName());return r==0?null:activity.findViewById(r);}
    private static void fail(Activity activity,Throwable error){DebugDiagnostics.failure("message.forward.failed",error);toast(activity,"转发未完成，请重新选择消息");}
    private static void toast(Context context,String text){Toast.makeText(context,text,Toast.LENGTH_SHORT).show();}
    private static Object call(Object value,String name)throws Exception{return HostBindings.messageMethod(value.getClass(),name).invoke(value);}
    private static Object field(Object value,String name)throws Exception {
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(value);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
