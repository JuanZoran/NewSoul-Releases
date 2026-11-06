package dev.local.newsoul;

import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Runs the production response hook; fake transport never performs I/O. */
public final class ChatGateControlsTest {
    static final Map<Executable, XposedInterface.Hooker> hooks = new HashMap<>();
    static final Set<Executable> deoptimized = new HashSet<>();
    static int checks, calls;
    public static class Url {
        final String value; Url(String value) { this.value=value; }
        public String toString() { return value; }
    }
    public static class Request { final Url url; Request(String path) { url=new Url(path); } public Url k(){return url;} }
    public static class Raw { final Request request; Raw(String path){request=new Request(path);} public Request t(){return request;} }
    public static class Result { Object body; Result(Object body){this.body=body;} public Object body(){return body;} }
    public static class Envelope { final Object data; Envelope(Object data){this.data=data;} public Object getData(){return data;} }
    public static class Model {
        boolean limit; int writes; boolean fail;
        Model(boolean limit){this.limit=limit;}
        public boolean isLimit(){return limit;}
        public void setLimit(boolean value){if(fail)throw new IllegalStateException("setter");limit=value;writes++;}
    }
    public static class Call { Result parseResponse(Raw raw){return null;} public Result execute(){return null;} }
    public interface HttpCall { Request request(); Raw execute(); }
    public static class Async { public void onResponse(Raw raw){} }
    public static class Config { public Boolean getShowPopup(){return null;} }
    public static class InvalidConfig { public String getShowPopup(){return null;} }
    public static class Dialog { public static class Companion { public void show(Config config){} } }
    static ClassLoader loader(boolean missing) {
        Map<String,Class<?>> types=Map.ofEntries(
            Map.entry("okhttp3.Call",HttpCall.class),Map.entry("okhttp3.v",Raw.class),Map.entry("okhttp3.t",Request.class),Map.entry("okhttp3.n",Url.class),
            Map.entry("retrofit2.Response",Result.class),Map.entry("retrofit2.OkHttpCall",Call.class),
            Map.entry("retrofit2.OkHttpCall$1",Async.class),Map.entry("cn.soulapp.android.net.HttpResult",Envelope.class),
            Map.entry("cn.soulapp.android.lib.common.bean.ChatLimitModel",Model.class),
            Map.entry("cn.soulapp.android.component.home.lovebell.bean.LoveBellProConfig",Config.class),
            Map.entry("cn.soulapp.android.component.home.lovebell.LoveBellProGiftDialog",Dialog.class));
        return new ClassLoader(){ public Class<?> loadClass(String name)throws ClassNotFoundException{
            if(missing && name.equals("retrofit2.OkHttpCall"))throw new ClassNotFoundException(name);
            return types.containsKey(name)?types.get(name):super.loadClass(name);
        }};
    }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    static void check(boolean pass,String reason){checks++;if(!pass)throw new AssertionError(reason);}
    static Object invoke(Method target,Object receiver,Object result,boolean fail,Object...args)throws Throwable{
        return hooks.get(target).intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{
            switch(m.getName()){
                case "proceed": calls++;if(fail)throw new IllegalStateException("original");return result;
                case "getArg": return args[(Integer)a[0]];
                case "getThisObject":return receiver;
                default:throw new AssertionError(m);
            }
        }));
    }
    public static void main(String[]args)throws Throwable{
        XposedModule module=new XposedModule(){};
        module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
            if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{
                if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}return b;
            });
            if(m.getName().equals("deoptimize")){deoptimized.add((Executable)a[0]);return true;}return null;
        }),()->{});
        ChatGateControls.install(module,loader(false));
        check(ChatGateControls.giftReady&&ChatGateControls.bellReady,"both hooks ready");
        check(hooks.size()==2,"only response and separate bell hook; old getters/dialog hooks removed");
        Method parse=Call.class.getDeclaredMethod("parseResponse",Raw.class);
        check(deoptimized.contains(parse)&&deoptimized.contains(Async.class.getMethod("onResponse",Raw.class)),"sync and async callers deoptimized");
        for(boolean enabled:new boolean[]{false,true})for(boolean original:new boolean[]{false,true}){
            ChatExperienceControls.gift=enabled; Model model=new Model(original);Result response=new Result(new Envelope(model));calls=0;
            Object actual=invoke(parse,new Call(),response,false,new Raw("https://api-a.soulapp.cn/chat/limitInfo?token=not-logged"));
            check(actual==response&&calls==1,"preserve decoded response and call original once");
            check(model.limit==(original&&!enabled)&&model.writes==(enabled?1:0),"clear limit when enabled; switch off untouched");
        }
        ChatExperienceControls.gift=true;
        for(String path:new String[]{"/chat/limit/friendly/check","/messages/send","/chat/limitInfoExtra","/chat/limitInfo/"}){
            Model model=new Model(true);Result response=new Result(new Envelope(model));
            invoke(parse,new Call(),response,false,new Raw("https://api-a.soulapp.cn"+path));
            check(model.limit&&model.writes==0,"unrelated responses untouched");
        }
        for(Object body:new Object[]{null,new Object(),new Envelope(null),new Envelope(new Object())}){
            Result response=new Result(body);check(invoke(parse,new Call(),response,false,new Raw("https://api-a.soulapp.cn/chat/limitInfo"))==response,"empty/error/foreign bodies unchanged");
        }
        check(invoke(parse,new Call(),null,false,new Raw("https://api-a.soulapp.cn/chat/limitInfo"))==null,"null response unchanged");
        Model faulty=new Model(true);faulty.fail=true;Result fault=new Result(new Envelope(faulty));calls=0;
        check(invoke(parse,new Call(),fault,false,new Raw("https://api-a.soulapp.cn/chat/limitInfo"))==fault&&calls==1,"postprocessing failure does not retry original");
        calls=0;
        try{invoke(parse,new Call(),null,true,new Raw("https://api-a.soulapp.cn/chat/limitInfo"));throw new AssertionError("swallowed original");}
        catch(IllegalStateException expected){check(calls==1,"original failure preserved");}
        Method popup=Config.class.getMethod("getShowPopup");
        for(boolean enabled:new boolean[]{false,true})for(Boolean original:new Boolean[]{null,false,true}){
            ChatExperienceControls.bell=enabled;check(Objects.equals(invoke(popup,new Config(),original,false),enabled?Boolean.FALSE:original),"bell independent and nullable when off");
        }
        hooks.clear();ChatGateControls.install(module,loader(true));
        check(!ChatGateControls.giftReady&&ChatGateControls.bellReady,"missing response ABI fails independently");
        ClassLoader original=loader(false);
        ClassLoader absent=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{
            if(name.endsWith("LoveBellProConfig"))throw new ClassNotFoundException(name);
            return original.loadClass(name);
        }};
        hooks.clear();ChatGateControls.install(module,absent);
        check(ChatGateControls.giftReady&&!ChatGateControls.bellReady&&!ChatGateControls.bellSupported,"absent bell clears readiness and preserves gift response");
        check(hooks.size()==1&&hooks.containsKey(parse),"absent bell installs no replacement dialog hook");
        ClassLoader invalid=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{
            if(name.endsWith("LoveBellProConfig"))return InvalidConfig.class;
            return original.loadClass(name);
        }};
        hooks.clear();ChatGateControls.install(module,invalid);
        check(ChatGateControls.giftReady&&!ChatGateControls.bellReady&&ChatGateControls.bellSupported,"existing changed bell ABI stays unavailable");
        System.out.println("ChatGateControls: "+checks+" production checks passed");
    }
}
