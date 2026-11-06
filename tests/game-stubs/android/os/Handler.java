package android.os;
public class Handler {public Handler(Looper l){}public static java.util.ArrayDeque<Runnable> tasks=new java.util.ArrayDeque<>();public boolean post(Runnable r){tasks.add(r);return true;}public static void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
