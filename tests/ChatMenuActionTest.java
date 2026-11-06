package dev.local.newsoul;
import android.app.Activity;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;
import cn.soulapp.imlib.msg.chat.*;

/** Real plugin view consumer and forward.open with no u0.p or host hooks installed. No sends. */
public class ChatMenuActionTest {
 static int checks;static String own="self";
 static final int FORWARD=0x4e5302;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static String account(){return own;}
 public static class Item {public final int id;public Item(String t,int icon,int id){this.id=id;}public int b(){return id;}}
 public static class Fragment {
  public String L="peer";public boolean resumed=true;public Activity activity=new Activity();
  public Activity getActivity(){return activity;}public android.content.Context getContext(){return activity;}
  public boolean isResumed(){return resumed;}
 }
 public static class Chat extends ChatMessage {
  public int getSnapChat(){return 0;}public String getStringTransExt(String s){return null;}
 }
 public static class Message {
  public Chat chat=new Chat();public Message(){chat.type=1;chat.body=new TextMsg("fixture");}
  public String getFrom(){return "peer";}public String getTo(){return "self";}
  public int getMsgType(){return 1;}public Chat getChatMessage(){return chat;}public boolean isAnonymityChat(){return false;}
 }
 public static class Popup {public android.widget.GridLayout grid=new android.widget.GridLayout();int closes;boolean animated;boolean fail;public void f(boolean a){closes++;animated=a;if(fail)throw new IllegalStateException();}}
 public static class Picker {}
 public static class LegacyPopup extends Popup {public android.widget.LinearLayout row=new android.widget.LinearLayout();}
 static void set(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static Object get(Object o,String n)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object entry(ChatRepeatControls c,Fragment f,Message m)throws Exception {
  Method append=ChatRepeatControls.class.getDeclaredMethod("appendActions",Object.class,Object.class,Object.class);append.setAccessible(true);
  List<?> items=(List<?>)append.invoke(c,List.of(new Item("host",1,0)),f,m);
  return items.stream().filter(i->((Item)i).id==FORWARD).findFirst().orElseThrow();
 }
 static boolean bind(ChatRepeatControls c,View v,Object e,Popup p)throws Exception {
  Method bind=ChatRepeatControls.class.getDeclaredMethod("bindAction",View.class,Object.class,Object.class,Method.class);bind.setAccessible(true);
  return (boolean)bind.invoke(c,v,e,p,Popup.class.getMethod("f",boolean.class));
 }
 static boolean hook(ChatRepeatControls c,Object e)throws Exception {
  Method handle=ChatRepeatControls.class.getDeclaredMethod("handleAction",Object.class,android.content.Context.class,String.class);handle.setAccessible(true);
  return (boolean)handle.invoke(c,e,new android.content.Context(),"host_hook");
 }
 static void click(ChatRepeatControls c,Fragment f,Message m,Popup p)throws Exception {
  View v=new View();check(bind(c,v,entry(c,f,m),p),"forward binds");v.performClick();
 }
 public static void main(String[] args)throws Exception {
  ChatRepeatControls c=new ChatRepeatControls();
  set(c,"peerField",Fragment.class.getField("L"));
  set(c,"item",Item.class.getConstructor(String.class,int.class,int.class));set(c,"itemId",Item.class.getMethod("b"));
  set(c,"account",ChatMenuActionTest.class.getMethod("account"));
  MessageForwardControls forward=new MessageForwardControls();set(c,"forward",forward);
  set(forward,"ready",true);set(forward,"account",ChatMenuActionTest.class.getMethod("account"));set(forward,"selector",Picker.class);
  Fragment f=new Fragment();Message m=new Message();Popup p=new Popup();View v=new View();Object e=entry(c,f,m);
  int[] nativeClicks={0};v.setOnClickListener(view->nativeClicks[0]++);
  check(!bind(c,v,new Item("host",1,0),p),"native listener not replaced");v.performClick();
  check(nativeClicks[0]==1&&p.closes==0&&f.activity.starts==0,"native behavior intact");
  check(!hook(c,new Item("host",1,0)),"native hook proceeds");check(!hook(c,null),"null hook proceeds");
  check(bind(c,v,e,p),"plugin listener bound");v.performClick();
  check(f.activity.starts==1&&nativeClicks[0]==1,"opens picker without host callback");
  check(p.closes==1&&p.animated,"native animated dismissal");
  check(f.activity.lastIntent.target==Picker.class&&Boolean.TRUE.equals(f.activity.lastIntent.extras.get("isFromPreview")),"native picker extras retained");
  MessageForwardRequest request=(MessageForwardRequest)get(forward,"pending");
  check(request.type==1&&request.body!=m.chat.body&&request.live(own,android.os.SystemClock.elapsedRealtime()),"payload snapshot remains unclaimed before confirmation");
  v.performClick();check(hook(c,e),"host fallback consumes same plugin entry");
  check(f.activity.starts==1,"double click plus fallback cannot reopen");
  check(DebugDiagnostics.events.stream().anyMatch(s->s.contains("already_consumed")),"duplicate diagnosed");
  click(c,f,m,new Popup());check(f.activity.starts==2,"fresh menu can reopen");
  DebugDiagnostics.enabled=false;click(c,f,m,new Popup());check(f.activity.starts==3,"works with debug off");DebugDiagnostics.enabled=true;
  int before=f.activity.starts;
  Object stale=entry(c,f,m);f.resumed=false;Popup stalePopup=new Popup();View staleView=new View();bind(c,staleView,stale,stalePopup);staleView.performClick();
  check(f.activity.starts==before&&stalePopup.closes==1,"inactive fragment rejected and dismissed");f.resumed=true;
  stale=entry(c,f,m);own="other";bind(c,staleView,stale,new Popup());staleView.performClick();check(f.activity.starts==before,"account switch rejected");own="self";
  stale=entry(c,f,m);f.L="other";bind(c,staleView,stale,new Popup());staleView.performClick();check(f.activity.starts==before,"conversation switch rejected");f.L="peer";
  stale=entry(c,f,m);f.activity.destroyed=true;bind(c,staleView,stale,new Popup());staleView.performClick();check(f.activity.starts==before,"destroyed activity rejected");f.activity.destroyed=false;
  f.activity.failStart=true;click(c,f,m,new Popup());check(f.activity.starts==before&&get(forward,"pending")==null,"start failure cleans pending request");f.activity.failStart=false;
  Popup failClose=new Popup();failClose.fail=true;click(c,f,m,failClose);check(f.activity.starts==before+1&&failClose.closes==1,"dismiss failure never retries action");
  View missing=new View();Popup missingPopup=new Popup();bind(c,missing,new Item("stale",1,FORWARD),missingPopup);missing.performClick();
  check(f.activity.starts==before+1&&missingPopup.closes==1,"unbound entry rejected and dismissed");
  check(DebugDiagnostics.events.stream().anyMatch(s->s.contains("selection_missing")),"missing selection diagnosed");
  for(int action:new int[]{0x4e5301,0x4e5303})check(bind(c,new View(),new Item("plugin",1,action),new Popup()),"all plugin actions use direct binding");
  check(DebugDiagnostics.events.stream().anyMatch(s->s.contains("repeat.action.click")&&s.contains("direct_view")),"raw click boundary recorded");
  Object hookFirst=entry(c,f,m);View hookFirstView=new View();bind(c,hookFirstView,hookFirst,new Popup());
  before=f.activity.starts;check(hook(c,hookFirst),"host fallback still opens plugin action");hookFirstView.performClick();
  check(f.activity.starts==before+1,"hook then direct click cannot duplicate");
  ImgMsg image=new ImgMsg();image.imageUrl="https://example.com/image";image.imageW=20;image.imageH=30;
  Object[] bodies={image,new ImgMsgs(new ArrayList<>(List.of(image))),new AudioMsg("https://example.com/audio","",7,"")};
  int[] types={2,3,5};
  for(int i=0;i<types.length;i++){
   m.chat.type=types[i];m.chat.body=bodies[i];before=f.activity.starts;click(c,f,m,new Popup());
   MessageForwardRequest media=(MessageForwardRequest)get(forward,"pending");
   check(f.activity.starts==before+1&&media.type==types[i]&&media.body!=bodies[i],"media click opens independent picker snapshot");
  }
  m.chat.type=1;m.chat.body=new TextMsg("fixture");
  Method renderBind=ChatRepeatControls.class.getDeclaredMethod("bindRenderedActions",Object.class,List.class,Method.class);renderBind.setAccessible(true);
  Popup rendered=new Popup();View nativeView=new View(),forwardView=new View();int[] renderedNative={0};nativeView.setOnClickListener(x->renderedNative[0]++);
  rendered.grid.children.add(nativeView);rendered.grid.children.add(forwardView);
  List<Item> renderedEntries=List.of(new Item("native",1,0),(Item)entry(c,f,m));
  renderBind.invoke(c,rendered,renderedEntries,Popup.class.getMethod("f",boolean.class));
  before=f.activity.starts;nativeView.performClick();forwardView.performClick();
  check(renderedNative[0]==1&&f.activity.starts==before+1&&rendered.closes==1,"renderer maps actual child and preserves native listener");
  check(DebugDiagnostics.events.stream().anyMatch(x->x.contains("repeat.action.bind:")&&x.contains("count=1")),"binding count diagnosed");
  // Rebinding a recycled native row must leave the host's freshly assigned native listener intact.
  forwardView.setOnClickListener(x->renderedNative[0]++);
  renderBind.invoke(c,rendered,List.of(new Item("native",1,0),new Item("native",1,1)),Popup.class.getMethod("f",boolean.class));
  before=f.activity.starts;forwardView.performClick();check(renderedNative[0]==2&&f.activity.starts==before,"reused native row not polluted");
  Popup mismatch=new Popup();View mismatchView=new View();int[] mismatchClicks={0};mismatchView.setOnClickListener(x->mismatchClicks[0]++);mismatch.grid.children.add(mismatchView);
  renderBind.invoke(c,mismatch,renderedEntries,Popup.class.getMethod("f",boolean.class));mismatchView.performClick();
  check(mismatchClicks[0]==1,"mismatched grid never binds wrong entry");
  check(DebugDiagnostics.events.stream().anyMatch(x->x.contains("grid_mismatch")),"grid mismatch diagnosed");
  LegacyPopup legacyPopup=new LegacyPopup();View legacyNative=new View(),legacyForward=new View();int[] legacyClicks={0};
  legacyNative.setOnClickListener(x->legacyClicks[0]++);legacyPopup.row.children.add(legacyNative);legacyPopup.row.children.add(legacyForward);
  List<Item> legacyEntries=List.of(new Item("native",1,0),(Item)entry(c,f,m));
  set(c,"legacy",true);
  renderBind.invoke(c,legacyPopup,legacyEntries,Popup.class.getMethod("f",boolean.class));
  before=f.activity.starts;legacyNative.performClick();legacyForward.performClick();
  check(legacyClicks[0]==1&&f.activity.starts==before+1&&legacyPopup.closes==1,"legacy horizontal menu preserves native click and binds forward");
  set(c,"legacy",false);
  System.out.println("ChatMenuAction: "+checks+" checks passed");
 }
}
