package dev.local.newsoul;
public final class LocalHistoryRulesTest {
    public static void main(String[] args) {
        check(LocalHistoryRules.timestamp(1, 1000) == 1000, "placeholder time");
        check(LocalHistoryRules.timestamp(0, 1000) == 1000, "unsent local time");
        check(LocalHistoryRules.timestamp(900, 1000) == 900, "server chronology");
        System.out.println("LocalHistoryRules: 3 checks passed");
    }
    static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);}
}
