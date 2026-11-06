package dev.local.newsoul;
import android.content.Context;import java.lang.reflect.*;import java.util.*;import io.github.libxposed.api.*;
public class InteractionPrivacyControlsTest {
 static Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();static int sends,popups,checks;
 public static class Access{public static Context context(){return new Context();}}
 public static class Payload{int type=1;public int getMsgType(){return type;}}
 public static class Trans{boolean broken;public String messageType="ONLINE_STATE";String params="{\"msgType\":1,\"msgState\":0}";public String getParamJson(){if(broken)throw new IllegalStateException("host payload unavailable");return params;}}
 public static class Message{Trans trans;String to="peer";public Trans getTransCmd(){return trans;}public String getTo(){return to;}int type=1;Payload payload=new Payload();public int getMsgType(){return type;}public Payload getChatMessage(){return payload;}}
 public static class Manager{public void p0(Message m,boolean retry){sends++;}public void o0(Message m){p0(m,false);}}
 public static class Album{int photos=9,videos=1;public int getMaxSelectNum(){return photos;}public int getMaxVideoSelectNum(){return videos;}}
 public static class Result{}
 public static class Invite{public void a(Result r){popups++;}}
 static <T>T proxy(Class<T> c,InvocationHandler h){return c.cast(Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h));}
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Object call(Method m,Object receiver,Object...args)throws Throwable{return hooks.get(m).intercept(proxy(XposedInterface.Chain.class,(p,n,a)->switch(n.getName()){
  case "getThisObject"->receiver;case "getArg"->args[(int)a[0]];case "proceed"->m.invoke(receiver,args);default->throw new AssertionError(n);
 }));}
 public static void main(String[] args)throws Throwable{
  XposedModule module=new XposedModule(){};module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}return b;});
   if(m.getName().equals("deoptimize"))return true;return null;
  }),()->{});
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{return switch(name){
   case "cn.soulapp.imlib.msg.ImMessage"->Message.class;case "cn.soulapp.imlib.msg.chat.ChatMessage"->Payload.class;
   case "cn.soulapp.imlib.msg.transcmd.TransCmdMsg"->Trans.class;
   case "cn.soulapp.imlib.ChatManager"->Manager.class;case "cn.soulapp.android.lib.photopicker.manager.AlbumConfig"->Album.class;
   case "cn.soulapp.android.component.chat.business.g$a"->Invite.class;case "com.soulapp.soulgift.bean.CommonDataBo"->Result.class;default->super.loadClass(name);
  };}};
  InteractionPrivacyControls.install(module,loader);
  check(hooks.size()==4&&InteractionPrivacyControls.profileReady&&InteractionPrivacyControls.commandReady&&InteractionPrivacyControls.inviteReady&&InteractionPrivacyControls.photosReady&&InteractionPrivacyControls.videosReady,"all independent hooks installed");
  Method send=Manager.class.getMethod("p0",Message.class,boolean.class);Manager manager=new Manager();Message message=new Message();message.payload.type=25;
  call(send,manager,message,false);check(sends==1,"off preserves receipts");
  SettingsUi.values.put(InteractionPrivacyControls.RECEIPT,true);call(send,manager,message,false);check(sends==1,"on blocks receipt");
  for(int t:new int[]{1,5,22,23}){message.payload.type=t;call(send,manager,message,false);}check(sends==5,"ordinary typing voice untouched");
  for(int t:new int[]{2,5,6,7}){message.type=t;message.payload.type=25;call(send,manager,message,false);}check(sends==9,"other envelopes including all trans commands untouched");
  message.type=1;message.payload=null;call(send,manager,message,false);check(sends==10,"missing payload passes through");
  SettingsUi.values.put(InteractionPrivacyControls.RECEIPT,false);message.payload=new Payload();message.payload.type=25;call(send,manager,message,false);check(sends==11,"live off restores receipts");
  message.type=5;message.trans=new Trans();
  int before=sends;
  call(send,manager,message,false);check(sends==before+1,"profile default off sends start");
  SettingsUi.values.put(InteractionPrivacyControls.PROFILE,true);
  message.trans.params="{\"msgType\":1,\"msgState\":1}";
  call(send,manager,message,false);check(sends==before+2,"enabling mid-visit permits earlier start cleanup");
  call(send,manager,message,false);check(sends==before+2,"unmatched end blocked");
  message.trans.params="{\"msgType\":1,\"msgState\":0}";
  call(send,manager,message,false);check(sends==before+2,"profile start blocked");
  message.trans.params="{\"msgType\":1,\"msgState\":1}";
  call(send,manager,message,false);check(sends==before+2,"suppressed visit end blocked too");
  for(String cmd:new String[]{"CHAT_ONLINE_CALL_MUTED","chat_magic_card","ONLINE_CALL_GIFT","ONLINE_STATE_OTHER"}) {
   message.trans.messageType=cmd;int count=sends;call(send,manager,message,false);check(sends==count+1,"unrelated command passes: "+cmd);
  }
  message.trans.messageType="ONLINE_STATE";
  for(String json:new String[]{null,"","bad","{}","{\"msgType\":1}","{\"msgType\":2,\"msgState\":0}","{\"msgType\":0,\"msgState\":0}","{\"msgType\":1,\"msgState\":2}","{\"msgType\":\"1\",\"msgState\":0}"}) {
   message.trans.params=json;int count=sends;call(send,manager,message,false);check(sends==count+1,"unknown and other states pass: "+json);
  }
  message.trans.broken=true;before=sends;call(send,manager,message,false);check(sends==before+1,"host payload exception fails open");
  message.trans.broken=false;
  message.trans=null;before=sends;call(send,manager,message,false);check(sends==before+1,"missing trans payload passes");
  message.trans=new Trans();SettingsUi.values.put(InteractionPrivacyControls.PROFILE,false);
  before=sends;call(send,manager,message,false);check(sends==before+1,"profile live off restores start");
  SettingsUi.values.put(InteractionPrivacyControls.PROFILE,true);message.to="other-peer";
  message.trans.params="{\"msgType\":1,\"msgState\":1}";
  call(send,manager,message,false);check(sends==before+1,"cleanup exception scoped to original target");
  message.to="peer";call(send,manager,message,false);check(sends==before+2,"correct peer receives pending cleanup");
  message.type=1;message.payload.type=25;SettingsUi.values.put(InteractionPrivacyControls.RECEIPT,true);
  call(send,manager,message,false);check(sends==before+2,"receipt block independent when profile enabled");
  message.payload.type=1;call(send,manager,message,false);check(sends==before+3,"ordinary chat still sends with both enabled");
  Album album=new Album();Method photos=Album.class.getMethod("getMaxSelectNum"),videos=Album.class.getMethod("getMaxVideoSelectNum");
  check((int)call(photos,album)==9&&(int)call(videos,album)==1,"picker unchanged by default");
  SettingsUi.values.put(InteractionPrivacyControls.PHOTOS,true);check((int)call(photos,album)==99&&(int)call(videos,album)==1,"photo switch independent");
  SettingsUi.values.put(InteractionPrivacyControls.VIDEOS,true);check((int)call(videos,album)==99,"video switch works");
  album.photos=150;check((int)call(photos,album)==150,"higher native limit preserved");
  SettingsUi.values.put(InteractionPrivacyControls.PHOTOS,false);album.photos=9;check((int)call(photos,album)==9,"photo switch off restores current host value");
  Method invite=Invite.class.getMethod("a",Result.class);call(invite,new Invite(),new Result());check(popups==1,"invite off preserves popup callback");
  SettingsUi.values.put(InteractionPrivacyControls.INVITE,true);call(invite,new Invite(),new Result());check(popups==1,"invite on skips only callback");
  SettingsUi.values.put(InteractionPrivacyControls.INVITE,false);call(invite,new Invite(),new Result());check(popups==2,"invite live off restores callback");
  check(InteractionPrivacyControls.inviteSupported,"modern route supported");
  ClassLoader absentLoader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{
   if(name.equals(HostBindings.FRAGMENT))return cn.soulapp.android.component.chat.fragment.ConversationFragment.class;
   if(name.equals("cn.soulapp.android.component.chat.business.g$a")||name.equals("com.soulapp.soulgift.api.IActivityOpenApi"))throw new ClassNotFoundException(name);
   return loader.loadClass(name);
  }};
  DebugDiagnostics.events.clear();InteractionPrivacyControls.install(module,absentLoader);
  check(!InteractionPrivacyControls.inviteSupported&&!InteractionPrivacyControls.inviteReady,"legacy missing callback and request classified absent");
  check(DebugDiagnostics.events.stream().noneMatch(e->e.startsWith("interaction.invite:")),"verified absent invite does not log registration error");
  check(InteractionPrivacyControls.commandReady&&InteractionPrivacyControls.photosReady&&InteractionPrivacyControls.videosReady,"absent invite preserves independent controls");
  ClassLoader unknownLegacy=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{
   if(name.equals("com.soulapp.soulgift.api.IActivityOpenApi"))return Object.class;
   return absentLoader.loadClass(name);
  }};
  InteractionPrivacyControls.install(module,unknownLegacy);
  check(InteractionPrivacyControls.inviteSupported&&!InteractionPrivacyControls.inviteReady,"unknown legacy request schema stays unavailable");
  ClassLoader missingModern=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{
   if(name.equals("cn.soulapp.android.component.chat.business.g$a"))throw new ClassNotFoundException(name);
   if(name.equals(HostBindings.FRAGMENT))return Object.class;
   return loader.loadClass(name);
  }};
  InteractionPrivacyControls.install(module,missingModern);
  check(InteractionPrivacyControls.inviteSupported&&!InteractionPrivacyControls.inviteReady,"modern missing callback stays unavailable");
  System.out.println("Interaction hooks: "+checks+" production callback checks passed; no network");
 }
}
