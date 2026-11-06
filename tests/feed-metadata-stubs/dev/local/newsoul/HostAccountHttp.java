package dev.local.newsoul;
import android.content.Context;
final class HostAccountHttp {
 static String user="me";
 static String account(){return user;}
 static Context context(){return new Context();}
 static Object field(Object value,String key)throws Exception{return value.getClass().getField(key).get(value);}
}
