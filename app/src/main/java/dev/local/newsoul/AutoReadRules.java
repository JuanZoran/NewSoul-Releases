package dev.local.newsoul;

import java.util.LinkedHashMap;

/** Only ordinary incoming bodies; never receipts, history scans or local unread mutations. */
final class AutoReadRules {
    private AutoReadRules() {}
    static boolean eligible(String own, String from, String to, String id, int envelope,
            int type, String bodyClass, int snap, String snapFlag, boolean anonymous, int ack) {
        if (!ChatRecallRules.incoming(own, from, to) || id == null || id.isEmpty()
                || envelope != 1 || anonymous || ack != 0 || snap != 0
                || !(snapFlag == null || snapFlag.isEmpty() || "0".equals(snapFlag) || "false".equals(snapFlag))) return false;
        String expected = switch (type) {
            case 1 -> "TextMsg";
            case 2 -> "ImgMsg";
            case 3 -> "ImgMsgs";
            case 4 -> "VideoMsg";
            case 5 -> "AudioMsg";
            case 8 -> "ExpressionMsg";
            default -> "";
        };
        return !expected.isEmpty() && ("cn.soulapp.imlib.msg.chat." + expected).equals(bodyClass);
    }

    record Key(String account, String peer, String id) {}
    /** Bounded process-local enqueue deduplication; host persists successful acknowledgements. */
    static final class Recent {
        private final LinkedHashMap<Key, Long> entries = new LinkedHashMap<>();
        private final int capacity;
        private final long ttl;
        Recent(int capacity, long ttl) { this.capacity = capacity; this.ttl = ttl; }
        synchronized boolean claim(Key key, long now) {
            entries.entrySet().removeIf(e -> now - e.getValue() >= ttl || now < e.getValue());
            if (entries.containsKey(key)) return false;
            remember(key, now); return true;
        }
        synchronized void remember(Key key, long now) {
            entries.remove(key); entries.put(key, now);
            while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
        }
        synchronized void forget(Key key) { entries.remove(key); }
    }
}
