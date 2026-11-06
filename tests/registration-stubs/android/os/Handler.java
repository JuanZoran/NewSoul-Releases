package android.os;
import java.util.concurrent.*;
public class Handler {
 public Handler(Looper l){}public static final ConcurrentLinkedQueue<Runnable> immediate=new ConcurrentLinkedQueue<>(),delayed=new ConcurrentLinkedQueue<>();
 private static final ConcurrentLinkedQueue<Runnable> refresh=new ConcurrentLinkedQueue<>();
 public boolean post(Runnable r){immediate.add(r);return true;}public boolean postDelayed(Runnable r,long ms){(ms<1000?refresh:delayed).add(r);return true;}
 public void removeCallbacks(Runnable r){immediate.remove(r);refresh.remove(r);delayed.remove(r);}
 public static void drain(){Runnable r;while(!immediate.isEmpty()||!refresh.isEmpty()){while((r=immediate.poll())!=null)r.run();while((r=refresh.poll())!=null)r.run();}}
 public static void expire(){Runnable r;while((r=delayed.poll())!=null)r.run();}
}
