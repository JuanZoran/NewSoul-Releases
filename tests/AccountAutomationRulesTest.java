package dev.local.newsoul;
public final class AccountAutomationRulesTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(AccountAutomationRules.allowsDays(null, 30, 60));
        check(AccountAutomationRules.allowsDays(-1, 30, 60));
        check(!AccountAutomationRules.allowsDays(29, 30, 60));
        check(AccountAutomationRules.allowsDays(30, 30, 60));
        check(AccountAutomationRules.allowsDays(60, 30, 60));
        check(!AccountAutomationRules.allowsDays(61, 30, 60));
        check(AccountAutomationRules.allowsDays(100000, 30, 0));
        check(AccountAutomationRules.allowsDays(0, 0, 0));
        check(!AccountAutomationRules.validRange(60, 30));
        check(!AccountAutomationRules.validRange(-1, 0));
        check(AccountAutomationRules.allowsDays(40, 60, 30));
        System.out.println("AccountAutomationRules: 11 boundary checks passed");
    }
}
