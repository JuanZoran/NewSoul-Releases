package dev.local.newsoul;
final class ChatExperienceControls {
    static boolean gift, bell;
    static boolean enabled(String key) { return ChatGateControls.GIFT.equals(key) ? gift : bell; }
}
