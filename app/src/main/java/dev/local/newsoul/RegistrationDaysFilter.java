package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Bounded account-scoped lookups; pending rows wait, failed/unknown results stay visible. */
final class RegistrationDaysFilter {
    static final String ENABLED = "feed_registration_days_v1", MIN = "feed_registration_min_v1", MAX = "feed_registration_max_v1";
    static final String DISPLAY = "square_show_registration_days_v1";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
    private static final Map<String, Entry> CACHE = new LinkedHashMap<String, Entry>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, RegistrationDaysFilter.Entry> entry) { return size() > 512; }
    };
    private static final Map<String, Object> PENDING = new HashMap<>();
    private static final class LookupToken {
        final HostRequestScope request;
        volatile boolean started;
        Runnable task, timeout;
        LookupToken(SharedPreferences prefs, String account) {
            request = new HostRequestScope(15000, () -> (prefs.getBoolean(ENABLED, false)
                    || prefs.getBoolean(DISPLAY, false) || prefs.getBoolean(SquareFeedFilter.ENABLED, false))
                    && HostAccountHttp.available() && account.equals(HostAccountHttp.account()));
        }
    }
    private static final class Entry {
        final Integer days, displayDays; final String gender; final boolean genderKnown; final long expires;
        final Object timeoutToken;
        Entry(Integer days, String gender, boolean genderKnown) {
            this(days, gender, genderKnown, days, System.currentTimeMillis()
                    + (days == null && !genderKnown ? 300000 : 3600000), null);
        }
        Entry(Integer days, String gender, boolean genderKnown, Integer displayDays, long expires, Object timeoutToken) {
            this.days = days; this.gender = gender; this.genderKnown = genderKnown;
            this.displayDays = displayDays; this.expires = expires; this.timeoutToken = timeoutToken;
        }
    }
    static boolean allows(Context context, Object post) {
        SharedPreferences prefs = PluginPreferences.get(context);
        if (!prefs.getBoolean(ENABLED, false)) return true;
        int min = Math.max(1, prefs.getInt(MIN, 1)), max = prefs.getInt(MAX, 0);
        if (!AccountAutomationRules.validRange(min, max) || min == 0 && max == 0) return true;
        try {
            String account = HostAccountHttp.account();
            String id = (String)HostAccountHttp.field(post, "authorIdEcpt");
            if (account.isEmpty() || id == null || id.isEmpty() || id.equals(account)) return true;
            String key = account + ":" + id;
            Entry entry;
            synchronized (CACHE) { entry = CACHE.get(key); }
            if (entry != null && entry.expires > System.currentTimeMillis())
                return AccountAutomationRules.allowsDays(entry.days, Math.max(1, prefs.getInt(MIN, 1)), prefs.getInt(MAX, 0));
            if (HostAccountHttp.available()) {
                // Do not display an unclassified row, then remove it after the reply.
                return !schedule(prefs, account, id, key);
            }
        } catch (Exception error) { DebugDiagnostics.failure("feed.registration", error); }
        return true;
    }
    /** Display never blocks a row; filtering and display share requests and account-scoped results. */
    static Integer resolve(Context context, Object post) {
        SharedPreferences prefs = PluginPreferences.get(context);
        if (!prefs.getBoolean(DISPLAY, false)) return null;
        try {
            String account = HostAccountHttp.account();
            String id = (String) HostAccountHttp.field(post, "authorIdEcpt");
            if (account.isEmpty() || id == null || id.isEmpty()) return null;
            String key = account + ":" + id;
            Entry entry;
            synchronized (CACHE) { entry = CACHE.get(key); }
            if (entry != null && entry.expires > System.currentTimeMillis()) return entry.displayDays;
            if (HostAccountHttp.available()) schedule(prefs, account, id, key);
        } catch (Exception error) { DebugDiagnostics.failure("feed.registration.display", error); }
        return null;
    }

    /** The old Post has no gender; share its bounded userDetail lookup with registration age. */
    static String resolveGender(Context context, Object post) {
        SharedPreferences prefs = PluginPreferences.get(context);
        if (!prefs.getBoolean(SquareFeedFilter.ENABLED, false)) return null;
        try {
            String account = HostAccountHttp.account();
            String id = (String) HostAccountHttp.field(post, "authorIdEcpt");
            if (account.isEmpty() || id == null || id.isEmpty()) return null;
            String key = account + ":" + id;
            synchronized (CACHE) {
                Entry entry = CACHE.get(key);
                if (entry != null && entry.expires > System.currentTimeMillis()) return entry.gender;
            }
            if (HostAccountHttp.available()) schedule(prefs, account, id, key);
        } catch (Exception error) { DebugDiagnostics.failure("feed.gender", error); }
        return null;
    }

    static String gender(Object value) {
        if ("MALE".equals(value) || "FEMALE".equals(value)) return (String) value;
        // userDetail uses numeric gender, unlike the native User model's enum.
        // Same mapping as Gender.b(): male=0, female=1, unknown=-1.
        if (value instanceof Number || value instanceof String) {
            String code = value.toString();
            if ("0".equals(code)) return "MALE";
            if ("1".equals(code)) return "FEMALE";
        }
        return null;
    }

    private static boolean unknownGender(Object value) {
        return "UNKNOWN".equals(value)
                || (value instanceof Number || value instanceof String) && "-1".equals(value.toString());
    }

    static boolean allowsGender(Context context, Object post, String wanted, boolean keepUnknown) {
        if ("ANY".equals(wanted)) return true;
        try {
            String account = HostAccountHttp.account();
            String id = (String) HostAccountHttp.field(post, "authorIdEcpt");
            if (account.isEmpty() || id == null || id.isEmpty()) return true;
            String key = account + ":" + id;
            synchronized (CACHE) {
                Entry entry = CACHE.get(key);
                if (entry != null && entry.expires > System.currentTimeMillis())
                    return !entry.genderKnown || FeedFilterRules.matches(wanted, entry.gender, keepUnknown);
            }
            // Unavailable/failed metadata is not a known-unknown author. Release it on failure.
            return !HostAccountHttp.available() || !schedule(PluginPreferences.get(context), account, id, key);
        } catch (Exception error) { DebugDiagnostics.failure("feed.gender.filter", error); return true; }
    }

    private static boolean schedule(SharedPreferences prefs, String account, String id, String key) {
        LookupToken token = new LookupToken(prefs, account);
        synchronized (PENDING) {
            if (PENDING.containsKey(key)) return true;
            PENDING.put(key, token);
        }
        token.task = () -> {
            Integer days = null;
            String gender = null;
            boolean genderKnown = false;
            boolean attempted = false;
            try {
                synchronized (PENDING) { if (PENDING.get(key) != token) return; }
                if ((!prefs.getBoolean(ENABLED, false) && !prefs.getBoolean(DISPLAY, false)
                        && !prefs.getBoolean(SquareFeedFilter.ENABLED, false))
                        || !account.equals(HostAccountHttp.account())) return;
                attempted = true;
                token.started = true;
                JSONObject result = HostAccountHttp.request(account,
                    "https://api-a.soulapp.cn/html/user/userDetail?userIdEcpt=" + java.net.URLEncoder.encode(id, "UTF-8"), false, token.request);
                JSONObject data = result.optJSONObject("data");
                if (result.optInt("code") == 10001 && data != null) {
                    days = AccountAutomationRules.registrationDays(data.opt("registerDay"));
                    gender = gender(data.opt("gender"));
                    genderKnown = gender != null || unknownGender(data.opt("gender"));
                }
                DebugDiagnostics.event("feed.registration.lookup", "code=" + result.optInt("code") + "; dataPresent=" + (data != null)
                        + "; registerDayType=" + FeedMetadataFacts.valueType(data, "registerDay")
                        + "; daysPresent=" + (days != null)
                        + "; genderKnown=" + genderKnown);
            } catch (Exception error) { DebugDiagnostics.failure("feed.registration.request", error); }
            finally {
                token.request.close();
                // A disabled feature or obsolete request is not a failed author lookup.
                boolean cache = (!token.request.isCancelled() || token.request.timedOut())
                        && attempted && account.equals(HostAccountHttp.account());
                complete(key, token, days, gender, genderKnown, cache);
            }
        };
        token.timeout = () -> {
            token.request.refreshCancellation();
            // A timeout releases the row; an already-returned response may only fill its display.
            boolean cache = (!token.request.isCancelled() || token.request.timedOut())
                    && account.equals(HostAccountHttp.account());
            complete(key, token, null, null, false, cache, true);
            token.request.cancel();
            if (IO.remove(token.task)) token.request.close();
        };
        try {
            MAIN.postDelayed(token.timeout, 15000);
            IO.execute(token.task);
            return true;
        } catch (RejectedExecutionException full) {
            DebugDiagnostics.event("feed.registration.queue", "outcome=full");
            token.request.close();
            complete(key, token, null, true);
            return false;
        }
    }

    private static void complete(String key, Object token, Integer days, boolean cache) {
        complete(key, token, days, null, false, cache);
    }
    private static void complete(String key, Object token, Integer days, String gender, boolean genderKnown, boolean cache) {
        complete(key, token, days, gender, genderKnown, cache, false);
    }
    private static void complete(String key, Object token, Integer days, String gender, boolean genderKnown, boolean cache, boolean timeout) {
        synchronized (PENDING) {
            if (PENDING.get(key) != token) {
                synchronized (CACHE) {
                    Entry previous = CACHE.get(key);
                    if (!cache || timeout || days == null || previous == null
                            || previous.timeoutToken != token || previous.expires <= System.currentTimeMillis()) return;
                    // Fill only the display. A timed-out row must not disappear later
                    // because the delayed reply is outside the current gender/days rule.
                    CACHE.put(key, new Entry(previous.days, previous.gender, previous.genderKnown,
                            days, previous.expires, null));
                }
                DebugDiagnostics.event("feed.registration.late", "outcome=display-filled; filtering=unchanged");
            } else {
                PENDING.remove(key);
                MAIN.removeCallbacks(((LookupToken) token).timeout);
                if (cache) synchronized (CACHE) {
                    Entry entry = new Entry(days, gender, genderKnown);
                    CACHE.put(key, timeout ? new Entry(entry.days, entry.gender, entry.genderKnown,
                            entry.displayDays, entry.expires, token) : entry);
                }
                if (timeout) DebugDiagnostics.event("feed.registration.timeout", "outcome=released; phase=" + (((LookupToken) token).started ? "request" : "queue"));
            }
        }
        MAIN.post(() -> SquareFeedFilter.metadataResolved(key, true));
    }
}
