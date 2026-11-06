package dev.local.newsoul;

public final class AiModelConfigTest {
    private static int checks;

    public static void main(String[] args) {
        check(AiModelConfig.fromId(null) == AiModelConfig.DEEPSEEK, "null defaults to DeepSeek");
        check(AiModelConfig.fromId("unknown") == AiModelConfig.DEEPSEEK, "unknown defaults to DeepSeek");
        check(AiModelConfig.fromId("deepseek-flash") == AiModelConfig.DEEPSEEK, "DeepSeek selected");
        check(AiModelConfig.fromId("mimo-v2.6-flash") == AiModelConfig.MIMO, "MiMo selected");
        check("https://api.deepseek.com/chat/completions".equals(AiModelConfig.DEEPSEEK.endpoint),
                "DeepSeek endpoint");
        check("https://api.xiaomimimo.com/v1/chat/completions".equals(AiModelConfig.MIMO.endpoint),
                "MiMo endpoint");
        check("max_tokens".equals(AiModelConfig.DEEPSEEK.outputTokenField), "DeepSeek token field");
        check("max_completion_tokens".equals(AiModelConfig.MIMO.outputTokenField), "MiMo token field");
        check(AiModelConfig.fromId(AiModelConfig.CUSTOM_ID) == AiModelConfig.CUSTOM, "custom selection marker");
        check(!AiModelConfig.CUSTOM.configured(), "unconfigured custom model cannot request");
        String[][] addresses = {
            {"http://127.0.0.1:11434", "http://127.0.0.1:11434/v1/chat/completions"},
            {" http://192.168.1.2:1234/v1/ ", "http://192.168.1.2:1234/v1/chat/completions"},
            {"https://example.com/proxy/v1", "https://example.com/proxy/v1/chat/completions"},
            {"https://example.com/api/chat/completions/", "https://example.com/api/chat/completions"},
            {"http://[::1]:1234/v1", "http://[::1]:1234/v1/chat/completions"}
        };
        for (String[] address : addresses) {
            AiModelConfig custom = AiModelConfig.custom(address[0], " qwen:8b ");
            check(address[1].equals(custom.endpoint), "normalized custom endpoint");
            check(custom.isCustom() && custom.configured() && custom.id.equals("qwen:8b")
                    && custom.selectionId.equals(AiModelConfig.CUSTOM_ID), "provider and requested model separated");
        }
        for (String address : new String[]{"", "ftp://example.com/v1", "https://user:secret@example.com/v1",
                "https://example.com/v1?key=secret", "https://example.com/v1#secret", "http:///v1",
                "http://example.com:0/v1", "http://example.com:65536/v1", "http://exam ple.com/v1"}) {
            try { AiModelConfig.custom(address, "model"); throw new AssertionError("accepted invalid address"); }
            catch (IllegalArgumentException expected) {
                check(!expected.getMessage().contains(address.isEmpty() ? "secret" : address), "safe address rejection");
            }
        }
        for (String name : new String[]{"", "a b", "a\nb", "x".repeat(257)}) {
            try { AiModelConfig.custom("https://example.com/v1", name); throw new AssertionError("invalid model"); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().contains("模型名"), "model rejected"); }
        }
        System.out.println("AiModelConfig: " + checks + " offline checks passed");
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
}
