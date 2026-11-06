package dev.local.newsoul;

import android.content.Context;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.*;

/** Executes production hook callbacks against inert hosts; no phone/network receipt is sent. */
public final class AutoReadControlsTest {
    static final Map<Executable,XposedInterface.Hooker> hooks = new HashMap<>();
    static int checks, sent, originalDispatch;
    static String own="me";
    static boolean failSend, missingConversation;
    public static class Access {
        public static Context context(){return new Context();}
        public static String g(){return own;}
    }
    public static class Payload {
        int kind=1, snap; String flag="0"; Object body=new cn.soulapp.imlib.msg.chat.TextMsg();
        public int getMsgType(){return kind;}
        public int getSnapChat(){return snap;}
        public String getStringTransExt(String key){return flag;}
        public Object getMsgContent(){return body;}
    }
    public static class Message {
        String sender="peer", target="me", mid=UUID.randomUUID().toString();
        int envelope=1, ack, unread=7, status=2; boolean anonymous;
        Payload payload=new Payload();
        public String getFrom(){return sender;} public String getTo(){return target;}
        public String getMsgId(){return mid;} public int getMsgType(){return envelope;}
        public Payload getChatMessage(){return payload;}
        public boolean isAnonymityChat(){return anonymous;} public int getIsAck(){return ack;}
    }
    public static class Conversation {
        public void ackMsgRead(Message value){
            if(failSend)throw new IllegalStateException("test send failure");
            sent++;
        }
    }
    public static class Manager {
        public static Manager M(){return new Manager();}
        public Conversation F(String peer){return missingConversation?null:new Conversation();}
    }
    public static class Dispatch implements Runnable {
        private List<Message> incoming;
        Dispatch(Message... items){incoming=Arrays.asList(items);}
        public void run(){originalDispatch++;}
    }
    public static class Ambiguous { List<?> a,b; }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Object call(Method m,Object receiver,Object... args)throws Throwable {
        XposedInterface.Chain chain=proxy(XposedInterface.Chain.class,(p,method,a)->{
            switch(method.getName()) {
                case "getThisObject":return receiver;
                case "getArg":return args[(int)a[0]];
                case "proceed":try{return m.invoke(receiver,args);}catch(InvocationTargetException e){throw e.getCause();}
                default:throw new AssertionError(method);
            }
        });
        return hooks.get(m).intercept(chain);
    }
    static void deliver(Message... messages)throws Throwable {call(Dispatch.class.getMethod("run"),new Dispatch(messages));}
    public static void main(String[] args)throws Throwable {
        XposedModule module=new XposedModule(){};
        module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
            if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{
                if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}
                return b;
            });
            if(m.getName().equals("deoptimize"))return true;
            return null;
        }),()->{});
        ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException {
            return switch(n){
                case "cn.soulapp.imlib.msg.ImMessage"->Message.class;
                case "cn.soulapp.imlib.msg.chat.ChatMessage"->Payload.class;
                case "cn.soulapp.imlib.ChatManager"->Manager.class;
                case "cn.soulapp.imlib.Conversation"->Conversation.class;
                case "cn.soulapp.imlib.j0"->Access.class;
                default->super.loadClass(n);
            };
        }};
        AutoReadControls.install(module,loader);
        check(AutoReadControls.ready && hooks.size()==2,"hooks installed");
        Message first=new Message();deliver(first);check(sent==0 && originalDispatch==1,"off preserves dispatch");
        SettingsUi.enabled=true;deliver(first);check(sent==1,"on sends");deliver(first);check(sent==1,"duplicate suppressed");
        check(first.unread==7 && first.status==2 && first.ack==0,"local read state untouched");
        SettingsUi.enabled=false;deliver(new Message());check(sent==1,"live disable");SettingsUi.enabled=true;
        for(int n=0;n<12;n++) {
            Message m=new Message();
            switch(n){
                case 0:m.sender=own;break; case 1:m.target="other-account";break;
                case 2:m.envelope=2;break; case 3:m.anonymous=true;break;
                case 4:m.payload.snap=1;break; case 5:m.payload.flag="true";break;
                case 6:m.ack=1;break;case 7:m.mid="";break;
                case 8:m.payload.kind=25;break;case 9:m.payload.kind=9;break;
                case 10:m.payload=null;break;case 11:m.payload.body=null;break;
            }
            int before=sent;deliver(m);check(sent==before,"filtered "+n);
        }
        for(int type:new int[]{1,2,3,4,5,8}) {
            String c=switch(type){case 1->"TextMsg";case 2->"ImgMsg";case 3->"ImgMsgs";case 4->"VideoMsg";case 5->"AudioMsg";default->"ExpressionMsg";};
            Message m=new Message();m.payload.kind=type;m.payload.body=Class.forName("cn.soulapp.imlib.msg.chat."+c).getConstructor().newInstance();
            int before=sent;deliver(m);check(sent==before+1,"body type "+type);
        }
        Message retry=new Message();failSend=true;int before=sent;deliver(retry);check(sent==before,"failure contained");
        failSend=false;deliver(retry);check(sent==before+1,"sync failure releases dedup");
        Message missing=new Message();missingConversation=true;before=sent;deliver(missing);check(sent==before,"missing session");
        missingConversation=false;deliver(missing);check(sent==before+1,"session recovered");
        Message nativeRead=new Message();before=sent;
        call(Conversation.class.getMethod("ackMsgRead",Message.class),new Conversation(),nativeRead);
        deliver(nativeRead);check(sent==before+1,"normal receipt observed without automatic duplicate");
        SettingsUi.blockReceipt=true;before=sent;Message blocked=new Message();deliver(blocked);
        check(sent==before,"receipt block takes precedence over automatic read");
        SettingsUi.blockReceipt=false;deliver(blocked);check(sent==before+1,"disable block restores automatic read without losing message");
        Message later=new Message();before=sent;deliver(null,later);check(sent==before+1,"bad item does not stop batch");
        own="other-account";before=sent;deliver(new Message());check(sent==before,"account change rejects old batch");own="me";
        try{AutoReadControls.batchField(Ambiguous.class);throw new AssertionError();}catch(IllegalStateException expected){checks++;}
        AutoReadRules.Recent cache=new AutoReadRules.Recent(2,100);
        AutoReadRules.Key a=new AutoReadRules.Key("me","p","1"),b=new AutoReadRules.Key("me","p","2"),c=new AutoReadRules.Key("me2","p","1");
        check(cache.claim(a,10)&&!cache.claim(a,11),"reserve");check(cache.claim(c,11),"account isolation");
        check(cache.claim(b,12)&&cache.claim(a,13),"bounded eviction");check(cache.claim(a,114),"ttl retry");
        cache.forget(a);check(cache.claim(a,115),"forget");
        System.out.println("AutoReadControls: "+checks+" checks passed; no network");
    }
}
