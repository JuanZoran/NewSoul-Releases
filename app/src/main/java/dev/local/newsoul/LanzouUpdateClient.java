package dev.local.newsoul;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only public release index. No host network configuration or download/install behavior. */
final class LanzouUpdateClient {
    static final String ORIGIN = "https://wwbnj.lanzoux.com";
    static final String FOLDER_PATH = "/b007utki2d";
    static final String FOLDER = ORIGIN + FOLDER_PATH;
    // Retry x last as well: a transient failure of the default x host may have recovered.
    private static final String[] ORIGINS = {ORIGIN, "https://wwbnj.lanzoul.com",
            "https://wwbnj.lanzoui.com", "https://wwbnj.lanzoux.com"};
    static final String PASSWORD = "3gin";
    private static final Pattern VERSION = Pattern.compile("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}");
    private static final Pattern FILE = Pattern.compile("new-soul-([0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9})\\.zip");
    static final class Release {
        final String version, url;
        Release(String version, String url) { this.version = version; this.url = url; }
    }
    interface Transport { String request(String url, String body) throws Exception; }
    static int compare(String left, String right) {
        if (!VERSION.matcher(left).matches() || !VERSION.matcher(right).matches())
            throw new IllegalArgumentException("无法识别版本号");
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int c = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
            if (c != 0) return c;
        }
        return 0;
    }
    static Release check() throws Exception {
        final long[] deadline = {0};
        return check((url, body) -> {
            // Every domain gets its own budget, including when the preceding one timed out.
            if (body == null) deadline[0] = System.nanoTime() + 15_000_000_000L;
            return request(url, body, deadline[0]);
        });
    }
    static Release check(Transport transport) throws Exception {
        Exception last = null;
        for (String origin : ORIGINS) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("检测已取消");
            try { return checkAt(transport, origin); }
            catch (Exception failure) {
                if (failure instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IOException("检测已取消", failure);
                }
                last = failure;
            }
        }
        throw new IOException("所有发布域名均无法读取，请稍后重试", last);
    }
    private static Release checkAt(Transport transport, String origin) throws Exception {
        String html = transport.request(origin + FOLDER_PATH, null);
        String endpoint = capture(html, "url\\s*:\\s*['\"](/filemoreajax\\.php\\?file=[0-9]{1,12})['\"]");
        String data = capture(html, "data\\s*:\\s*\\{([^}]{1,4096})\\}");
        String fid = capture(data, "['\"]fid['\"]\\s*:\\s*([0-9]{1,12})\\s*,");
        if (!endpoint.endsWith("=" + fid)) throw new IOException("发布页格式已变化");
        StringBuilder form = new StringBuilder("lx=2&fid=" + fid);
        for (String key : new String[]{"uid", "puid", "rep", "t", "k", "up", "ls"}) {
            String value = capture(data, "['\"]" + key + "['\"]\\s*:\\s*([^,}]+)").trim();
            if (value.matches("['\"][A-Za-z0-9_=-]{1,256}['\"]")) value = value.substring(1, value.length() - 1);
            else if (!value.matches("[0-9]{1,12}")) {
                if (!value.matches("[A-Za-z_$][A-Za-z0-9_$]{0,63}")) throw new IOException("发布页格式已变化");
                value = capture(html, "\\bvar\\s+" + Pattern.quote(value) + "\\s*=\\s*['\"]([A-Za-z0-9_=-]{1,256})['\"]\\s*;");
            }
            form.append('&').append(key).append('=').append(URLEncoder.encode(value, "UTF-8"));
        }
        form.append("&pwd=").append(PASSWORD);
        Release latest = null;
        Set<String> pages = new HashSet<>();
        for (int page = 1; page <= 20; page++) {
            String raw = transport.request(origin + endpoint, form + "&pg=" + page);
            JSONObject response = new JSONObject(raw);
            String status = String.valueOf(response.get("zt"));
            if ("2".equals(status)) {
                if (latest == null) throw new IOException("发布目录中没有可识别的安装包");
                return latest;
            }
            if (!"1".equals(status)) throw new IOException("发布目录暂时无法读取，请稍后重试");
            JSONArray files = response.getJSONArray("text");
            if (files.length() > 50 || !pages.add(files.toString())) throw new IOException("发布目录分页异常");
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String type = String.valueOf(file.get("t"));
                if ("1".equals(type)) continue; // Sponsored links are never release candidates.
                if (!"0".equals(type)) throw new IOException("发布目录格式已变化");
                Matcher name = FILE.matcher(file.getString("name_all"));
                if (!name.matches()) continue;
                String id = file.getString("id");
                // Public folder responses may attach a webpage routing token to the file id.
                // Keep it intact, but never accept another path, host or arbitrary query.
                if (!id.matches("i[A-Za-z0-9]{5,63}(\\?webpage=[A-Za-z0-9_=-]{1,512})?"))
                    throw new IOException("安装包链接格式异常");
                Release candidate = new Release(name.group(1), origin + "/" + id);
                if (latest == null || compare(candidate.version, latest.version) > 0) latest = candidate;
            }
            if (files.length() < 50) {
                if (latest == null) throw new IOException("发布目录中没有可识别的安装包");
                return latest;
            }
        }
        throw new IOException("发布目录文件过多，请直接打开发布页");
    }
    private static String capture(String text, String expression) throws IOException {
        Matcher matcher = Pattern.compile(expression).matcher(text);
        if (!matcher.find()) throw new IOException("发布页格式已变化，请直接打开发布页");
        return matcher.group(1);
    }
    private static String request(String url, String body, long deadline) throws IOException {
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw new IOException("检测超时");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout((int) Math.min(8000, remaining));
            connection.setReadTimeout((int) Math.min(8000, remaining));
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 NewSoul-UpdateCheck");
            connection.setRequestProperty("Referer", new URL(new URL(url), FOLDER_PATH).toString());
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            if (connection.getResponseCode() != 200) throw new IOException("发布页连接失败");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 512 * 1024) throw new IOException("发布页响应过大");
                    if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) throw new IOException("检测超时");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
