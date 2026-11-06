package dev.local.newsoul;
import android.content.*;import android.view.*;import java.util.*;import java.lang.reflect.*;import io.github.libxposed.api.*;
public class SquareTimeOrderTest {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static class Post{public long createTime;boolean ad,filtered;Post(long t){createTime=t;}public Object getAdResponse(){return ad?this:null;}}
 public static class Notify {
  final List<Integer> contentPositions=new ArrayList<>();final List<Object> payloads=new ArrayList<>();
  final List<List<Object>> seen=new ArrayList<>();final List<String> events=new ArrayList<>();final List<Object> rendered=new ArrayList<>();
  void emit(String e){events.add(e);seen.add(new ArrayList<>(((Base)this).items()));}
  public void notifyDataSetChanged(){rendered.clear();rendered.addAll(((Base)this).items());emit("full");}public void notifyItemRangeInserted(int i,int n){rendered.addAll(i,((Base)this).items().subList(i,i+n));emit("insert");}
  public void notifyItemRangeRemoved(int i,int n){for(int j=0;j<n;j++)rendered.remove(i);emit("remove");}public void notifyItemMoved(int a,int b){Object item=rendered.remove(a);rendered.add(b,item);emit("move");}
  public void notifyItemRangeChanged(int i,int n){if(i>=((Base)this).items().size()){((Base)this).footerBinds++;((Base)this).shownFooter=((Base)this).footerStatus;emit("footer");return;}for(int j=i;j<i+n;j++){contentPositions.add(j);rendered.set(j,((Base)this).items().get(j));}emit("change");}
  public void notifyItemRangeChanged(int i,int n,Object payload){payloads.add(payload);notifyItemRangeChanged(i,n);}
  public void notifyItemChanged(int i,Object payload){payloads.add(payload);notifyItemRangeChanged(i,1);}
  void fire(String name,Class<?>[] types,Object...args){try{invoke(Notify.class.getMethod(name,types),this,args);}catch(Throwable e){throw new RuntimeException(e);}}
 }
 public static class Base extends Notify{private List<Object> data=new ArrayList<>();int footerRows,footerBinds;String footerStatus="Complete",shownFooter="Complete";boolean footerOnAppend;int headers, nativeDiffCalls,nativeTwoCalls,generation;boolean deferred,multiDiff;Runnable pending;
 public void setList(Collection<?> c){data=new ArrayList<>(c);fire("notifyDataSetChanged",new Class<?>[0]);}
 public void addData(Collection<?> c){int start=data.size();data.addAll(c);fire("notifyItemRangeInserted",new Class<?>[]{int.class,int.class},start,c.size());if(footerOnAppend)fire("notifyItemChanged",new Class<?>[]{int.class,Object.class},data.size(),"footer-payload");}
 public void removeAt(int i){data.remove(i);fire("notifyItemRangeRemoved",new Class<?>[]{int.class,int.class},i,1);}
 public void setData(int i,Object o){data.set(i,o);fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class},i,1);}
 public void setNewData(List<?> l){try{invoke(Base.class.getMethod("setList",Collection.class),this,l);}catch(Throwable e){throw new RuntimeException(e);}}
 public void setDiffNewData(List<?> values){nativeDiffCalls++;int token=++generation;if(values==null){int size=data.size();data=new ArrayList<>();fire("notifyItemRangeRemoved",new Class<?>[]{int.class,int.class},0,size);return;}if(data.isEmpty()){data=new ArrayList<>(values);fire("notifyItemRangeInserted",new Class<?>[]{int.class,int.class},0,values.size());return;}pending=()->{if(token!=generation)return;data=new ArrayList<>(values);try{invoke(DiffResult.class.getMethod("dispatchUpdatesTo",ListUpdateCallback.class),new DiffResult(multiDiff),new BrvahCallback(this));}catch(Throwable error){throw new RuntimeException(error);}};if(!deferred){pending.run();pending=null;}}
 public void setDiffNewData(DiffResult diff,List<?> values){nativeTwoCalls++;try{invoke(DiffResult.class.getMethod("dispatchUpdatesTo",ListUpdateCallback.class),diff,new BrvahCallback(this));}catch(Throwable error){throw new RuntimeException(error);}data=new ArrayList<>(values);}
 public void onAttachedToRecyclerView(Recycler r){r.adapter=this;}
 public List<Object> items(){return data;}public int getHeaderLayoutCount(){return headers;}public int getItemCount(){return data.size()+headers+footerRows;}}
 public static class AsyncDiffer{private void e(List<?> list,DiffResult result,Runnable callback){}public void g(List<?> list,Runnable callback){}}
 public interface ListUpdateCallback {}
 public static class BrvahCallback implements ListUpdateCallback{private final Base a;BrvahCallback(Base a){this.a=a;}public void onInserted(int p,int n){}public void onRemoved(int p,int n){}public void onMoved(int p,int n){}public void onChanged(int p,int n,Object payload){}}
 public static class DiffResult{boolean multi;DiffResult(boolean multi){this.multi=multi;}public void dispatchUpdatesTo(ListUpdateCallback callback){Base a=((BrvahCallback)callback).a;if(multi){a.fire("notifyItemRangeRemoved",new Class<?>[]{int.class,int.class},0,a.rendered.size());a.fire("notifyItemRangeInserted",new Class<?>[]{int.class,int.class},0,a.items().size());}else a.fire("notifyDataSetChanged",new Class<?>[0]);}}
 public static class Adapter extends Base{public void onAttachedToRecyclerView(Recycler r){super.onAttachedToRecyclerView(r);}}
 public static class LegacyAdapter extends Base{}
 public static class OtherAdapter extends Base{}
 public static class Timeline {
  private LegacyAdapter l;private int S;int requests;boolean tab7,refresh;String cursor="server-cursor",sentCursor;
  Timeline(LegacyAdapter adapter){l=adapter;}
  public static void w(Timeline page){page.H2();}
  private void H2(){if(l.items().size()>=5||tab7){S=3;e3(false);}}
  private void e3(boolean refresh){this.refresh=refresh;requests++;sentCursor=cursor;}
 }
 public static class PostedLoad {public void run(){}}
 public static class LoadModule {private void n(){}}
 public static class LoadListener {public void onLoadMore(){}}
 public static class Recycler extends View{Base adapter;boolean layout;Recycler(){super(new Context());}public Base getAdapter(){return adapter;}public boolean isComputingLayout(){return layout;}}
 static Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();static Set<Executable> deopts=new HashSet<>();
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
 static void invoke(Method m,Object self,Object...args)throws Throwable{
  var hook=hooks.get(m);if(hook==null){m.invoke(self,args);return;}
  hook.intercept(proxy(XposedInterface.Chain.class,(p,call,a)->switch(call.getName()){
   case "getThisObject"->self;case "getArg"->args[(int)a[0]];case "proceed"->m.invoke(self,args);default->throw new AssertionError(call);
  }));
 }
 static void enabled(boolean b){SettingsUi.values.put(SquareTimeOrder.ENABLED,b);SquareTimeOrder.refresh();View.drain();}
 static void order(Base a,Object...items){check(FeedTimeOrder.same(a.items(),Arrays.asList(items)),"expected identity order");}
 static void onlyFinal(Base a){check(FeedTimeOrder.same(a.rendered,a.items()),"notification positions reconstruct exact final order");for(var seen:a.seen)check(FeedTimeOrder.same(seen,a.items()),"observers must never see unprepared data");a.seen.clear();}
 public static void main(String[] args)throws Throwable{
  XposedModule module=new XposedModule(){};module.attachFramework(proxy(XposedInterface.class,(p,m,a)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){hooks.put((Executable)a[0],(XposedInterface.Hooker)ba[0]);return null;}return b;});
   if(m.getName().equals("deoptimize")){deopts.add((Executable)a[0]);return true;}return null;
  }),()->{});
  ClassLoader loader=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{return switch(n){
   case "cn.soulapp.android.component.chat.fragment.ConversationFragment"->Object.class;
   case "cn.soulapp.android.square.post.bean.Post"->Post.class;
   case "cn.soulapp.android.component.square.main.squarepost.SquareAdapter"->Adapter.class;
   case "com.chad.library.adapter.base.BaseQuickAdapter"->Base.class;
   case "androidx.recyclerview.widget.RecyclerView"->Recycler.class;
   case "androidx.recyclerview.widget.RecyclerView$Adapter"->Notify.class;
   case "androidx.recyclerview.widget.ListUpdateCallback"->ListUpdateCallback.class;
   case "androidx.recyclerview.widget.DiffUtil$DiffResult"->DiffResult.class;
   case "com.chad.library.adapter.base.diff.BrvahListUpdateCallback"->BrvahCallback.class;
   case "com.chad.library.adapter.base.diff.BrvahAsyncDiffer"->AsyncDiffer.class;default->super.loadClass(n);
  };}};
  SquareTimeOrder.install(module,loader);check(SquareTimeOrder.ready,"ready");check(!SquareTimeOrder.manages(new Adapter()),"unattached adapter falls back safely");
  Method attach=Adapter.class.getMethod("onAttachedToRecyclerView",Recycler.class),set=Base.class.getMethod("setList",Collection.class),add=Base.class.getMethod("addData",Collection.class),remove=Base.class.getMethod("removeAt",int.class);
  Post old=new Post(10),fresh=new Post(30),equal=new Post(30),unknown=new Post(0),ad=new Post(100);ad.ad=true;Object header=new Object();
  // Disabled features retain host async Diff; later activation reads the actual committed list.
  SquareFeedFilter.fieldsEnabled=false;
  Adapter nativeAdapter=new Adapter();Recycler nr=new Recycler();nr.parent=new View(new Context());nr.parent.id=1;
  nativeAdapter.setList(List.of(old,fresh));invoke(attach,nativeAdapter,nr);
  check(!SquareTimeOrder.manages(nativeAdapter),"no rules means native adapter ownership");
  nativeAdapter.deferred=true;
  Method diff=Base.class.getMethod("setDiffNewData",List.class);
  invoke(diff,nativeAdapter,List.of(equal));order(nativeAdapter,old,fresh);
  check(nativeAdapter.nativeDiffCalls==1,"disabled Diff is native and asynchronous");
  nativeAdapter.pending.run();nativeAdapter.pending=null;order(nativeAdapter,equal);
  enabled(true);order(nativeAdapter,equal);enabled(false);order(nativeAdapter,equal);
  // Exit filtering restores hidden rows before a native append, without stale resurrection.
  SquareFeedFilter.blocked.add(old);SquareTimeOrder.refresh();View.drain();
  invoke(set,nativeAdapter,List.of(old,fresh));order(nativeAdapter,fresh);
  SquareFeedFilter.blocked.clear(); // no refresh: the mutation itself must exit projection safely
  invoke(add,nativeAdapter,List.of(equal));order(nativeAdapter,old,fresh,equal);
  invoke(diff,nativeAdapter,List.of(unknown));check(nativeAdapter.nativeDiffCalls==2,"native Diff after filter exit");
  enabled(true);nativeAdapter.multiDiff=true;nativeAdapter.pending.run();nativeAdapter.pending=null;order(nativeAdapter,unknown);
  check(FeedTimeOrder.same(nativeAdapter.rendered,nativeAdapter.items()),"multi-event native diff is replaced by exactly one projected delta");nativeAdapter.multiDiff=false;
  // No next frame/post is needed to release interception: unrelated same-turn changes must render.
  unknown.createTime=100;nativeAdapter.events.clear();
  nativeAdapter.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class},0,1);
  check(nativeAdapter.events.equals(List.of("change")),"same-turn independent content notification retained");
  nativeAdapter.items().add(fresh);nativeAdapter.fire("notifyItemRangeInserted",new Class<?>[]{int.class,int.class},1,1);
  order(nativeAdapter,unknown,fresh);check(FeedTimeOrder.same(nativeAdapter.rendered,nativeAdapter.items()),"same-turn independent structure notification retained");
  nativeAdapter.items().remove(fresh);nativeAdapter.fire("notifyItemRangeRemoved",new Class<?>[]{int.class,int.class},1,1);unknown.createTime=0;
  nr.rejectPosts=true;nativeAdapter.events.clear();nativeAdapter.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class},0,1);
  check(nativeAdapter.events.equals(List.of("change")),"post failure cannot leave a suppression marker");nr.rejectPosts=false;
  enabled(false);order(nativeAdapter,unknown); // previous hidden/original rows cannot resurrect
  invoke(add,nativeAdapter,List.of(old));enabled(true);order(nativeAdapter,unknown,old);enabled(false);order(nativeAdapter,unknown,old);
  Method twoDiff=Base.class.getMethod("setDiffNewData",DiffResult.class,List.class);
  check(deopts.contains(twoDiff)&&deopts.contains(DiffResult.class.getMethod("dispatchUpdatesTo",ListUpdateCallback.class))
      &&deopts.contains(AsyncDiffer.class.getDeclaredMethod("e",List.class,DiffResult.class,Runnable.class)),"native Diff callers and dispatch deoptimized");
  enabled(true);invoke(twoDiff,nativeAdapter,new DiffResult(true),List.of(fresh,old));order(nativeAdapter,fresh,old);
  check(nativeAdapter.nativeTwoCalls==0,"enabled two-arg dispatch-before-data setter replaced atomically");enabled(false);order(nativeAdapter,fresh,old);
  invoke(twoDiff,nativeAdapter,new DiffResult(false),List.of(fresh,old));
  check(nativeAdapter.nativeTwoCalls==1,"disabled two-arg native setter preserved");
  // Native generations must survive projection toggles and later async submissions.
  invoke(diff,nativeAdapter,List.of(equal));Runnable staleCommit=nativeAdapter.pending;
  enabled(true);List<Object> beforeAsync=new ArrayList<>(nativeAdapter.items());
  invoke(diff,nativeAdapter,List.of(old,fresh));Runnable newestCommit=nativeAdapter.pending;
  check(FeedTimeOrder.same(beforeAsync,nativeAdapter.items()),"active one-arg Diff remains asynchronous");
  staleCommit.run();check(FeedTimeOrder.same(beforeAsync,nativeAdapter.items()),"new native generation cancels pre-toggle stale commit");
  newestCommit.run();order(nativeAdapter,fresh,old);check(FeedTimeOrder.same(nativeAdapter.rendered,nativeAdapter.items()),"latest native commit projects exactly once");
  staleCommit.run();order(nativeAdapter,fresh,old);enabled(false);order(nativeAdapter,old,fresh);
  SquareFeedFilter.blocked.add(old);SquareFeedFilter.blocked.add(fresh);SquareTimeOrder.refresh();View.drain();order(nativeAdapter);
  // Hidden original data is nonempty; host sees an empty display and takes synchronous g fast path.
  invoke(diff,nativeAdapter,List.of(equal));order(nativeAdapter,equal);
  SquareFeedFilter.blocked.clear();SquareTimeOrder.refresh();View.drain();order(nativeAdapter,equal);
  check(!nativeAdapter.items().contains(old)&&!nativeAdapter.items().contains(fresh),"empty-display fast replacement cannot resurrect hidden old rows");
  enabled(true);invoke(diff,nativeAdapter,(Object)null);order(nativeAdapter);enabled(false);order(nativeAdapter);
  invoke(diff,nativeAdapter,List.of(old,fresh));order(nativeAdapter,old,fresh);
  check(deopts.contains(AsyncDiffer.class.getMethod("g",List.class,Runnable.class))
      &&deopts.contains(BrvahCallback.class.getMethod("onInserted",int.class,int.class))
      &&deopts.contains(BrvahCallback.class.getMethod("onRemoved",int.class,int.class)),"native fast-path and callbacks deoptimized");
  nativeAdapter.deferred=false;enabled(true);nativeAdapter.events.clear();
  invoke(diff,nativeAdapter,List.of(equal,fresh));order(nativeAdapter,equal,fresh);
  check(FeedTimeOrder.same(nativeAdapter.rendered,nativeAdapter.items()),"synchronous executor commit renders exact projected data");
  check(Collections.frequency(nativeAdapter.events,"change")==1,"synchronous executor dispatch publishes only once");
  check(android.util.Log.failures==0,"synchronous and async native paths log no preparation failures");
  enabled(false);
  SquareFeedFilter.fieldsEnabled=true;
  Adapter a=new Adapter();Recycler r=new Recycler();r.parent=new View(new Context());r.parent.id=1;
  List<Object> response=new ArrayList<>(List.of(old,header,fresh,unknown,equal,ad));a.setList(response);a.events.clear();a.seen.clear();invoke(attach,a,r);order(a,old,header,fresh,unknown,equal,ad);check(a.events.isEmpty(),"default off unchanged on attach");
  enabled(true);order(a,fresh,header,equal,unknown,old,ad);onlyFinal(a);check(response.get(0)==old,"response never mutated");
  int notices=a.events.size();SquareTimeOrder.refresh();View.drain();check(a.events.size()==notices,"idempotent");
  Post newest=new Post(Long.MAX_VALUE);a.events.clear();invoke(add,a,List.of(newest));order(a,newest,header,fresh,unknown,equal,ad,old);onlyFinal(a);check(!a.events.contains("full"),"append no full rebind");check(View.queue.isEmpty(),"append completed before posted frame");
  enabled(false);order(a,old,header,fresh,unknown,equal,ad,newest);a.seen.clear();
  enabled(true);a.seen.clear();invoke(remove,a,2);check(!a.items().contains(fresh),"removed stays removed");onlyFinal(a);enabled(false);check(!a.items().contains(fresh),"restore never resurrects removed");a.seen.clear();
  enabled(true);a.seen.clear();invoke(set,a,List.of(old,fresh));order(a,fresh,old);onlyFinal(a);enabled(false);order(a,old,fresh);a.seen.clear();
  r.layout=true;enabled(true);order(a,old,fresh);r.layout=false;View.drain();order(a,fresh,old);a.seen.clear();
  // Filtering and sorting share one projection; hidden rows are retained only in original data.
  SquareFeedFilter.blocked.add(fresh);invoke(set,a,List.of(old,fresh,newest));order(a,newest,old);onlyFinal(a);
  invoke(add,a,List.of(equal));order(a,newest,equal,old);onlyFinal(a);
  SquareFeedFilter.blocked.clear();SquareTimeOrder.refresh();View.drain();order(a,newest,fresh,equal,old);onlyFinal(a);
  enabled(false);order(a,old,fresh,newest,equal);a.seen.clear();
  SquareFeedFilter.blocked.add(old);invoke(set,a,List.of(old,fresh));order(a,fresh);onlyFinal(a);
  invoke(remove,a,0);order(a);onlyFinal(a);SquareFeedFilter.blocked.clear();SquareTimeOrder.refresh();View.drain();order(a,old);onlyFinal(a);
  // Refresh must drop previous hidden rows, and nested setters publish only the final result.
  SquareFeedFilter.blocked.add(old);invoke(set,a,List.of(old));a.seen.clear();
  invoke(Base.class.getMethod("setNewData",List.class),a,List.of(fresh,equal));order(a,fresh,equal);onlyFinal(a);
  SquareFeedFilter.blocked.clear();SquareTimeOrder.refresh();View.drain();order(a,fresh,equal);
  a.seen.clear();invoke(Base.class.getMethod("setDiffNewData",List.class),a,List.of(old,newest));order(a,old,newest);onlyFinal(a);
  SquareFeedFilter.blocked.add(equal);a.items().add(equal);a.fire("notifyItemRangeInserted",new Class<?>[]{int.class,int.class},2,1);order(a,old,newest);onlyFinal(a);
  SquareFeedFilter.blocked.clear();SquareTimeOrder.refresh();View.drain();order(a,old,newest,equal);a.seen.clear();
  // Full notifications merge direct edits rather than discarding hidden original rows.
  Adapter b=new Adapter();Recycler br=new Recycler();br.parent=new View(new Context());br.parent.id=1;
  Post hidden=new Post(20),visible=new Post(10),appended=new Post(30);hidden.filtered=true;
  b.setList(List.of(hidden,visible));b.events.clear();b.seen.clear();invoke(attach,b,br);onlyFinal(b);
  b.items().add(appended);b.fire("notifyDataSetChanged",new Class<?>[0]);order(b,visible,appended);onlyFinal(b);
  hidden.filtered=false;SquareTimeOrder.refresh();View.drain();order(b,hidden,visible,appended);onlyFinal(b);
  hidden.filtered=true;SquareTimeOrder.refresh();View.drain();b.seen.clear();
  b.items().remove(visible);b.fire("notifyDataSetChanged",new Class<?>[0]);order(b,appended);onlyFinal(b);
  hidden.filtered=false;SquareTimeOrder.refresh();View.drain();order(b,hidden,appended);onlyFinal(b);
  // In-place time changes must re-sort before dispatch and rebind only the affected post.
  enabled(true);b.seen.clear();order(b,appended,hidden);b.events.clear();b.contentPositions.clear();b.payloads.clear();
  Object payload=new Object();hidden.createTime=50;
  b.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class,Object.class},1,1,payload);
  order(b,hidden,appended);onlyFinal(b);check(!b.events.contains("full"),"in-place range update uses structural deltas");
  check(b.contentPositions.equals(List.of(0)),"changed post rebound at projected position only");check(b.payloads.equals(List.of(payload)),"range payload retained");
  b.events.clear();b.contentPositions.clear();b.payloads.clear();
  b.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class,Object.class},0,2,payload);
  check(b.events.equals(List.of("change"))&&b.contentPositions.equals(List.of(0,1)),"unchanged projection preserves host range notification");onlyFinal(b);
  b.events.clear();b.contentPositions.clear();b.payloads.clear();appended.createTime=70;
  b.fire("notifyItemChanged",new Class<?>[]{int.class,Object.class},1,payload);
  order(b,appended,hidden);onlyFinal(b);check(b.contentPositions.equals(List.of(0))&&b.payloads.equals(List.of(payload)),"single-item payload remapped without full rebind");
  hidden.createTime=90;b.fire("notifyDataSetChanged",new Class<?>[0]);order(b,hidden,appended);onlyFinal(b);
  // In-place filter fields are re-evaluated for both range and full notifications.
  b.events.clear();hidden.filtered=true;
  b.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class},0,1);order(b,appended);onlyFinal(b);
  check(!b.events.contains("full"),"filtering one changed post avoids full rebind");
  hidden.filtered=false;b.fire("notifyDataSetChanged",new Class<?>[0]);order(b,hidden,appended);onlyFinal(b);
  appended.filtered=true;b.fire("notifyDataSetChanged",new Class<?>[0]);order(b,hidden);onlyFinal(b);
  appended.filtered=false;enabled(false);order(b,hidden,appended);onlyFinal(b);
  // Header/empty-state fallbacks already rebind everything; do not replay stale range indices.
  b.events.clear();b.contentPositions.clear();b.headers=1;hidden.filtered=true;
  b.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class,Object.class},1,1,payload);
  order(b,appended);onlyFinal(b);check(b.events.equals(List.of("full"))&&b.contentPositions.isEmpty(),"header fallback skips range replay");
  b.events.clear();b.headers=0;hidden.filtered=false;
  b.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class},0,1);order(b,hidden,appended);onlyFinal(b);
  check(b.events.equals(List.of("full")),"header restoration uses full fallback only");
  b.events.clear();b.contentPositions.clear();hidden.filtered=true;appended.filtered=true;
  b.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class},0,2);order(b);onlyFinal(b);
  check(b.events.equals(List.of("full"))&&b.contentPositions.isEmpty(),"empty-state fallback skips stale content positions");
  hidden.filtered=false;appended.filtered=false;b.fire("notifyDataSetChanged",new Class<?>[0]);order(b,hidden,appended);onlyFinal(b);
  Adapter unrelated=new Adapter();Recycler other=new Recycler();unrelated.setList(List.of(old,fresh));invoke(attach,unrelated,other);enabled(true);order(unrelated,old,fresh);
  Base base=new Base();invoke(set,base,List.of(old,fresh));order(base,old,fresh);
  r.parent=null;SquareTimeOrder.refresh();View.drain();order(a,old,newest,equal);
  check(FeedTimeOrder.sorted(List.of(unknown,header),x->0).equals(List.of(unknown,header)),"undated only");
  check(FeedTimeOrder.merge(List.of(old,fresh),List.of(fresh),List.of(equal)).equals(List.of(old,equal)),"hidden preserved on replacement");
  check(FeedTimeOrder.merge(List.of(old,fresh),List.of(old,fresh),List.of(header,old,fresh)).equals(List.of(header,old,fresh)),"indexed insert keeps host position");
  check(FeedTimeOrder.merge(List.of(old,fresh),List.of(old,fresh),List.of(equal,fresh)).equals(List.of(equal,fresh)),"replacement preserves following anchor");
  // Image-delay investigation: local filtering plus metadata-completion refreshes
  // must not issue content notifications for already-visible media rows.
  enabled(false);SquareFeedFilter.blocked.clear();SquareFeedFilter.fieldsEnabled=true;
  Adapter media=new Adapter();Recycler mediaView=new Recycler();
  mediaView.parent=new View(new Context());mediaView.parent.id=1;
  invoke(attach,media,mediaView);
  List<Object> page=new ArrayList<>(),kept=new ArrayList<>();
  for(int i=0;i<20;i++){Post p=new Post(i+1);p.filtered=i%2==0;page.add(p);if(!p.filtered)kept.add(p);}
  invoke(set,media,page);
  check(FeedTimeOrder.same(media.items(),kept),"half-filtered page preserves visible media identities");
  check(page.size()==20,"filter never truncates source response");
  media.events.clear();media.contentPositions.clear();
  for(int i=0;i<10;i++){SquareTimeOrder.refresh();View.drain();}
  check(media.events.isEmpty(),"metadata completions with unchanged membership issue no adapter notifications");
  check(FeedTimeOrder.same(media.items(),kept),"metadata completions retain media identities");
  List<Object> next=new ArrayList<>();
  for(int i=20;i<40;i++){Post p=new Post(i+1);p.filtered=i%2==0;next.add(p);if(!p.filtered)kept.add(p);}
  invoke(add,media,next);
  check(FeedTimeOrder.same(media.items(),kept),"second half-filtered page appends in host order");
  check(!media.events.contains("full")&&media.contentPositions.isEmpty(),"ordinary append does not request rebind of old media");
  for(Object item:page)((Post)item).filtered=false;
  for(Object item:next)((Post)item).filtered=false;
  SquareTimeOrder.refresh();View.drain();
  check(media.items().size()==40,"disabling matching rules restores hidden rows without losing media");
  footerNotifications(module,loader);
  legacyInheritedAttachment(module,loader);
  System.out.println("Pre-display: "+checks+" production-hook checks passed");
 }

 static void legacyPagination(XposedModule module,ClassLoader loader)throws Throwable{
  enabled(false);SquareFeedFilter.blocked.clear();SquareFeedFilter.fieldsEnabled=true;
  LegacyAdapter a=new LegacyAdapter();Recycler r=new Recycler();r.parent=new View(new Context());r.parent.id=1;
  List<Post> source=new ArrayList<>();for(int i=0;i<10;i++){Post p=new Post(i+1);p.filtered=true;source.add(p);}
  a.setList(source);invoke(LegacyAdapter.class.getMethod("onAttachedToRecyclerView",Recycler.class),a,r);order(a);
  Timeline page=new Timeline(a);Method callback=Timeline.class.getDeclaredMethod("H2");callback.setAccessible(true);
  // Native load module enters Loading before invoking this callback.
  a.footerStatus="Loading";invoke(callback,page);
  check(page.requests==1,"all-pending filtered page still reaches native next-page request");
  check(page.S==3&&!page.refresh&&page.sentCursor.equals(page.cursor),"load scene, append mode and native cursor retained");
  check(a.items().isEmpty()&&source.size()==10,"pagination guard does not expose hidden rows or edit response");
  check(deopts.contains(callback)&&deopts.contains(LoadListener.class.getDeclaredMethod("onLoadMore")),"legacy callback and its caller deoptimized");
  check(deopts.contains(Timeline.class.getDeclaredMethod("w",Timeline.class))&&deopts.contains(PostedLoad.class.getDeclaredMethod("run"))&&deopts.contains(LoadModule.class.getDeclaredMethod("n")),"whole legacy dispatch chain deoptimized");
  // Partial filtering after the first response, not only all-pending metadata.
  source.get(0).filtered=false;source.get(1).filtered=false;SquareTimeOrder.refresh();View.drain();
  invoke(callback,page);check(page.requests==2&&a.items().size()==2,"two-visible page retains raw eligibility");
  List<Post> next=new ArrayList<>();for(int i=0;i<8;i++){Post p=new Post(i+11);p.filtered=true;next.add(p);}
  invoke(Base.class.getMethod("addData",Collection.class),a,next);invoke(callback,page);
  check(page.requests==3&&a.items().size()==2,"next mostly-filtered page remains eligible");
  // Native small-page guard and special tab are unchanged.
  invoke(Base.class.getMethod("setList",Collection.class),a,source.subList(0,4));invoke(callback,page);
  check(page.requests==3,"truly-small raw list retains host guard");
  page.tab7=true;invoke(callback,page);check(page.requests==4,"native special tab still loads below threshold");
  page.tab7=false;for(Post p:source)p.filtered=false;invoke(Base.class.getMethod("setList",Collection.class),a,source);
  invoke(callback,page);check(page.requests==5,"native five-plus visible branch called once");
  SquareFeedFilter.fieldsEnabled=false;enabled(false);invoke(Base.class.getMethod("setList",Collection.class),a,source.subList(0,2));invoke(callback,page);
  check(page.requests==5,"disabled projection does not bypass native guard");
  // Attached foreign context does not gain broad paging eligibility.
  LegacyAdapter outside=new LegacyAdapter();Recycler other=new Recycler();outside.setList(source.subList(0,2));invoke(LegacyAdapter.class.getMethod("onAttachedToRecyclerView",Recycler.class),outside,other);
  Timeline foreign=new Timeline(outside);invoke(callback,foreign);check(foreign.requests==0,"non-square callback retains native behavior");
  SquareFeedFilter.fieldsEnabled=true;
 }
 static void footerNotifications(XposedModule module,ClassLoader loader)throws Throwable{
  enabled(false);SquareFeedFilter.blocked.clear();
  Class<?> adapterClass=loader.loadClass("cn.soulapp.android.component.square.main.squarepost.SquareAdapter");
  Base a=(Base)adapterClass.getDeclaredConstructor().newInstance();a.footerRows=1;Recycler r=new Recycler();r.parent=new View(new Context());r.parent.id=1;
  Post one=new Post(10),two=new Post(20),three=new Post(30);
  a.setList(List.of(one,two));invoke(adapterClass.getMethod("onAttachedToRecyclerView",Recycler.class),a,r);
  a.footerStatus="Loading";a.fire("notifyItemChanged",new Class<?>[]{int.class,Object.class},2,"loading");
  check(a.shownFooter.equals("Loading"),"native Loading footer rendered");
  // Async metadata becomes available just before the host completes the page.
  one.filtered=true;a.footerStatus="Complete";a.footerBinds=0;a.payloads.clear();
  a.fire("notifyItemChanged",new Class<?>[]{int.class,Object.class},2,"complete");
  order(a,two);check(a.footerBinds==1&&a.shownFooter.equals("Complete"),"completion footer survives simultaneous filtering");
  check(a.payloads.equals(List.of("complete")),"completion footer payload retained");
  a.footerOnAppend=true;a.footerStatus="Fail";a.footerBinds=0;a.payloads.clear();
  invoke(Base.class.getMethod("addData",Collection.class),a,List.of(three));
  order(a,two,three);check(a.footerBinds==1&&a.shownFooter.equals("Fail"),"footer notification inside append transaction survives");
  a.footerOnAppend=false;enabled(true);a.footerStatus="Complete";a.footerBinds=0;two.createTime=40;
  a.fire("notifyItemChanged",new Class<?>[]{int.class,Object.class},2,"retry");
  order(a,two,three);check(a.footerBinds==1&&a.shownFooter.equals("Complete"),"retry footer survives simultaneous sorting");
  // A range can span a filtered post and the footer.
  three.filtered=true;a.footerBinds=0;a.footerStatus="Fail";
  a.fire("notifyItemRangeChanged",new Class<?>[]{int.class,int.class,Object.class},1,2,"mixed");
  order(a,two);check(a.footerBinds==1&&a.shownFooter.equals("Fail"),"mixed range retains trailing host row");
  one.filtered=false;three.filtered=false;enabled(false);
 }
 static void legacyInheritedAttachment(XposedModule module,ClassLoader modernLoader)throws Throwable{
  Field stateField=SquareTimeOrder.class.getDeclaredField("states");stateField.setAccessible(true);
  Map<?,?> states=(Map<?,?>)stateField.get(null);
  states.clear();hooks.clear();deopts.clear();View.queue.clear();
  ClassLoader legacyLoader=new ClassLoader(){public Class<?> loadClass(String name)throws ClassNotFoundException{
   return switch(name){
    case "cn.soulapp.android.component.chat.fragment.ConversationFragment"->cn.soulapp.android.component.chat.fragment.ConversationFragment.class;
    case "cn.soulapp.android.component.square.main.squarepost.SquareAdapter"->LegacyAdapter.class;
    case "cn.soulapp.android.component.square.recommend.TimeLineFragment"->Timeline.class;
    case "cn.soulapp.android.component.square.recommend.s1"->LoadListener.class;
    case "gx.b$c"->PostedLoad.class;
    case "gx.b"->LoadModule.class;
    case "cx.c"->BrvahCallback.class;
    case "cx.a"->AsyncDiffer.class;
    default->modernLoader.loadClass(name);
   };
  }};
  check(HostBindings.legacyChat(legacyLoader),"old loader selects legacy ABI");
  Method attach=LegacyAdapter.class.getMethod("onAttachedToRecyclerView",Recycler.class);
  check(attach.getDeclaringClass()==Base.class,"old SquareAdapter inherits Base attachment entry");
  SquareTimeOrder.install(module,legacyLoader);
  check(SquareTimeOrder.ready&&hooks.containsKey(attach),"legacy install hooks the inherited attachment method");
  SettingsUi.values.put(SquareTimeOrder.ENABLED,true);SquareFeedFilter.blocked.clear();SquareFeedFilter.fieldsEnabled=true;
  Post old=new Post(10),fresh=new Post(30),hidden=new Post(20),append=new Post(40);hidden.filtered=true;
  LegacyAdapter square=new LegacyAdapter();Recycler squareView=new Recycler();
  squareView.parent=new View(new Context());squareView.parent.id=1;
  List<Object> response=new ArrayList<>(List.of(old,hidden,fresh));
  square.setList(response);square.events.clear();square.seen.clear();invoke(attach,square,squareView);
  check(squareView.getAdapter()==square,"legacy attachment still calls the host");
  check(states.containsKey(square)&&SquareTimeOrder.manages(square),"legacy SquareAdapter owns an active projection");
  order(square,fresh,old);onlyFinal(square);
  check(FeedTimeOrder.same(response,List.of(old,hidden,fresh)),"legacy attachment preserves source response");

  // Both foreign adapters live under the same managed feed, so pager ancestry
  // alone cannot prevent this shared inherited hook from changing nested lists.
  Base plain=new Base();OtherAdapter other=new OtherAdapter();
  Recycler plainView=new Recycler(),otherView=new Recycler();plainView.parent=squareView;otherView.parent=squareView;
  plain.setList(response);other.setList(response);plain.events.clear();other.events.clear();plain.seen.clear();other.seen.clear();
  List<Object> plainData=plain.items(),otherData=other.items();
  invoke(attach,plain,plainView);invoke(attach,other,otherView);
  check(plainView.getAdapter()==plain&&otherView.getAdapter()==other,"foreign attachment retains host setup");
  check(!states.containsKey(plain)&&!states.containsKey(other)&&states.size()==1,"shared Base attachment creates state only for SquareAdapter");
  check(!SquareTimeOrder.manages(plain)&&!SquareTimeOrder.manages(other),"foreign nested lists are never managed");
  check(plain.items()==plainData&&other.items()==otherData,"foreign attachment leaves native data instances intact");
  order(plain,old,hidden,fresh);order(other,old,hidden,fresh);
  check(plain.events.isEmpty()&&other.events.isEmpty(),"foreign attachment emits no projection notifications");

  Method add=Base.class.getMethod("addData",Collection.class),set=Base.class.getMethod("setList",Collection.class);
  invoke(add,plain,List.of(append));order(plain,old,hidden,fresh,append);onlyFinal(plain);
  check(plain.events.equals(List.of("insert")),"foreign native append dispatches once without sorting or filtering");
  invoke(set,other,List.of(hidden,fresh,old));order(other,hidden,fresh,old);onlyFinal(other);
  check(other.events.equals(List.of("full")),"foreign native replacement dispatches once without projection");
  plain.events.clear();plain.deferred=true;
  invoke(Base.class.getMethod("setDiffNewData",List.class),plain,List.of(hidden,append,old));
  order(plain,old,hidden,fresh,append);
  check(plain.nativeDiffCalls==1&&plain.events.isEmpty(),"foreign asynchronous Diff retains native pending behavior");
  plain.pending.run();plain.pending=null;order(plain,hidden,append,old);onlyFinal(plain);
  check(plain.events.equals(List.of("full")),"foreign native Diff dispatch passes through shared legacy callback");
  int plainEvents=plain.events.size(),otherEvents=other.events.size();
  enabled(false);order(square,old,fresh);enabled(true);order(square,fresh,old);
  order(plain,hidden,append,old);order(other,hidden,fresh,old);
  check(plain.events.size()==plainEvents&&other.events.size()==otherEvents,"feed toggles and refreshes never notify foreign nested lists");
  check(states.size()==1&&!states.containsKey(plain)&&!states.containsKey(other),"mutations and refreshes do not create foreign sorting state");
  check(android.util.Log.failures==0,"legacy inherited attachment and nested adapters log no preparation failures");
  footerNotifications(module,legacyLoader);
  legacyPagination(module,legacyLoader);
 }
}
