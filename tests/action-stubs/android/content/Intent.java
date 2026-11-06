package android.content;
public class Intent {
 public final Class<?> target;public final java.util.Map<String,Object> extras=new java.util.HashMap<>();
 public Intent(Context c,Class<?> target){this.target=target;}
 public Intent putExtra(String k,String v){extras.put(k,v);return this;}
 public Intent putExtra(String k,boolean v){extras.put(k,v);return this;}
}
