package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import org.json.JSONObject;

/** Fallback uses the same cityName source as Soul's native IP-region label. */
final class SquareIpLocationLookup {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
    private static final Map<String, Entry> CACHE = new LinkedHashMap<String, Entry>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, SquareIpLocationLookup.Entry> entry) { return size() > 512; }
    };
    private static final Map<String, Object> PENDING = new HashMap<>();
    private static final class LookupToken {
        final HostRequestScope request;
        volatile boolean started;
        Runnable task, timeout;
        LookupToken(SharedPreferences prefs, String account) {
            request = new HostRequestScope(15000, () -> prefs.getBoolean(SquareIpLocation.ENABLED, true)
                    && HostAccountHttp.available() && account.equals(HostAccountHttp.account()));
        }
    }
    private static final class Entry {
        final String region; final long expires; final Object timeoutToken;
        Entry(String region) { this(region, null); }
        Entry(String region, Object timeoutToken) {
            this.region = region; this.timeoutToken = timeoutToken;
            expires = System.currentTimeMillis() + (region.isEmpty() ? 300000 : 3600000);
        }
    }

    static String resolve(Context context, Object post) {
        SharedPreferences prefs = PluginPreferences.get(context);
        if (!prefs.getBoolean(SquareIpLocation.ENABLED, true)) return "";
        try {
            String account = HostAccountHttp.account();
            Object author = HostAccountHttp.field(post, "authorIdEcpt");
            if (account.isEmpty() || !(author instanceof String) || ((String) author).isEmpty()) {
                return "";
            }
            String id = (String) author, key = account + ":" + id;
            synchronized (CACHE) {
                Entry entry = CACHE.get(key);
                if (entry != null && entry.expires > System.currentTimeMillis()) return entry.region;
            }
            if (!HostAccountHttp.available()) {
                return "";
            }
            LookupToken token = new LookupToken(prefs, account);
            synchronized (PENDING) {
                if (PENDING.containsKey(key)) return "";
                PENDING.put(key, token);
            }
            token.task = () -> {
                String region = "";
                boolean attempted = false;
                try {
                    synchronized (PENDING) { if (PENDING.get(key) != token) return; }
                    if (!prefs.getBoolean(SquareIpLocation.ENABLED, true) || !account.equals(HostAccountHttp.account())) return;
                    attempted = true;
                    token.started = true;
                    JSONObject result = HostAccountHttp.request(account,
                            "https://api-user.soulapp.cn/user/homePage/contentInfo?targetUserIdEcpt="
                                    + java.net.URLEncoder.encode(id, "UTF-8"), false, token.request);
                    region = fromResponse(result);
                    DebugDiagnostics.event("feed.ip_location.lookup", "code=" + result.optInt("code") + "; dataPresent=" + (result.optJSONObject("data") != null)
                            + "; cityNameType=" + FeedMetadataFacts.valueType(result.optJSONObject("data"), "cityName")
                            + "; regionPresent=" + !region.isEmpty());
                } catch (Exception error) {
                    DebugDiagnostics.failure("feed.ip_location.lookup", error);
                }
                finally {
                    token.request.close();
                    boolean cache = (!token.request.isCancelled() || token.request.timedOut())
                            && attempted && account.equals(HostAccountHttp.account());
                    complete(key, token, region, cache);
                }
            };
            token.timeout = () -> {
                token.request.refreshCancellation();
                boolean cache = (!token.request.isCancelled() || token.request.timedOut())
                        && account.equals(HostAccountHttp.account());
                complete(key, token, "", cache, true);
                token.request.cancel();
                if (IO.remove(token.task)) token.request.close();
            };
            try {
                MAIN.postDelayed(token.timeout, 15000);
                IO.execute(token.task);
            } catch (RejectedExecutionException full) {
                token.request.close();
                DebugDiagnostics.event("feed.ip_location.queue", "outcome=full"); complete(key, token, "", true);
            }
        } catch (Exception error) { DebugDiagnostics.failure("feed.ip_location.author", error); }
        return "";
    }

    static String fromResponse(JSONObject result) {
        JSONObject data = result.optJSONObject("data");
        if (result.optInt("code") != 10001 || data == null || !(data.opt("cityName") instanceof String)) return "";
        return ((String) data.opt("cityName")).trim();
    }

    private static void complete(String key, Object token, String region, boolean cache) {
        complete(key, token, region, cache, false);
    }
    private static void complete(String key, Object token, String region, boolean cache, boolean timeout) {
        synchronized (PENDING) {
            if (PENDING.get(key) != token) {
                synchronized (CACHE) {
                    Entry previous = CACHE.get(key);
                    if (!cache || timeout || region.isEmpty() || previous == null
                            || previous.timeoutToken != token || previous.expires <= System.currentTimeMillis()) return;
                    CACHE.put(key, new Entry(region));
                }
                DebugDiagnostics.event("feed.ip_location.late", "outcome=display-filled");
            } else {
                PENDING.remove(key);
                MAIN.removeCallbacks(((LookupToken) token).timeout);
                if (cache) synchronized (CACHE) { CACHE.put(key, new Entry(region, timeout ? token : null)); }
                if (timeout) DebugDiagnostics.event("feed.ip_location.timeout", "outcome=released; phase=" + (((LookupToken) token).started ? "request" : "queue"));
            }
        }
        MAIN.post(() -> SquareFeedFilter.metadataResolved(key, false));
    }
    private SquareIpLocationLookup() {}
}
