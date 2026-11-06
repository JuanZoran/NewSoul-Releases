package dev.local.newsoul;

final class SoulUpdateRules {
    static boolean block(boolean enabled, int trigger, boolean showPrompt) {
        // Soul uses trigger 1 for an explicit check; false selects the download path.
        return enabled && showPrompt && trigger != 1;
    }
}
