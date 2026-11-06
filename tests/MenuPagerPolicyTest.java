package dev.local.newsoul;

public final class MenuPagerPolicyTest {
    private static int checks;
    private static void eq(int expected, int actual, String scenario) {
        checks++;
        if (expected != actual) throw new AssertionError(scenario + ": expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        eq(MenuPagerPolicy.UNDECIDED, MenuPagerPolicy.direction(4, 3, 8), "tap jitter");
        eq(MenuPagerPolicy.HORIZONTAL, MenuPagerPolicy.direction(-40, 8, 8), "clear horizontal swipe");
        eq(MenuPagerPolicy.VERTICAL, MenuPagerPolicy.direction(12, 30, 8), "list scrolling");
        eq(MenuPagerPolicy.VERTICAL, MenuPagerPolicy.direction(30, 25, 8), "diagonal favors vertical");
        eq(MenuPagerPolicy.VERTICAL, MenuPagerPolicy.direction(26, 18, 8), "ambiguous long diagonal");
        eq(0, MenuPagerPolicy.destination(0, 4, 250, 360, 1800, 400, false), "first page cannot wrap");
        eq(3, MenuPagerPolicy.destination(3, 4, -250, 360, -1800, 400, false), "last page cannot wrap");
        eq(1, MenuPagerPolicy.destination(1, 4, -70, 360, 0, 400, false), "short drag rebounds");
        eq(2, MenuPagerPolicy.destination(1, 4, -120, 360, 0, 400, false), "long drag advances");
        eq(0, MenuPagerPolicy.destination(1, 4, 120, 360, 0, 400, false), "long reverse drag advances");
        eq(2, MenuPagerPolicy.destination(1, 4, -30, 360, -900, 400, false), "short decisive fling");
        eq(1, MenuPagerPolicy.destination(1, 4, -180, 360, 900, 400, false), "reverse fling cancels long drag");
        eq(1, MenuPagerPolicy.destination(1, 4, -300, 360, -1900, 400, true), "cancel overrides distance and speed");
        eq(1, MenuPagerPolicy.destination(1, 4, -100, 0, -1900, 400, false), "zero size remains stable");
        eq(1, MenuPagerPolicy.destination(1, 4, -2, 360, -1900, 400, false), "velocity spike without displacement");
        eq(2, MenuPagerPolicy.destination(1, 4, -1000, 360, -1900, 400, false), "one gesture advances one category");
        System.out.println("MenuPagerPolicyTest: " + checks + " scenarios passed");
    }
}
