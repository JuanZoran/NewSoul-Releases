package dev.local.newsoul;
import android.app.Activity;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.*;
public class LocationUiTest {
 static int checks,clicks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static class Model {boolean city;Model(boolean c){city=c;}public boolean isSameCityTab(){return city;}}
 public static class Adapter {public List<Model> models=new ArrayList<>(List.of(new Model(false),new Model(true)));public int getCount(){return models.size();}public Model b(int i){return models.get(i);}}
 public static class Tab {View custom=new View();Tab(){custom.child=new View();}public View getCustomView(){return custom;}}
 public static class Tabs {List<Tab> items=new ArrayList<>(List.of(new Tab(),new Tab()));public int getTabCount(){return items.size();}public Tab getTabAt(int i){return items.get(i);}}
 public static class Fragment {public void initTabLayout(){}public void onResume(){}Adapter n=new Adapter();Tabs o=new Tabs();Activity activity=new Activity();boolean resumed=true;public boolean isResumed(){return resumed;}public Activity getActivity(){return activity;}}
 public static class NewFragment extends Fragment {Adapter n=new Adapter();Tabs o=new Tabs();public void initTabLayout(){}public void onResume(){}}
 public static class Account {static String id="owner";public static String g(){return id;}}
 public static class Coordinates {public double lat=31.23,lng=121.47;}
 public static class LegacyCoordinates {public double latitude=31.23,longitude=121.47;}
 public static class LegacyPoi {public Object location=new LegacyCoordinates();public String name="人民广场",city="上海市",address="上海市黄浦区",uid="289";public int distance=-2;}
 public static class Poi {public Object location=new Coordinates();public String title="人民广场",city="上海市",province="",country="",code="",bdCityCode="",address="上海市黄浦区";}
 public static class Nearby {public interface OnPoiItemClickListener{void onPoiItemClick(Poi p);}OnPoiItemClickListener listener;public void e(OnPoiItemClickListener l){listener=l;}}
 public static class Search {public interface OnPoiItemClickListener{void onPoiItemClick(Poi p);}OnPoiItemClickListener listener;public void d(OnPoiItemClickListener l){listener=l;}}
 public static class Presenter {Activity a;Nearby g=new Nearby();Search h=new Search();int nativeSelections;Presenter(Activity screen){a=screen;}public void A(){g=new Nearby();h=new Search();}public void F(Poi p){nativeSelections++;}}
 public static class Picker extends Activity {Presenter presenter=new Presenter(this);public void bindEvent(){}}
 static final Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
 static Object invoke(Method m,Object receiver,Object... args)throws Throwable {
  m.setAccessible(true);
  return hooks.get(m).intercept(proxy(XposedInterface.Chain.class,(p,method,a)->switch(method.getName()){
   case "getThisObject"->receiver;case "getArg"->args[(int)a[0]];
   case "proceed"->m.invoke(receiver,args);default->throw new AssertionError(method);
  }));
 }
 static void set(String n,Object v)throws Exception{Field f=LocationOverridePicker.class.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}
 static Object get(String n)throws Exception{Field f=LocationOverridePicker.class.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
 static void select(Activity a,Object poi)throws Exception{Method m=LocationOverridePicker.class.getDeclaredMethod("choose",Activity.class,Object.class);m.setAccessible(true);m.invoke(null,a,poi);}
 static Activity picker(Activity parent){LocationOverridePicker.open(parent);Activity a=new Activity();a.intent=parent.started;return a;}
 public static void main(String[] args)throws Throwable{
  Method bind=LocationCityEntry.class.getDeclaredMethod("bind",Object.class);bind.setAccessible(true);
  Fragment fragment=new Fragment();View city=fragment.o.items.get(1).custom;View recommend=fragment.o.items.get(0).custom;
  city.setOnClickListener(v->clicks++);bind.invoke(null,fragment);
  check(recommend.longClick==null,"recommend unchanged");check(city.child.longClick==null,"child label does not intercept native short tap");check(city.performLongClick()&&LocationOverrideUi.opens==1,"semantic city opens panel");
  city.performClick();check(clicks==1,"native short click retained");
  fragment.resumed=false;check(!city.performLongClick(),"paused fragment rejected");fragment.resumed=true;
  city.attached=false;check(!city.performLongClick(),"detached view rejected");city.attached=true;
  fragment.activity.finishing=true;check(!city.performLongClick(),"finishing activity rejected");fragment.activity.finishing=false;
  fragment.o.items.set(1,new Tab());check(!city.performLongClick(),"old label at reused position rejected");bind.invoke(null,fragment);
  check(city.longClick==null&&!city.longClickable,"old city binding removed");city.performClick();check(clicks==2,"cleanup preserves native click");
  View fresh=fragment.o.items.get(1).custom;check(fresh.performLongClick(),"replacement city bound");
  fragment.n.models.get(1).city=false;check(!fresh.performLongClick(),"changed semantic type rejected before rebind");bind.invoke(null,fragment);check(fresh.longClick==null,"recycled native tab cleaned");
  fragment.n.models.get(0).city=true;bind.invoke(null,fragment);check(recommend.performLongClick(),"city can move to another position");
  LocationOverridePicker.ready=true;set("account",Account.class.getMethod("g"));set("picker",Activity.class);
  Activity parent=new Activity();Activity a=picker(parent);select(a,new Poi());
  LocationOverridePoint point=LocationOverridePoint.decode((String)SettingsUi.values.get(LocationOverridePoint.POINT));
  check(a.finishing&&point.latitude==31.23&&point.city.equals("上海市"),"native POI saves selected coordinates");
  check(!SettingsUi.values.containsKey(LocationOverridePoint.ENABLED),"selection never implicitly enables override");
  check(point.country.isEmpty()&&point.province.isEmpty(),"missing POI metadata never copies real place");
  String saved=(String)SettingsUi.values.get(LocationOverridePoint.POINT);
  a=picker(parent);Account.id="different";Poi other=new Poi();other.title="不同地点";select(a,other);check(a.finishing&&saved.equals(SettingsUi.values.get(LocationOverridePoint.POINT)),"account switch rejects selection");Account.id="owner";
  Activity stale=picker(parent);picker(parent);select(stale,other);check(stale.finishing&&saved.equals(SettingsUi.values.get(LocationOverridePoint.POINT)),"stale picker token rejected");
  a=picker(parent);Poi bad=new Poi();bad.location=null;select(a,bad);check(!a.finishing&&saved.equals(SettingsUi.values.get(LocationOverridePoint.POINT)),"invalid POI stays open and preserves saved point");
  SettingsUi.save=false;select(a,other);check(!a.finishing&&saved.equals(SettingsUi.values.get(LocationOverridePoint.POINT)),"failed save stays open");SettingsUi.save=true;
  select(a,other);check(a.finishing,"valid retry succeeds");
  XposedModule module=new XposedModule(){};
  module.attachFramework(proxy(XposedInterface.class,(p,m,ar)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){hooks.put((Executable)ar[0],(XposedInterface.Hooker)ba[0]);return null;}return b;});
   if(m.getName().equals("deoptimize"))return true;return null;
  }),()->{});
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{
   if(n.equals(HostBindings.FRAGMENT))return Object.class;
   if(n.endsWith("NewPoiActivityB"))return Picker.class;
   if(n.endsWith("position.b"))return Presenter.class;
   if(n.endsWith("bean.Poi"))return Poi.class;
   if(n.endsWith("imlib.j0"))return Account.class;
   if(n.endsWith("position.PoiChatAdapterB"))return Nearby.class;
   if(n.endsWith("position.PoiChatSearchAdapterB"))return Search.class;
   return super.loadClass(n);
  }};
  LocationOverridePicker.install(module,loader);check(LocationOverridePicker.ready&&hooks.size()==3,"picker hooks registered");
  Method select=Presenter.class.getMethod("F",Poi.class);
  Picker nativeScreen=new Picker();invoke(select,nativeScreen.presenter,new Poi());check(nativeScreen.presenter.nativeSelections==1,"ordinary picker preserves host selection event");
  LocationOverridePicker.open(parent);Picker custom=new Picker();custom.intent=parent.started;
  invoke(Picker.class.getMethod("bindEvent"),custom);check(custom.presenter.g.listener!=null&&custom.presenter.h.listener!=null,"both native lists receive owned callbacks");
  invoke(Presenter.class.getMethod("A"),custom.presenter);check(custom.presenter.g.listener!=null&&custom.presenter.h.listener!=null,"adapter recreation rebinds owned callbacks");
  custom.presenter.h.listener.onPoiItemClick(new Poi());check(custom.finishing&&custom.presenter.nativeSelections==0,"search callback saves without original host event");
  LocationOverridePicker.open(parent);custom=new Picker();custom.intent=parent.started;
  invoke(select,custom.presenter,new Poi());check(custom.finishing&&custom.presenter.nativeSelections==0,"fallback selection hook suppresses host event");
  invoke(select,custom.presenter,new Poi());check(custom.presenter.nativeSelections==0,"stale marked picker never emits host event");
  ClassLoader cityLoader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){
   case HostBindings.FRAGMENT->Object.class;
   case "cn.soulapp.android.component.square.main.SquareFragment"->Fragment.class;
   case "cn.soulapp.android.component.square.main.newui.SquareFragmentV2"->NewFragment.class;
   case "cn.soulapp.android.component.square.bean.SquareMainTab"->Model.class;
   default->super.loadClass(n);
  };}};
  hooks.clear();LocationCityEntry.install(module,cityLoader);check(LocationCityEntry.ready&&hooks.size()==4,"both square implementations installed");
  for(Fragment screen:new Fragment[]{new Fragment(),new NewFragment()}){
   invoke(screen.getClass().getDeclaredMethod("initTabLayout"),screen);
   Tabs tabs=(Tabs)LocationOverridePicker.field(screen,"o");
   View target=tabs.items.get(1).custom;
   check(target.performLongClick(),"actual lifecycle hook binds city in each implementation");
   check(target.child.longClick==null,"lifecycle leaves native text non-intercepting");
  }
  hooks.clear();LocationCityEntry.install(module,new ClassLoader(cityLoader){public Class<?> loadClass(String n)throws ClassNotFoundException{
   if(n.equals("cn.soulapp.android.component.square.main.newui.SquareFragmentV2"))throw new ClassNotFoundException(n);
   return cityLoader.loadClass(n);
  }});check(LocationCityEntry.ready&&hooks.size()==2,"one missing variant does not block other variant");
  LegacyPoi oldPoi=new LegacyPoi();LocationOverridePoint oldPoint=LocationOverridePicker.fromLegacyPoi(oldPoi);
  check(oldPoint.latitude==31.23&&oldPoint.longitude==121.47&&oldPoint.title.equals("人民广场"),"legacy Baidu coordinates preserved");
  check(oldPoint.cityCode.equals("289")&&oldPoint.country.isEmpty()&&oldPoint.province.isEmpty(),"legacy city code without invented metadata");
  oldPoi.distance=50;check(LocationOverridePicker.fromLegacyPoi(oldPoi).cityCode.isEmpty(),"ordinary POI UID is not a city code");
  oldPoi.location=null;try{LocationOverridePicker.fromLegacyPoi(oldPoi);throw new AssertionError("missing old coordinates accepted");}catch(IllegalArgumentException expected){checks++;}
  permissionStart();
  System.out.println("Location UI: "+checks+" checks passed; no device interaction");
 }
 static void permissionStart()throws Exception{
  set("legacy",true);set("picker",Activity.class);set("account",Account.class.getMethod("g"));LocationOverridePicker.ready=true;
  Map<String,Object> saved=new HashMap<>(SettingsUi.values);
  Activity denied=new Activity();set("token","previous-token");set("owner","previous-owner");
  int toasts=android.widget.Toast.shows;
  LocationOverridePicker.open(denied);
  check(denied.started==null&&denied.starts==0,"legacy denied permissions never start the native loading page");
  check(get("token")==null&&get("owner")==null,"denied open clears prior picker ownership");
  check(android.widget.Toast.shows==toasts+1&&"请先在系统设置中允许 Soul 定位".equals(android.widget.Toast.lastText),"denied open gives the system settings next step once");
  check(!denied.coarsePermission&&!denied.finePermission,"denied open does not change permissions");
  denied.coarsePermission=true;LocationOverridePicker.open(denied);
  check(denied.starts==1&&denied.started!=null,"coarse permission restoration permits retry on the same activity");
  check(denied.started.getStringExtra("dev.local.newsoul.location_picker_token").equals(get("token"))&&"owner".equals(get("owner")),"authorized old launch has fresh account-scoped ownership");
  check("".equals(denied.started.getStringExtra("pos_name"))&&!denied.started.getBooleanExtra("fromPop",true),"authorized old launch preserves native extras");
  check(android.widget.Toast.shows==toasts+1,"authorized retry adds no failure feedback");
  Activity fineOnly=new Activity();fineOnly.finePermission=true;LocationOverridePicker.open(fineOnly);
  check(fineOnly.starts==1,"fine permission alone also permits old native picker");
  Activity modern=new Activity();set("legacy",false);LocationOverridePicker.open(modern);
  check(modern.starts==1&&modern.started!=null,"modern picker keeps its existing launch behavior without permissions");
  check(modern.permissionChecks==0,"modern picker does not query old location prerequisites");
  check("".equals(modern.started.getStringExtra("pos_name"))&&!modern.started.getBooleanExtra("fromPop",true),"modern launch extras remain unchanged");
  check(saved.equals(SettingsUi.values),"opening and rejecting a picker never changes the saved location or switch");
 }
}
