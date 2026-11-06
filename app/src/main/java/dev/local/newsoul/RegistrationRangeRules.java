package dev.local.newsoul;

/** Slider geometry keeps the last stop reserved for an unbounded upper limit. */
final class RegistrationRangeRules {
    static String label(int value, boolean upper) {
        return (upper && value == 0 ? "+∞" : String.valueOf(upper ? value : Math.max(1, value))) + " 天";
    }
    static int parse(String text, boolean upper) {
        String value = text.trim().replace(" 天", "").trim();
        if (value.isEmpty() && upper) return 0;
        if (upper && value.equals("+∞")) return 0;
        int number = Integer.parseInt(value);
        if (number < 1) throw new NumberFormatException();
        return number;
    }
    static int ceiling(int low, int high) { return Math.max(3650, Math.max(low, high)); }
    static float position(int value, int ceiling, boolean upper) {
        return upper && value == 0 ? 1f : (float)(.96 * Math.log(Math.max(1, value)) / Math.log(ceiling));
    }
    static int value(float position, int ceiling, boolean upper) {
        if (upper && position >= .98f) return 0;
        if (position >= .96f) return ceiling;
        long value = Math.round(Math.exp(Math.max(0, position) / .96 * Math.log(ceiling)));
        return (int)Math.max(1, Math.min(Integer.MAX_VALUE, value));
    }
}
