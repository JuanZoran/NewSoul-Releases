package dev.local.newsoul;

import java.util.*;

/** Real splitter -> real scheduler -> real reflection sender -> synthetic Soul host. */
public final class AiSendSequenceTest {
    static int checks;
    static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    static final class Clock implements AiSendSequence.Clock {
        long time; final TreeMap<Long,Runnable> queue=new TreeMap<>();
        public long now(){return time;}
        public void later(Runnable action,long delay){ok(delay==300);queue.put(time+delay,action);}
        void advance(long delta){long target=time+delta;while(!queue.isEmpty()&&queue.firstKey()<=target){time=queue.firstKey();queue.pollFirstEntry().getValue().run();}time=target;}
    }
    static final class Run implements AiSendSequence.Listener {
        final Clock clock=new Clock(); final AiReplySenderTest.Cache cache=new AiReplySenderTest.Cache();
        final AiReplySenderTest.Screen screen=new AiReplySenderTest.Screen();
        final AiSendSequence sequence; boolean alive=true;int completed,confirmed=-1;String reason="";
        Run(String text) throws Exception {
            AiChatContext.same=true;AiChatContext.visible=true;AiChatContext.cache=cache;
            screen.f.Q=new AiReplySenderTest.Sender(cache);
            AiReplySender.install(new SoulHook(),screen.getClassLoader());
            sequence=new AiSendSequence(new AiReplySender(screen,new AiChatContext.Snapshot()),clock,()->alive,this,AiSendRules.parts(text));
        }
        public void progress(int index,int total){}
        public void completed(int total){completed=total;}
        public void stopped(int count,int total,String why){confirmed=count;reason=why;}
        void ack(int status){cache.list.get(cache.list.size()-1).state=status;}
        int calls(){return screen.f.Q.calls;}
    }
    public static void main(String[] args) throws Exception {
        Run r=new Run("这条街看起来挺舒服的。你拍的是哪一段？\n我还挺喜欢这种小巷");
        r.sequence.start();r.sequence.start();ok(r.calls()==1);r.ack(4);
        r.clock.advance(299);ok(r.calls()==1);r.clock.advance(1);ok(r.calls()==2);
        ok(r.cache.list.get(0).chat.body.text.equals("这条街看起来挺舒服的。"));
        ok(r.cache.list.get(1).chat.body.text.equals("你拍的是哪一段？"));
        r.clock.advance(900);ok(r.calls()==2&&r.completed==0); // awaiting acknowledgment never dispatches next
        r.ack(3);r.clock.advance(300);ok(r.calls()==3);r.ack(4);r.clock.advance(300);ok(r.completed==3);
        r.clock.advance(30000);r.sequence.start();ok(r.calls()==3);
        r=new Run("第一段\n第二段\n第三段");r.sequence.start();r.ack(4);r.clock.advance(300);r.ack(5);r.clock.advance(300);
        ok(r.calls()==2&&r.confirmed==1&&r.reason.equals("native_failed"));r.clock.advance(30000);ok(r.calls()==2);
        r=new Run("第一段\n第二段");r.sequence.start();r.clock.advance(20100);
        ok(r.calls()==1&&r.confirmed==0&&r.reason.equals("confirmation_timeout"));r.ack(4);r.clock.advance(30000);ok(r.calls()==1);
        r=new Run("第一段\n第二段");r.sequence.start();r.ack(4);
        AiReplySenderTest.Message incoming=new AiReplySenderTest.Message("incoming","等一下");incoming.from="peer";incoming.to="self";r.cache.list.add(incoming);
        r.clock.advance(300);ok(r.calls()==1&&r.confirmed==1&&r.reason.equals("dispatch_chat_context_changed"));
        r=new Run("第一段\n第二段");r.sequence.start();r.ack(4);r.alive=false;r.clock.advance(30000);ok(r.calls()==1&&r.completed==0);
        r=new Run("第一段\n第二段");r.alive=false;r.sequence.start();ok(r.calls()==0);
        r=new Run("第一段\n第二段");AiChatContext.visible=false;r.sequence.start();ok(r.calls()==0&&r.reason.equals("dispatch_chat_context_changed"));
        r=new Run("第一段\n第二段");r.sequence.start();r.ack(4);AiChatContext.same=false;r.clock.advance(300);ok(r.calls()==1&&r.reason.equals("poll_account_or_conversation_changed"));
        for(String manualText:new String[]{"第一段","手动新消息"}) {
            r=new Run("第一段\n第二段");r.sequence.start();r.ack(4);
            AiReplySenderTest.Message manual=AiReplySenderTest.Message.createChatSendMsg(new AiReplySenderTest.Chat(manualText),"peer");
            manual.state=4;r.cache.list.add(manual);r.clock.advance(300);
            ok(r.calls()==1&&r.confirmed==1&&r.reason.equals("dispatch_chat_context_changed"));
        }
        r=new Run("第一段\n第二段");r.screen.f.Q.delayed=true;r.sequence.start();
        r.clock.advance(300);ok(r.calls()==1&&r.completed==0);
        r.cache.list.add(r.screen.f.Q.deferred);r.ack(4);r.clock.advance(300);ok(r.calls()==2);
        r.cache.list.add(r.screen.f.Q.deferred);r.ack(4);r.clock.advance(300);ok(r.completed==2);
        r=new Run("第一段\n第二段");r.screen.f.Q.create=false;r.sequence.start();
        ok(r.calls()==1&&r.reason.equals("dispatch_message_not_created"));
        AiReplySenderTest.Message late=AiReplySenderTest.Message.createChatSendMsg(new AiReplySenderTest.Chat("第一段"),"peer");late.state=4;r.cache.list.add(late);
        r.clock.advance(30000);ok(r.calls()==1&&r.completed==0);
        r=new Run("第一段\n第二段");r.sequence.start();r.ack(4);r.screen.f.resumed=false;r.clock.advance(300);
        ok(r.calls()==1&&r.reason.equals("poll_fragment_changed_or_paused"));
        r.screen.f.resumed=true;r.clock.advance(30000);ok(r.calls()==1);
        System.out.println("AiSendSequenceTest: "+checks+" checks passed; real production scheduler + reflection adapter, fake clock/host, no device messages");
    }
}
