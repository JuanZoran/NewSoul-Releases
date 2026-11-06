package dev.local.newsoul;

final class LocalHistoryRules {
    static long timestamp(long server, long local) { return server > 1 ? server : local; }
}
