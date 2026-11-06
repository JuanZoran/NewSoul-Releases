package dev.local.newsoul;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Pattern;

/** Small deterministic privacy/budget rules, independent of Soul and Android. */
final class AiContextRules {
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>]+", Pattern.UNICODE_CASE);
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+?86[- ]?)?1[3-9][0-9]{9}(?![0-9])");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(?:authorization|bearer|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|cookie)\\s*[:=：]?\\s*[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{12,}\\b");

    static String cleanText(String value, int maxChars) {
        if (value == null || maxChars <= 0) return "";
        // Do not export embedded links, common phone numbers or explicit credentials even
        // when they occur inside an otherwise permitted natural-language field.
        String text = URL.matcher(value).replaceAll("[链接已省略]");
        text = SECRET.matcher(text).replaceAll("[凭据已省略]");
        text = KEY.matcher(text).replaceAll("[凭据已省略]");
        text = PHONE.matcher(text).replaceAll("[手机号已省略]");
        text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (text.length() > maxChars) {
            int end = maxChars;
            if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            return text.substring(0, end);
        }
        return text;
    }

    static String ordinaryImageUrl(String value) {
        if (value == null || value.length() > 4096) return null;
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            host = host.toLowerCase(Locale.ROOT);
            // Only ordinary images served by the host app's CDN. Never export local,
            // content://, arbitrary web, account API or signed credential-bearing URLs.
            if (!host.endsWith(".soulapp.cn")) return null;
            String query = uri.getRawQuery();
            if (query != null && query.toLowerCase(Locale.ROOT)
                    .matches(".*(?:token|auth|credential|signature|secret|cookie|key=).*")) return null;
            return uri.toASCIIString();
        } catch (Exception ignored) { return null; }
    }

    /** JSON object insertion order is irrelevant; array order and all source values remain significant. */
    static String canonical(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (String key : keys) {
                if (out.length() > 1) out.append(',');
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.get(i)));
            }
            return out.append(']').toString();
        }
        // Android's JSONObject omits the desktop org.json valueToString helper.
        String scalar=new JSONArray().put(value).toString();
        return scalar.substring(1,scalar.length()-1);
    }

    static String digest(String value) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String hex(byte[] hash) {
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte value : hash) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
}
