package dev.local.newsoul;

public final class FollowDisguiseRulesTest {
    private static int checks;
    private static void equal(int actual, int expected) {
        checks++; if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    private static void rejects(String value, boolean delta) {
        checks++;
        try { FollowDisguiseRules.parse(value, delta); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid count: " + value);
    }
    public static void main(String[] args) {
        for (String label : new String[]{"看过我", "访客", " 访客数 "}) {
            checks++; if (!FollowDisguiseRules.isVisitorMetric(label)) throw new AssertionError(label);
        }
        for (String label : new String[]{"赞过我", "获赞", "礼物", "", null}) {
            checks++; if (FollowDisguiseRules.isVisitorMetric(label)) throw new AssertionError(label);
        }
        equal(FollowDisguiseRules.apply(123, false, "absolute", 999), 123);
        equal(FollowDisguiseRules.apply(123, true, "absolute", 999), 999);
        equal(FollowDisguiseRules.apply(123, true, "absolute", 0), 0);
        equal(FollowDisguiseRules.apply(123, true, "delta", 50), 173);
        equal(FollowDisguiseRules.apply(123, true, "delta", -50), 73);
        equal(FollowDisguiseRules.apply(123, true, "delta", -500), 0);
        equal(FollowDisguiseRules.apply(Integer.MAX_VALUE, true, "delta", Integer.MAX_VALUE), Integer.MAX_VALUE);
        equal(FollowDisguiseRules.apply(123, true, "delta", Integer.MIN_VALUE), 0);
        for (int i = 0; i < 10; i++) equal(FollowDisguiseRules.apply(123, true, "delta", 50), 173);
        equal(FollowDisguiseRules.parse(" +50 ", true), 50);
        equal(FollowDisguiseRules.parse("-50", true), -50);
        equal(FollowDisguiseRules.parse("0", false), 0);
        equal(FollowDisguiseRules.parse("2147483647", false), Integer.MAX_VALUE);
        rejects("", false); rejects("-", true); rejects("1.5", true);
        rejects("-1", false); rejects("2147483648", true); rejects("-2147483649", true);
        String[][] texts = {{"20天", "20", "100天"}, {"已加入20天", "20", "已加入100天"},
                {"20 days", "20", "100 days"}, {"已加入20天", "", "已加入20天"}, {"用户已注销", "20", "用户已注销"}};
        for (String[] item : texts) {
            checks++;
            if (!item[2].equals(FollowDisguiseRules.replaceDays(item[0], item[1], 100))) throw new AssertionError(item[0]);
        }
        System.out.println("FollowDisguiseRules: " + checks + " checks passed");
    }
}
