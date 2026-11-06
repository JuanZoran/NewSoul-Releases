package android.widget;
public class Toast {
 public static final int LENGTH_SHORT=0;
 public static int shown;
 public static Toast makeText(android.content.Context c,CharSequence t,int n){return new Toast();}
 public void show(){shown++;}
}
