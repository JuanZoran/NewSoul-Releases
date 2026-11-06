package android.view;
import android.content.Context;import android.graphics.Rect;import java.util.*;
public class View implements ViewParent {
 public static final int GONE=8;
 public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public List<OnAttachStateChangeListener> listeners=new ArrayList<>();
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}
 public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
 public void attach(){attached=true;for(var l:new ArrayList<>(listeners))l.onViewAttachedToWindow(this);}
 public void detach(){attached=false;for(var l:new ArrayList<>(listeners))l.onViewDetachedFromWindow(this);}
 public float translationX,translationY;public float getTranslationX(){return translationX;}public float getTranslationY(){return translationY;}public void setTranslationX(float v){translationX=v;}public void setTranslationY(float v){translationY=v;}public void setAlpha(float v){}
 public boolean post(Runnable r){if(queued==null)queued=new ArrayList<>();queued.add(r);return true;}
 public float x,y; public int screenTop,screenLeft;
 public void setX(float x){this.x=x;}public void setY(float y){this.y=y;}
 public Context context;public ViewGroup parent;public boolean shown=true,attached=true,enabled=true;public int visibility=0,id,clicks;public Object tag;public int width=100,height=100;public Map<Integer,Object> tags=new HashMap<>();public List<Runnable> queued=new ArrayList<>();
 public View(Context c){context=c;}public View(){}
 public Context getContext(){return context;}public android.content.res.Resources getResources(){return context.getResources();}
 public ViewParent getParent(){return parent;}public boolean isShown(){return shown&&visibility==0&&(parent==null||parent.isShown());}public boolean isAttachedToWindow(){return attached;}
 public boolean getGlobalVisibleRect(Rect r){r.w=width;r.h=height;r.left=screenLeft;r.top=screenTop;r.right=screenLeft+width;r.bottom=screenTop+height;return isShown()&&width>0&&height>0;}public float getAlpha(){return 1;}public boolean isEnabled(){return enabled;}public void setEnabled(boolean b){enabled=b;}
 public View findViewById(int n){return id==n?this:null;}public Object getTag(int n){return tags==null?null:tags.get(n);}public void setTag(int n,Object o){tags.put(n,o);}
 public boolean hasOnClickListeners(){return true;}public boolean performClick(){clicks++;return true;}
 public boolean postDelayed(Runnable r,long ms){if(queued==null)queued=new ArrayList<>();queued.add(r);return true;}public boolean removeCallbacks(Runnable r){if(queued!=null)queued.remove(r);return true;}
 public void setVisibility(int v){visibility=v;}public int getVisibility(){return visibility;}public void invalidate(){}public void setContentDescription(CharSequence s){}
 public ViewTreeObserver getViewTreeObserver(){return new ViewTreeObserver();}
}
