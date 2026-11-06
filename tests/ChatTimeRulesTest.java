package dev.local.newsoul;

import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;

public final class ChatTimeRulesTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) throws Exception {
        long time = Instant.parse("2026-10-03T05:06:07Z").toEpochMilli();
        TimeZone china = TimeZone.getTimeZone("Asia/Shanghai");
        check("2026-10-03 13:06:07".equals(ChatTimeRules.format(ChatTimeRules.DEFAULT, time, Locale.CHINA, china)));
        check("13:06:07".equals(ChatTimeRules.format("HH:mm:ss", time, Locale.CHINA, china)));
        check("10-03 13:06".equals(ChatTimeRules.format("MM-dd HH:mm", time, Locale.CHINA, china)));
        check("2026年10月03日".equals(ChatTimeRules.format("yyyy年MM月dd日", time, Locale.CHINA, china)));
        check("01:06 PM".equals(ChatTimeRules.format("hh:mm a", time, Locale.US, china)));
        check("at 13:06".equals(ChatTimeRules.format("'at' HH:mm", time, Locale.US, china)));
        check("13'06".equals(ChatTimeRules.format("HH''mm", time, Locale.US, china)));
        check("05:06".equals(ChatTimeRules.format("HH:mm", time, Locale.US, TimeZone.getTimeZone("UTC"))));
        long midnight = Instant.parse("2026-10-02T16:00:00Z").toEpochMilli();
        check("2026-10-03 00:00:00".equals(ChatTimeRules.format(ChatTimeRules.DEFAULT, midnight, Locale.CHINA, china)));
        for (String bad : new String[]{null, "", " ", "纯文字", "'yyyy'", "yyyy '", "yyyy QQ", "YYYY-MM-dd", "HH\nmm", "HH\tmm", "y".repeat(65)})
            check(!ChatTimeRules.valid(bad));
        check(ChatTimeRules.valid("yyyy-MM-dd E a hh:mm:ss"));
        // Formatter instances must not share mutable locale/timezone state across threads.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            String[] patterns = {"HH:mm:ss", "yyyy-MM-dd HH:mm", "EEE hh:mm a", "MM-dd HH:mm:ss"};
            long[] times = {time, time + 86400000L, midnight, time + 3600000L};
            Locale[] locales = {Locale.CHINA, Locale.US, Locale.US, Locale.CHINA};
            TimeZone[] zones = {china, TimeZone.getTimeZone("UTC"),
                    TimeZone.getTimeZone("America/Los_Angeles"), TimeZone.getTimeZone("Asia/Tokyo")};
            String[] expected = {"13:06:07", "2026-10-04 05:06", "Fri 09:00 AM", "10-03 15:06:07"};
            var start = new java.util.concurrent.CyclicBarrier(4);
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i=0;i<4;i++) {
                final int index=i;
                results.add(pool.submit(() -> {
                    for (int round=0;round<20;round++) {
                        start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        String actual=ChatTimeRules.format(patterns[index], times[index], locales[index], zones[index]);
                        if (!expected[index].equals(actual))
                            throw new AssertionError("concurrent configuration " + index + ": " + actual);
                    }
                    return 20;
                }));
            }
            for (var result : results) check(result.get(10, java.util.concurrent.TimeUnit.SECONDS)==20);
        } finally { pool.shutdownNow(); }
        check(SoulUpdateRules.block(true, 2, true));
        check(SoulUpdateRules.block(true, 0, true));
        check(!SoulUpdateRules.block(false, 2, true));
        check(!SoulUpdateRules.block(true, 1, true));
        check(!SoulUpdateRules.block(true, 2, false));
        System.out.println("PASS " + checks + " time/update checks");
    }
}
