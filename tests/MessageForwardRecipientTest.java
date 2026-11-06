package dev.local.newsoul;

import java.lang.reflect.*;
import java.util.*;
import android.widget.TextView;
import cn.soulapp.imlib.msg.chat.TextMsg;

/** Run the actual filtered legacy picker consumer; fake rows, no hooks or sends. */
public class MessageForwardRecipientTest {
 static int checks; static String own="me";
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static String account(){return own;}
 public static class Conversation {
  final String target;final int type;Conversation(String target,int type){this.target=target;this.type=type;}
  public String getToUserId(){return target;}public int getChatType(){return type;}
 }
 public static class User {public String alias,signature;User(String name){alias=name;signature="fallback";}}
 public static class Row {
  public Conversation conversation;public User user;
  Row(String target,int type){conversation=new Conversation(target,type);user=new User(target);}
 }
 public static class Adapter {
  final List<Row> rows;int refreshes;Adapter(List<Row> rows){this.rows=rows;}
  public List<Row> getAllData(){return rows;}public void notifyDataSetChanged(){refreshes++;}
 }
 public static class Presenter {
  public final List<Row> p;final Map<String,String> chosen=new LinkedHashMap<>();int calls,last=-1;
  Presenter(List<Row> rows){p=rows;}
  public Map<String,String> Z(){return chosen;}
  public void s1(int index){calls++;last=index;String target=p.get(index).conversation.target;
   if(chosen.containsValue(target))chosen.remove(target);else chosen.put(target,target);}
 }
 public static class Picker extends android.app.Activity {
  public Adapter b;public TextView e=new TextView();public Presenter presenter;
 }
 static void set(Object object,String name,Object value)throws Exception {
  Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
 }
 static Object call(MessageForwardControls controls,String name,Class<?>[] types,Object... args)throws Exception {
  Method method=MessageForwardControls.class.getDeclaredMethod(name,types);method.setAccessible(true);
  try{return method.invoke(controls,args);}catch(InvocationTargetException error){
   if(error.getCause() instanceof Exception)throw (Exception)error.getCause();throw error;
  }
 }
 static void toggle(MessageForwardControls c,Picker p,int index)throws Exception {
  call(c,"toggle",new Class<?>[]{android.app.Activity.class,int.class},p,index);
 }
 public static void main(String[] args)throws Exception {
  MessageForwardControls c=new MessageForwardControls();set(c,"ready",true);set(c,"legacy",true);
  set(c,"account",MessageForwardRecipientTest.class.getMethod("account"));set(c,"selector",Picker.class);
  set(c,"selectedRecipients",Presenter.class.getMethod("Z"));set(c,"toggleRecipient",Presenter.class.getMethod("s1",int.class));
  set(c,"presenterRows",Presenter.class.getField("p"));
  Row self=new Row("me",0),source=new Row("source",0),group=new Row("group",1),a=new Row("a",0),b=new Row("b",0);
  List<Row> original=new ArrayList<>(List.of(self,source,group,a,b));
  Picker p=new Picker();p.presenter=new Presenter(original);p.b=new Adapter(new ArrayList<>(List.of(a,b)));
  c.open(p,own,"source",1,new TextMsg("fixture"));
  toggle(c,p,0);check(p.presenter.last==3,"visible index maps to original identity");
  check(p.presenter.chosen.equals(Map.of("a","a"))&&p.b.refreshes==1,"right target toggled and UI refreshed");
  check(p.e.text.equals("完成(1)")&&p.e.enabled&&p.e.alpha==1,"selection status enabled");
  toggle(c,p,0);check(p.presenter.chosen.isEmpty(),"repeat toggle removes same original row");
  check(p.e.text.equals("完成")&&!p.e.enabled&&p.e.alpha==.5f,"empty selection disabled");
  Collections.swap(original,3,4);toggle(c,p,0);check(p.presenter.last==4&&p.presenter.chosen.containsValue("a"),"reordered original list resolved at click");
  original.remove(a);int before=p.presenter.calls;
  try{toggle(c,p,0);throw new AssertionError("removed row accepted");}catch(IllegalStateException expected){checks++;}
  check(p.presenter.calls==before,"missing identity never toggles another row");original.add(a);
  for(Row forbidden:List.of(self,source,group)){
   p.b.rows.add(forbidden);before=p.presenter.calls;toggle(c,p,p.b.rows.size()-1);
   check(p.presenter.calls==before,"forbidden target not toggled");p.b.rows.remove(forbidden);
  }
  before=p.presenter.calls;toggle(c,p,-1);toggle(c,p,99);check(p.presenter.calls==before,"out of bounds ignored");
  p.presenter.chosen.clear();for(int i=0;i<9;i++)p.presenter.chosen.put("target"+i,"target"+i);
  before=p.presenter.calls;toggle(c,p,1);check(p.presenter.calls==before,"tenth new target blocked");
  p.presenter.chosen.remove("target0");p.presenter.chosen.put("a","a");toggle(c,p,0);
  check(p.presenter.calls==before+1&&!p.presenter.chosen.containsValue("a"),"deselect remains possible at limit");
  p.presenter.chosen.clear();toggle(c,p,1);
  Method pending=MessageForwardControls.class.getDeclaredMethod("request",android.app.Activity.class);pending.setAccessible(true);
  MessageForwardRequest request=(MessageForwardRequest)pending.invoke(c,p);
  @SuppressWarnings("unchecked") Map<String,String> recipients=(Map<String,String>)call(c,"recipients",new Class<?>[]{android.app.Activity.class,MessageForwardRequest.class},p,request);
  check(recipients.equals(Map.of("b","b")),"confirmation resolves only visible allowed recipients");
  b.user.alias="";recipients=(Map<String,String>)call(c,"recipients",new Class<?>[]{android.app.Activity.class,MessageForwardRequest.class},p,request);
  check(recipients.equals(Map.of("b","fallback")),"empty alias uses host signature");
  p.b.rows.remove(b);
  try{call(c,"recipients",new Class<?>[]{android.app.Activity.class,MessageForwardRequest.class},p,request);throw new AssertionError("stale target accepted");}
  catch(IllegalStateException expected){checks++;}p.b.rows.add(b);
  check(request.live(own,android.os.SystemClock.elapsedRealtime()),"opening, selection and preview never claim/send");
  own="other";before=p.presenter.calls;toggle(c,p,0);
  check(p.finishes==1&&p.presenter.calls==before,"account change closes picker without dispatch");
  System.out.println("MessageForwardRecipient: "+checks+" checks passed");
 }
}
