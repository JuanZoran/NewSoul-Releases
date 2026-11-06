package dev.local.newsoul;

final class ChatRecallRules {
    static boolean incoming(String own, String from, String to) {
        return own != null && !own.isEmpty() && from != null && !from.isEmpty()
                && !from.equals(own) && own.equals(to);
    }
}
