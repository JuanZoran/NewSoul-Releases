package dev.local.newsoul;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import cn.soulapp.imlib.msg.chat.*;

/** Production dispatch with an in-memory ChatManager; never connects to a host or network. */
public class MessageForwardPayloadTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static class Chat {
  public String target;public Object body;public int type;public Map<?,?> extras;
  public static Chat create(String target){Chat c=new Chat();c.target=target;return c;}
  public void setMsgContent(Serializable b){body=b;}public void setMsgType(int t){type=t;}public void setTransExtMap(Map<?,?> m){extras=m;}
 }
 public static class Message {final Chat chat;final String target;Message(Chat c,String t){chat=c;target=t;}
  public static Message createChatSendMsg(Chat c,String t){return new Message(c,t);}}
 public static class Manager {
  static final Manager INSTANCE=new Manager();static final List<Message> sent=new ArrayList<>();
  public static Manager v(){return INSTANCE;}public Manager o(){return this;}public void q0(Message m){sent.add(m);}
 }
 public static void u(Chat c){}
 static void set(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 public static void main(String[] args)throws Exception {
  MessageForwardControls c=new MessageForwardControls();
  set(c,"createChat",Chat.class.getMethod("create",String.class));set(c,"setContent",Chat.class.getMethod("setMsgContent",Serializable.class));
  set(c,"setType",Chat.class.getMethod("setMsgType",int.class));set(c,"setExtras",Chat.class.getMethod("setTransExtMap",Map.class));
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException {
   if(name.equals(HostBindings.FRAGMENT))return Object.class;
   if(name.equals(HostBindings.SENDER))return MessageForwardPayloadTest.class;
   if(name.equals("cn.soulapp.imlib.msg.chat.ChatMessage"))return Chat.class;
   return super.loadClass(name);
  }};
  set(c,"decoration",new HostMessageDecoration(loader));set(c,"createMessage",Message.class.getMethod("createChatSendMsg",Chat.class,String.class));
  set(c,"getImManager",Manager.class.getMethod("v"));set(c,"getChatManager",Manager.class.getMethod("o"));set(c,"deliver",Manager.class.getMethod("q0",Message.class));
  Method send=MessageForwardControls.class.getDeclaredMethod("send",MessageForwardRequest.class,String.class);send.setAccessible(true);
  ImgMsg image=new ImgMsg();image.imageUrl="https://example.com/image";image.imageW=20;image.imageH=30;image.imageLocalPath="private";
  Object[] bodies={new TextMsg("original\ntext"),image,new ImgMsgs(new ArrayList<>(List.of(image))),new AudioMsg("https://example.com/audio","private",7,"private"),new VideoMsg("https://example.com/video","private",720,1280)};
  int[] types={1,2,3,5,4};
  for(int i=0;i<types.length;i++){
   Manager.sent.clear();MessageForwardRequest r=new MessageForwardRequest("me","source",types[i],bodies[i],100);
   for(String target:r.claim("me",List.of("a","b"),100))send.invoke(c,r,target);
   check(Manager.sent.size()==2);Message a=Manager.sent.get(0),b=Manager.sent.get(1);
   check(a!=b&&a.chat!=b.chat&&a.chat.body!=b.chat.body&&a.chat.body!=bodies[i]);
   check(a.target.equals("a")&&a.chat.target.equals("a")&&b.target.equals("b")&&b.chat.target.equals("b"));
   check(a.chat.type==types[i]&&b.chat.type==types[i]);check(a.chat.extras.isEmpty()&&a.chat.extras!=b.chat.extras);
   if(types[i]==4){VideoMsg v=(VideoMsg)a.chat.body;check(v.localUrl.isEmpty()&&v.width==720&&v.height==1280);}
   check(r.claim("me",List.of("a","b"),101)==null);
  }
  System.out.println("MessageForwardPayload: "+checks+" checks passed");
 }
}
