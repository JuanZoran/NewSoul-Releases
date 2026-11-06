package dev.local.newsoul;
public final class ChatGateRulesTest {
    public static void main(String[] args) {
        int checks = 0;
        for (String url : new String[]{"https://api-a.soulapp.cn/chat/limitInfo", "https://api-a.soulapp.cn/chat/limitInfo?uid=test"}) {
            if (!ChatGateRules.limitInfo(url)) throw new AssertionError(url); checks++;
        }
        for (String url : new String[]{null, "", "bad url", "https://api-a.soulapp.cn/chat/limit/friendly/check", "https://api-a.soulapp.cn/chat/limitInfoExtra", "https://api-a.soulapp.cn/other?path=/chat/limitInfo", "https://api-a.soulapp.cn/chat/limitInfo/", "https://api-a.soulapp.cn/v2/chat/limitInfo"}) {
            if (ChatGateRules.limitInfo(url)) throw new AssertionError(url); checks++;
        }
        System.out.println("ChatGateRules: " + checks + " checks passed");
    }
}
