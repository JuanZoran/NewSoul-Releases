package dev.local.newsoul;
import android.app.Activity;import android.view.*;import java.lang.reflect.*;import java.util.*;
public class CityLikeTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static class Post{public long id;public Object incentiveModel;boolean liked,filtered;public boolean hasDeclare(){return liked;}public Object getAdResponse(){return null;}Post(long id){this.id=id;}}
 public static class Account{static String id="A";static boolean unavailable;public static String g(){if(unavailable)throw new IllegalStateException("unavailable");return id;}}
 public interface Consumer{void accept(Object o);}
 public static class Reply implements Runnable {Post post;Consumer ok,failure;Reply(Post p,Consumer o,Consumer f){post=p;ok=o;failure=f;}public void run(){post.liked=true;ok.accept(new Object());}void fail(){failure.accept(new Object());}}
 public static class Single{Post post;Single(Post p){post=p;}public void subscribe(Consumer ok,Consumer failure){Helper.pending.add(new Reply(post,ok,failure));if(Helper.throwAfterSubscribe)throw new IllegalStateException("submitted before throwing");}}
 public static class Helper{static int sends;static boolean throwAfterSubscribe;static List<Reply> pending=new ArrayList<>();Post post;public Helper(Post p){post=p;}public Single h(int type){sends++;return new Single(post);}}
 public static class AdapterBase{int refreshes;public void notifyDataSetChanged(){refreshes++;}}
 public static class MainAdapter extends AdapterBase{List<Object> data=new ArrayList<>();public List<?> getData(){return data;}}
 public static class ProfileAdapter extends AdapterBase{List<Object> data=new ArrayList<>();public List<?> getData(){return data;}}
 public static class CityAdapter extends AdapterBase{List<Object> data=new ArrayList<>();public List<?> getDatas(){return data;}}
 public static class PostHolder{Post post;int updates;public Post getPost(){return post;}public void updateLikeState(Post p){updates++;}}
 public static class Recycler extends ViewGroup{Object adapter;Map<View,Object> holders=new HashMap<>();public Object getAdapter(){return adapter;}public Object getChildViewHolder(View child){return holders.get(child);}}
 public static class NoClickView extends View{NoClickView(Activity a){super(a);}public boolean hasOnClickListeners(){return false;}}
 static final Class<?> controller=cls("dev.local.newsoul.SquareLikeControls$Controller");
 static final Class<?> action=cls("dev.local.newsoul.SquareLikeControls$ActionView");
 static Class<?> cls(String n){try{return Class.forName(n);}catch(Exception e){throw new RuntimeException(e);}}
 static final sun.misc.Unsafe unsafe;
 static {try{Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
 static void field(Object o,Class<?> c,String n,Object value)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,value);}
 static Object get(Object o,String n)throws Exception{Field f=controller.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{Method m=controller.getDeclaredMethod(n,types);m.setAccessible(true);try{return m.invoke(o,args);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}}
 static Object call(Object o,String n)throws Exception{return call(o,n,new Class<?>[0]);}
 static Object create(Activity a,boolean city)throws Exception{
  Object c=unsafe.allocateInstance(controller);View button=(View)unsafe.allocateInstance(action);field(button,action,"controller",c);button.context=a;button.shown=true;button.attached=true;button.enabled=true;
  field(c,controller,"activity",a);field(c,controller,"envelope",new View(a));field(c,controller,"button",button);field(c,controller,"cityPage",city);
  field(c,controller,"pagerId",1);field(c,controller,"tagId",2);field(c,controller,"likeId",3);field(c,controller,"active",true);
  field(c,controller,"profileLikeId",4);
  Field recent=SquareLikeControls.class.getDeclaredField("recentRequests");recent.setAccessible(true);field(c,controller,"pending",recent.get(null));field(c,controller,"inFlight",new HashSet<>());field(c,controller,"batch",new ArrayList<>());
  field(c,controller,"worker",(Runnable)()->{});field(c,controller,"ticker",(Runnable)()->{});field(c,controller,"requestTimeout",(Runnable)()->{try{call(c,"timeout");}catch(Exception e){throw new RuntimeException(e);}});return c;
 }
 static void flag(String n,boolean b){SettingsUi.preferences(null).edit().putBoolean(n,b).apply();}
 public static void main(String[] args)throws Exception{
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){
   case "cn.soulapp.android.component.chat.fragment.ConversationFragment"->Object.class;
   case "androidx.recyclerview.widget.RecyclerView"->Recycler.class;case "androidx.recyclerview.widget.RecyclerView$Adapter"->AdapterBase.class;
   case "cn.soulapp.android.component.square.main.squarepost.SquareAdapter"->MainAdapter.class;
   case "cn.soulapp.android.component.square.location.PostLightAdapter"->CityAdapter.class;
   case "cn.soulapp.android.square.post.viewholder.PostAttachmentViewHolder"->PostHolder.class;
   case "io.reactivex.functions.Consumer"->Consumer.class;case "cn.soulapp.android.component.square.post.b1"->Helper.class;default->super.loadClass(n);
  };}};
  field(null,SquareLikeControls.class,"accountId",Account.class.getMethod("g"));
  LoadedPostLikes.install(loader,Post.class);check(LoadedPostLikes.ready&&LoadedPostLikes.cityReady,"both sources ready");
  field(null,SquareLikeControls.class,"postClass",Post.class);field(null,SquareLikeControls.class,"postId",Post.class.getField("id"));field(null,SquareLikeControls.class,"hasDeclare",Post.class.getMethod("hasDeclare"));field(null,SquareLikeControls.class,"getAdResponse",Post.class.getMethod("getAdResponse"));field(null,SquareLikeControls.class,"incentive",Post.class.getField("incentiveModel"));
  Activity a=new Activity();ViewGroup decor=new ViewGroup();a.window.decor=decor;Recycler city=new Recycler();CityAdapter ca=new CityAdapter();city.adapter=ca;decor.add(city);
  Recycler main=new Recycler();main.id=1;MainAdapter ma=new MainAdapter();main.adapter=ma;decor.add(main);
  Post p=new Post(1),hidden=new Post(2),liked=new Post(3);hidden.filtered=true;liked.liked=true;ca.data.addAll(List.of(p,hidden,liked,p,new Object()));ma.data.add(new Post(9));
  check(LoadedPostLikes.find(decor,true).adapter==ca&&LoadedPostLikes.find(decor,false).adapter==ma,"main and city sources remain separate");
  var src=LoadedPostLikes.find(decor,true);List<?> copy=src.data();ca.data.add(new Post(4));check(copy.size()==5,"snapshot data copy");ca.data.remove(ca.data.size()-1);View target=new View(a),otherRow=new View(a);city.add(target);city.add(otherRow);
  PostHolder targetHolder=new PostHolder(),otherHolder=new PostHolder();targetHolder.post=p;otherHolder.post=hidden;city.holders.put(target,targetHolder);city.holders.put(otherRow,otherHolder);
  src.refresh(p);check(targetHolder.updates==1&&otherHolder.updates==0&&ca.refreshes==0,"only target footer updated; no whole-list refresh");
  targetHolder.post=hidden;src.refresh(p);check(targetHolder.updates==1,"recycled holder rejected");targetHolder.post=p;
  city.adapter=new CityAdapter();src.refresh(p);check(targetHolder.updates==1,"replaced adapter rejected");city.adapter=ca;
  ca.data.removeIf(o->o==p);src.refresh(p);check(targetHolder.updates==1,"removed post rejected");ca.data.add(p);
  target.visibility=8;src.refresh(p);check(targetHolder.updates==1,"filtered hidden row left untouched");target.visibility=0;
  city.attached=false;src.refresh(p);check(targetHolder.updates==1,"detached list ignored");city.attached=true;
  src.refresh(new Post(1));check(targetHolder.updates==1,"same id with different object cannot update stale holder");
  Object c=create(a,true);check(call(c,"pager")==null,"city scope defaults off");flag(SquareLikeControls.CITY_PAGES,true);check(call(c,"pager")==city,"enabled scope chooses city recycler");
  flag(SquareLikeControls.ALL_LOADED,true);check(((List<?>)call(c,"snapshot")).size()==2,"dedupe and skip liked posts; city filter off stays independent");
  flag(SquareFeedFilter.CITY_PAGES,true);check(((List<?>)call(c,"snapshot")).size()==1,"enabled city filter excludes hidden post");
  call(c,"startBatch",new Class<?>[]{boolean.class},false);check(Helper.sends==1&&((Set<?>)get(c,"inFlight")).contains(1L),"loaded request tracked once");
  Object second=create(a,true);check(((List<?>)call(second,"snapshot")).isEmpty(),"another page controller skips recently submitted post");
  flag(SquareLikeControls.CITY_PAGES,false);call(c,"next");check(!(boolean)get(c,"running"),"scope off stops batch before another request");Helper.pending.remove(0).run();check(Helper.sends==1&&((Set<?>)get(c,"inFlight")).isEmpty(),"late completion does not restart stopped batch");
  p.liked=false;flag(SquareLikeControls.CITY_PAGES,true);((Map<?,?>)get(c,"pending")).clear();flag(SquareLikeControls.ALL_LOADED,false);
  ViewGroup row=new ViewGroup();row.context=a;row.setTag(2,p);View heart=new View(a);heart.id=3;row.add(heart);city.add(row);
  check(((List<?>)call(c,"snapshot")).size()==1,"visible mode reads native row tag and heart");call(c,"startBatch",new Class<?>[]{boolean.class},false);check(heart.clicks==1&&Helper.sends==1,"visible mode uses native click only");call(c,"finish",new Class<?>[]{boolean.class},false);
  ((Map<?,?>)get(c,"pending")).clear();row.visibility=8;check(((List<?>)call(c,"snapshot")).isEmpty(),"hidden rows not clicked");row.visibility=0;
  a.focus=false;check(call(c,"pager")==null,"dialog or other window pauses scope");a.focus=true;
  flag(SquareLikeControls.CITY_PAGES,false);Object mc=create(a,false);check(call(mc,"pager")==main,"main scope unaffected by city switch");
  flag(SquareLikeControls.CITY_PAGES,true);flag(SquareLikeControls.AUTO,true);flag(SquareLikeControls.ALL_LOADED,false);check((boolean)call(c,"allLoaded"),"auto retains loaded-post scope");
  call(c,"pause");check(!(boolean)get(c,"active")&&!(boolean)get(c,"running"),"activity pause stops work");
  check(Helper.sends==1,"no unexpected network requests");
  check(ca.refreshes==0&&ma.refreshes==0,"success callbacks never rebind either full list");
  // A prefetched holder is bound but not attached when the network result arrives.
  Post cachedPost=new Post(101);ca.data.add(cachedPost);
  View cachedRow=new View(a);PostHolder cachedHolder=new PostHolder();cachedHolder.post=cachedPost;
  city.holders.put(cachedRow,cachedHolder);
  LoadedPostLikes.send(a,src,cachedPost,ok->check(ok,"cached post request succeeded"));
  Helper.pending.remove(0).run();
  check(cachedPost.liked&&cachedHolder.updates==0,"offscreen model updated without touching absent row");
  city.add(cachedRow);src.refreshCompleted();
  check(cachedHolder.updates==1,"cached attached row gets missed footer update");
  src.refreshCompleted();check(cachedHolder.updates==1,"stable visible footer is not repeatedly refreshed");
  Post unsubmitted=new Post(102);ca.data.add(unsubmitted);cachedHolder.post=unsubmitted;
  src.refreshCompleted();check(cachedHolder.updates==1,"recycled row bound to another post is untouched");
  cachedHolder.post=cachedPost;src.refreshCompleted();
  check(cachedHolder.updates==2,"returning completed post resynchronizes recycled row");
  View hiddenCached=new View(a);PostHolder hiddenCachedHolder=new PostHolder();hiddenCachedHolder.post=cachedPost;
  hiddenCached.visibility=8;city.add(hiddenCached);city.holders.put(hiddenCached,hiddenCachedHolder);
  src.refreshCompleted();check(hiddenCachedHolder.updates==0,"hidden completed row stays hidden and untouched");
  city.adapter=new CityAdapter();hiddenCached.visibility=0;src.refreshCompleted();
  check(hiddenCachedHolder.updates==0,"old source cannot touch replacement adapter");city.adapter=ca;
  ca.data.remove(cachedPost);src.refreshCompleted();check(hiddenCachedHolder.updates==0,"removed completed model ignored");
  ca.data.add(cachedPost);city.attached=false;src.refreshCompleted();check(hiddenCachedHolder.updates==0,"detached completed source ignored");city.attached=true;
  src.refreshCompleted();check(hiddenCachedHolder.updates==1,"newly visible cached row eventually synchronizes");
  check(ca.refreshes==0&&ma.refreshes==0,"cached repair never triggers full list refresh");
  field(null,LoadedPostLikes.class,"profileAdapterClass",ProfileAdapter.class);
  field(null,LoadedPostLikes.class,"profileGetData",ProfileAdapter.class.getMethod("getData"));
  LoadedPostLikes.profileReady=true;
  ViewGroup profileRoot=new ViewGroup();profileRoot.context=a;
  Recycler profileList=new Recycler();profileList.context=a;ProfileAdapter profileData=new ProfileAdapter();
  Post moment=new Post(300);Post likedMoment=new Post(301);likedMoment.liked=true;
  profileData.data.addAll(List.of(moment,moment,likedMoment));profileList.adapter=profileData;profileRoot.add(profileList);
  check(LoadedPostLikes.find(profileRoot,false)==null,"profile adapter cannot enter square source");
  check(LoadedPostLikes.find(profileRoot,false,true).adapter==profileData,"profile source binds exact adapter");
  Object pc=create(a,false);field(pc,controller,"profilePage",true);field(pc,controller,"envelope",profileRoot);
  flag(SquareLikeControls.AUTO,true);flag(SquareLikeControls.ENABLED,false);flag(SquareLikeControls.ENABLED,false);
  check(call(pc,"pager")==null&&!(boolean)call(pc,"autoLike"),"shared manual switch off hides profile despite square auto");
  flag(SquareLikeControls.ENABLED,true);flag(SquareLikeControls.ALL_LOADED,true);
  check(call(pc,"pager")==profileList,"shared manual switch enables profile");
  check(((List<?>)call(pc,"snapshot")).size()==1,"profile deduplicates and skips liked moments");
  int beforeProfile=Helper.sends;call(pc,"startBatch",new Class<?>[]{boolean.class},false);
  check(Helper.sends==beforeProfile+1,"profile click submits one manual request");
  flag(SquareLikeControls.ENABLED,false);call(pc,"next");
  check(!(boolean)get(pc,"running"),"disabling shared manual switch stops profile batch");
  flag(SquareLikeControls.ENABLED,true);profileRoot.shown=false;
  check(call(pc,"pager")==null,"hidden moments tab has no active source");profileRoot.shown=true;
  a.focus=false;check(call(pc,"pager")==null,"settings window pauses profile");a.focus=true;
  field(pc,controller,"profileAccess",(java.util.function.BooleanSupplier)()->false);
  check(call(pc,"pager")==null&&((List<?>)call(pc,"snapshot")).isEmpty(),"own profile hides entry and empties targets");
  int ownSends=Helper.sends;call(pc,"startBatch",new Class<?>[]{boolean.class},false);
  check(Helper.sends==ownSends,"own profile cannot submit likes through stale click");
  field(pc,controller,"profileAccess",(java.util.function.BooleanSupplier)()->true);
  check(call(pc,"pager")==profileList,"other profile entry remains available");
  Post nativeMoment=new Post(302);profileData.data.add(nativeMoment);
  ViewGroup nativeMomentRow=new ViewGroup();nativeMomentRow.context=a;nativeMomentRow.setTag(2,nativeMoment);profileList.add(nativeMomentRow);
  View textHeart=new View(a);textHeart.id=4;nativeMomentRow.add(textHeart);flag(SquareLikeControls.ALL_LOADED,false);
  check(((List<?>)call(pc,"snapshot")).size()==1,"old profile visible scope binds native tvLike heart");
  int beforeNative=Helper.sends;call(pc,"startBatch",new Class<?>[]{boolean.class},false);
  check(textHeart.clicks==1&&Helper.sends==beforeNative,"old profile visible click preserves native handler");call(pc,"finish",new Class<?>[]{boolean.class},false);
  // Timeout ends only the current batch; unknown requests stay deduplicated.
  Helper.pending.clear();ca.data.clear();Post timed=new Post(1000),afterTimeout=new Post(1001);
  ca.data.addAll(List.of(timed,afterTimeout));flag(SquareFeedFilter.CITY_PAGES,false);
  flag(SquareLikeControls.CITY_PAGES,true);flag(SquareLikeControls.AUTO,true);
  Object recovery=create(a,true);((Map<?,?>)get(recovery,"pending")).clear();
  int recoverySends=Helper.sends;call(recovery,"startBatch",new Class<?>[]{boolean.class},true);
  Reply oldRequest=Helper.pending.remove(0);((Runnable)get(recovery,"requestTimeout")).run();
  check(!(boolean)get(recovery,"running")&&((Set<?>)get(recovery,"inFlight")).isEmpty(),"timeout releases batch blocker");
  check(!(boolean)call(recovery,"autoLike"),"timeout still disables automatic likes");
  ((Map<?,?>)get(recovery,"pending")).clear(); // Simulate expired recent-request window.
  check(((List<?>)call(recovery,"snapshot")).size()==1,"unknown timed-out post stays excluded after recent expiry");
  check(((List<?>)call(create(a,true),"snapshot")).size()==1,"unknown request deduplicated across controllers");
  flag(SquareLikeControls.AUTO,true);call(recovery,"refresh");
  check(Helper.sends==recoverySends+2&&(boolean)get(recovery,"running"),"reenabling starts unrelated post without waiting for old callback");
  int newRun=(int)get(recovery,"runId");oldRequest.run();
  check((int)get(recovery,"runId")==newRun&&(int)get(recovery,"submitted")==0
    &&((Set<?>)get(recovery,"inFlight")).contains(1001L),"late success cannot complete or unblock newer request");
  oldRequest.fail();check((boolean)call(recovery,"autoLike")&&(boolean)get(recovery,"running"),"duplicate terminal callback ignored");
  Helper.pending.remove(0).run();check((int)get(recovery,"submitted")==1,"current callback advances current batch");
  call(recovery,"finish",new Class<?>[]{boolean.class},false);
  ca.data.clear();Post paused=new Post(1002),afterPause=new Post(1003);ca.data.add(paused);
  ((Map<?,?>)get(recovery,"pending")).clear();call(recovery,"startBatch",new Class<?>[]{boolean.class},true);
  Reply pausedRequest=Helper.pending.remove(0);call(recovery,"pause");
  check(((Set<?>)get(recovery,"inFlight")).isEmpty()&&!(boolean)get(recovery,"active"),"page pause releases batch blocker");
  ca.data.add(afterPause);((Map<?,?>)get(recovery,"pending")).clear();
  Object reopened=create(a,true);call(reopened,"refresh");
  check((boolean)get(reopened,"running")&&((Set<?>)get(reopened,"inFlight")).contains(1003L),"reopened page can like another post without duplicating paused request");
  pausedRequest.fail();
  check((boolean)call(reopened,"autoLike")&&(boolean)get(reopened,"running"),"late failure from paused page cannot disable or stop new page batch");
  check(((List<?>)call(recovery,"snapshot")).isEmpty(),"paused controller has no visible targets");
  Helper.pending.remove(0).run();call(reopened,"finish",new Class<?>[]{boolean.class},false);
  ca.data.clear();Post uncertain=new Post(1004),safeNext=new Post(1005);ca.data.addAll(List.of(uncertain,safeNext));
  Helper.throwAfterSubscribe=true;flag(SquareLikeControls.AUTO,true);Object uncertainController=create(a,true);
  call(uncertainController,"startBatch",new Class<?>[]{boolean.class},true);Helper.throwAfterSubscribe=false;
  ((Map<?,?>)get(uncertainController,"pending")).clear();
  check(((Set<?>)get(uncertainController,"inFlight")).isEmpty()&&((List<?>)call(uncertainController,"snapshot")).size()==1,"subscribe throw after submission retains unknown-post dedupe without blocking other posts");
  flag(SquareLikeControls.AUTO,true);call(uncertainController,"refresh");
  check(((Set<?>)get(uncertainController,"inFlight")).contains(1005L),"recovery after uncertain subscribe submits unrelated post");
  Helper.pending.remove(0).fail();
  check((boolean)call(uncertainController,"autoLike")&&((Set<?>)get(uncertainController,"inFlight")).contains(1005L),"late terminal failure after subscribe throw cannot alter recovery batch");
  Helper.pending.remove(0).run();call(uncertainController,"finish",new Class<?>[]{boolean.class},false);

  // The same post belongs to separate account request namespaces, including unknown results.
  Helper.pending.clear();ca.data.clear();((Map<?,?>)get(c,"pending")).clear();
  Field unresolved=SquareLikeControls.class.getDeclaredField("unresolvedRequests");unresolved.setAccessible(true);((Map<?,?>)unresolved.get(null)).clear();
  Account.id="A";flag(SquareLikeControls.AUTO,false);flag(SquareLikeControls.ALL_LOADED,true);
  Post accountA=new Post(2000),oldNext=new Post(2001);ca.data.addAll(List.of(accountA,oldNext));
  Object accountController=create(a,true);int accountSends=Helper.sends;
  call(accountController,"startBatch",new Class<?>[]{boolean.class},false);Reply requestA=Helper.pending.remove(0);
  Account.id="B";call(accountController,"next");
  check(!(boolean)get(accountController,"running")&&Helper.sends==accountSends+1,"account change stops frozen loaded batch before next target");
  Post accountB=new Post(2000);ca.data.clear();ca.data.add(accountB);
  Object bc=create(a,true);check(((List<?>)call(bc,"snapshot")).size()==1,"B can select same post while A request unresolved");
  call(bc,"startBatch",new Class<?>[]{boolean.class},false);Reply requestB=Helper.pending.remove(0);
  check(Helper.sends==accountSends+2,"B independently submits same post");
  ((Map<?,?>)get(bc,"pending")).clear();
  Account.id="A";check(((List<?>)call(create(a,true),"snapshot")).isEmpty(),"return to A keeps unknown request excluded after recent expiry");
  Account.id="B";requestA.fail();
  check((boolean)get(bc,"running")&&((Set<?>)get(bc,"inFlight")).contains(2000L)
    &&((List<?>)call(create(a,true),"snapshot")).isEmpty(),"late A failure releases only A token and leaves B request blocked");
  Account.id="A";check(((List<?>)call(create(a,true),"snapshot")).size()==1,"terminal A failure permits A retry only");
  Account.id="B";requestB.run();call(bc,"finish",new Class<?>[]{boolean.class},false);

  // A late success updates neither B's visible footer nor B's completed-row cache.
  ca.data.clear();Post staleSuccess=new Post(2010);ca.data.add(staleSuccess);
  View staleRow=new View(a);PostHolder staleHolder=new PostHolder();staleHolder.post=staleSuccess;
  city.add(staleRow);city.holders.put(staleRow,staleHolder);
  Account.id="A";Object stale=create(a,true);call(stale,"startBatch",new Class<?>[]{boolean.class},false);
  Reply staleRequest=Helper.pending.remove(0);Account.id="B";staleRequest.run();
  check(!(boolean)get(stale,"running")&&staleHolder.updates==0,"late success ends A batch without refreshing B footer");
  src.refreshCompleted();check(staleHolder.updates==0,"completed cache ignores A success while B active");
  Account.id="A";src.refreshCompleted();check(staleHolder.updates==1,"A completion can repair footer after A returns");

  // Visible clicks freeze the same account and isolate their recent-request window.
  Account.id="A";flag(SquareLikeControls.ALL_LOADED,false);((Map<?,?>)get(c,"pending")).clear();
  city.children.clear();ViewGroup visibleOne=new ViewGroup();visibleOne.context=a;visibleOne.setTag(2,new Post(2020));
  View firstHeart=new View(a);firstHeart.id=3;visibleOne.add(firstHeart);city.add(visibleOne);
  ViewGroup visibleTwo=new ViewGroup();visibleTwo.context=a;visibleTwo.setTag(2,new Post(2021));
  View secondHeart=new View(a);secondHeart.id=3;visibleTwo.add(secondHeart);city.add(visibleTwo);
  Object visibleA=create(a,true);call(visibleA,"startBatch",new Class<?>[]{boolean.class},false);
  Account.id="B";call(visibleA,"next");
  check(firstHeart.clicks==1&&secondHeart.clicks==0&&!(boolean)get(visibleA,"running"),"account change prevents remaining native visible clicks");
  Object visibleB=create(a,true);check(((List<?>)call(visibleB,"snapshot")).size()==2,"B visible snapshot ignores A recent request");
  call(visibleB,"startBatch",new Class<?>[]{boolean.class},false);call(visibleB,"finish",new Class<?>[]{boolean.class},false);
  check(firstHeart.clicks==2,"B can native-click same post independently");
  Account.id="A";check(((List<?>)call(create(a,true),"snapshot")).size()==1,"A recent native click remains deduplicated");

  int unknownSends=Helper.sends,unknownClicks=firstHeart.clicks;
  Account.id="";Object unknown=create(a,true);
  check(((List<?>)call(unknown,"snapshot")).isEmpty(),"unknown account exposes no selectable targets");
  call(unknown,"startBatch",new Class<?>[]{boolean.class},false);
  check(Helper.sends==unknownSends&&firstHeart.clicks==unknownClicks,"unknown account cannot submit visible clicks");
  flag(SquareLikeControls.ALL_LOADED,true);call(unknown,"startBatch",new Class<?>[]{boolean.class},false);
  check(Helper.sends==unknownSends,"unknown account cannot submit loaded requests");
  Account.unavailable=true;check(((List<?>)call(unknown,"snapshot")).isEmpty(),"account lookup exception fails closed");Account.unavailable=false;
  Account.id="B";boolean refused=false;
  try{LoadedPostLikes.send(a,src,new Post(2030),"A",ok->{});}catch(ReflectiveOperationException expected){refused=true;}
  check(refused&&Helper.sends==unknownSends,"direct loaded send rejects frozen account mismatch");
  Account.id="A";ca.data.clear();ca.data.add(new Post(2040));flag(SquareLikeControls.AUTO,true);
  Object switchedTimeout=create(a,true);call(switchedTimeout,"startBatch",new Class<?>[]{boolean.class},true);
  Account.id="B";((Runnable)get(switchedTimeout,"requestTimeout")).run();
  check(!(boolean)get(switchedTimeout,"running")&&(boolean)call(switchedTimeout,"autoLike"),"old account timeout cannot disable new account automatic preference");

  // Old square uses tvLike when its iv_like resource is absent from the visible footer.
  Account.id="A";flag(SquareLikeControls.AUTO,false);flag(SquareLikeControls.ALL_LOADED,false);
  main.children.clear();ViewGroup oldSquareRow=new ViewGroup();oldSquareRow.context=a;
  oldSquareRow.setTag(2,new Post(2050));main.add(oldSquareRow);
  View textLike=new View(a);textLike.id=4;oldSquareRow.add(textLike);
  Object oldSquare=create(a,false);((Map<?,?>)get(oldSquare,"pending")).clear();
  check(((List<?>)call(oldSquare,"snapshot")).isEmpty(),"modern square does not fall back to unrelated tvLike");
  field(null,SquareLikeControls.class,"legacy",true);
  check(((List<?>)call(oldSquare,"snapshot")).size()==1,"legacy square uses tvLike when iv_like is absent");
  View hiddenImageLike=new View(a);hiddenImageLike.id=3;hiddenImageLike.visibility=8;oldSquareRow.add(hiddenImageLike);
  int fakeSends=Helper.sends;
  call(oldSquare,"startBatch",new Class<?>[]{boolean.class},false);
  check(textLike.clicks==1&&hiddenImageLike.clicks==0&&Helper.sends==fakeSends,"legacy square clicks visible tvLike instead of hidden iv_like");
  call(oldSquare,"finish",new Class<?>[]{boolean.class},false);((Map<?,?>)get(oldSquare,"pending")).clear();
  hiddenImageLike.visibility=0;hiddenImageLike.enabled=false;
  check(((List<?>)call(oldSquare,"snapshot")).size()==1,"disabled legacy iv_like permits visible tvLike fallback");
  hiddenImageLike.enabled=true;
  call(oldSquare,"startBatch",new Class<?>[]{boolean.class},false);
  check(hiddenImageLike.clicks==1&&textLike.clicks==1,"usable native iv_like remains first choice in legacy square");
  call(oldSquare,"finish",new Class<?>[]{boolean.class},false);((Map<?,?>)get(oldSquare,"pending")).clear();
  oldSquareRow.removeView(hiddenImageLike);View noHandler=new NoClickView(a);noHandler.id=3;oldSquareRow.add(noHandler);
  check(((List<?>)call(oldSquare,"snapshot")).size()==1,"legacy iv_like without handler falls back to tvLike");
  textLike.enabled=false;check(((List<?>)call(oldSquare,"snapshot")).isEmpty(),"fallback tvLike still requires an enabled native action");textLike.enabled=true;
  field(oldSquare,controller,"likeId",0);
  check(call(oldSquare,"pager")==main&&((List<?>)call(oldSquare,"snapshot")).size()==1,"legacy pager permits a tvLike-only footer");
  field(null,SquareLikeControls.class,"legacy",false);
  check(call(oldSquare,"pager")==null,"modern pager keeps iv_like registration requirement");
  field(oldSquare,controller,"likeId",3);
  check(((List<?>)call(oldSquare,"snapshot")).isEmpty(),"modern square does not use tvLike when iv_like lacks a handler");
  check(Helper.sends==fakeSends,"native footer selection tests never submit loaded requests");
  System.out.println("City and profile likes: "+checks+" production-controller checks passed; fake requests only");
 }
}
