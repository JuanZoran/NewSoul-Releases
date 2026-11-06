package android.content;
public class Intent {
 public final java.util.Map<String,Object> extras=new java.util.HashMap<>();
 public Intent(Context context,Class<?> target){}
 public Intent putExtra(String key,String value){extras.put(key,value);return this;}
 public Intent putExtra(String key,boolean value){extras.put(key,value);return this;}
 public String getStringExtra(String key){return (String)extras.get(key);}
 public boolean hasExtra(String key){return extras.containsKey(key);}
}
