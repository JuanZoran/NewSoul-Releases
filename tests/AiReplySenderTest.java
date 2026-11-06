package dev.local.newsoul;
import java.util.*;
import cn.soulapp.lib_input.view.IMediaKeyBoard;
import cn.soulapp.android.client.component.middle.platform.model.api.user.ImUserBean;

/** Exercises the production reflection adapter against a deterministic host double, no network. */
public class AiReplySenderTest {
    public static class Body { public String text; Body(String s){text=s;} }
    public static class Chat {
        Body body; Chat(String s){body=new Body(s);}
        public int getMsgType(){return 1;} public Body getMsgContent(){return body;}
    }
    public static class Message {
        String id, from="self", to="peer"; int state=1; Chat chat;
        Message(String id,String text){this.id=id;chat=new Chat(text);}
        public static Message createChatSendMsg(Chat chat,String to) {
            return (Message) SoulHook.create(() -> {Message m=new Message("new"+(++nextId),chat.body.text);m.to=to;return m;});
        }
        static int nextId;
        public String getMsgId(){return id;} public String getFrom(){return from;}
        public String getTo(){return to;}
        public Chat getChatMessage(){return chat;} public int getMsgStatus(){return state;}
    }
    public static class Cache {
        public String getSessionId(){return "session";} public int getChatType(){return 0;}
        String recipient="peer";
        public String getToUserId(){return recipient;}
        List<Message> list=new ArrayList<>();
        public List<Message> getCacheMsgs(){return new ArrayList<>(list);}
    }
    public static class Sender {
        public Fragment a; Cache cache; int calls; boolean create=true, delayed, duplicate, throwsAfterCreate, asyncCreate; Message deferred;
        Sender(Cache c){cache=c;}
        public void Y1(String text,IMediaKeyBoard k,ImUserBean u){
            calls++;
            if(!create)return;
            if(asyncCreate) {
                Thread worker=new Thread(()->cache.list.add(Message.createChatSendMsg(new Chat(text),"peer")));
                worker.start();try{worker.join();}catch(InterruptedException error){throw new AssertionError(error);}
                return;
            }
            Message message=Message.createChatSendMsg(new Chat(text),"peer");
            if(delayed)deferred=message;else cache.list.add(message);
            if(duplicate)Message.createChatSendMsg(new Chat(text),"peer");
            if(throwsAfterCreate)throw new IllegalStateException("synthetic sender failure");
        }
    }
    public static class InheritedSender extends Sender { InheritedSender(Cache c){super(c);} }
    public static class Fragment extends androidx.fragment.app.Fragment {
        public String L="peer"; public Sender Q; public IMediaKeyBoard s=new IMediaKeyBoard(){};
        public ImUserBean O=new ImUserBean(); boolean resumed=true;
        public Cache conversation; public androidx.fragment.app.FragmentActivity getActivity(){return null;}
        public boolean isResumed(){return resumed;}
    }
    public static class Screen extends android.app.Activity {
        Fragment f=new Fragment(); public androidx.fragment.app.Fragment y(){return f;}
        private final ClassLoader loader = new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){
            case "cn.soulapp.android.component.chat.ConversationActivity" -> Screen.class;
            case "cn.soulapp.android.component.chat.utils.y4" -> Sender.class;
            case "cn.soulapp.android.component.chat.fragment.ConversationFragment" -> Fragment.class;
            case "cn.soulapp.imlib.Conversation" -> Cache.class;
            case "cn.soulapp.imlib.msg.ImMessage" -> Message.class;
            case "cn.soulapp.imlib.msg.chat.ChatMessage" -> Chat.class;
            default -> super.loadClass(n);
        };}};
        public ClassLoader getClassLoader(){return loader;}
    }
    static int checks;
    interface Action {void run() throws Exception;}
    static void rejects(Action a) throws Exception {
        try {a.run();throw new AssertionError("Expected guard failure");}catch(IllegalStateException expected){checks++;}
    }
    public static void main(String[] args) throws Exception {
        Cache cache=new Cache();AiChatContext.cache=cache;
        Message old=new Message("old","第一段");old.state=4;cache.list.add(old);
        Screen screen=new Screen();screen.f.Q=new InheritedSender(cache);
        rejects(()->new AiReplySender(screen,new AiChatContext.Snapshot())); // missing hook fails closed
        AiReplySender.install(new SoulHook(),screen.getClassLoader());
        check(SoulHook.deoptimized.contains("Y1")&&SoulHook.deoptimized.contains("createChatSendMsg"));
        AiReplySender sender=new AiReplySender(screen,new AiChatContext.Snapshot());
        sender.dispatch("第一段");check(sender.status()==1); // old matching text cannot acknowledge a new send
        cache.list.get(1).state=4;check(sender.status()==4);
        sender.dispatch("第二段");check(sender.status()==1);
        cache.list.get(2).state=5;check(sender.status()==5);
        check(screen.f.Q.calls==2); // polling must never resend
        AiChatContext.same=false;rejects(()->sender.dispatch("不应发出"));rejects(sender::status);
        AiChatContext.same=true;screen.f.L="other";rejects(()->sender.dispatch("不应发出"));
        screen.f.L="peer";screen.f.resumed=false;rejects(()->sender.dispatch("不应发出"));
        screen.f.resumed=true;
        cache.recipient="changed";rejects(()->sender.dispatch("不应发出"));cache.recipient="peer";
        screen.f.O.userIdEcpt="encrypted-other";rejects(()->sender.dispatch("不应发出"));screen.f.O.userIdEcpt="encrypted-peer";
        Sender originalSender=screen.f.Q;screen.f.Q=new Sender(cache);rejects(()->sender.dispatch("不应发出"));screen.f.Q=originalSender;
        Message foreign=new Message("foreign","第二段");foreign.to="different-peer";foreign.state=4;cache.list.add(foreign);
        check(sender.status()==5); // another recipient cannot satisfy this send even if text matches
        Message duplicate=new Message("duplicate","第二段");duplicate.state=4;cache.list.add(duplicate);
        check(sender.status()==5); // same-text manual message cannot substitute for our exact ID
        rejects(()->sender.dispatch("不应继续"));check(screen.f.Q.calls==2);
        Fragment original=screen.f;screen.f=new Fragment();rejects(()->sender.dispatch("不应发出"));screen.f=original;
        AiReplySender fresh = new AiReplySender(screen, new AiChatContext.Snapshot());
        AiChatContext.visible=false;rejects(()->fresh.dispatch("旧候选"));check(screen.f.Q.calls==2);
        AiChatContext.visible=true;fresh.dispatch("新第一段");check(screen.f.Q.calls==3);
        Message incoming=new Message("incoming", "等一下");incoming.from="peer";incoming.to="self";cache.list.add(incoming);
        rejects(()->fresh.dispatch("不应继续的第二段"));check(screen.f.Q.calls==3);
        Cache deferredCache=new Cache();AiChatContext.cache=deferredCache;
        Screen deferredScreen=new Screen();deferredScreen.f.Q=new Sender(deferredCache);deferredScreen.f.Q.delayed=true;
        AiReplySender.install(new SoulHook(),deferredScreen.getClassLoader());
        AiReplySender delayed=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());delayed.dispatch("延迟提交");
        Message manual=Message.createChatSendMsg(new Chat("延迟提交"),"peer");manual.state=4;deferredCache.list.add(manual);
        check(delayed.status()==0); // manually generated outside dispatch scope cannot confirm
        deferredScreen.f.Q.deferred.state=4;deferredCache.list.add(deferredScreen.f.Q.deferred);
        check(delayed.status()==4);rejects(()->delayed.dispatch("被手动消息打断"));
        deferredScreen.f.Q.create=false;
        AiReplySender noMessage=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());
        rejects(()->noMessage.dispatch("提前返回"));
        Message late=Message.createChatSendMsg(new Chat("提前返回"),"peer");late.state=4;deferredCache.list.add(late);
        rejects(noMessage::status); // an asynchronous factory outside Y1 is never adopted
        deferredScreen.f.Q.create=true;
        AiReplySender clean=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());clean.dispatch("新序列");
        deferredCache.list.add(deferredScreen.f.Q.deferred);deferredScreen.f.Q.deferred.state=4;check(clean.status()==4);
        deferredScreen.f.Q.asyncCreate=true;
        AiReplySender asynchronous=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());
        rejects(()->asynchronous.dispatch("异步创建"));rejects(asynchronous::status);
        deferredScreen.f.Q.asyncCreate=false;
        deferredScreen.f.Q.duplicate=true;
        AiReplySender ambiguous=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());rejects(()->ambiguous.dispatch("多个创建"));
        deferredScreen.f.Q.duplicate=false;deferredScreen.f.Q.throwsAfterCreate=true;
        AiReplySender broken=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());
        try{broken.dispatch("发送异常");throw new AssertionError();}catch(java.lang.reflect.InvocationTargetException expected){checks++;}
        deferredScreen.f.Q.throwsAfterCreate=false;
        AiReplySender recovered=new AiReplySender(deferredScreen,new AiChatContext.Snapshot());recovered.dispatch("异常后新序列");
        deferredCache.list.add(deferredScreen.f.Q.deferred);deferredScreen.f.Q.deferred.state=4;check(recovered.status()==4);
        System.out.println("AiReplySenderTest: " + checks + " checks passed (host double; device sending not verified)");
    }
    static void check(boolean ok){if(!ok)throw new AssertionError();checks++;}
}
