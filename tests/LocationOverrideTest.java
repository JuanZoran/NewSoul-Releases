package dev.local.newsoul;
import android.content.Context;
import android.os.Handler;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.*;

/** Executes production location hooks with an inert service and controllable main-thread queue. */
public class LocationOverrideTest {
 static final Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static class Access {public static Context context(){return new Context();}}
 public static class Data {
  public String latitude,longitude,city,province,country,adCode,cityCode,district,countryCode;
  public int where;public long time;public float accuracy;
  public Data(String lat,String lng){latitude=lat;longitude=lng;}
  public void setCity(String s){city=s;}public void setProvince(String s){province=s;}public void setCountry(String s){country=s;}
  public void setAdCode(String s){adCode=s;}public void setCityCode(String s){cityCode=s;}
  public void setDistrictName(String s){district=s;}public void setCountryCode(String s){countryCode=s;}
  public void setLocationWhere(int n){where=n;}public void setCacheTime(long n){time=n;}public void setHorizontalAccuracy(float n){accuracy=n;}
 }
 public static class Listener {int calls,code;Data last;public boolean onLocated(int c,Data d){calls++;code=c;last=d;return true;}}
 public static class Option {}
 public static class Service {
  final Data real=new Data("39.9","116.4");int reads,requests,stops,dispatches;Data dispatchData;
  public Data getLastLocation(){reads++;return real;}private Data getInnerLastLocation(){reads++;return real;}public Data getMLastLocation(){reads++;return real;}
  public void getLocationWithCache(Listener l){requests++;if(l!=null)l.onLocated(0,real);}
  public void startLocation(Listener l){requests++;if(l!=null)l.onLocated(0,real);}
  private void callback2Listener(int code,Data d){dispatches++;dispatchData=d;}
  public void stopLocation(Listener l){stops++;}
 }
 public static class Client {
  final Data real=new Data("39.9","116.4");int reads,requests,stops,dispatches;Data dispatchData;
  public Data getLastLocation(){reads++;return real;}private Data getInnerLastLocation(){reads++;return real;}public Data getMLastLocation(){reads++;return real;}
  public void getLocationWithCache(Listener l){requests++;if(l!=null)l.onLocated(0,real);}
  public void startLocation(Option o,Listener l){requests++;if(l!=null)l.onLocated(0,real);}
  private void startLocation(Option o,Listener l,boolean refresh){requests++;if(l!=null)l.onLocated(0,real);}
  private void callbackCachedLocation(Listener l){requests++;if(l!=null)l.onLocated(0,real);}
  private void callback2Listener(int code,Data d){dispatches++;dispatchData=d;}
  public void stopLocation(Listener l){stops++;}
 }
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
 static Object invoke(Method m,Object receiver,Object... args)throws Throwable {
  m.setAccessible(true);
  XposedInterface.Chain chain=proxy(XposedInterface.Chain.class,(p,method,a)->switch(method.getName()){
   case "getThisObject"->receiver;case "getArg"->args[(int)a[0]];case "getArgs"->Arrays.asList(args);
   case "proceed"->{try{yield m.invoke(receiver,a==null||a.length==0?args:(Object[])a[0]);}catch(InvocationTargetException e){throw e.getCause();}}
   default->throw new AssertionError(method);
  });
  return hooks.get(m).intercept(chain);
 }
 static LocationOverridePoint point(double lat,double lng,String title){return new LocationOverridePoint(lat,lng,title,"上海市","上海市","中国","310101","289");}
 static void store(LocationOverridePoint p)throws Exception{SettingsUi.values.put(LocationOverridePoint.POINT,p.encode());}
 static void enabled(boolean b){SettingsUi.values.put(LocationOverridePoint.ENABLED,b);}
 public static void main(String[] args)throws Throwable {
  LocationOverridePoint first=point(31.2304,121.4737,"测试地点");
  LocationOverridePoint round=LocationOverridePoint.decode(first.encode());check(round.latitude==first.latitude&&round.city.equals(first.city),"point round-trip");
  for(double[] a:new double[][]{{Double.NaN,1},{1,Double.POSITIVE_INFINITY},{91,1},{1,181},{0,1},{1,0},{Double.MIN_VALUE,1}}){
   try{point(a[0],a[1],"x");throw new AssertionError("bad coordinate accepted");}catch(IllegalArgumentException ok){checks++;}
  }
  try{LocationOverridePoint.decode(first.encode().replace("bd09ll","gcj02"));throw new AssertionError();}catch(IllegalArgumentException ok){checks++;}
  try{new LocationOverridePoint(1,2,"x","","","","","");throw new AssertionError();}catch(IllegalArgumentException ok){checks++;}
  check(new LocationOverridePoint(1,2,"x","y","","","","").locationWhere()==2,"missing country stays unknown");
  check(new LocationOverridePoint(1,2,"x","y","","France","","").locationWhere()==0,"foreign country preserved");
  XposedModule module=new XposedModule(){};
  module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{
    if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}return b;
   });
   if(m.getName().equals("deoptimize"))return true;return null;
  }),()->{});
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){
   case HostBindings.FRAGMENT->Object.class;
   case "cn.soulapp.android.lib.location.model.LocationData"->Data.class;
   case "cn.soulapp.android.lib.location.model.LocationOption"->Option.class;
   case "cn.soulapp.android.lib.location.LocationListener"->Listener.class;
   case "cn.soulapp.android.lib.location.bdmap.BDMapLocationServiceImpl"->Service.class;
   case "cn.soulapp.android.lib.lbs.bd.SoulBaiduLocationClient"->Client.class;
   default->super.loadClass(n);
  };}};
  LocationOverrideControls.install(module,loader);check(LocationOverrideControls.ready&&hooks.size()==16,"both service layers registered");
  Service service=new Service();Client client=new Client();
  Method getter=Client.class.getMethod("getLastLocation");
  check(invoke(getter,client)==client.real&&client.reads==1,"default off preserves getter");
  enabled(true);check(invoke(getter,client)==client.real,"missing point preserves getter");store(first);
  for(Object host:new Object[]{service,client})for(String n:new String[]{"getLastLocation","getInnerLastLocation","getMLastLocation"}){
   Data d=(Data)invoke(host.getClass().getDeclaredMethod(n),host);
   check(d.latitude.equals("31.2304")&&d.longitude.equals("121.4737")&&d.city.equals("上海市"),"all getters replace coordinates and city");
   check(d.where==1&&d.time>0&&d.district.isEmpty()&&d.countryCode.isEmpty(),"fresh metadata without real place leakage");
  }
  check(client.real.latitude.equals("39.9"),"real cache not mutated");
  Data one=(Data)invoke(getter,client),two=(Data)invoke(getter,client);check(one!=two,"independent result objects");
  Method request=Client.class.getMethod("startLocation",Option.class,Listener.class);
  Listener listener=new Listener();invoke(request,client,null,listener);check(listener.calls==0&&client.requests==0,"main callback queued without real request");
  store(point(30,120,"新地点"));Handler.drain();check(listener.calls==1&&listener.last.latitude.equals("30.0"),"queued callback uses latest target");
  invoke(request,client,null,listener);enabled(false);Handler.drain();check(listener.calls==2&&listener.last==client.real&&client.requests==1,"disable before delivery resumes real request once");
  enabled(true);invoke(request,client,null,listener);invoke(Client.class.getMethod("stopLocation",Listener.class),client,listener);Handler.drain();check(listener.calls==2&&client.stops==1,"cancel prevents pending delivery");
  for(Object host:new Object[]{service,client}){
   Method m=host.getClass().getDeclaredMethod("callback2Listener",int.class,Data.class);Data real=new Data("1","2");invoke(m,host,2,real);
   Data delivered=(Data)host.getClass().getDeclaredField("dispatchData").get(host);
   check(delivered!=real&&delivered.latitude.equals("30.0"),"in-flight real callback replaced");
  }
  for(Method m:hooks.keySet().stream().map(e->(Method)e).toList()){
   if(m.getName().equals("getLocationWithCache")||m.getName().equals("callbackCachedLocation")||m.getName().equals("startLocation")){
    Object host=m.getDeclaringClass()==Service.class?service:client;Listener l=new Listener();Object[] a=new Object[m.getParameterCount()];
    for(int i=0;i<a.length;i++)a[i]=m.getParameterTypes()[i]==Listener.class?l:m.getParameterTypes()[i]==boolean.class?true:null;
    invoke(m,host,a);Handler.drain();check(l.calls==1&&l.last.latitude.equals("30.0"),"each request path overridden");
   }
  }
  enabled(false);check(invoke(getter,client)==client.real,"switch off restores real getter");enabled(true);SettingsUi.values.put(LocationOverridePoint.POINT,"bad");
  check(invoke(getter,client)==client.real,"corrupt saved value preserves original behavior");
  check(DebugDiagnostics.events.stream().noneMatch(s->s.contains("31.2304")||s.contains("上海")),"diagnostics omit coordinates and place");
  System.out.println("LocationOverride: "+checks+" checks passed; no real GPS/network");
 }
}
