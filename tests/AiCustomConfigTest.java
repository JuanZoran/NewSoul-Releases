package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Real configuration code with an in-memory preference boundary; no Android Keystore claim. */
public final class AiCustomConfigTest {
    private static final Map<String,Object> saved = new HashMap<>();
    private static int checks, commits;
    static final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
            AiCustomConfigTest.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "contains": return saved.containsKey(args[0]);
                    case "getString": case "getBoolean": case "getInt": return saved.getOrDefault(args[0], args[1]);
                    case "edit": return editor();
                    default: throw new AssertionError(method.getName());
                }
            });

    private static SharedPreferences.Editor editor() {
        Map<String,Object> pending = new HashMap<>(saved);
        return (SharedPreferences.Editor) Proxy.newProxyInstance(AiCustomConfigTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "putString": pending.put((String) args[0], args[1]); return proxy;
                        case "remove": pending.remove(args[0]); return proxy;
                        case "commit": saved.clear(); saved.putAll(pending); commits++; return true;
                        default: throw new AssertionError(method.getName());
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        Context context = new Context();
        saved.put(AiReplyConfig.MODEL, AiModelConfig.CUSTOM_ID);
        require(!AiReplyConfig.hasSelectedKey(context), "missing endpoint/model fails readiness");
        saved.put("ai_reply_credential_v1", "deepseek-encrypted");
        saved.put("ai_reply_mimo_credential_v1", "mimo-encrypted");
        AiReplyConfig.saveCustom(context, "http://127.0.0.1:1234/v1", "model-a", "", false);
        require(commits == 1 && AiReplyConfig.hasSelectedKey(context), "one commit makes keyless custom ready");
        require(!AiReplyConfig.hasKey(context, AiModelConfig.CUSTOM), "custom does not borrow another provider key");
        require(AiReplyConfig.readKey(context, AiModelConfig.CUSTOM).isEmpty(), "keyless custom read");
        AiModelConfig first = AiReplyConfig.selectedModel(context);
        require(first.isCustom() && first.id.equals("model-a"), "selected dynamic model");
        String test = AiReplyConfig.credentialFingerprint(context, AiModelConfig.CUSTOM);
        String cache = AiReplyConfig.requestFingerprint(context);
        saved.put("ai_reply_custom_credential_v1", "custom-encrypted");
        require(!test.equals(AiReplyConfig.credentialFingerprint(context, AiModelConfig.CUSTOM)), "key change invalidates test");
        require(!cache.equals(AiReplyConfig.requestFingerprint(context)), "key change invalidates suggestions");
        test = AiReplyConfig.credentialFingerprint(context, AiModelConfig.CUSTOM);
        cache = AiReplyConfig.requestFingerprint(context);
        AiReplyConfig.saveCustom(context, "http://192.168.1.2:1234/v1", "model-a", "", false);
        require("custom-encrypted".equals(saved.get("ai_reply_custom_credential_v1")), "blank keeps saved custom key");
        require(!test.equals(AiReplyConfig.credentialFingerprint(context, first)), "endpoint change invalidates old model test");
        require(!cache.equals(AiReplyConfig.requestFingerprint(context)), "endpoint change invalidates suggestions");
        test = AiReplyConfig.credentialFingerprint(context, first);
        cache = AiReplyConfig.requestFingerprint(context);
        AiReplyConfig.saveCustom(context, "http://192.168.1.2:1234/v1", "model-b", "", false);
        require(!test.equals(AiReplyConfig.credentialFingerprint(context, first)), "model change invalidates test");
        require(!cache.equals(AiReplyConfig.requestFingerprint(context)), "model change invalidates suggestions");
        AiReplyConfig.saveCustom(context, "http://192.168.1.2:1234/v1", "model-b", "", true);
        require(AiReplyConfig.hasSelectedKey(context) && !AiReplyConfig.hasKey(context, first), "explicit clear retains keyless readiness");
        Map<String,Object> before = new HashMap<>(saved);
        int count = commits;
        for (String[] invalid : new String[][]{{"invalid", "model"}, {"https://example.com/v1", ""}}) {
            try { AiReplyConfig.saveCustom(context, invalid[0], invalid[1], "", true); throw new AssertionError("invalid settings"); }
            catch (IllegalArgumentException expected) { require(saved.equals(before) && count == commits, "invalid save never writes"); }
        }
        require("deepseek-encrypted".equals(saved.get("ai_reply_credential_v1"))
                && "mimo-encrypted".equals(saved.get("ai_reply_mimo_credential_v1")), "existing credentials preserved");
        saved.put(AiReplyConfig.MODEL, AiModelConfig.MIMO_ID);
        require(AiReplyConfig.selectedModel(context) == AiModelConfig.MIMO && AiReplyConfig.hasSelectedKey(context), "existing selection preserved");
        System.out.println("AiCustomConfigTest: " + checks + " configuration checks passed; Keystore/device pending");
    }

    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
