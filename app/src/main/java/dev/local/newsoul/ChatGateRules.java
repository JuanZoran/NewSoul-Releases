package dev.local.newsoul;

final class ChatGateRules {
    static boolean limitInfo(String url) {
        return HostDecodedResponse.matchesPath(url, "/chat/limitInfo");
    }
    private ChatGateRules() {}
}
