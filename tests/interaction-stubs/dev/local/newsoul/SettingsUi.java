package dev.local.newsoul;
import android.content.*;import java.util.*;import java.lang.reflect.*;
final class SettingsUi {
 static final Map<String,Boolean> values=new HashMap<>();
 static SharedPreferences preferences(Context c){return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
  if(m.getName().equals("getBoolean"))return values.getOrDefault((String)a[0],(Boolean)a[1]);throw new AssertionError(m);
 });}
}
