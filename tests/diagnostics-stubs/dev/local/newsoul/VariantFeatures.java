package dev.local.newsoul;

/** Controls edition and dynamic listener readiness without loading Android AI state. */
final class VariantFeatures {
    static boolean ai;
    static boolean ready;
    static int readinessChecks;
    static boolean hasAi() { return ai; }
    static boolean aiEntryReady() { readinessChecks++; return ready; }
}
