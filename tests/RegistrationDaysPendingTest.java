package dev.local.newsoul;
import android.content.Context;import android.os.Handler;import java.util.concurrent.*;import java.lang.reflect.*;
public class RegistrationDaysPendingTest {
 public static class Post{public String authorIdEcpt;Post(String id){authorIdEcpt=id;}}
 static int checks;static Context ctx=new Context();
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static boolean allows(String id){return RegistrationDaysFilter.allows(ctx,new Post(id));}
 public static void main(String[]args)throws Exception{
  Field field=RegistrationDaysFilter.class.getDeclaredField("IO");field.setAccessible(true);ThreadPoolExecutor io=(ThreadPoolExecutor)field.get(null);
  try{
   check(allows("a"),"default off");SettingsUi.values.put(RegistrationDaysFilter.ENABLED,true);SettingsUi.values.put(RegistrationDaysFilter.MIN,10);
   check(!allows("a"),"pending hidden before response");check(HostAccountHttp.started.await(2,TimeUnit.SECONDS),"lookup started");check(!allows("a")&&HostAccountHttp.calls==1,"deduplicate pending");
   HostAccountHttp.release.countDown();io.submit(()->{}).get(2,TimeUnit.SECONDS);check(!allows("a"),"below range remains hidden");
   HostAccountHttp.days=20;check(!allows("b"),"eligible also waits");io.submit(()->{}).get(2,TimeUnit.SECONDS);check(allows("b"),"eligible shown after reply");
   HostAccountHttp.fail=true;check(!allows("failure"),"failure initially waits");io.submit(()->{}).get(2,TimeUnit.SECONDS);check(allows("failure"),"failure retained");HostAccountHttp.fail=false;
   HostAccountHttp.days=null;check(!allows("missing"),"missing initially waits");io.submit(()->{}).get(2,TimeUnit.SECONDS);check(allows("missing"),"missing retained");
   HostAccountHttp.release=new CountDownLatch(1);HostAccountHttp.started=new CountDownLatch(1);HostAccountHttp.days=1;
   check(!allows("timeout"),"timeout waits");HostAccountHttp.started.await(2,TimeUnit.SECONDS);Handler.expire();check(allows("timeout"),"timeout releases row");HostAccountHttp.release.countDown();io.submit(()->{}).get(2,TimeUnit.SECONDS);check(allows("timeout"),"late reply cannot hide released row");
   SettingsUi.values.put(RegistrationDaysFilter.DISPLAY,true);
   check(RegistrationDaysFilter.resolve(ctx,new Post("timeout"))==null,"cancelled lookup cannot publish a late day value");
   check(RegistrationDaysFilter.allowsGender(ctx,new Post("timeout"),"MALE",false),"late female classification cannot remove timeout-released row");
   Handler.expire();check(RegistrationDaysFilter.resolve(ctx,new Post("timeout"))==null,"stale timeout does not restart a cancelled lookup");
   SettingsUi.values.put(RegistrationDaysFilter.DISPLAY,false);
   check(DebugDiagnostics.events.stream().anyMatch(x->x.contains("feed.registration.timeout")&&x.contains("phase=request")),"registration timeout phase recorded");
   check(DebugDiagnostics.events.stream().anyMatch(x->x.contains("feed.registration.lookup")&&x.contains("registerDayType=Integer")),"registration response field type recorded");
   check(allows("me"),"self remains");check(allows(""),"missing author remains");
   HostAccountHttp.available=false;check(allows("offline"),"unavailable lookup remains");
   SettingsUi.values.put(RegistrationDaysFilter.ENABLED,false);check(allows("a"),"off restores failed range");
   HostAccountHttp.available=true;HostAccountHttp.days=104;
   check(RegistrationDaysFilter.resolve(ctx,new Post("display"))==null,"display default off");
   SettingsUi.values.put(RegistrationDaysFilter.DISPLAY,true);
   int before=HostAccountHttp.calls;
   HostAccountHttp.release=new CountDownLatch(1);HostAccountHttp.started=new CountDownLatch(1);
   check(RegistrationDaysFilter.resolve(ctx,new Post("display"))==null,"display pending has no invented number");
   check(HostAccountHttp.started.await(2,TimeUnit.SECONDS),"display request starts with filter off");
   check(allows("display"),"display alone does not hide pending row");
   Runnable update;while((update=Handler.immediate.poll())!=null)update.run();SquareFeedFilter.updates.clear();
   SettingsUi.values.put(RegistrationDaysFilter.ENABLED,true);
   check(!allows("display")&&HostAccountHttp.calls==before+1,"filter shares display pending request");
   HostAccountHttp.release.countDown();io.submit(()->{}).get(2,TimeUnit.SECONDS);
   check(Integer.valueOf(104).equals(RegistrationDaysFilter.resolve(ctx,new Post("display"))),"real registration days displayed");
   check(allows("display")&&HostAccountHttp.calls==before+1,"filter shares display cached result");
   while((update=Handler.immediate.poll())!=null)update.run();
   check(SquareFeedFilter.updates.equals(java.util.List.of("me:display:true")),"day completion routes through targeted metadata callback");
   check(RegistrationDaysFilter.resolve(ctx,new Post("missing"))==null,"missing result never becomes zero");
   HostAccountHttp.user="other";HostAccountHttp.days=200;
   check(RegistrationDaysFilter.resolve(ctx,new Post("display"))==null,"account switch cannot read previous cache");
   io.submit(()->{}).get(2,TimeUnit.SECONDS);
   check(Integer.valueOf(200).equals(RegistrationDaysFilter.resolve(ctx,new Post("display"))),"new account obtains own result");
   HostAccountHttp.days=" 1179 ";
   check(RegistrationDaysFilter.resolve(ctx,new Post("string-days"))==null,"string value waits for reply");
   io.submit(()->{}).get(2,TimeUnit.SECONDS);
   check(Integer.valueOf(1179).equals(RegistrationDaysFilter.resolve(ctx,new Post("string-days"))),"numeric string response accepted");
   for(Object invalid:new Object[]{"", "unknown", "12天", "1.5", -1, 1.5, 4294967296L, true, new Object()})
    check(AccountAutomationRules.registrationDays(invalid)==null,"invalid day value rejected: "+invalid);
   SettingsUi.values.put(RegistrationDaysFilter.DISPLAY,false);
   check(RegistrationDaysFilter.resolve(ctx,new Post("display"))==null,"disabled display does not expose cached value");
   SettingsUi.values.put(SquareFeedFilter.ENABLED,true);
   int sharedCalls=HostAccountHttp.calls;
   check("FEMALE".equals(RegistrationDaysFilter.resolveGender(ctx,new Post("display")))&&HostAccountHttp.calls==sharedCalls,"gender shares account-scoped age cache without duplicate request");
   HostAccountHttp.gender="MALE";
   check(RegistrationDaysFilter.resolveGender(ctx,new Post("gender-only"))==null,"gender-only lookup starts without age/display enabled");
   io.submit(()->{}).get(2,TimeUnit.SECONDS);
   check("MALE".equals(RegistrationDaysFilter.resolveGender(ctx,new Post("gender-only"))),"old User enum is read without numeric or avatar inference");
   check(!RegistrationDaysFilter.allowsGender(ctx,new Post("gender-only"),"FEMALE",false),"known opposite gender filtered");
   HostAccountHttp.fail=true;
   check(!RegistrationDaysFilter.allowsGender(ctx,new Post("gender-failure"),"FEMALE",false),"pending gender waits for reply");
   io.submit(()->{}).get(2,TimeUnit.SECONDS);
   check(RegistrationDaysFilter.allowsGender(ctx,new Post("gender-failure"),"FEMALE",false),"request failure releases row despite unknown disabled");
   HostAccountHttp.fail=false;HostAccountHttp.available=false;
   check(RegistrationDaysFilter.allowsGender(ctx,new Post("unavailable"),"FEMALE",false),"unavailable transport never empties feed");
   HostAccountHttp.available=true;HostAccountHttp.gender="UNKNOWN";
   RegistrationDaysFilter.allowsGender(ctx,new Post("known-unknown"),"FEMALE",false);io.submit(()->{}).get(2,TimeUnit.SECONDS);
   check(!RegistrationDaysFilter.allowsGender(ctx,new Post("known-unknown"),"FEMALE",false)&&RegistrationDaysFilter.allowsGender(ctx,new Post("known-unknown"),"FEMALE",true),"verified UNKNOWN follows original keep-unknown preference");
   for(Object male:new Object[]{0,0L,"0","MALE"})check("MALE".equals(RegistrationDaysFilter.gender(male)),"male payload accepted: "+male);
   for(Object female:new Object[]{1,1L,"1","FEMALE"})check("FEMALE".equals(RegistrationDaysFilter.gender(female)),"female payload accepted: "+female);
   for(Object invalid:new Object[]{"UNKNOWN","female",-1,2,true,"",null,org.json.JSONObject.NULL,1.5,4294967296L,"01",new Object()})check(RegistrationDaysFilter.gender(invalid)==null,"unknown gender stays unknown");
   for(Object value:new Object[]{0,1,"0","1"}){
    HostAccountHttp.gender=value;String id="numeric-gender-"+value+"-"+value.getClass().getSimpleName();
    check(!RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",false),"numeric reply waits before classification");
    io.submit(()->{}).get(2,TimeUnit.SECONDS);
    boolean female="1".equals(value.toString());
    check(RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",false)==female,"actual userDetail payload filters male and retains female");
    check(RegistrationDaysFilter.allowsGender(ctx,new Post(id),"MALE",false)!=female,"switching to male reuses classified cache");
    check(RegistrationDaysFilter.allowsGender(ctx,new Post(id),"ANY",false),"any restores either numeric gender");
   }
   for(Object value:new Object[]{-1,"-1"}){
    HostAccountHttp.gender=value;String id="numeric-unknown-"+value.getClass().getSimpleName();
    RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",false);io.submit(()->{}).get(2,TimeUnit.SECONDS);
    check(!RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",false),"explicit numeric unknown follows keep-unknown off");
    check(RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",true),"explicit numeric unknown follows keep-unknown on");
   }
   for(Object value:new Object[]{org.json.JSONObject.NULL,2,true,"invalid"}){
    HostAccountHttp.gender=value;String id="unreadable-gender-"+value;
    RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",false);io.submit(()->{}).get(2,TimeUnit.SECONDS);
    check(RegistrationDaysFilter.allowsGender(ctx,new Post(id),"FEMALE",false),"missing or malformed metadata fails open");
   }
   HostAccountHttp.user="third";
   check(RegistrationDaysFilter.resolveGender(ctx,new Post("gender-only"))==null,"gender cannot read prior account cache");
   io.submit(()->{}).get(2,TimeUnit.SECONDS);
   SettingsUi.values.put(SquareFeedFilter.ENABLED,false);
   check(RegistrationDaysFilter.resolveGender(ctx,new Post("gender-only"))==null,"disabled gender does not expose cached value");
   System.out.println("Registration pending: "+checks+" production checks passed");
  }finally{HostAccountHttp.release.countDown();io.shutdownNow();}
 }
}
