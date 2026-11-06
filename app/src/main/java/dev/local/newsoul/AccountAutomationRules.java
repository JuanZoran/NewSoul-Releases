package dev.local.newsoul;

final class AccountAutomationRules {
    static Integer registrationDays(Object raw) {
        if (!(raw instanceof Number) && !(raw instanceof String)) return null;
        String value = raw.toString().trim();
        if (!value.matches("[0-9]+")) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException overflow) { return null; }
    }
    static boolean allowsDays(Integer days, int min, int max) {
        return !validRange(min, max) || days == null || days < 0 || (days >= min && (max == 0 || days <= max));
    }
    static boolean validRange(int min, int max) {
        return min >= 0 && max >= 0 && (max == 0 || min <= max);
    }
}
