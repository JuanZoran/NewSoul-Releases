"""Run production launcher persistence with Android's memory-before-disk failure semantics."""
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sources = {
'android/content/SharedPreferences.java': '''package android.content;
import java.util.Map; import java.util.Set;
public interface SharedPreferences {
Map<String,?> getAll(); String getString(String k,String d); Set<String> getStringSet(String k,Set<String> d);
int getInt(String k,int d); long getLong(String k,long d); float getFloat(String k,float d);
boolean getBoolean(String k,boolean d); boolean contains(String k); Editor edit();
void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences p,String k); }
interface Editor { Editor putString(String k,String v); Editor putStringSet(String k,Set<String> v);
Editor putInt(String k,int v); Editor putLong(String k,long v); Editor putFloat(String k,float v);
Editor putBoolean(String k,boolean v); Editor remove(String k); Editor clear(); boolean commit(); void apply(); } }''',
'android/content/Context.java': '''package android.content;
public abstract class Context { public static final int MODE_PRIVATE=0;
public abstract SharedPreferences getSharedPreferences(String name,int mode);
public abstract android.content.pm.PackageManager getPackageManager();
public String getPackageName(){return "test.module";} }''',
'android/content/ComponentName.java': '''package android.content;
public class ComponentName { public ComponentName(String p,String n){} }''',
'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager { public static final int COMPONENT_ENABLED_STATE_ENABLED=1, COMPONENT_ENABLED_STATE_DISABLED=2,DONT_KILL_APP=1;
public int state=0,calls=0; public boolean fail;
public int getComponentEnabledSetting(android.content.ComponentName c){return state;}
public void setComponentEnabledSetting(android.content.ComponentName c,int value,int flags){
if(fail)throw new SecurityException(); state=value;calls++;} }''',
'android/app/Application.java': '''package android.app;
public class Application extends android.content.Context { public void onCreate(){}
public android.content.SharedPreferences getSharedPreferences(String n,int m){return null;}
public android.content.pm.PackageManager getPackageManager(){return null;} }''',
'android/util/Log.java': '''package android.util;
public class Log { public static int w(String tag,String message,Throwable t){return 0;} }''',
'android/os/Looper.java': '''package android.os;
public class Looper { static final Looper MAIN=new Looper();
public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return MAIN;} }''',
'android/os/Handler.java': '''package android.os;
public class Handler { public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;} }''',
'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION { public static int SDK_INT=35; } }''',
'dev/local/newsoul/LauncherTest.java': '''package dev.local.newsoul;
import android.content.*; import android.content.pm.PackageManager; import java.util.*;
public class LauncherTest {
static int checks;
static void expect(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
static class Fake extends Context implements SharedPreferences {
final PackageManager pm=new PackageManager(); final Map<String,Object> memory=new HashMap<>();
boolean writeFail, restoreFail;
public SharedPreferences getSharedPreferences(String n,int m){return this;}
public PackageManager getPackageManager(){return pm;}
public Map<String,?> getAll(){return new HashMap<>(memory);}
public String getString(String k,String d){return (String)memory.getOrDefault(k,d);}
@SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String>d){return (Set<String>)memory.getOrDefault(k,d);}
public int getInt(String k,int d){return (Integer)memory.getOrDefault(k,d);}
public long getLong(String k,long d){return (Long)memory.getOrDefault(k,d);}
public float getFloat(String k,float d){return (Float)memory.getOrDefault(k,d);}
public boolean getBoolean(String k,boolean d){return (Boolean)memory.getOrDefault(k,d);}
public boolean contains(String k){return memory.containsKey(k);}
public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
public Editor edit(){return new Editor(){
final Map<String,Object> pending=new HashMap<>(); boolean clearing;
public Editor putString(String k,String v){pending.put(k,v);return this;}
public Editor putStringSet(String k,Set<String>v){pending.put(k,v);return this;}
public Editor putInt(String k,int v){pending.put(k,v);return this;}
public Editor putLong(String k,long v){pending.put(k,v);return this;}
public Editor putFloat(String k,float v){pending.put(k,v);return this;}
public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
public Editor remove(String k){pending.put(k,null);return this;}
public Editor clear(){clearing=true;return this;}
void commitToMemory(){if(clearing)memory.clear();for(Map.Entry<String,Object>e:pending.entrySet()){
if(e.getValue()==null)memory.remove(e.getKey());else memory.put(e.getKey(),e.getValue());}}
public boolean commit(){commitToMemory();return !writeFail;}
public void apply(){if(restoreFail)throw new IllegalStateException("restore failed");commitToMemory();}
};}
}
static void failed(Fake c,boolean visible){try{HomeApplication.setVisible(c,visible);throw new AssertionError("write unexpectedly accepted");}catch(IllegalStateException expected){}}
public static void main(String[] args){
Fake c=new Fake();
HomeApplication.initializeLauncher(c); expect(c.pm.state==2);
c.pm.state=1; HomeApplication.initializeLauncher(c); expect(c.pm.state==2);
HomeApplication.setVisible(c,true); expect(c.pm.state==1 && c.getBoolean("user_visible",false));
HomeApplication.initializeLauncher(c); expect(c.pm.state==1);
HomeApplication.setVisible(c,false); expect(c.pm.state==2 && !c.getBoolean("user_visible",true));
HomeApplication.initializeLauncher(c); expect(c.pm.state==2);
int calls=c.pm.calls; HomeApplication.initializeLauncher(c); expect(c.pm.calls==calls);
c.writeFail=true; failed(c,true); expect(c.pm.state==2 && !c.getBoolean("user_visible",true));
HomeApplication.initializeLauncher(c); expect(c.pm.state==2 && c.pm.calls==calls);
c.writeFail=false; HomeApplication.setVisible(c,true); expect(c.pm.state==1);
c.writeFail=true; failed(c,false); HomeApplication.initializeLauncher(c);
expect(c.pm.state==1 && c.getBoolean("user_visible",false));
c.writeFail=false; HomeApplication.setVisible(c,false);c.pm.fail=true;
try{HomeApplication.setVisible(c,true);throw new AssertionError();}catch(SecurityException expected){}
expect(c.pm.state==2 && !c.getBoolean("user_visible",true));
c.pm.fail=false; HomeApplication.initializeLauncher(c);expect(c.pm.state==2);
// Even an unsuccessful raw-store restore must not change the accepted runtime choice.
c.writeFail=true;c.restoreFail=true;failed(c,true);expect(c.getBoolean("user_visible",false));
HomeApplication.initializeLauncher(c);expect(c.pm.state==2);
c.writeFail=false;c.restoreFail=false;
TransactionalPreferences.wrap(c).edit().putBoolean("unrelated",true).commit();
expect(!c.getBoolean("user_visible",true) && c.getBoolean("unrelated",false));
HomeApplication.initializeLauncher(c);expect(c.pm.state==2);
System.out.println("Launcher default/persistence/failure checks: "+checks);
} }'''
}
with tempfile.TemporaryDirectory(prefix='newsoul-launcher-') as folder:
    base = Path(folder)
    for name, text in sources.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
    subprocess.run(['javac', '-d', str(base / 'classes'), *[str(base / n) for n in sources],
        str(root / 'app/src/main/java/dev/local/newsoul/TransactionalPreferences.java'),
        str(root / 'app/src/main/java/dev/local/newsoul/HomeApplication.java')], check=True)
    subprocess.run(['java', '-cp', str(base / 'classes'), 'dev.local.newsoul.LauncherTest'], check=True)
