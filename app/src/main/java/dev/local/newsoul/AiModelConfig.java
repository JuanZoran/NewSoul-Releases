package dev.local.newsoul;

/** Immutable, Android-free model metadata shared by request building and settings UI. */
final class AiModelConfig {
    static final String DEEPSEEK_ID = "deepseek-flash";
    static final String MIMO_ID = "mimo-v2.6-flash";
    static final String CUSTOM_ID = "custom-openai-v1";
    static final AiModelConfig CUSTOM = new AiModelConfig(CUSTOM_ID, "自定义接口", "", "max_tokens", CUSTOM_ID);

    static final AiModelConfig DEEPSEEK = new AiModelConfig(
            DEEPSEEK_ID, "DeepSeek V4.1 Flash",
            "https://api.deepseek.com/chat/completions", "max_tokens");
    static final AiModelConfig MIMO = new AiModelConfig(
            MIMO_ID, "MiMo V2.6 Flash",
            "https://api.xiaomimimo.com/v1/chat/completions", "max_completion_tokens");

    final String id;
    final String displayName;
    final String endpoint;
    final String outputTokenField;
    final String selectionId;

    private AiModelConfig(String id, String displayName, String endpoint, String outputTokenField) {
        this(id, displayName, endpoint, outputTokenField, id);
    }

    private AiModelConfig(String id, String displayName, String endpoint, String outputTokenField, String selectionId) {
        this.id = id;
        this.displayName = displayName;
        this.endpoint = endpoint;
        this.outputTokenField = outputTokenField;
        this.selectionId = selectionId;
    }

    static AiModelConfig fromId(String id) {
        return CUSTOM_ID.equals(id) ? CUSTOM : MIMO_ID.equals(id) ? MIMO : DEEPSEEK;
    }

    static AiModelConfig custom(String address, String model) {
        String id = model == null ? "" : model.trim();
        if (id.isEmpty() || id.length() > 256 || !id.matches("[!-~]+"))
            throw new IllegalArgumentException("请填写有效的模型名");
        return new AiModelConfig(id, CUSTOM.displayName, normalizeEndpoint(address), "max_tokens", CUSTOM_ID);
    }

    /** Accept a base URL (including a proxy prefix), /v1, or the complete chat endpoint. */
    static String normalizeEndpoint(String address) {
        try {
            String value = address == null ? "" : address.trim();
            if (value.length() > 2048 || !value.matches("[!-~]+")) throw new IllegalArgumentException();
            java.net.URI uri = new java.net.URI(value);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            String path = uri.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) value += "/v1/chat/completions";
            else if (!value.endsWith("/chat/completions")) value += "/chat/completions";
            return value;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("请填写 HTTP 或 HTTPS 接口地址，不含账号、查询参数或片段");
        }
    }

    boolean isCustom() { return CUSTOM_ID.equals(selectionId); }
    boolean configured() { return !endpoint.isEmpty(); }

    void requireConfigured() {
        if (!configured()) throw new IllegalArgumentException("请先填写自定义接口地址与模型名");
    }

    String serviceName() {
        return isCustom() ? "自定义接口" : this == MIMO ? "MiMo" : "DeepSeek";
    }
}
