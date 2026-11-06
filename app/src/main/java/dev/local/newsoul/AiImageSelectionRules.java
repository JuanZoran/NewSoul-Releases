package dev.local.newsoul;

final class AiImageSelectionRules {
    // Zero disables; 2..5 means strictly fewer than N, within the four-image request budget.
    static boolean validThreshold(int threshold) { return threshold == 0 || threshold >= 2 && threshold <= 5; }
    static boolean autoSelect(int count, int threshold) {
        return validThreshold(threshold) && threshold > 0 && count > 0
                && count < threshold && count <= AiReplyRules.MAX_IMAGES;
    }
}
