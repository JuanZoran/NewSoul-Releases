package dev.local.newsoul;
import android.content.*;
import java.lang.reflect.*;
import java.util.*;
final class SettingsUi {
 static final Map<String,Object> values=new HashMap<>();
 static SharedPreferences preferences(Context c) {
  return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->switch(m.getName()){
   case "getAll"->new HashMap<>(values);
   case "getBoolean","getString","getInt"->values.getOrDefault(a[0],a[1]);
   default->throw new AssertionError(m);
  });
 }
}
