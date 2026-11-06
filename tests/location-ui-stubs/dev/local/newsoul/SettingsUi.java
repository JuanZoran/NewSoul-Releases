package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Stable disk fixture; production callers read/write through the real transaction wrapper. */
final class SettingsUi {
 static final Map<String,Object> values=new HashMap<>();
 static boolean save=true;
 private static final SharedPreferences disk=new Store();
 static SharedPreferences preferences(Context context) { return TransactionalPreferences.wrap(disk); }
 private static Object copy(Object value) { return value instanceof Set ? new HashSet<>((Set<?>)value) : value; }
 private static final class Store implements SharedPreferences {
  public Map<String,?> getAll(){Map<String,Object> result=new HashMap<>();values.forEach((key,value)->result.put(key,copy(value)));return result;}
  public String getString(String key,String fallback){return (String)values.getOrDefault(key,fallback);}
  @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback){return (Set<String>)copy(values.getOrDefault(key,fallback));}
  public int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}
  public long getLong(String key,long fallback){return (Long)values.getOrDefault(key,fallback);}
  public float getFloat(String key,float fallback){return (Float)values.getOrDefault(key,fallback);}
  public boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}
  public boolean contains(String key){return values.containsKey(key);}
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
  public Editor edit(){return new Changes();}
 }
 private static final class Changes implements SharedPreferences.Editor {
  private final Map<String,Object> changes=new HashMap<>();private boolean clear;
  private SharedPreferences.Editor put(String key,Object value){changes.put(key,copy(value));return this;}
  public SharedPreferences.Editor putBoolean(String key,boolean value){return put(key,value);}
  public SharedPreferences.Editor putInt(String key,int value){return put(key,value);}
  public SharedPreferences.Editor putLong(String key,long value){return put(key,value);}
  public SharedPreferences.Editor putFloat(String key,float value){return put(key,value);}
  public SharedPreferences.Editor putString(String key,String value){return put(key,value);}
  public SharedPreferences.Editor putStringSet(String key,Set<String> value){return put(key,value);}
  public SharedPreferences.Editor remove(String key){return put(key,null);}
  public SharedPreferences.Editor clear(){clear=true;return this;}
  public boolean commit(){apply();return save;}
  public void apply(){
   if(clear)values.clear();
   changes.forEach((key,value)->{if(value==null)values.remove(key);else values.put(key,copy(value));});
   changes.clear();clear=false;
  }
 }
}
