package dev.local.newsoul;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONObject;

/** Runs the real transport against in-memory URL connections; no socket or model request. */
public final class AiCustomTransportTest {
    private static FakeConnection last;
    private static int opens, checks;
    private static int status = 200;
    private static IOException failure;
    private static AiReplyClient.Cancellation cancelDuringWrite;

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> (protocol.equals("http") || protocol.equals("https"))
                ? new URLStreamHandler() {
                    protected URLConnection openConnection(URL url) {
                        opens++; return last = new FakeConnection(url);
                    }
                } : null);
        for (String scheme : new String[]{"http", "https"}) {
            AiModelConfig model = AiModelConfig.custom(scheme + "://example.invalid:1234/v1", "local-model");
            for (String key : new String[]{"", "  local-key  "}) {
                JSONObject request = AiReplyClient.createRequest(model, "规则", "{}", Collections.emptyList());
                require(request.getString("model").equals("local-model") && !request.has("thinking"), "portable model request");
                require(request.getInt("max_tokens") == 2500 && !request.has("max_completion_tokens"), "custom token field");
                AiReplyClient.ParsedResponse result = AiReplyClient.executeDetailed(model, key, request, null);
                require(result.value.getBoolean("ok") && result.requestBytes > 0, "real response parsing and metrics");
                require(last.getURL().toString().equals(model.endpoint), "configured destination");
                require(last.getRequestMethod().equals("POST") && !last.getInstanceFollowRedirects(), "POST without credential forwarding redirects");
                require(key.trim().isEmpty() ? last.getRequestProperty("Authorization") == null
                        : "Bearer local-key".equals(last.getRequestProperty("Authorization")), "optional header");
                require(!last.output.toString(StandardCharsets.UTF_8).contains("local-key"), "credential excluded from body");
                require(last.disconnected && last.getConnectTimeout() == 15000 && last.getReadTimeout() == 60000, "bounded connection cleanup");
            }
        }
        AiModelConfig model = AiModelConfig.custom("http://example.invalid/v1", "local-model");
        JSONObject request = AiApiKeyProbe.request(model);
        status = 302;
        int before = opens;
        rejected(model, "local-key", request, null, "暂时不可用");
        require(opens == before + 1 && last.disconnected, "redirect is never followed or retried");
        status = 401;
        AiApiKeyProbe.Result unauthorized = AiApiKeyProbe.run(model, "local-key", null);
        require(unauthorized.status == AiApiKeyProbe.Status.AUTHENTICATION_FAILED, "custom auth classification");
        require(!unauthorized.message.contains("local-key") && !unauthorized.diagnostics.contains("local-key"), "safe failure");
        status = 200;
        failure = new IOException("CLEARTEXT communication to private-host not permitted: local-key");
        rejected(model, "local-key", request, null, "改用 HTTPS");
        require(last.disconnected, "blocked HTTP cleanup");
        failure = null;
        before = opens;
        rejected(model, "bad\nkey", request, null, "格式无效");
        rejected(AiModelConfig.CUSTOM, "", request, null, "接口地址与模型名");
        require(opens == before, "invalid settings rejected before connecting");
        AiReplyClient.Cancellation cancelled = new AiReplyClient.Cancellation();
        cancelled.cancel();
        rejected(model, "", request, cancelled, "已取消");
        require(opens == before, "pre-cancel rejected before connecting");
        AiReplyClient.Cancellation active = new AiReplyClient.Cancellation();
        cancelDuringWrite = active;
        rejected(model, "", request, active, "已取消");
        require(last.disconnected && active.isCancelled(), "active HTTP request cancellation disconnects");
        System.out.println("AiCustomTransportTest: " + checks + " in-memory transport checks passed; no network");
    }

    private static void rejected(AiModelConfig model, String key, JSONObject request,
                                 AiReplyClient.Cancellation token, String text) throws Exception {
        try { AiReplyClient.executeDetailed(model, key, request, token); throw new AssertionError("expected rejection"); }
        catch (AiReplyClient.ClientException error) {
            require(error.getMessage().contains(text) && !error.getMessage().contains("local-key")
                    && error.getCause() == null, "safe actionable error");
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean disconnected;
        FakeConnection(URL url) { super(url); }
        public void connect() { }
        public void disconnect() { disconnected = true; }
        public boolean usingProxy() { return false; }
        public OutputStream getOutputStream() throws IOException {
            if (failure != null) throw failure;
            if (cancelDuringWrite != null) { cancelDuringWrite.cancel(); cancelDuringWrite = null; }
            return output;
        }
        public int getResponseCode() { return status; }
        public long getContentLengthLong() { return -1; }
        public InputStream getInputStream() {
            return new ByteArrayInputStream(("{\"choices\":[{\"finish_reason\":\"stop\","
                    + "\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
