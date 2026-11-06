package android.widget;
import android.content.Context;
public class Toast {public static final int LENGTH_SHORT=0;public static String lastText;public static int shows;private String text;public static Toast makeText(Context c,String s,int n){Toast toast=new Toast();toast.text=s;return toast;}public void show(){lastText=text;shows++;}}
