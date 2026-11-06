package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Preferences belong to Soul's sandbox; no credential is embedded in the module. */
final class AiReplyConfig {
    static final String ENABLED = "ai_reply_enabled_v1";
    static final String AUTO_IMAGE_THRESHOLD = "ai_reply_auto_image_threshold_v1";
    static final String AUTO_SUGGESTIONS = "ai_reply_auto_suggestions_v1";
    static final String PROFILE = "ai_reply_profile_v1";
    static final String MODEL = "ai_reply_model_v1";
    private static final String CREDENTIAL = "ai_reply_credential_v1";
    private static final String ALIAS = "dev.local.soulhookdemo.deepseek.v1";
    private static final String MIMO_CREDENTIAL = "ai_reply_mimo_credential_v1";
    private static final String MIMO_ALIAS = "dev.local.soulhookdemo.mimo.v1";
    static final String CUSTOM_ENDPOINT = "ai_reply_custom_endpoint_v1";
    static final String CUSTOM_MODEL = "ai_reply_custom_model_v1";
    private static final String CUSTOM_CREDENTIAL = "ai_reply_custom_credential_v1";
    private static final String CUSTOM_ALIAS = "dev.local.soulhookdemo.custom.v1";

    static boolean enabled(Context context) {
        return PluginPreferences.get(context).getBoolean(ENABLED, true);
    }

    static boolean autoSuggestions(Context context) {
        return enabled(context) && PluginPreferences.get(context).getBoolean(AUTO_SUGGESTIONS, false);
    }

    /** Isolates cached suggestions after any request-affecting settings or credential change. */
    static String requestFingerprint(Context context) throws Exception {
        SharedPreferences prefs=PluginPreferences.get(context);
        AiModelConfig model=selectedModel(context);
        return AiContextRules.digest(new org.json.JSONObject().put("model",model.id)
                .put("provider",model.selectionId).put("endpoint",model.endpoint)
                .put("profile",prefs.getString(PROFILE,""))
                .put("images",autoImageThreshold(context))
                .put("credential",prefs.getString(credential(model),""))
                .put("auto",autoSuggestions(context)).toString());
    }

    static int autoImageThreshold(Context context) {
        int value = PluginPreferences.get(context).getInt(AUTO_IMAGE_THRESHOLD, 0);
        return AiImageSelectionRules.validThreshold(value) ? value : 0;
    }

    static boolean hasKey(Context context) {
        return hasKey(context, AiModelConfig.DEEPSEEK);
    }

    static AiModelConfig selectedModel(Context context) {
        return resolveModel(context, AiModelConfig.fromId(PluginPreferences.get(context)
                .getString(MODEL, AiModelConfig.DEEPSEEK_ID)));
    }

    static AiModelConfig resolveModel(Context context, AiModelConfig model) {
        if (!model.isCustom()) return model;
        SharedPreferences prefs = PluginPreferences.get(context);
        try {
            return AiModelConfig.custom(prefs.getString(CUSTOM_ENDPOINT, ""), prefs.getString(CUSTOM_MODEL, ""));
        } catch (IllegalArgumentException ignored) { return AiModelConfig.CUSTOM; }
    }

    static boolean ready(Context context, AiModelConfig model) {
        AiModelConfig current = resolveModel(context, model);
        return current.configured() && (current.isCustom() || hasKey(context, current));
    }

    static boolean hasSelectedKey(Context context) {
        return ready(context, selectedModel(context));
    }

    static boolean hasKey(Context context, AiModelConfig model) {
        return PluginPreferences.get(context).contains(credential(model));
    }

    /** Bind tests to the provider, destination, model, and saved encrypted credential. */
    static String credentialFingerprint(Context context, AiModelConfig model) throws Exception {
        AiModelConfig current = resolveModel(context, model);
        return AiContextRules.digest(current.selectionId + "\n" + current.id + "\n" + current.endpoint + "\n"
                + PluginPreferences.get(context).getString(credential(model), ""));
    }

    static synchronized void saveKey(Context context, String value) throws Exception {
        saveKey(context, AiModelConfig.DEEPSEEK, value);
    }

    static synchronized void saveKey(Context context, AiModelConfig model, String value) throws Exception {
        String encoded = encodeKey(model, value);
        if (!PluginPreferences.get(context).edit().putString(credential(model), encoded).commit())
            throw new IllegalStateException("密钥保存失败，请重试");
    }

    /** Validate and encrypt before one commit, so a failed save cannot mix old/new settings. */
    static synchronized void saveCustom(Context context, String address, String modelName,
                                        String value, boolean clearKey) throws Exception {
        AiModelConfig model = AiModelConfig.custom(address, modelName);
        String encoded = value.trim().isEmpty() ? null : encodeKey(model, value);
        SharedPreferences.Editor editor = PluginPreferences.get(context).edit()
                .putString(CUSTOM_ENDPOINT, model.endpoint).putString(CUSTOM_MODEL, model.id);
        if (encoded != null) editor.putString(CUSTOM_CREDENTIAL, encoded);
        else if (clearKey) editor.remove(CUSTOM_CREDENTIAL);
        if (!editor.commit()) throw new IllegalStateException("接口保存失败，请重试");
    }

    private static String encodeKey(AiModelConfig model, String value) throws Exception {
        String key = value.trim();
        if (key.length() < (model.isCustom() ? 1 : 8) || key.length() > 1024 || !key.matches("[!-~]+"))
            throw new IllegalArgumentException("密钥格式不正确");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret(model, true));
        byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    static synchronized String readKey(Context context) throws Exception {
        return readKey(context, AiModelConfig.DEEPSEEK);
    }

    static synchronized String readKey(Context context, AiModelConfig model) throws Exception {
        String saved = PluginPreferences.get(context).getString(credential(model), "");
        if (saved.isEmpty() && model.isCustom()) return "";
        if (saved.isEmpty()) throw new IllegalStateException("请先填写 " + model.serviceName() + " 密钥");
        try {
            String[] parts = saved.split(":", -1);
            if (parts.length != 2) throw new IllegalStateException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(model, false),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            throw new IllegalStateException("已保存密钥无法读取，请重新填写");
        }
    }

    static void clearKey(Context context) {
        clearKey(context, AiModelConfig.DEEPSEEK);
    }

    static void clearKey(Context context, AiModelConfig model) {
        PluginPreferences.get(context).edit().remove(credential(model)).apply();
    }

    private static String credential(AiModelConfig model) {
        return model.isCustom() ? CUSTOM_CREDENTIAL : model == AiModelConfig.MIMO ? MIMO_CREDENTIAL : CREDENTIAL;
    }

    private static String alias(AiModelConfig model) {
        return model.isCustom() ? CUSTOM_ALIAS : model == AiModelConfig.MIMO ? MIMO_ALIAS : ALIAS;
    }

    private static SecretKey secret(AiModelConfig model, boolean create) throws Exception {
        String alias = alias(model);
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias, null);
        if (!create) throw new IllegalStateException("密钥不存在");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    static String systemPrompt() throws Exception {
        return AiReplyPrompt.systemPrompt();
    }
}
