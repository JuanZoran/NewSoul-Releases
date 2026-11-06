package dev.local.newsoul;
import android.content.Context;
import android.os.Handler;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import org.json.JSONObject;
public final class SquareIpLocationLookupTest {
    public static final class Post { public String authorIdEcpt; Post(String id) { authorIdEcpt=id; } }
    static final Context CTX = new Context();
    static int checks;
    static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
    static String resolve(String id) { return SquareIpLocationLookup.resolve(CTX,new Post(id)); }
    public static void main(String[] args) throws Exception {
        Field field=SquareIpLocationLookup.class.getDeclaredField("IO");field.setAccessible(true);
        ThreadPoolExecutor io=(ThreadPoolExecutor)field.get(null);
        try {
            check(resolve("a+b").isEmpty(),"pending label stays absent");
            check(HostAccountHttp.started.await(2,TimeUnit.SECONDS),"request started");
            check(resolve("a+b").isEmpty() && HostAccountHttp.calls==1,"pending deduplicated");
            check(HostAccountHttp.lastUrl.equals("https://api-user.soulapp.cn/user/homePage/contentInfo?targetUserIdEcpt=a%2Bb"),"native source and encoded author");
            HostAccountHttp.release.countDown();io.submit(()->{}).get(2,TimeUnit.SECONDS);
            check(resolve("a+b").equals("江西") && HostAccountHttp.calls==1,"result cached");
            Runnable update;while((update=Handler.immediate.poll())!=null)update.run();
            check(SquareFeedFilter.updates.equals(java.util.List.of("me:a+b:false")),"IP completion posts only targeted metadata update");
            SettingsUi.values.put("square_show_ip_location_v1",false);
            check(resolve("a+b").isEmpty(),"disabled even with cache");
            SettingsUi.values.put("square_show_ip_location_v1",true);
            HostAccountHttp.fail=true;resolve("failure");io.submit(()->{}).get(2,TimeUnit.SECONDS);
            int before=HostAccountHttp.calls;
            check(resolve("failure").isEmpty() && before==HostAccountHttp.calls,"failure cached without guessed region");
            HostAccountHttp.fail=false;HostAccountHttp.region=null;resolve("missing");io.submit(()->{}).get(2,TimeUnit.SECONDS);
            check(resolve("missing").isEmpty(),"missing region remains absent");
            HostAccountHttp.release=new CountDownLatch(1);HostAccountHttp.started=new CountDownLatch(1);HostAccountHttp.region="广东";
            resolve("timeout");check(HostAccountHttp.started.await(2,TimeUnit.SECONDS),"timeout lookup started");
            Handler.expire();HostAccountHttp.release.countDown();io.submit(()->{}).get(2,TimeUnit.SECONDS);
            check(resolve("timeout").isEmpty(),"cancelled IP lookup cannot publish a late value");
            Handler.expire();check(resolve("timeout").isEmpty(),"stale timeout cannot restart a cancelled lookup");
            HostAccountHttp.release=new CountDownLatch(1);HostAccountHttp.started=new CountDownLatch(1);
            resolve("switched-late");check(HostAccountHttp.started.await(2,TimeUnit.SECONDS),"account-switch request starts");
            Handler.expire();HostAccountHttp.user="changed";HostAccountHttp.release.countDown();io.submit(()->{}).get(2,TimeUnit.SECONDS);
            HostAccountHttp.available=false;check(resolve("switched-late").isEmpty(),"late old-account response never displays in new account");
            HostAccountHttp.available=true;HostAccountHttp.user="me";
            check(resolve("switched-late").isEmpty(),"account-switched late response cannot overwrite timeout cache");
            check(DebugDiagnostics.events.stream().anyMatch(x->x.contains("feed.ip_location.timeout")&&x.contains("phase=request")),"request timeout reported distinctly");
            check(DebugDiagnostics.events.stream().anyMatch(x->x.contains("feed.ip_location.lookup")&&x.contains("cityNameType=String")),"response diagnostics retain field type without value");
            HostAccountHttp.user="other";resolve("a+b");io.submit(()->{}).get(2,TimeUnit.SECONDS);
            check(resolve("a+b").equals("广东"),"new account does not reuse old account cache");
            before=HostAccountHttp.calls;check(resolve("").isEmpty() && before==HostAccountHttp.calls,"missing author does not query");
            HostAccountHttp.available=false;check(resolve("unavailable").isEmpty() && before==HostAccountHttp.calls,"no transport does not query");
            check(SquareIpLocationLookup.fromResponse(new JSONObject().put("code",10001).put("data",new JSONObject().put("location","南昌市"))).isEmpty(),"location is not an IP source");
            check(SquareIpLocationLookup.fromResponse(new JSONObject().put("code",10001).put("data",new JSONObject().put("cityName",123))).isEmpty(),"nontext region rejected");
            check(SquareIpLocationLookup.fromResponse(new JSONObject().put("code",500).put("data",new JSONObject().put("cityName","江西"))).isEmpty(),"API failure rejected");
            System.out.println("IP lookup: "+checks+" production checks passed");
        } finally { HostAccountHttp.release.countDown();io.shutdownNow(); }
    }
}
