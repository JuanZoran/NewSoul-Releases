package dev.local.newsoul;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AiSuggestionCacheTest {
    private static final String ANSWER = "{\"snapshot_id\":\"s1\",\"suggestion\":\"你好\"}";

    public static void main(String[] args) throws Exception {
        isolation();
        timingAndFailures();
        leasesAndClearing();
        boundedStorage();
        concurrency();
        System.out.println("AiSuggestionCacheTest: isolated keys, bounded TTL, throttling and stale leases passed");
    }

    private static AiSuggestionCache.Key key(String account, String request) {
        return AiSuggestionCache.Key.of(account, "session", "target", "history", "guard", "model", request);
    }

    private static void isolation() throws Exception {
        String[] fields = {"account", "session", "target", "history", "guard", "model", "settings"};
        for (int i = 0; i < fields.length; i++) {
            AiSuggestionCache cache = new AiSuggestionCache();
            AiSuggestionCache.Key first = of(fields);
            AiSuggestionCache.Lease lease = start(cache, first, 0L);
            require(cache.complete(lease, ANSWER, 1L), "store result");
            String[] changed = fields.clone();
            changed[i] += "-changed";
            require(cache.acquire(of(changed), 15000L).status == AiSuggestionCache.Status.START,
                    "key dimension isolated: " + i);
            require(cache.acquire(of(fields.clone()), 15000L).status == AiSuggestionCache.Status.HIT,
                    "equivalent key reuses result");
        }
        AiSuggestionCache cache = new AiSuggestionCache();
        String[] first = fields.clone(); first[0] = "a"; first[1] = "bc";
        String[] second = fields.clone(); second[0] = "ab"; second[1] = "c";
        require(cache.complete(start(cache, of(first), 0L), ANSWER, 1L), "store ambiguous raw concatenation");
        require(cache.acquire(of(second), 15000L).status == AiSuggestionCache.Status.START,
                "length prefixes avoid concatenation collision");
        for (Field field : AiSuggestionCache.Key.class.getDeclaredFields()) {
            field.setAccessible(true);
            require(((String) field.get(of(fields))).matches("[0-9a-f]{64}"), "key retains only hashes");
        }
        try { key("", "settings"); throw new AssertionError("missing identity accepted"); }
        catch (IllegalArgumentException expected) { }
        try { of(new String[] {"a", "s", "t", "h", null, "m", "p"});
            throw new AssertionError("missing guard accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void timingAndFailures() {
        AiSuggestionCache cache = new AiSuggestionCache();
        AiSuggestionCache.Key first = key("account", "one");
        AiSuggestionCache.Key second = key("account", "two");
        AiSuggestionCache.Lease lease = start(cache, first, 0L);
        require(cache.acquire(first, 1L).status == AiSuggestionCache.Status.IN_FLIGHT, "deduplicate in flight");
        require(cache.fail(lease, 2L), "mark failure");
        AiSuggestionCache.Decision global = cache.acquire(second, 14999L);
        require(global.status == AiSuggestionCache.Status.THROTTLED && global.retryAfterMs == 1L,
                "global interval including failures");
        start(cache, second, 15000L);
        AiSuggestionCache.Decision same = cache.acquire(first, 59999L);
        require(same.status == AiSuggestionCache.Status.THROTTLED && same.retryAfterMs == 1L,
                "same key interval includes failures");
        AiSuggestionCache.Lease retry = start(cache, first, 60000L);
        require(cache.complete(retry, ANSWER, 70000L), "successful later attempt");
        require(ANSWER.equals(cache.acquire(first, 189999L).cachedJson), "TTL starts at successful completion");
        require(cache.acquire(first, 190000L).status == AiSuggestionCache.Status.START, "TTL exact boundary");

        AiSuggestionCache cached = new AiSuggestionCache();
        require(cached.complete(start(cached, first, 0L), ANSWER, 1L), "success");
        require(cached.acquire(first, 2L).status == AiSuggestionCache.Status.HIT, "cache hit bypasses network cooldown");
        require(cached.acquire(second, 2L).status == AiSuggestionCache.Status.THROTTLED, "hit does not make new network request");
    }

    private static void leasesAndClearing() {
        AiSuggestionCache cache = new AiSuggestionCache();
        AiSuggestionCache.Key first = key("account", "one");
        AiSuggestionCache.Lease expired = start(cache, first, 0L);
        require(cache.acquire(first, 119999L).status == AiSuggestionCache.Status.IN_FLIGHT, "live lease until timeout");
        AiSuggestionCache.Lease current = start(cache, first, 120000L);
        require(!cache.complete(expired, ANSWER, 120001L), "expired completion cannot fill newer entry");
        require(!cache.fail(expired, 120001L), "expired failure cannot cancel newer entry");
        require(cache.complete(current, ANSWER, 120002L), "current lease completes");
        require(!cache.complete(current, "replacement", 120003L), "lease is single use");
        cache.clear();
        require(cache.acquire(first, 135000L).status == AiSuggestionCache.Status.THROTTLED,
                "clear drops answer but preserves same key interval");
        AiSuggestionCache.Lease cleared = start(cache, first, 180000L);
        cache.clear();
        require(!cache.complete(cleared, ANSWER, 180001L), "clear invalidates in-flight completion");
        require(cache.acquire(key("other", "two"), 180001L).status == AiSuggestionCache.Status.THROTTLED,
                "clear preserves global interval");

        AiSuggestionCache accounts = new AiSuggestionCache();
        AiSuggestionCache.Key a = key("a", "settings"), b = key("b", "settings");
        AiSuggestionCache.Lease aLease = start(accounts, a, 0L);
        require(accounts.complete(start(accounts, b, 15000L), ANSWER, 15001L), "second account complete");
        accounts.clearAccount("a");
        require(!accounts.complete(aLease, ANSWER, 15002L), "account clear invalidates its lease");
        require(accounts.acquire(b, 15002L).status == AiSuggestionCache.Status.HIT, "account clear isolates other results");
        require(accounts.acquire(a, 15002L).status == AiSuggestionCache.Status.THROTTLED, "account clear retains cooldown");
        accounts.clearAccount("b");
        require(accounts.acquire(b, 15003L).status == AiSuggestionCache.Status.THROTTLED, "account clear drops its result");

        AiSuggestionCache outsider = new AiSuggestionCache();
        AiSuggestionCache.Lease foreign = start(outsider, first, 0L);
        require(!cache.complete(foreign, ANSWER, 180002L), "foreign cache cannot complete a lease");
        require(!outsider.fail(null, 1L), "missing lease ignored");
    }

    private static void boundedStorage() {
        AiSuggestionCache cache = new AiSuggestionCache();
        for (int i = 0; i < AiSuggestionCache.MAX_ENTRIES; i++) {
            long now = i * 15000L;
            require(cache.complete(start(cache, key("a", "request" + i), now), ANSWER, now + 10000L),
                    "fill cache");
            require(cache.size() <= AiSuggestionCache.MAX_ENTRIES, "bounded entries while filling");
        }
        require(cache.acquire(key("a", "request0"), 119999L).status == AiSuggestionCache.Status.HIT,
                "touch oldest cached result");
        AiSuggestionCache.Lease ninth = start(cache, key("a", "request8"), 120000L);
        require(cache.size() == 8, "ninth request evicts one completed cache entry");
        require(cache.acquire(key("a", "request0"), 120001L).status == AiSuggestionCache.Status.HIT,
                "recently reused result preserved by LRU eviction");
        require(cache.acquire(key("a", "request1"), 120001L).status == AiSuggestionCache.Status.THROTTLED,
                "oldest untouched result was evicted");
        cache.clear();
        require(!cache.complete(ninth, ANSWER, 120002L), "bounded clear invalidates surviving leases");

        AiSuggestionCache oversized = new AiSuggestionCache();
        char[] huge = new char[AiSuggestionCache.MAX_CACHED_CHARS + 1];
        java.util.Arrays.fill(huge, 'a');
        AiSuggestionCache.Key key = key("a", "large");
        require(!oversized.complete(start(oversized, key, 0L), new String(huge), 1L), "oversize answer not retained");
        require(oversized.acquire(key, 15000L).status == AiSuggestionCache.Status.THROTTLED, "oversize result counts as attempt");
        require(!oversized.complete(start(oversized, key, 60000L), " \n", 60001L), "blank answer not retained");
        require(!oversized.complete(start(oversized, key, 120000L), null, 120001L), "missing answer not retained");
        require(oversized.size() <= 8, "all failure records bounded");

        AiSuggestionCache clock = new AiSuggestionCache();
        require(clock.fail(start(clock, key, Long.MAX_VALUE - 60000L), Long.MAX_VALUE - 60000L), "large monotonic clock supported");
        require(clock.acquire(key, Long.MAX_VALUE).status == AiSuggestionCache.Status.START, "clock arithmetic avoids overflow");
        require(clock.acquire(key, Long.MAX_VALUE - 1L).status == AiSuggestionCache.Status.IN_FLIGHT,
                "concurrent out-of-order clock samples do not move time backward");
    }

    private static void concurrency() throws Exception {
        AiSuggestionCache cache = new AiSuggestionCache();
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger(), waiting = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    ready.countDown(); start.await();
                    AiSuggestionCache.Status status = cache.acquire(key("a", "same"), 0L).status;
                    if (status == AiSuggestionCache.Status.START) started.incrementAndGet();
                    else if (status == AiSuggestionCache.Status.IN_FLIGHT) waiting.incrementAndGet();
                    else throw new AssertionError("unexpected concurrent decision " + status);
                } catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            threads[i].start();
        }
        ready.await(); start.countDown();
        for (Thread thread : threads) thread.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        require(started.get() == 1 && waiting.get() == 7, "only one concurrent caller gets request lease");
    }

    private static AiSuggestionCache.Key of(String[] fields) {
        return AiSuggestionCache.Key.of(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6]);
    }

    private static AiSuggestionCache.Lease start(AiSuggestionCache cache, AiSuggestionCache.Key key, long now) {
        AiSuggestionCache.Decision decision = cache.acquire(key, now);
        require(decision.status == AiSuggestionCache.Status.START && decision.lease != null
                && decision.cachedJson == null, "request lease granted at " + now + ": " + decision.status);
        return decision.lease;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
