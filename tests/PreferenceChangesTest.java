package dev.local.newsoul;

import android.content.SharedPreferences;
import android.os.Handler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercises the real coordinator with observable persistence and runtime dependencies. */
public final class PreferenceChangesTest {
    static final class Store {
        final Map<String, Object> values = new LinkedHashMap<>();
        int commits, applies;
        Runnable duringCommit;
        boolean applyThrows, commitThrows;
        boolean save = true;
        final SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (p, method, args) -> {
                    if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                    if (method.getName().equals("equals")) return p == args[0];
                    if (method.getName().equals("getAll")) return new LinkedHashMap<>(values);
                    if (method.getName().equals("edit")) return editor();
                    throw new AssertionError(method.getName());
                });
        void merge(Map<String, Object> pending) {
            for (Map.Entry<String, Object> e : pending.entrySet()) {
                if (e.getValue() == null) values.remove(e.getKey()); else values.put(e.getKey(), e.getValue());
            }
        }
        Object editor() {
            Map<String, Object> pending = new LinkedHashMap<>();
            boolean[] clear = {false};
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (p, method, args) -> {
                        if (method.getName().equals("clear")) { clear[0] = true; return p; }
                        if (method.getName().startsWith("put")) { pending.put((String) args[0], args[1]); return p; }
                        if (method.getName().equals("remove")) { pending.put((String) args[0], null); return p; }
                        if (method.getName().equals("apply")) { if (applyThrows) throw new IllegalStateException("restore failed"); applies++; if (clear[0]) values.clear(); merge(pending); return null; }
                        if (method.getName().equals("commit")) { commits++; if (clear[0]) values.clear(); merge(pending); if (duringCommit != null) duringCommit.run(); if (commitThrows) throw new IllegalStateException("commit failed"); return save; }
                        throw new AssertionError(method.getName());
                    });
        }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Store store = new Store();
        SharedPreferences managed = TransactionalPreferences.wrap(store.prefs);
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("ad_feed", true); batch.put("ad_detail", true);
        batch.put("feed_filter_enabled", true); batch.put("feed_keywords_v1", "x");
        batch.put("feed_registration_min_v1", 5); batch.put(SquareTimeOrder.ENABLED, true);
        batch.put(AccountAutomationControls.SIGN, true); batch.put(AccountAutomationControls.HIDE, true);
        batch.put("account_welfare_play_v1", true);
        batch.put("profile_disguise_days_absolute", 7); batch.put("profile_disguise_visitor_enabled", true);
        batch.put(ChatTimeControls.FORMAT, "HH:mm"); batch.put(ChatTimeControls.EVERY, true);
        batch.put(ChatRecallControls.STYLE, "below"); batch.put(ChatRecallControls.ENABLED, true);
        check(PreferenceChanges.write(managed, batch), "batch committed");
        check(store.commits == 1 && store.applies == 0, "one durable transaction");
        check(PreferenceRuntime.calls.size() == 7, "all affected runtimes refreshed");
        for (int count : PreferenceRuntime.calls.values()) check(count == 1, "once per affected runtime");
        PreferenceRuntime.calls.clear();
        check(PreferenceChanges.write(managed, batch), "unchanged batch accepted");
        check(store.commits == 2 && PreferenceRuntime.calls.isEmpty(), "durable no-op still confirms disk save without refresh");
        store.save = false;
        int[] notifications = {0};
        SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> notifications[0]++;
        managed.registerOnSharedPreferenceChangeListener(listener);
        store.duringCommit = () -> check(managed.getBoolean("ad_feed", false), "commit memory mutation is hidden until success");
        check(!PreferenceChanges.write(managed, Map.of("ad_feed", false)), "failed save reported");
        check(PreferenceRuntime.calls.isEmpty(), "failed durable save does not notify");
        check(managed.getBoolean("ad_feed", false) && Boolean.TRUE.equals(store.values.get("ad_feed")), "failed save restores live and underlying memory");
        check(!PreferenceChanges.write(managed, Map.of("square_auto_like", true)), "second disk failure reported");
        check(!managed.contains("square_auto_like") && !store.values.containsKey("square_auto_like"), "failure does not activate absent runtime option");
        check(notifications[0] == 0, "failure sends no preference listener events");
        store.duringCommit = null;
        store.save = true;
        check(PreferenceChanges.write(managed, Map.of("ad_feed", false)), "failed in-memory save can be retried");
        check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("ads")), "successful retry delivers unnotified runtime change");
        managed.unregisterOnSharedPreferenceChangeListener(listener);
        int appliesBeforeEdits = store.applies;
        PreferenceRuntime.calls.clear();
        PreferenceChanges.putBoolean(managed, AutoReadControls.ENABLED, true);
        check(Boolean.FALSE.equals(store.values.get(InteractionPrivacyControls.RECEIPT)), "explicit auto-read edit clears receipt");
        PreferenceChanges.putBoolean(managed, InteractionPrivacyControls.RECEIPT, true);
        check(Boolean.FALSE.equals(store.values.get(AutoReadControls.ENABLED)), "explicit receipt edit clears auto-read");
        check(store.applies == appliesBeforeEdits + 2, "ordinary edits use apply");
        PreferenceRuntime.calls.clear();
        PreferenceChanges.applyUpdates(managed, Map.of("square_auto_like", true, "location_override_enabled_v1", true));
        check(PreferenceRuntime.calls.isEmpty(), "unrelated edits do not restart automation or send/like");
        var worker = TestWorkers.start(() -> PreferenceChanges.write(managed, Map.of("ad_feed", true)));
        TestWorkers.await(worker);
        check(PreferenceRuntime.calls.isEmpty(), "worker save queues runtime work");
        Handler.drain();
        check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("ads")), "queued refresh on main thread");
        PreferenceRuntime.calls.clear();
        PreferenceRuntime.accountEnabled = () -> Boolean.TRUE.equals(store.values.get(AccountAutomationControls.SIGN));
        var changes = TestWorkers.start(() -> {
            PreferenceChanges.write(managed, Map.of(AccountAutomationControls.SIGN, false));
            PreferenceChanges.write(managed, Map.of(AccountAutomationControls.SIGN, true));
            PreferenceChanges.write(managed, Map.of(AccountAutomationControls.SIGN, false));
        });
        TestWorkers.await(changes);
        Handler.drain();
        check(!PreferenceRuntime.observedAccountEnabled, "late refresh reads latest configuration instead of captured enable value");
        Store isolated = new Store();
        isolated.values.put("keep", "original");
        SharedPreferences safe = TransactionalPreferences.wrap(isolated.prefs);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        isolated.save = false;
        isolated.duringCommit = () -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException error) { throw new AssertionError(error); }
        };
        var importing = TestWorkers.start(() -> check(!PreferenceChanges.write(safe,
                Map.of("square_auto_like", true, "keep", "imported")), "concurrent import fails"));
        check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS), "concurrent import started");
        check("original".equals(safe.getString("keep", "")), "runtime read stays old during slow disk write");
        check(!safe.getBoolean("square_auto_like", false), "automation stays disabled during disk write");
        var editing = TestWorkers.start(() -> safe.edit().putString("keep", "manual").apply());
        release.countDown(); TestWorkers.await(importing); TestWorkers.await(editing);
        check("manual".equals(safe.getString("keep", "")), "concurrent explicit edit preserved after failed import");
        check(!safe.contains("square_auto_like"), "rejected new key absent after concurrent edit");
        java.util.List<String> events = new java.util.ArrayList<>();
        SharedPreferences.OnSharedPreferenceChangeListener observe = (prefs, key) -> events.add(key);
        safe.registerOnSharedPreferenceChangeListener(observe);
        safe.edit().putStringSet("set", new java.util.HashSet<>(java.util.Set.of("a"))).apply();
        safe.getStringSet("set", java.util.Set.of()).add("b");
        check(safe.getStringSet("set", java.util.Set.of()).size() == 1, "string set snapshots cannot mutate store");
        events.clear();
        safe.edit().clear().apply();
        check(safe.getAll().isEmpty() && events.size() == 1 && events.get(0) == null, "R clear emits null key and clears state");
        safe.unregisterOnSharedPreferenceChangeListener(observe);
        safe.edit().putLong("long", 3).putFloat("float", 2f).putInt("int", 1).apply();
        check(safe.getLong("long", 0) == 3 && safe.getFloat("float", 0) == 2f && safe.getInt("int", 0) == 1, "typed values retained");
        safe.edit().remove("long").apply();
        check(!safe.contains("long") && events.size() == 1, "remove and listener unregistration honored");
        Store broken = new Store();
        broken.values.put("secret", "keep");
        SharedPreferences stable = TransactionalPreferences.wrap(broken.prefs);
        broken.save = false; broken.applyThrows = true;
        try {
            PreferenceChanges.write(stable, Map.of("square_auto_like", true));
            throw new AssertionError("rollback failure propagated");
        } catch (IllegalStateException expected) {}
        check(TransactionalPreferences.wrap(broken.prefs) == stable && !stable.contains("square_auto_like"), "failed rollback retains process snapshot");
        broken.commitThrows = true;
        try {
            PreferenceChanges.write(stable, Map.of("ad_feed", true));
            throw new AssertionError("commit exception propagated");
        } catch (IllegalStateException expected) {
            check("commit failed".equals(expected.getMessage()) && expected.getSuppressed().length == 1, "recovery error does not mask commit error");
        }
        broken.commitThrows = false; broken.applyThrows = false; broken.save = true;
        check(PreferenceChanges.write(stable, Map.of("ad_detail", true)), "later independent save succeeds");
        check(!broken.values.containsKey("square_auto_like") && !broken.values.containsKey("ad_feed"), "later disk write removes rejected leftovers");
        check("keep".equals(broken.values.get("secret")), "unrelated credentials retained");
        PreferenceRuntime.calls.clear();
        check(PreferenceChanges.putBoolean(stable, "square_show_ip_location_v1", false), "IP display toggle saved");
        check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("feed")), "IP toggle refreshes existing feed rows");
        PreferenceRuntime.calls.clear();
        check(PreferenceChanges.putBoolean(stable, "square_show_registration_days_v1", true), "registration display toggle saved");
        check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("feed")), "registration toggle refreshes existing feed rows");
        for (String key : new String[]{"square_ip_label_color", "square_ip_label_size",
                "square_days_label_color", "square_days_label_size"}) {
            PreferenceRuntime.calls.clear();
            check(PreferenceChanges.putString(stable, key, key.endsWith("color") ? "mint" : "14"), "label style saved");
            check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("feed")), "label style refreshes existing rows");
        }
        PreferenceRuntime.calls.clear();
        check(PreferenceChanges.putString(stable, "feed_keywords_name_v1", "nickname"), "name words saved");
        check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("feed")), "name words refresh feed");
        PreferenceRuntime.calls.clear();
        check(PreferenceChanges.putBoolean(stable, "feed_keywords_name_enabled_v1", true), "name toggle saved");
        check(Integer.valueOf(1).equals(PreferenceRuntime.calls.get("feed")), "name toggle refreshes feed");
        System.out.println("PreferenceChangesTest passed");
    }
}
