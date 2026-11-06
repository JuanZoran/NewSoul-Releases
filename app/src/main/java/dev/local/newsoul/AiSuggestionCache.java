package dev.local.newsoul;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-only suggestions. Callers must revalidate the current conversation before reuse. */
final class AiSuggestionCache {
    static final int MAX_ENTRIES = 8;
    static final int MAX_CACHED_CHARS = 128 * 1024;
    static final long TTL_MS = 120000L;
    static final long KEY_INTERVAL_MS = 60000L;
    static final long GLOBAL_INTERVAL_MS = 15000L;

    enum Status { HIT, START, IN_FLIGHT, THROTTLED, CAPACITY }

    /** No account IDs, context text, model credentials or other raw inputs are retained. */
    static final class Key {
        private final String accountHash;
        private final String fingerprint;

        private Key(String accountHash, String fingerprint) {
            this.accountHash = accountHash;
            this.fingerprint = fingerprint;
        }

        boolean same(Key other) { return other != null && fingerprint.equals(other.fingerprint); }

        /** requestFingerprint must cover all other inputs, including selected media and source context. */
        static Key of(String account, String sessionId, String target, String historyFingerprint,
                String cacheGuard, String modelId, String requestFingerprint) {
            String[] fields = {account, sessionId, target, historyFingerprint, cacheGuard,
                    modelId, requestFingerprint};
            for (String field : fields) {
                if (field == null || field.isEmpty())
                    throw new IllegalArgumentException("Missing suggestion cache key field");
            }
            return new Key(hash("account-v1", account), hash("suggestion-v1", fields));
        }
    }

    /** Identity-bound completion permission; an old request cannot fill a newer entry. */
    static final class Lease {
        private final Object owner;
        private final String fingerprint;
        private final Object token;

        private Lease(Object owner, String fingerprint, Object token) {
            this.owner = owner;
            this.fingerprint = fingerprint;
            this.token = token;
        }
    }

    static final class Decision {
        final Status status;
        final String cachedJson;
        final Lease lease;
        final long retryAfterMs;

