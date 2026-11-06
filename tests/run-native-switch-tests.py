#!/usr/bin/env python3
"""Exercise the production adapter against a tiny host double; no Android/device effects."""
from pathlib import Path
import subprocess
import tempfile
_test_dir = tempfile.TemporaryDirectory(prefix='newsoul-native-switch-')
out = Path(_test_dir.name)
src = out / 'src'
files = {
'android/content/Context.java': '''package android.content; public class Context { public ClassLoader getClassLoader(){return getClass().getClassLoader();} }''',
'android/animation/ValueAnimator.java': '''package android.animation; public class ValueAnimator { public static boolean enabled=true; public static boolean areAnimatorsEnabled(){return enabled;} }''',
'android/util/Log.java': '''package android.util; public class Log { public static int w(String a,String b,Throwable t){return 0;} }''',
'android/view/Gravity.java': '''package android.view; public class Gravity {public static final int CENTER=17;}''',
'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {}''',
'android/view/View.java': '''package android.view; public class View {
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2;
 public boolean attached,laid,shown=true,enabled=true; public CharSequence description; public OnClickListener click;
 public interface OnClickListener {void onClick(View v);}
 public boolean isAttachedToWindow(){return attached;} public boolean isLaidOut(){return laid;} public boolean isShown(){return shown;}
 public void setEnabled(boolean v){enabled=v;} public boolean isEnabled(){return enabled;}
 public void setContentDescription(CharSequence s){description=s;}
 public void setMinimumWidth(int x){} public void setMinimumHeight(int x){} public void setImportantForAccessibility(int x){}
 public void setOnClickListener(OnClickListener l){click=l;} public boolean dispatchTouchEvent(MotionEvent e){return false;}
 protected void onDetachedFromWindow(){} public void jumpDrawablesToCurrentState(){}
}''',
'android/widget/Checkable.java': '''package android.widget; public interface Checkable { boolean isChecked(); void setChecked(boolean b); void toggle(); }''',
'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout extends android.view.View {
 public android.view.View child; public FrameLayout(android.content.Context c){} public void addView(android.view.View v,LayoutParams p){child=v;}
 public static class LayoutParams {public LayoutParams(int w,int h,int g){}}
}''',
'android/widget/CompoundButton.java': '''package android.widget; public class CompoundButton extends android.view.View implements Checkable {
 protected boolean checked; protected OnCheckedChangeListener listener;
 public interface OnCheckedChangeListener {void onCheckedChanged(CompoundButton b,boolean c);}
 public void setOnCheckedChangeListener(OnCheckedChangeListener l){listener=l;}
 public boolean isChecked(){return checked;} public void setChecked(boolean b){boolean changed=b!=checked;checked=b;if(changed&&listener!=null)listener.onCheckedChanged(this,b);}
 public void toggle(){setChecked(!checked);}
}''',
'android/widget/Switch.java': '''package android.widget; public class Switch extends CompoundButton {public Switch(android.content.Context c){}}''',
'cn/android/lib/soul_view/switchcompat/SoulSwitchCompat.java': '''package cn.android.lib.soul_view.switchcompat;
 public class SoulSwitchCompat extends android.widget.CompoundButton {
 public static boolean fail; public int animated,immediate,tint;
 public SoulSwitchCompat(android.content.Context c){if(fail)throw new IllegalStateException("missing host");}
 public void setTintColor(int c){tint=c;}
 public void setChecked(boolean b){if(b!=checked)animated++;super.setChecked(b);}
 public void setCheckedImmediatelyNoEvent(boolean b){checked=b;immediate++;}
 }'''
}
production = Path('app/src/main/java/dev/local/newsoul/SettingsUi.java').read_text()
a = production.index('    static final class MenuSwitch extends FrameLayout')
b = production.index('    /** A single conditional row;', a)
files['dev/local/newsoul/SettingsUi.java'] = '''package dev.local.newsoul;
import android.content.Context; import android.view.View; import android.view.Gravity; import android.widget.*;
class SettingsUi { static final String TAG="test"; static final int ACCENT=0xff00bfa9;
 static int dp(Context c,int v){return v;}
 static class Appearance {void switchControl(Switch v){}}
 static class MenuPanel {Context context=new Context(),activity=new Context(); Appearance appearance=new Appearance();}
''' + production[a:b] + '\n}'
files['dev/local/newsoul/NativeSwitchTest.java'] = '''package dev.local.newsoul;
import cn.android.lib.soul_view.switchcompat.SoulSwitchCompat;
class NativeSwitchTest {
 static int n; static void check(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 public static void main(String[]args){
  SettingsUi.MenuSwitch s=new SettingsUi.MenuSwitch(new SettingsUi.MenuPanel());
  check(s.child instanceof SoulSwitchCompat); SoulSwitchCompat host=(SoulSwitchCompat)s.child;
  int[] events={0}, observations={0}; s.setOnCheckedChangeListener((b,v)->events[0]++);s.observe(()->observations[0]++);
  s.setChecked(true);check(s.isChecked()&&events[0]==1&&host.animated==0);
  s.setChecked(true);check(events[0]==1);
  s.attached=s.laid=true;s.toggle();check(!s.isChecked()&&events[0]==2&&host.animated==1);
  host.toggle();check(s.isChecked()&&events[0]==3);
  s.setEnabled(false);s.toggle();check(!host.isEnabled()&&s.isChecked()&&events[0]==3);
  s.setEnabled(true);check(host.isEnabled());
  android.animation.ValueAnimator.enabled=false;s.setChecked(false);check(!s.isChecked()&&events[0]==4&&host.animated==2);
  s.setContentDescription("test");check("test".contentEquals(host.description));
  int before=events[0];s.onDetachedFromWindow();check(events[0]==before&&observations[0]>4);
  SoulSwitchCompat.fail=true;SettingsUi.MenuSwitch fallback=new SettingsUi.MenuSwitch(new SettingsUi.MenuPanel());
  check(fallback.child instanceof android.widget.Switch);int[] f={0};fallback.setOnCheckedChangeListener((b,v)->f[0]++);
  fallback.setChecked(true);fallback.setChecked(true);check(fallback.isChecked()&&f[0]==1);
  System.out.println("Native switch adapter: "+n+" callback, lifecycle, animation-path and fallback checks passed");
 }
}'''
for name, text in files.items():
    p = src / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(out/'classes'), *map(str, src.rglob('*.java'))], check=True)
subprocess.run(['java', '-cp', str(out/'classes'), 'dev.local.newsoul.NativeSwitchTest'], check=True)
