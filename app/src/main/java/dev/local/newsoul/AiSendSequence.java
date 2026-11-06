package dev.local.newsoul;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Production send/acknowledgment scheduler, also runnable against a fake clock and host. */
final class AiSendSequence {
    interface Transport { void dispatch(String text) throws Exception; int status() throws Exception; }
    interface Clock { long now(); void later(Runnable action,long delay); }
    interface Listener {
        void progress(int index,int total);
        void completed(int total);
        void stopped(int confirmed,int total,String reason);
    }
    private final Transport transport;
    private final Clock clock;
    private final BooleanSupplier alive;
    private final Listener listener;
    private final List<String> parts;
    private boolean started,finished;
    AiSendSequence(Transport transport,Clock clock,BooleanSupplier alive,Listener listener,List<String> parts) {
        this.transport=transport;this.clock=clock;this.alive=alive;this.listener=listener;
        this.parts=new ArrayList<>(parts);
        if(parts.isEmpty())throw new IllegalArgumentException("Empty send sequence");
    }
    void start(){if(started)return;started=true;send(0);}
    private boolean active(){return !finished&&alive.getAsBoolean();}
    private void send(int index) {
        if(!active()){finished=true;return;}
        listener.progress(index,parts.size());
        try{transport.dispatch(parts.get(index));}
        catch(Exception error){stop(index,"dispatch_"+AiReplySender.failureCode(error));return;}
        poll(index,clock.now()+20000);
    }
    private void poll(int index,long deadline) {
        clock.later(()->{
            if(!active()){finished=true;return;}
            try {
                int state=transport.status();
                if(AiMessageStatus.sent(state)) {
                    if(index+1==parts.size()){finished=true;listener.completed(parts.size());}
                    else send(index+1);
                } else if(AiMessageStatus.failed(state))stop(index,"native_failed");
                else if(clock.now()>=deadline)stop(index,"confirmation_timeout");
                else poll(index,deadline);
            }catch(Exception error){stop(index,"poll_"+AiReplySender.failureCode(error));}
        },300);
    }
    private void stop(int confirmed,String reason){finished=true;listener.stopped(confirmed,parts.size(),reason);}
}
