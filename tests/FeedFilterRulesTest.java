package dev.local.newsoul;
public class FeedFilterRulesTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        check(FeedFilterRules.matches("FEMALE", "FEMALE", false));
        check(!FeedFilterRules.matches("FEMALE", "MALE", false));
        check(!FeedFilterRules.matches("FEMALE", "MALE", true));
        check(!FeedFilterRules.matches("MALE", "FEMALE", true));
        check(FeedFilterRules.matches("MALE", "MALE", false));
        check(!FeedFilterRules.matches("FEMALE", null, false));
        check(!FeedFilterRules.matches("FEMALE", "UNKNOWN", false));
        check(FeedFilterRules.matches("FEMALE", null, true));
        check(FeedFilterRules.matches("FEMALE", "UNKNOWN", true));
        check(FeedFilterRules.matches("ANY", null, false));
        check(FeedFilterRules.matches("ANY", "FEMALE", false));
        check(FeedFilterRules.matches("ANY", "MALE", false));
        System.out.println(checks + " filter rule checks passed");
    }
}
