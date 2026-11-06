package dev.local.newsoul;
public final class InteractionPrivacyRulesTest {
    static int checks;
    static void check(boolean value) { checks++; if(!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        for (int outer : new int[]{1,2,5,6,7}) for (int inner : new int[]{-1,1,5,22,23,25}) {
            check(!InteractionPrivacyRules.blockReceipt(false,outer,inner));
            check(InteractionPrivacyRules.blockReceipt(true,outer,inner)==(outer==1&&inner==25));
        }
        check(InteractionPrivacyRules.selectionLimit(false,9)==9);
        check(InteractionPrivacyRules.selectionLimit(true,9)==99);
        check(InteractionPrivacyRules.selectionLimit(true,120)==120);
        System.out.println("Interaction privacy: " + checks + " checks passed");
    }
}
