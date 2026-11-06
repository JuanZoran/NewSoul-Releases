package android.os;
import java.util.*;
public class Handler {public Handler(Looper l){}static List<Runnable> tasks=new ArrayList<>();public boolean postDelayed(Runnable r,long delay){tasks.add(r);return true;}public static void drain(){while(!tasks.isEmpty())tasks.remove(0).run();}}
