#!/usr/bin/env python3
"""Exercise the real window-session state machine with a JVM lifecycle harness."""
from pathlib import Path
import subprocess
import tempfile
source = Path('app/src/main/java/dev/local/newsoul/FrostedMenuEffect.java').read_text()
session = source[source.index('    private static final class Session'):source.index('    private FrostedMenuEffect()')]
session = session.replace('android.view.ViewTreeObserver.OnGlobalLayoutListener', 'Observer.OnGlobalLayoutListener')
appearance_source = Path('app/src/main/java/dev/local/newsoul/UiAppearance.java').read_text()
card_snapshot = appearance_source[appearance_source.index('    static final class CardPresentation'):appearance_source.index('    CardPresentation outlineCard')]
harness = r'''
import java.util.*;
import java.util.function.Consumer;
class FrostedMenuTest {
 static final String ENABLED="menu_frosted_glass_v1";
 static class ConfigDefaults {static boolean booleanValue(String key){return true;}}
 static class Drawable {int alpha=255; int getAlpha(){return alpha;} void setAlpha(int a){alpha=a;}}
 static class Observer {
  interface OnGlobalLayoutListener {void onGlobalLayout();}
  Set<OnGlobalLayoutListener> listeners=new HashSet<>();
  void addOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.add(l);}
  void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.remove(l);}
  boolean isAlive(){return true;}
 }
 static class View {
  interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
  Observer observer=new Observer(); Drawable background=new Drawable(); boolean card;
  Drawable foreground=new Drawable(); float elevation=6;
  Drawable getForeground(){return foreground;}void setForeground(Drawable d){foreground=d;}
  float getElevation(){return elevation;}void setElevation(float v){elevation=v;}
  float alpha=1,y; List<Runnable> posted=new ArrayList<>();
  Drawable getBackground(){return background;} Observer getViewTreeObserver(){return observer;}
  void post(Runnable r){posted.add(r);} void setAlpha(float a){alpha=a;} void setTranslationY(float v){y=v;}
 }
 static class ViewGroup extends View {
  List<View> children=new ArrayList<>(); int getChildCount(){return children.size();}
  View getChildAt(int i){return children.get(i);}
 }
 static class GlassTabTrack extends View {
  boolean outlined;boolean isFrostedOutline(){return outlined;}void setFrostedOutline(boolean b){outlined=b;}
 }
 static class SharedPreferences {
  interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences p,String key);}
  Set<OnSharedPreferenceChangeListener> listeners=new HashSet<>(); Boolean enabled;
  boolean getBoolean(String k,boolean d){return enabled==null?d:enabled;}
  void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}
  void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
  void set(boolean b){enabled=b;for(var l:listeners)l.onSharedPreferenceChanged(this,ENABLED);}
 }
 static class WindowManager {
  boolean enabled=true; Set<Consumer<Boolean>> listeners=new HashSet<>();
  static class LayoutParams {
   static final int FLAG_BLUR_BEHIND=4; int flags,windowAnimations=9,radius;float dimAmount=.6f;
   void setBlurBehindRadius(int v){radius=v;} int getBlurBehindRadius(){return radius;}
  }
  boolean isCrossWindowBlurEnabled(){return enabled;}
  void addCrossWindowBlurEnabledListener(Object executor,Consumer<Boolean> l){listeners.add(l);}
  void removeCrossWindowBlurEnabledListener(Consumer<Boolean> l){listeners.remove(l);}
  void emit(boolean b){enabled=b;for(var l:listeners)l.accept(b);}
 }
 static class Context {
  WindowManager manager=new WindowManager();WindowManager getSystemService(Class<?> c){return manager;}
  Object getMainExecutor(){return this;}
 }
 static class Window {
  ViewGroup decor=new ViewGroup();Context context=new Context(); WindowManager.LayoutParams attrs=new WindowManager.LayoutParams();int radius;
  View getDecorView(){return decor;} Context getContext(){return context;} WindowManager.LayoutParams getAttributes(){return attrs;}
  void setAttributes(WindowManager.LayoutParams a){attrs=a;} void setBackgroundBlurRadius(int r){radius=r;}
  void addFlags(int f){attrs.flags|=f;} void setDimAmount(float d){attrs.dimAmount=d;} void setWindowAnimations(int a){attrs.windowAnimations=a;}
 }
 static class UiAppearance {
  boolean dark;float density=3;static boolean isCard(View v){return v.card;}
  CARD_SNAPSHOT
  static void bind(View v,Drawable d){v.background=d;}
  CardPresentation outlineCard(View v){CardPresentation s=new CardPresentation(v);v.background=new Drawable();v.background.alpha=4;v.foreground=null;v.elevation=0;return s;}
  void frostedWindow(Window w){w.decor.background=new Drawable();}
  void restoreWindow(Window w,Drawable d){w.decor.background=d;}
 }
 static class DecelerateInterpolator {}
 static class ValueAnimator {
  static boolean enabled=true;boolean cancelled; Consumer<ValueAnimator> listener;float value;
  static boolean areAnimatorsEnabled(){return enabled;}static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}
  void setDuration(int v){}void setInterpolator(Object o){}void addUpdateListener(Consumer<ValueAnimator> l){listener=l;}
  void start(){} void cancel(){cancelled=true;}Object getAnimatedValue(){return value;}
  void tick(float v){value=v;listener.accept(this);}
 }
''' + session + r'''
 static int checks;
 static void check(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 static void flush(Window w){var jobs=new ArrayList<>(w.decor.posted);w.decor.posted.clear();jobs.forEach(Runnable::run);}
 public static void main(String[] args){
  for(boolean dark:new boolean[]{false,true}) {
   Window w=new Window();Drawable original=w.decor.background;UiAppearance a=new UiAppearance();a.dark=dark;
   SharedPreferences p=new SharedPreferences();p.enabled=false;View card=new View();card.card=true;card.background.alpha=240;w.decor.children.add(card);
   Drawable originalCard=card.background,originalEdge=card.foreground;
   GlassTabTrack tab=new GlassTabTrack();w.decor.children.add(tab);
   Session s=new Session(w,a,p);s.start();
   check(!s.active&&w.radius==0&&w.decor.background==original);
   p.set(true);flush(w);check(s.active&&w.radius==80&&w.attrs.radius==20);
   check(card.background.alpha<240&&w.decor.alpha==1);
   check(card.elevation==0&&card.foreground==null&&tab.outlined);
   Drawable outlined=card.background;s.layoutListener.onGlobalLayout();check(card.background==outlined);
   w.context.manager.emit(false);check(!s.active&&w.radius==0&&w.attrs.radius==0&&w.decor.background==original);
   check(card.background.alpha==240&&w.attrs.dimAmount==.6f&&w.attrs.flags==0&&p.enabled);
   check(card.background==originalCard&&card.foreground==originalEdge&&card.elevation==6&&!tab.outlined);
   w.context.manager.emit(true);check(s.active&&w.radius==80);
   p.set(false);flush(w);check(!s.active&&w.decor.background==original&&card.background.alpha==240);
   p.enabled=true;s.update(true);ValueAnimator animation=s.animator;animation.tick(.5f);
   check(w.radius==40&&w.attrs.radius==10&&w.decor.alpha==.5f&&w.decor.y==0);
   s.onViewDetachedFromWindow(w.decor);
   check(animation.cancelled&&w.decor.alpha==1&&w.decor.y==0&&w.radius==0);
   check(p.listeners.isEmpty()&&w.context.manager.listeners.isEmpty()&&w.decor.observer.listeners.isEmpty());
   check(card.background.alpha==240&&w.attrs.windowAnimations==9);
   animation.tick(.8f);check(w.radius==0&&w.decor.alpha==1);
   s.onViewAttachedToWindow(w.decor);check(s.active&&p.listeners.size()==1&&w.context.manager.listeners.size()==1);
   p.set(false);s.onViewDetachedFromWindow(w.decor);flush(w);check(!s.active&&w.radius==0);
   ValueAnimator.enabled=false;s.onViewAttachedToWindow(w.decor);p.set(true);flush(w);
   check(s.active&&s.animator==null&&w.decor.alpha==1&&w.radius==80);
   View late=new View();late.card=true;w.decor.children.add(late);s.layoutListener.onGlobalLayout();
   check(late.background.alpha<255);s.onViewDetachedFromWindow(w.decor);check(late.background.alpha==255);
   ValueAnimator.enabled=true;
  }
  Window fresh=new Window();SharedPreferences absent=new SharedPreferences();ValueAnimator.enabled=false;
  Session defaults=new Session(fresh,new UiAppearance(),absent);defaults.start();
  check(defaults.active&&fresh.radius==80&&absent.enabled==null);
  absent.set(false);flush(fresh);check(!defaults.active&&fresh.radius==0);
  defaults.onViewDetachedFromWindow(fresh.decor);defaults.onViewAttachedToWindow(fresh.decor);
  check(!defaults.active&&!absent.enabled);defaults.onViewDetachedFromWindow(fresh.decor);
  System.out.println("Frosted menu: "+checks+" lifecycle and fallback checks passed");
 }
}
'''
harness = harness.replace('CARD_SNAPSHOT', card_snapshot)
with tempfile.TemporaryDirectory(prefix='newsoul-frosted-test-') as tmp:
    path = Path(tmp) / 'FrostedMenuTest.java'
    path.write_text(harness)
    subprocess.run(['javac', str(path)], check=True)
    subprocess.run(['java', '-cp', tmp, 'FrostedMenuTest'], check=True)
