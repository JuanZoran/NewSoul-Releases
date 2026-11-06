package dev.local.newsoul;

/** Configuration contract independent of Android widgets. Legacy palette values remain valid. */
final class FeedLabelStyleRules {
    static boolean color(String value) {
        return value != null && (java.util.Arrays.asList("default", "mint", "blue", "orange").contains(value)
                || value.matches("#[0-9a-fA-F]{6}"));
    }
    static boolean size(String value) {
        if ("default".equals(value)) return true;
        if (value == null || !value.matches("[0-9]{1,2}")) return false;
        int n = Integer.parseInt(value);
        return n >= 8 && n <= 24;
    }
    private FeedLabelStyleRules() {}
}
