package dev.local.newsoul;
import android.content.*;import android.view.*;import android.os.Handler;import java.util.*;import java.lang.reflect.*;import io.github.libxposed.api.*;
public class CityFeedFilterTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static class Author{public int gender;Author(int n){gender=n;}}
 public static class Tag{public String name;}
 public enum Media { TEXT, IMAGE, VIDEO, IMG_VDO_MIX, AUDIO }
 public static class Post{public String alias,signature;public boolean brokenName;public String showName(){if(brokenName)throw new IllegalStateException("unavailable");return alias==null||alias.isEmpty()?signature:alias;}public String authorIdEcpt="peer";public Media type=Media.TEXT;public boolean superVIP,authorOnline;public Author postAuthorModel;public String content="",title="",contentTitle="";public List<Tag> tags=new ArrayList<>();Post(int n){postAuthorModel=new Author(n);}}
 public static class City extends Context{}
 public static class Holder{public View itemView;Holder(Context c){itemView=new View(c);}}
 public static class Square {public void d(Holder h,Object p){
  if(SquareIpLocation.visible.containsKey(h.itemView))throw new AssertionError("stale IP label survives into host bind");
  if(SquareRegistrationDays.visible.containsKey(h.itemView))throw new AssertionError("stale registration label survives into host bind");
 }public void j(Holder h){}public void h(Holder h){}}
 public static class Adapter{Object post;int originalHeight;public Object i(int position){return position==1?post:null;}public void onBindViewHolder(Holder h,int p){originalHeight=h.itemView.lp.height;}}
 static Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
 static void invoke(Method m,Object self,Object...args)throws Throwable{hooks.get(m).intercept(proxy(XposedInterface.Chain.class,(p,call,a)->switch(call.getName()){
 case "getThisObject"->self;case "getArg"->args[(int)a[0]];case "proceed"->m.invoke(self,args);default->throw new AssertionError(call);
 }));}
 static void flag(String key,boolean b){SettingsUi.values.put(key,b);}static boolean hidden(Holder h){return h.itemView.visibility==8&&h.itemView.lp.height==0;}
 public static void main(String[] args)throws Throwable{
  XposedModule module=new XposedModule(){};module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}return b;});
   if(m.getName().equals("deoptimize"))return true;return null;
  }),()->{});
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){
   case "cn.soulapp.android.component.chat.fragment.ConversationFragment"->Object.class;
   case "cn.soulapp.android.square.post.bean.Post"->Post.class;case "cn.soulapp.android.square.bean.Tag"->Tag.class;
   case "cn.soulapp.android.component.square.main.squarepost.SquareAdapter"->Square.class;
   case "cn.soulapp.android.component.square.main.squarepost.viewholder.BaseSquareViewHolder","androidx.recyclerview.widget.RecyclerView$ViewHolder"->Holder.class;
   case "cn.soulapp.android.component.square.location.LocationSquareActivity"->City.class;
   case "cn.soulapp.android.component.square.location.PostLightAdapter"->Adapter.class;default->super.loadClass(n);
  };}};
  SquareFeedFilter.install(module,loader);check(SquareFeedFilter.ready&&SquareFeedFilter.cityReady&&hooks.size()==4,"both adapters registered");
  Adapter adapter=new Adapter();adapter.post=new Post(0);Holder city=new Holder(new ContextWrapper(new City()));Method bind=Adapter.class.getMethod("onBindViewHolder",Holder.class,int.class);
  flag(SquareFeedFilter.ENABLED,true);invoke(bind,adapter,city,1);check(!hidden(city),"new city scope defaults off");
  flag(SquareFeedFilter.CITY_PAGES,true);SquareFeedFilter.refresh();Handler.drain();check(hidden(city),"toggle filters already bound city row through wrapped context");
  check(((ViewGroup.MarginLayoutParams)city.itemView.lp).topMargin==0,"hidden row has no margin gap");
  city.itemView.detach();check(!hidden(city)&&city.itemView.lp.height==120,"detachment restores layout");city.itemView.attach();check(hidden(city),"reattachment reapplies");
  flag(SquareFeedFilter.CITY_PAGES,false);SquareFeedFilter.refresh();Handler.drain();check(!hidden(city)&&city.itemView.lp.height==120&&((ViewGroup.MarginLayoutParams)city.itemView.lp).bottomMargin==9,"scope off restores original dimensions and margins");
  flag(SquareFeedFilter.CITY_PAGES,true);invoke(bind,adapter,city,1);adapter.post=new Post(1);invoke(bind,adapter,city,1);check(!hidden(city)&&adapter.originalHeight==120,"reuse restores before host bind and accepts matching author");
  adapter.post=new Post(0);invoke(bind,adapter,city,1);invoke(bind,adapter,city,0);check(!hidden(city),"header not filtered");invoke(bind,adapter,city,2);check(!hidden(city),"footer not filtered");
  flag(SquareFeedFilter.ENABLED,false);flag(SquareFeedFilter.KEYWORDS_ENABLED,true);SettingsUi.values.put(SquareFeedFilter.KEYWORDS,"禁止词");Post text=new Post(1);text.content="含有禁止词";adapter.post=text;invoke(bind,adapter,city,1);check(hidden(city),"city shares keyword rules independently from gender");
  flag(SquareFeedFilter.KEYWORDS_ENABLED,false);SquareFeedFilter.refresh();Handler.drain();check(!hidden(city),"scope switch alone never filters");
  flag(SquareFeedFilter.ENABLED,true);adapter.post=new Post(0);Holder other=new Holder(new Context());invoke(bind,adapter,other,1);check(!hidden(other),"same adapter on other activity unaffected");
  ContextWrapper loop=new ContextWrapper(null);loop.base=loop;Holder cyclic=new Holder(loop);invoke(bind,adapter,cyclic,1);check(!hidden(cyclic),"cyclic context fails open");
  flag(SquareFeedFilter.CITY_PAGES,false);Holder main=new Holder(new Context());main.itemView.parent=new View(new Context());main.itemView.parent.id=1;
  invoke(Square.class.getMethod("d",Holder.class,Object.class),new Square(),main,new Post(0));check(hidden(main),"main square filtering unaffected by city switch");
  flag(SquareFeedFilter.ENABLED,false);flag(SquareFeedFilter.VIP,true);
  Post vip=new Post(1);vip.superVIP=true;
  invoke(Square.class.getMethod("d",Holder.class,Object.class),new Square(),main,vip);
  check(hidden(main)&&!SquareFeedFilter.allows(main.itemView.getContext(),vip),"VIP filtered independently and excluded from likes");
  flag(SquareFeedFilter.VIP,false);SquareFeedFilter.refresh();Handler.drain();check(!hidden(main),"VIP switch restores bound row");
  flag(SquareFeedFilter.VIP,true);invoke(Square.class.getMethod("d",Holder.class,Object.class),new Square(),main,new Post(1));
  check(!hidden(main),"recycled non-VIP row restored");
  adapter.post=vip;flag(SquareFeedFilter.CITY_PAGES,false);invoke(bind,adapter,city,1);check(!hidden(city),"VIP city scope defaults independent");
  flag(SquareFeedFilter.CITY_PAGES,true);invoke(bind,adapter,city,1);check(hidden(city),"VIP city scope enabled");
  flag(SquareFeedFilter.VIP,false);flag(SquareFeedFilter.ENABLED,false);
  flag("feed_filter_online_v1",true); // Legacy installations must no longer hide offline posts.
  Post offline=new Post(1);offline.authorOnline=false;
  invoke(Square.class.getMethod("d",Holder.class,Object.class),new Square(),main,offline);
  check(!hidden(main)&&SquareFeedFilter.allows(main.itemView.getContext(),offline),"removed online preference cannot filter square or likes");
  adapter.post=offline;flag(SquareFeedFilter.CITY_PAGES,true);invoke(bind,adapter,city,1);
  check(!hidden(city),"removed online preference cannot filter city rows");
  check(SquareIpLocation.visible.get(main.itemView)==offline,"IP label uses the current bound post");
  check(SquareRegistrationDays.visible.get(main.itemView)==offline,"registration label uses the current bound post");
  check(!SquareIpLocation.visible.containsKey(city.itemView),"city rows stay outside IP display scope");
  check(!SquareRegistrationDays.visible.containsKey(city.itemView),"city rows stay outside registration display scope");
  invoke(Square.class.getMethod("j",Holder.class),new Square(),main);
  check(!SquareIpLocation.visible.containsKey(main.itemView),"recycling clears the IP label");
  check(!SquareRegistrationDays.visible.containsKey(main.itemView),"recycling clears the registration label");
  main.itemView.parent=null;
  invoke(Square.class.getMethod("d",Holder.class,Object.class),new Square(),main,new Post(1));
  check(!SquareIpLocation.visible.containsKey(main.itemView),"shared adapter outside square clears the IP label");
  check(!SquareRegistrationDays.visible.containsKey(main.itemView),"shared adapter outside square clears the registration label");
  flag(SquareFeedFilter.CONTENT_ENABLED,true);
  SettingsUi.values.put(SquareFeedFilter.CONTENT_TYPES,2);
  check(SquareFeedFilter.contentReady&&SquareFeedFilter.hasRules(new Context()),"content filter registered and activates projection");
  for(Media type:Media.values()) {
   Post post=new Post(1);post.type=type;
   check(SquareFeedFilter.allows(new Context(),post)==(type==Media.IMAGE||type==Media.AUDIO),"content classification "+type);
  }
  Post image=new Post(1);image.type=Media.IMAGE;
  invoke(Square.class.getMethod("d",Holder.class,Object.class),new Square(),main,image);
  SettingsUi.values.put(SquareFeedFilter.CONTENT_TYPES,7);
  check(!SquareFeedFilter.hasRules(new Context()),"all types makes projection inert");
  SettingsUi.values.put(SquareFeedFilter.CONTENT_TYPES,0);
  check(SquareFeedFilter.allows(new Context(),new Post(1)),"invalid empty mask fails open");
  flag(SquareFeedFilter.CONTENT_ENABLED,false);
  SettingsUi.values.put(SquareFeedFilter.CONTENT_TYPES,2);
  check(SquareFeedFilter.allows(new Context(),new Post(1)),"disabled content filter preserves selection without filtering");
  // Async display completion is a targeted text update, not another filter/bind pass.
  Holder first=new Holder(new Context()),second=new Holder(new Context());
  View squareParent=new View(new Context());squareParent.id=1;
  first.itemView.parent=squareParent;second.itemView.parent=squareParent;
  Post p1=new Post(1),p2=new Post(1);p1.authorIdEcpt="first";p2.authorIdEcpt="second";
  Method squareBind=Square.class.getMethod("d",Holder.class,Object.class);
  invoke(squareBind,new Square(),first,p1);invoke(squareBind,new Square(),second,p2);
  SquareIpLocation.visible.clear();SquareRegistrationDays.visible.clear();
  int refreshes=SquareTimeOrder.refreshes;
  SquareFeedFilter.metadataResolved("me:first",false);
  check(SquareIpLocation.visible.size()==1&&SquareIpLocation.visible.get(first.itemView)==p1,"IP result updates matching row only");
  check(SquareRegistrationDays.visible.isEmpty(),"IP result never touches day display");
  SquareFeedFilter.metadataResolved("me:second",true);
  check(SquareRegistrationDays.visible.size()==1&&SquareRegistrationDays.visible.get(second.itemView)==p2,"day result updates matching row only");
  check(SquareTimeOrder.refreshes==refreshes,"display completions never reproject feed");
  check(first.itemView.lp.height==120&&first.itemView.visibility==0,"display completion leaves post geometry visible");
  invoke(squareBind,new Square(),first,p2);SquareIpLocation.visible.clear();
  SquareFeedFilter.metadataResolved("me:first",false);
  check(SquareIpLocation.visible.isEmpty(),"late response cannot label a recycled row with former author");
  HostAccountHttp.user="other";SquareFeedFilter.metadataResolved("me:second",false);
  check(SquareIpLocation.visible.isEmpty(),"old account completion ignored");HostAccountHttp.user="me";
  invoke(Square.class.getMethod("j",Holder.class),new Square(),first);
  second.itemView.parent=null;SquareIpLocation.visible.clear();
  SquareFeedFilter.metadataResolved("me:second",false);
  check(SquareIpLocation.visible.isEmpty(),"recycled and out-of-square rows ignored");
  flag(RegistrationDaysFilter.ENABLED,true);
  SquareFeedFilter.metadataResolved("me:second",true);
  check(SquareTimeOrder.refreshes==refreshes+1,"explicit day filtering still recomputes membership");
  SquareFeedFilter.metadataResolved("other:second",true);
  check(SquareTimeOrder.refreshes==refreshes+1,"old account result cannot refresh active filtering");
  flag(RegistrationDaysFilter.ENABLED,false);
  SettingsUi.values.clear();
  Context ctx=new Context();Post named=new Post(1);named.signature="星空Ad测试";
  SettingsUi.values.put(SquareFeedFilter.NAME_KEYWORDS,"ad");
  check(SquareFeedFilter.nameKeywordsReady,"host showName contract ready");
  check(SquareFeedFilter.allows(ctx,named)&&!SquareFeedFilter.hasRules(ctx),"username filter defaults off");
  flag(SquareFeedFilter.NAME_KEYWORDS_ENABLED,true);
  check(SquareFeedFilter.hasRules(ctx),"username-only filtering participates in feed projection");
  check(!SquareFeedFilter.allows(ctx,named),"username substring ignores case");
  named.signature="普通用户";named.content="Ad广告";named.title="ad";
  check(SquareFeedFilter.allows(ctx,named),"username words never match content or title");
  named.alias="Ad备注";check(!SquareFeedFilter.allows(ctx,named),"displayed alias is matched");
  named.alias="朋友";named.signature="Ad用户";
  check(SquareFeedFilter.allows(ctx,named),"hidden original nickname is not matched behind alias");
  named.alias="";check(!SquareFeedFilter.allows(ctx,named),"empty alias falls back to nickname");
  named.signature=null;check(SquareFeedFilter.allows(ctx,named),"missing username stays visible");
  named.signature="Ad用户";named.brokenName=true;
  check(SquareFeedFilter.allows(ctx,named),"unreadable username fails open");named.brokenName=false;
  SettingsUi.values.put(SquareFeedFilter.NAME_KEYWORDS,"");
  check(!SquareFeedFilter.hasRules(ctx)&&SquareFeedFilter.allows(ctx,named),"empty name list filters nothing");
  SettingsUi.values.put(SquareFeedFilter.NAME_KEYWORDS,"ad");
  flag(SquareFeedFilter.KEYWORDS_ENABLED,true);SettingsUi.values.put(SquareFeedFilter.KEYWORDS,"正文词");
  named.signature="普通用户";named.content="正文词";
  check(!SquareFeedFilter.allows(ctx,named),"content list still independent");
  flag(SquareFeedFilter.KEYWORDS_ENABLED,false);
  check(SquareFeedFilter.allows(ctx,named),"disabling content leaves name list independent");
  named.signature="Ad用户";adapter.post=named;
  invoke(bind,adapter,city,1);check(!hidden(city),"username filter respects city scope off");
  flag(SquareFeedFilter.CITY_PAGES,true);invoke(bind,adapter,city,1);
  check(hidden(city),"username filter applies on opted-in city pages");
  SettingsUi.values.put(SquareFeedFilter.NAME_KEYWORDS,"other");SquareFeedFilter.refresh();Handler.drain();
  check(!hidden(city),"editing name words refreshes existing rows");
  SettingsUi.values.put(SquareFeedFilter.NAME_KEYWORDS,"ad");SquareFeedFilter.refresh();Handler.drain();
  check(hidden(city),"updated name cache reapplies filter");
  flag(SquareFeedFilter.NAME_KEYWORDS_ENABLED,false);SquareFeedFilter.refresh();Handler.drain();
  check(!hidden(city),"turning username filter off restores rows");
  System.out.println("City feed: "+checks+" production-hook checks passed");
 }
}
