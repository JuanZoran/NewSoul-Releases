package android.view;
import android.content.*;import android.content.res.*;import java.util.*;
public class View {
 public static final int NO_ID=-1,GONE=8;public Context context;public View parent;public int id=-1,visibility=0;public ViewGroup.LayoutParams lp=new ViewGroup.MarginLayoutParams(120);public List<OnAttachStateChangeListener> listeners=new ArrayList<>();
 public View(Context c){context=c;}public Context getContext(){return context;}public int getId(){return id;}public View getParent(){return parent;}public Resources getResources(){return new Resources();}
 public ViewGroup.LayoutParams getLayoutParams(){return lp;}public void setLayoutParams(ViewGroup.LayoutParams p){lp=p;}public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}public boolean post(Runnable r){r.run();return true;}
 public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
 public void attach(){for(var l:listeners)l.onViewAttachedToWindow(this);}public void detach(){for(var l:listeners)l.onViewDetachedFromWindow(this);}
}
