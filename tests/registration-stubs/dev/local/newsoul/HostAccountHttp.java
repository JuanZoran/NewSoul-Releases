package dev.local.newsoul;
import java.util.concurrent.*;import org.json.JSONObject;
final class HostAccountHttp {
 static volatile String user="me";static boolean available=true;static volatile int calls;static volatile Object days=8,gender="FEMALE";static boolean fail;
 static CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
 static String account(){return user;}static boolean available(){return available;}
 static android.content.Context context(){return new android.content.Context();}
 static Object field(Object o,String key)throws Exception{return o.getClass().getField(key).get(o);}
 static JSONObject request(String a,String u,boolean p,HostRequestScope scope)throws Exception{
  try(HostRequestScope ignored=scope){
   CountDownLatch wait=release;
   scope.attach(wait::countDown,()->a.equals(user));
   calls++;started.countDown();wait.await();scope.check();
   if(fail)throw new Exception("failed");
   return new JSONObject().put("code",10001).put("data",new JSONObject().put("registerDay",days).put("gender",gender));
  }
 }
}
