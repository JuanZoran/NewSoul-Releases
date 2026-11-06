package dev.local.newsoul;
import android.app.Activity;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;
import cn.soulapp.imlib.msg.chat.*;

/** Calls production renderer fallback with the original menu hook deliberately absent. */
public class ChatMenuFallbackTest {
 static int checks; static String account="self";
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static String account(){return account;}
 public static class Item {final int id; public Item(String t,int i,int id){this.id=id;}public int b(){return id;}}
 public static class Fragment {
  public String L="peer"; public boolean resumed=true; public Activity activity=new Activity();
  public Activity getActivity(){return activity;} public android.content.Context getContext(){return activity;}
  public boolean isResumed(){return resumed;}
 }
 public static class Chat extends ChatMessage {
  public int snap;public String flag;public int getSnapChat(){return snap;}public String getStringTransExt(String n){return flag;}
 }
 public static class Message {
  public String from="peer",to="self";public boolean anonymous;public Chat chat=new Chat();
  public Message(){chat.type=1;chat.body=new TextMsg("test");}
  public String getFrom(){return from;}public String getTo(){return to;}
  public int getMsgType(){return 1;}public Chat getChatMessage(){return chat;}public boolean isAnonymityChat(){return anonymous;}
 }
 static void set(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static Object invoke(ChatRepeatControls c,List<?> list)throws Exception {
  Method m=ChatRepeatControls.class.getDeclaredMethod("rendererEntries",Object.class);m.setAccessible(true);return m.invoke(c,list);
 }
 static Object render(ChatRepeatControls c,Fragment f,Message m,View v,List<?> list)throws Exception {
  try(ChatMenuContext ignored=ChatMenuContext.enter(f,m,v)){return invoke(c,list);}
 }
 public static void main(String[] args)throws Exception {
  ChatRepeatControls c=new ChatRepeatControls();
  set(c,"peerField",Fragment.class.getField("L"));ChatRepeatControls.ready=true;
  set(c,"item",Item.class.getConstructor(String.class,int.class,int.class));set(c,"itemId",Item.class.getMethod("b"));
  set(c,"account",ChatMenuFallbackTest.class.getMethod("account"));set(c,"forward",new MessageForwardControls());
  Fragment f=new Fragment();Message m=new Message();View v=new View();List<Item> original=List.of(new Item("host",1,1));
  check(invoke(c,original)==original,"no context passes through");
  DebugDiagnostics.enabled=false;
  List<?> added=(List<?>)render(c,f,m,v,original);
  check(added.size()==2&&original.size()==1,"fallback works with diagnostics off and immutable host list");
  Field selections=ChatRepeatControls.class.getDeclaredField("selections");selections.setAccessible(true);
  Object selection=((Map<?,?>)selections.get(c)).get(added.get(1));
  Field msg=selection.getClass().getDeclaredField("message");msg.setAccessible(true);
  check(msg.get(selection)==m,"click selection bound to exact message");
  check(render(c,f,m,v,added)==added,"existing action not duplicated");
  try(ChatMenuContext ignored=ChatMenuContext.enter(f,m,v)){
   check(((List<?>)invoke(c,original)).size()==2,"first renderer adds");check(invoke(c,original)==original,"second renderer cannot reuse context");
  }
  check(invoke(c,original)==original,"scope expires after return");
  Message second=new Message();
  try(ChatMenuContext outer=ChatMenuContext.enter(f,m,v)){
   try(ChatMenuContext inner=ChatMenuContext.enter(f,second,v)){check(ChatMenuContext.takeRenderer().message==second,"nested context isolated");}
   check(ChatMenuContext.takeRenderer().message==m,"outer restored");
   TestWorkers.await(TestWorkers.start(()->check(ChatMenuContext.takeRenderer()==null,"thread isolation")));
  }
  try(ChatMenuContext ignored=ChatMenuContext.enter(f,m,v)){throw new IllegalStateException();}catch(IllegalStateException expected){}
  check(ChatMenuContext.takeRenderer()==null,"exception cleanup");
  m.to="other";check(render(c,f,m,v,original)==original,"wrong chat rejected");m.to="self";
  m.anonymous=true;check(render(c,f,m,v,original)==original,"anonymous rejected");m.anonymous=false;
  m.chat.snap=1;check(render(c,f,m,v,original)==original,"snap rejected");m.chat.snap=0;
  m.chat.flag="true";check(render(c,f,m,v,original)==original,"snap flag rejected");m.chat.flag=null;
  m.chat.body=new TextMsg(" ");check(render(c,f,m,v,original)==original,"invalid body rejected");m.chat.body=new TextMsg("test");
  f.resumed=false;check(render(c,f,m,v,original)==original,"inactive fragment rejected");f.resumed=true;
  f.activity.destroyed=true;check(render(c,f,m,v,original)==original,"destroyed activity rejected");f.activity.destroyed=false;
  v.attached=false;check(render(c,f,m,v,original)==original,"detached anchor rejected");v.attached=true;
  android.content.res.Resources.icon=0;check(render(c,f,m,v,original)==original,"missing icon rejected");android.content.res.Resources.icon=1;
  List<Item> empty=List.of();check(render(c,f,m,v,empty)==empty,"empty host menu preserved");
  m.chat.type=5;m.chat.body=new AudioMsg("https://example.com/audio","",3,"");
  MessageForwardControls forward=new MessageForwardControls();set(forward,"ready",true);set(c,"forward",forward);VoiceLibraryControls.ready=true;
  m.chat.type=1;m.chat.body=new TextMsg("test");
  check(((List<?>)render(c,f,m,v,original)).size()==3,"text adds repeat and forward only");
  m.chat.type=5;m.chat.body=new AudioMsg("https://example.com/audio","",3,"");
  check(((List<?>)render(c,f,m,v,original)).size()==4,"voice adds repeat forward and library");
  System.out.println("ChatMenuFallback: "+checks+" checks passed");
 }
}
