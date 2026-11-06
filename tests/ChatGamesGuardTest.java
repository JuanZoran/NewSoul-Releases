package dev.local.newsoul;
import android.app.Activity;import android.os.Handler;import cn.soulapp.imlib.*;import java.lang.reflect.*;import java.util.*;import io.github.libxposed.api.*;
public class ChatGamesGuardTest {
 static int checks,sends;static final Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static class User{public String userIdEcpt="encrypted-peer";}
 public static class Fragment extends androidx.fragment.app.Fragment{public androidx.fragment.app.FragmentActivity activity;public String L="peer";public Sender Q;public User O=new User();public Conversation conversation=new Conversation();public boolean resumed=true;public androidx.fragment.app.FragmentActivity getActivity(){return activity;}public boolean isResumed(){return resumed;}}
 public static class Sender{public Fragment a;public void w0(){sends++;}public void h1(){sends++;}}
 public static class Dice{public int number;public Dice(int n){number=n;}}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
 static Object invoke(Method m,Object self,Object...args)throws Throwable {
  var hook=hooks.get(m);if(hook==null)return m.invoke(self,args);
  return hook.intercept(proxy(XposedInterface.Chain.class,(p,call,a)->switch(call.getName()){
   case "getThisObject"->self;case "getArg"->args[(int)a[0]];case "proceed"->m.invoke(self,a==null||a.length==0?args:(Object[])a[0]);default->throw new AssertionError(call);
  }));
 }
 static Fragment fresh(){j0.own="own";Fragment f=new Fragment();f.activity=new androidx.fragment.app.FragmentActivity();f.activity.fragment=f;f.Q=new Sender();f.Q.a=f;return f;}
 static void open(Fragment f)throws Throwable{invoke(Sender.class.getMethod("w0"),f.Q);Handler.drain();check(GamePicker.selection!=null,"picker opens");}
 interface Change{void apply(Fragment f);}
 static void cancelled(Change change,String label)throws Throwable{
  Fragment f=fresh();int before=sends;GamePicker.selection=null;open(f);change.apply(f);GamePicker.selection.accept(2);check(sends==before,label);
 }
 public static void main(String[] args)throws Throwable{
  XposedModule module=new XposedModule(){};module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}return b;});
   if(m.getName().equals("getInvoker"))return proxy(m.getReturnType(),(b,bm,ba)->{if(bm.getName().equals("invoke"))return invoke((Method)a[0],ba[0],(Object[])ba[1]);return b;});
   if(m.getName().equals("deoptimize"))return true;return null;
  }),()->{});
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){case "cn.soulapp.imlib.msg.chat.DiceFingerMsg"->Dice.class;case "cn.soulapp.android.component.chat.ConversationActivity"->Activity.class;case "cn.soulapp.android.component.chat.utils.y4"->Sender.class;case "cn.soulapp.android.component.chat.fragment.ConversationFragment"->Fragment.class;case "cn.soulapp.android.client.component.middle.platform.model.api.user.ImUserBean"->User.class;default->super.loadClass(n);};}};
  Activity.hostLoader=loader;ChatGames.install(module,loader);check(ChatGames.diceReady&&ChatGames.fingerReady,"hooks ready");
  Fragment f=fresh();open(f);GamePicker.selection.accept(2);check(sends==1,"unchanged identity sends exactly once through native hook");
  cancelled(x->j0.own="other","account switch cancelled");
  cancelled(x->x.L="other","fragment recipient switch cancelled");
  cancelled(x->x.conversation.peer="other","conversation recipient switch cancelled");
  cancelled(x->x.conversation.session="other","session changed cancelled");
  cancelled(x->x.conversation=new Conversation(),"conversation replacement cancelled even same ID");
  cancelled(x->x.Q=new Sender(),"sender replacement cancelled");
  cancelled(x->x.Q.a=fresh(),"sender owner changed cancelled");
  cancelled(x->x.O.userIdEcpt="other","encrypted recipient changed cancelled");
  cancelled(x->x.O=new User(),"user component replacement cancelled");
  cancelled(x->x.activity.fragment=fresh(),"active fragment replacement cancelled");
  cancelled(x->x.resumed=false,"paused fragment cancelled");
  cancelled(x->x.activity.destroyed=true,"destroyed activity cancelled");
  cancelled(x->x.activity.finishing=true,"finishing activity cancelled");
  cancelled(x->x.conversation.type=1,"group chat replacement cancelled");
  f=fresh();int opens=GamePicker.opens;invoke(Sender.class.getMethod("w0"),f.Q);f.resumed=false;Handler.drain();check(GamePicker.opens==opens,"identity change before queued picker prevents display");
  f=fresh();int before=sends;ChatGames.preview(f.activity,true);check(GamePicker.preview,"settings preview remains dry-run");GamePicker.selection.accept(1);check(before==sends,"preview never sends");
  SettingsUi.values.put("game_dice",false);f=fresh();invoke(Sender.class.getMethod("w0"),f.Q);check(sends==before+1,"disabled custom picker preserves native send");
  System.out.println("Chat games: "+checks+" production guard/hook checks passed");
 }
}
