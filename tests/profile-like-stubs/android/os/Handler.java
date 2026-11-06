package android.os;
public class Handler {
 public static final java.util.List<Runnable> tasks=new java.util.ArrayList<>();
 public Handler(Looper l){} public boolean post(Runnable r){tasks.add(r);return true;}
 public void removeCallbacks(Runnable r){tasks.removeIf(t->t==r);}
 public static void drain(){while(!tasks.isEmpty())tasks.remove(0).run();}
}
