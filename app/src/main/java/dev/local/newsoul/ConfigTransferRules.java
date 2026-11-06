package dev.local.newsoul;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Versioned, explicit allowlist. Never serialize the complete preferences map. */
final class ConfigTransferRules {
    static final int MAX_BYTES = 128 * 1024;
    private static final Map<String,Object> DEFAULTS = ConfigDefaults.REQUIRED;
    private static final Map<String,Object> OPTIONAL = ConfigDefaults.OPTIONAL;
    static String exportConfig(Map<String,?> preferences) throws Exception {
        JSONObject settings = new JSONObject();
        for(Map.Entry<String,Object> entry:DEFAULTS.entrySet()) {
            Object value=preferences.containsKey(entry.getKey())?preferences.get(entry.getKey()):entry.getValue();
            settings.put(entry.getKey(),validate(entry.getKey(),value,entry.getValue()));
        }
        for(Map.Entry<String,Object> entry:OPTIONAL.entrySet()) {
            if(preferences.containsKey(entry.getKey()))
                settings.put(entry.getKey(),validate(entry.getKey(),preferences.get(entry.getKey()),entry.getValue()));
        }
        String result=new JSONObject().put("format","newsoul-config").put("schemaVersion",1)
                .put("settings",settings).toString(2);
        if(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw invalid();
        return result;
    }
    static Map<String,Object> parse(String text) throws Exception {
        return parse(text, Collections.emptyMap());
    }
    static Map<String,Object> parse(String text, Map<String,?> current) throws Exception {
        if(text==null || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw invalid();
        JSONTokener parser=new JSONTokener(text);
        Object parsed=parser.nextValue();
        if(!(parsed instanceof JSONObject)||parser.nextClean()!=0)throw invalid();
        JSONObject root=(JSONObject)parsed;
        if(!"newsoul-config".equals(root.opt("format"))||!Integer.valueOf(1).equals(root.opt("schemaVersion")))throw invalid();
        JSONObject settings=root.getJSONObject("settings");
        Map<String,Object> accepted=new LinkedHashMap<>();
        java.util.Iterator<String> keys=settings.keys();
        while(keys.hasNext()) {
            String key=keys.next();
            Object type=DEFAULTS.containsKey(key)?DEFAULTS.get(key):OPTIONAL.get(key);
            if(type!=null)accepted.put(key,validate(key,settings.get(key),type));
        }
        if(accepted.isEmpty())throw invalid();
        validateImport(accepted, current);
        return Collections.unmodifiableMap(accepted);
    }
    /** Validate the effective configuration before obtaining a preference editor. */
    static void validateImport(Map<String,?> incoming, Map<String,?> current) {
        String enabled = "feed_registration_days_v1";
        if (!Boolean.TRUE.equals(effectiveValue(enabled, incoming, current))) return;
        int min = (Integer) effectiveValue("feed_registration_min_v1", incoming, current);
        int max = (Integer) effectiveValue("feed_registration_max_v1", incoming, current);
        if (!AccountAutomationRules.validRange(Math.max(1, min), max)) throw new InvalidRangeException();
    }
    interface ImportWriter {
        boolean write(Map<String,?> values) throws Exception;
    }
    /** No editor or write callback is reached if the merged configuration is invalid. */
    static boolean writeImport(Map<String,?> incoming, Map<String,?> current, ImportWriter writer) throws Exception {
        validateImport(incoming, current);
        return writer.write(incoming);
    }
    private static Object effectiveValue(String key, Map<String,?> incoming, Map<String,?> current) {
        Object value = incoming.containsKey(key) ? incoming.get(key)
                : current.containsKey(key) ? current.get(key) : ConfigDefaults.value(key);
        return validate(key, value, ConfigDefaults.value(key));
    }
    static final class InvalidRangeException extends IllegalArgumentException {
        InvalidRangeException() {
            super("注册天数下限不能大于上限\n修改配置文件中的范围，或将筛选开关设为关闭后重新导入");
        }
    }
    private static Object validate(String key,Object value,Object type) {
        if ("chat_time_format_v1".equals(key) && (!(value instanceof String) || !ChatTimeRules.valid((String) value))) throw invalid();
        if(type instanceof Boolean && !(value instanceof Boolean))throw invalid();
        if(type instanceof String && !(value instanceof String))throw invalid();
        if(type instanceof Integer && !(value instanceof Integer))throw invalid();
        if (key.equals("feed_filter_content_types_v1") && ((Integer)value < 1 || (Integer)value > 7)) throw invalid();
        if(value instanceof String && ((String)value).length()>24000)throw invalid();
        if (key.startsWith("profile_disguise_")) {
            if (key.endsWith("mode") && !java.util.Arrays.asList("absolute", "delta").contains(value)) throw invalid();
            if (key.endsWith("absolute") && (Integer) value < 0) throw invalid();
        }
        if ((key.equals("feed_registration_min_v1") || key.equals("feed_registration_max_v1")) && (Integer)value < 0) throw invalid();
        if(key.equals("feed_keywords_v1") || key.equals("feed_keywords_name_v1"))new FeedKeywordRules((String)value);
        if(key.equals("feed_filter_gender")&&!java.util.Arrays.asList("ANY","FEMALE","MALE").contains(value))throw invalid();
        if ((key.equals("square_ip_label_color") || key.equals("square_days_label_color"))
                && !FeedLabelStyleRules.color((String) value)) throw invalid();
        if ((key.equals("square_ip_label_size") || key.equals("square_days_label_size"))
                && !FeedLabelStyleRules.size((String) value)) throw invalid();
        if(key.equals("chat_recall_style")&&!java.util.Arrays.asList("below","nickname").contains(value))throw invalid();
        if(key.equals("ai_reply_auto_image_threshold_v1")) {
            int n=(Integer)value;
            if(n!=0 && (n<2||n>5))throw invalid();
        }
        return value;
    }
    static byte[] readBounded(InputStream in)throws java.io.IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=in.read(buffer))!=-1){
            if(count>ConfigTransferRules.MAX_BYTES-bytes.size())throw new java.io.IOException();
            bytes.write(buffer,0,count);
        }
        return bytes.toByteArray();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("配置格式或内容无效");}
}
