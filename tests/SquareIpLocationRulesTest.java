package dev.local.newsoul;
public final class SquareIpLocationRulesTest {
    static int checks;
    static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    public static void main(String[] args) {
        check(SquareIpLocationRules.label(null).isEmpty());
        check(SquareIpLocationRules.label("  ").isEmpty());
        check(SquareIpLocationRules.label("IP属地：").isEmpty());
        check(SquareIpLocationRules.label(" 江西 ").equals("IP属地：江西"));
        check(SquareIpLocationRules.label("IP属地: 广东").equals("IP属地：广东"));
        check(SquareIpLocationRules.label("IP属地：中国香港").equals("IP属地：中国香港"));
        check(SquareIpLocationRules.label("美国").equals("IP属地：美国"));
        check(SquareIpLocationRules.fits(240, 150, 90));
        check(!SquareIpLocationRules.fits(239, 150, 90));
        check(!SquareIpLocationRules.fits(0, 0, 90));
        check(!SquareIpLocationRules.fits(Integer.MAX_VALUE, Integer.MAX_VALUE, 90));
        System.out.println("IP location rules: " + checks + " checks passed");
    }
}
