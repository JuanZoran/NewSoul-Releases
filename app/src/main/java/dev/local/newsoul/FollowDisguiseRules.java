package dev.local.newsoul;

/** Count arithmetic is independent of host objects: offsets never accumulate across binds. */
final class FollowDisguiseRules {
    static boolean isVisitorMetric(CharSequence label) {
        if (label == null) return false;
        String text = label.toString().trim();
        return "看过我".equals(text) || "访客".equals(text) || "访客数".equals(text);
    }
    static String replaceDays(String original, String source, int value) {
        int start = source == null || source.isEmpty() ? -1 : original.indexOf(source);
        if (start < 0) return original;
        return original.substring(0, start) + value + original.substring(start + source.length());
    }
    static int apply(int original, boolean enabled, String mode, int value) {
        if (!enabled) return original;
        long result = "delta".equals(mode) ? (long) original + value : value;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, result));
    }
    static int parse(String text, boolean delta) {
        try {
            int value = Integer.parseInt(text.trim());
            if (!delta && value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(delta ? "请输入 -2147483648 至 2147483647 的整数"
                    : "请输入 0 至 2147483647 的整数");
        }
    }
}
