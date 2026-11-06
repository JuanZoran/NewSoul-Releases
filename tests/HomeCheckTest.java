package dev.local.newsoul;

public final class HomeCheckTest {
    private static int checks;
    private static void expect(boolean value) {checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args) {
        HomeCheck check=new HomeCheck();
        expect(!check.accept("x",10,283));
        check.begin("first",100,283);
        expect(!check.valid("wrong",101,283));
        expect(check.valid("first",101,283));
        expect(check.valid("first",102,283));
        expect(!check.valid("first",101,282));
        expect(!check.accept("wrong",101,283));
        expect(!check.accept("first",101,282));
        expect(!check.accept("first",99,283));
        expect(check.accept("first",8100,283));
        expect(!check.accept("first",8100,283));
        check.begin("second",9000,284);
        expect(!check.accept("first",9001,284));
        expect(!check.accept("second",17001,284));
        check.begin("third",18000,284);
        expect(check.accept("third",18001,284));
        for(String version:HomeCheck.VERSIONS)expect(HomeCheck.supported(version));
        expect(!HomeCheck.supported(null));expect(!HomeCheck.supported("6.38.01"));
        expect(!HomeCheck.supported("6.38.0-beta"));expect(!HomeCheck.supported("6.40.0"));
        expect(HomeCheck.windowStart(3, 2, 4) == 0);
        expect(HomeCheck.windowStart(8, 0, 4) == 0);
        expect(HomeCheck.windowStart(8, 3, 4) == 2);
        expect(HomeCheck.windowStart(8, 7, 4) == 4);
        expect(HomeCheck.windowStart(8, -1, 4) == 0);
        expect(HomeCheck.windowStart(8, 8, 4) == 0);
        expect(HomeCheck.windowStart(0, 0, 4) == 0);
        expect(HomeCheck.windowStart(8, 4, 1) == 4);
        for (int count = 1; count < 20; count++) {
            for (int slots = 1; slots <= Math.min(4, count); slots++) {
                for (int selected = 0; selected < count; selected++) {
                    int start = HomeCheck.windowStart(count, selected, slots);
                    expect(start >= 0 && start + slots <= count);
                    expect(start <= selected && selected < start + slots);
                }
            }
        }
        System.out.println("Home check: "+checks+" checks passed");
    }
}
