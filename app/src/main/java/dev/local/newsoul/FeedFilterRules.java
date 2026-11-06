package dev.local.newsoul;

/** Matches explicit author metadata; never infers identity from post content. */
final class FeedFilterRules {
    static boolean matches(String wantedGender, String gender, boolean keepUnknown) {
        if ("ANY".equals(wantedGender)) return true;
        if (!"MALE".equals(gender) && !"FEMALE".equals(gender)) return keepUnknown;
        return wantedGender.equals(gender);
    }
}
