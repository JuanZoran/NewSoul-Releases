package dev.local.newsoul;

public final class AiContextRulesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        eq("今天想去看展，周末有空吗？", AiContextRules.cleanText("今天想去看展，周末有空吗？", 100));
        eq("电话 [手机号已省略]", AiContextRules.cleanText("电话 13812345678", 100));
        eq("联系 [手机号已省略]", AiContextRules.cleanText("联系 +86 13812345678", 100));
        eq("看看 [链接已省略]", AiContextRules.cleanText("看看 https://img.soulapp.cn/a.jpg?token=secret", 100));
        eq("[凭据已省略]", AiContextRules.cleanText("sk-abcdefghijklmnopqrstuvwxyz", 100));
        eq("[凭据已省略]", AiContextRules.cleanText("Authorization: Bearerabcdefghijklmn", 100));
        eq("[凭据已省略]", AiContextRules.cleanText("api_key=abcdefghijklmn", 100));
        eq("周末", AiContextRules.cleanText("周末😀可以", 3));
        eq("周末😀", AiContextRules.cleanText("周末😀可以", 4));
        eq("", AiContextRules.cleanText(null, 10));
        eq("https://img.soulapp.cn/image/a.jpg", AiContextRules.ordinaryImageUrl("https://img.soulapp.cn/image/a.jpg"));
        eq("https://china-img.soulapp.cn/image/a.jpg?x-oss-process=image/resize,w_512",
                AiContextRules.ordinaryImageUrl("https://china-img.soulapp.cn/image/a.jpg?x-oss-process=image/resize,w_512"));
        for (String url : new String[]{"http://img.soulapp.cn/a.jpg", "https://evil.example/a.jpg",
                "https://img.soulapp.cn.evil.example/a.jpg", "https://127.0.0.1/a.jpg", "file:///tmp/image.jpg",
                "content://media/external/1", "https://secret@img.soulapp.cn/a.jpg", "https://img.soulapp.cn:8080/a.jpg",
                "https://img.soulapp.cn/a.jpg?token=secret", "https://img.soulapp.cn/a.jpg?Signature=secret",
                "https://img.soulapp.cn/a.jpg#credential", "not a url"}) eq(null, AiContextRules.ordinaryImageUrl(url));
        eq(AiContextRules.digest("same account/session/message"), AiContextRules.digest("same account/session/message"));
        if (AiContextRules.digest("a/session/message").equals(AiContextRules.digest("b/session/message"))) throw new AssertionError("Identity collision");
        checks++;
        eq("{\"a\":1,\"b\":{\"x\":2,\"y\":3}}", AiContextRules.canonical(
                new org.json.JSONObject("{\"b\":{\"y\":3,\"x\":2},\"a\":1}")));
        eq(AiContextRules.canonical(new org.json.JSONObject("{\"a\":1,\"b\":2}")),
                AiContextRules.canonical(new org.json.JSONObject("{\"b\":2,\"a\":1}")));
        if (AiContextRules.canonical(new org.json.JSONArray("[1,2]")).equals(
                AiContextRules.canonical(new org.json.JSONArray("[2,1]")))) throw new AssertionError("Array order lost");
        checks++;
        eq("[null,true,\"😀\"]", AiContextRules.canonical(new org.json.JSONArray("[null,true,\"😀\"]")));
        System.out.println("AiContextRulesTest: " + checks + " checks passed");
    }

    private static void eq(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
        checks++;
    }
}
