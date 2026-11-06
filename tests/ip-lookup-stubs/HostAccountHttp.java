package dev.local.newsoul;
import java.util.concurrent.CountDownLatch;
import org.json.JSONObject;
final class HostAccountHttp {
    static volatile String user = "me", lastUrl;
    static boolean available = true, fail;
    static volatile int calls;
    static volatile Object region = "江西";
    static CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    static String account() { return user; }
    static boolean available() { return available; }
    static Object field(Object object, String key) throws Exception { return object.getClass().getField(key).get(object); }
    static JSONObject request(String account, String url, boolean post, HostRequestScope scope) throws Exception {
        try (HostRequestScope ignored = scope) {
            CountDownLatch wait = release;
            scope.attach(wait::countDown, () -> account.equals(user));
            calls++; lastUrl = url; started.countDown(); wait.await(); scope.check();
            if (fail) throw new Exception("failed");
            return new JSONObject().put("code",10001).put("data",new JSONObject().put("cityName",region));
        }
    }
}
