package android.view;
import android.content.*;import android.content.res.*;import java.util.*;
public class View {
 public static final int NO_ID=-1,GONE=8;public Context context;public View parent;public int id=-1,visibility=0;public boolean rejectPosts;public ViewGroup.LayoutParams lp=new ViewGroup.MarginLayoutParams(120);public List<OnAttachStateChangeListener> listeners=new ArrayList<>();
 public View(Context c){context=c;}public Context getContext(){return context;}public int getId(){return id;}public View getParent(){return parent;}public Resources getResources(){return new Resources();}
 public ViewGroup.LayoutParams getLayoutParams(){return lp;}public void setLayoutParams(ViewGroup.LayoutParams p){lp=p;}public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}public static final java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>(); public boolean post(Runnable r){if(rejectPosts)return false;queue.add(r);return true;} public boolean postDelayed(Runnable r,long delay){queue.add(r);return true;} public int getTop(){return 0;} public int getPaddingTop(){return 0;} public static void drain(){int n=queue.size();while(n-->0)queue.remove().run();}
 public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
 public void attach(){for(var l:listeners)l.onViewAttachedToWindow(this);}public void detach(){for(var l:listeners)l.onViewDetachedFromWindow(this);}
}
