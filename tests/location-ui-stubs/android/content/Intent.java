package android.content;
import java.util.*;
public class Intent {
 private final Map<String,Object> extras=new HashMap<>();
 public Intent(){} public Intent(Context c,Class<?> type){}
 public Intent putExtra(String key,String value){extras.put(key,value);return this;}
 public Intent putExtra(String key,boolean value){extras.put(key,value);return this;}
 public boolean hasExtra(String key){return extras.containsKey(key);}
 public String getStringExtra(String key){return (String)extras.get(key);}
 public boolean getBooleanExtra(String key,boolean fallback){Object value=extras.get(key);return value instanceof Boolean?(Boolean)value:fallback;}
}