        private Decision(Status status, String cachedJson, Lease lease, long retryAfterMs) {
            this.status = status;
            this.cachedJson = cachedJson;
            this.lease = lease;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static final class Entry {
        final String accountHash;
        final long attemptedAt;
        Object token = new Object();
        boolean inFlight = true;
        String json;
        long completedAt;
        long accessedAt;

        Entry(String accountHash, long now) {
            this.accountHash = accountHash;
            this.attemptedAt = now;
            this.accessedAt = now;
        }
    }

    private final Object owner = new Object();
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private long lastAttemptAt = -1L;
    private long lastObservedAt = -1L;

    /** HIT never triggers another request; only START grants a lease for one automatic attempt. */
    synchronized Decision acquire(Key key, long monotonicMs) {
        if (key == null) throw new IllegalArgumentException("Missing suggestion cache key");
        long now = time(monotonicMs);
        prune(now);
        Entry existing = entries.get(key.fingerprint);
        if (existing != null) {
            if (existing.json != null) {
                existing.accessedAt = now;
                return new Decision(Status.HIT, existing.json, null, 0L);
            }
            if (existing.inFlight)
                return new Decision(Status.IN_FLIGHT, null, null,
                        remaining(now, existing.attemptedAt, TTL_MS));
        }
        long keyWait = existing == null ? 0L
                : remaining(now, existing.attemptedAt, KEY_INTERVAL_MS);
        long globalWait = lastAttemptAt < 0L ? 0L
                : remaining(now, lastAttemptAt, GLOBAL_INTERVAL_MS);
        long wait = Math.max(keyWait, globalWait);
        if (wait > 0L) return new Decision(Status.THROTTLED, null, null, wait);

        if (existing == null && entries.size() >= MAX_ENTRIES && !evict(now))
            return new Decision(Status.CAPACITY, null, null, capacityWait(now));
        Entry started = new Entry(key.accountHash, now);
        entries.put(key.fingerprint, started);
        lastAttemptAt = now;
        return new Decision(Status.START, null,
                new Lease(owner, key.fingerprint, started.token), 0L);
    }

    /** Accept a locally validated result/context packet. The cache never parses or trusts model output. */
    synchronized boolean complete(Lease lease, String json, long monotonicMs) {
        long now = time(monotonicMs);
        prune(now);
        Entry entry = current(lease);
        if (entry == null) return false;
        entry.inFlight = false;
        entry.token = null;
        if (json == null || json.trim().isEmpty() || json.length() > MAX_CACHED_CHARS) return false;
        entry.json = json;
        entry.completedAt = now;
        entry.accessedAt = now;
        return true;
    }

    /** Failure and cancellation consume the original attempt interval; nothing retries automatically. */
    synchronized boolean fail(Lease lease, long monotonicMs) {
        long now = time(monotonicMs);
        prune(now);
        Entry entry = current(lease);
        if (entry == null) return false;
        entry.inFlight = false;
        entry.token = null;
        return true;
    }

    /** Drop all response bodies and leases. Opaque cooldown records survive to prevent bypass. */
    synchronized void clear() {
        for (Entry entry : entries.values()) invalidate(entry);
    }

    /** Account switches can discard one account without exposing or retaining its raw identifier. */
    synchronized void clearAccount(String account) {
        if (account == null || account.isEmpty()) return;
        String accountHash = hash("account-v1", account);
        for (Entry entry : entries.values()) {
            if (entry.accountHash.equals(accountHash)) invalidate(entry);
        }
    }

    /** Includes opaque cooldown records as well as cached and in-flight entries. */
    synchronized int size() { return entries.size(); }

    private Entry current(Lease lease) {
        if (lease == null || lease.owner != owner) return null;
        Entry entry = entries.get(lease.fingerprint);
        return entry != null && entry.inFlight && entry.token == lease.token ? entry : null;
    }

    private void prune(long now) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.inFlight && remaining(now, entry.attemptedAt, TTL_MS) == 0L)
                invalidate(entry);
            if (entry.json != null && remaining(now, entry.completedAt, TTL_MS) == 0L)
                entry.json = null;
            if (!entry.inFlight && entry.json == null
                    && remaining(now, entry.attemptedAt, KEY_INTERVAL_MS) == 0L)
                iterator.remove();
        }
    }

    private boolean evict(long now) {
        String oldest = null;
        long accessedAt = Long.MAX_VALUE;
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            Entry entry = item.getValue();
            // Never evict a live lease or a cooldown record that would let a key retry early.
            if (!entry.inFlight && remaining(now, entry.attemptedAt, KEY_INTERVAL_MS) == 0L
                    && entry.accessedAt <= accessedAt) {
                oldest = item.getKey();
                accessedAt = entry.accessedAt;
            }
        }
        if (oldest == null) return false;
        entries.remove(oldest);
        return true;
    }

    private long capacityWait(long now) {
        long wait = TTL_MS;
        for (Entry entry : entries.values())
            wait = Math.min(wait, remaining(now, entry.attemptedAt,
                    entry.inFlight ? TTL_MS : KEY_INTERVAL_MS));
        return wait;
    }

    private static void invalidate(Entry entry) {
        entry.json = null;
        entry.inFlight = false;
        entry.token = null;
    }

    private long time(long now) {
        if (now < 0L) throw new IllegalArgumentException("A monotonic timestamp is required");
        // Concurrent callers can sample the same clock immediately before acquiring this lock.
        lastObservedAt = Math.max(lastObservedAt, now);
        return lastObservedAt;
    }

    private static long remaining(long now, long then, long duration) {
        long elapsed = now - then;
        return elapsed >= duration ? 0L : duration - elapsed;
    }

    private static String hash(String namespace, String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, namespace);
            for (String field : fields) update(digest, field);
            byte[] bytes = digest.digest();
            char[] out = new char[bytes.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                out[i * 2] = hex[(bytes[i] & 255) >>> 4];
                out[i * 2 + 1] = hex[bytes[i] & 15];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
