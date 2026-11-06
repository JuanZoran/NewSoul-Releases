package dev.local.newsoul;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import java.net.HttpURLConnection;

/** One explicit, non-streaming request. Call only on a background thread. */
final class AiReplyClient {
    static final String MODEL = AiModelConfig.DEEPSEEK_ID;
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final int MAX_REQUEST_BYTES = 12 * 1024 * 1024;
    static final String JSON_OUTPUT_SUFFIX = "\n仅输出规则要求的一个 JSON 对象，不要输出 Markdown 或额外说明。";
    private static final String REQUEST_TOO_LARGE = "AI 请求图片或资料过大，请减少所选图片或缩短资料后重试";

    private AiReplyClient() { }

    static JSONObject createRequest(String systemPrompt, String contextJson,
                                    List<String> imageUrls) throws JSONException {
        return createRequest(AiModelConfig.DEEPSEEK, systemPrompt, contextJson, imageUrls);
    }

    static JSONObject createRequest(AiModelConfig model, String systemPrompt, String contextJson,
                                    List<String> imageUrls) throws JSONException {
        java.util.ArrayList<ImageInput> images=new java.util.ArrayList<>();
        if(imageUrls!=null)for(String url:imageUrls)images.add(ImageInput.remote(url));
        return createImageRequest(model,systemPrompt,contextJson,images);
    }

    static final class ImageInput {
        final String url;
        private ImageInput(String url){this.url=url;}
        static ImageInput remote(String url){requireImageUrl(url);return new ImageInput(url);}
        static ImageInput original(byte[] bytes) {
            if(bytes==null||bytes.length<12||bytes.length>8*1024*1024)throw new IllegalArgumentException("Invalid image size");
            String mime;
            if(bytes[0]=='G'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='8'
                    &&(bytes[4]=='7'||bytes[4]=='9')&&bytes[5]=='a')mime="image/gif";
            else if((bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71
                    &&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)mime="image/png";
            else if((bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)mime="image/jpeg";
            else if(bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F'
                    &&bytes[8]=='W'&&bytes[9]=='E'&&bytes[10]=='B'&&bytes[11]=='P')mime="image/webp";
            else throw new IllegalArgumentException("Unsupported image format");
            return new ImageInput("data:"+mime+";base64,"+java.util.Base64.getEncoder().encodeToString(bytes));
        }
    }

    static JSONObject createImageRequest(String systemPrompt,String contextJson,List<ImageInput> images) throws JSONException {
        return createImageRequest(AiModelConfig.DEEPSEEK,systemPrompt,contextJson,images);
    }

    static JSONObject createImageRequest(AiModelConfig model,String systemPrompt,String contextJson,List<ImageInput> images) throws JSONException {
        if(model==null)throw new IllegalArgumentException("请先选择 AI 模型");
        model.requireConfigured();
        if (systemPrompt == null || systemPrompt.trim().isEmpty()
                || contextJson == null || contextJson.trim().isEmpty()) {
            throw new IllegalArgumentException("聊天资料未准备好，请更新资料后重试");
        }
        AiRequestContext.requireInputBudget(systemPrompt,contextJson);
        long imageBytes=0;
        if(images!=null)for(ImageInput image:images) {
            if(image==null)throw new IllegalArgumentException("图片资料未准备好，请重新选择图片");
            imageBytes+=utf8Bytes(image.url);
            if(imageBytes>MAX_REQUEST_BYTES)throw new IllegalArgumentException(REQUEST_TOO_LARGE);
        }
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content",
                systemPrompt + JSON_OUTPUT_SUFFIX));
        Object content = contextJson;
        if (images != null && !images.isEmpty()) {
            JSONArray blocks = new JSONArray();
            blocks.put(new JSONObject().put("type", "text").put("text", contextJson));
            for (ImageInput image : images) {
                blocks.put(new JSONObject().put("type", "image_url").put("image_url",
                        new JSONObject().put("url", image.url)));
            }
            content = blocks;
        }
        messages.put(new JSONObject().put("role", "user").put("content", content));
        JSONObject request = new JSONObject()
                .put("model", model.id)
                .put("messages", messages)
                .put("response_format", new JSONObject().put("type", "json_object"))
                .put(model.outputTokenField, 2500)
                .put("stream", false);
        if (!model.isCustom()) request.put("thinking", new JSONObject().put("type", "disabled"));
        if(utf8Bytes(request.toString())>MAX_REQUEST_BYTES)throw new IllegalArgumentException(REQUEST_TOO_LARGE);
        return request;
    }

    static JSONObject execute(String apiKey, JSONObject request,
                              Cancellation cancellation) throws Exception {
        return execute(AiModelConfig.DEEPSEEK,apiKey,request,cancellation);
    }

    static JSONObject execute(AiModelConfig model,String apiKey, JSONObject request,
                              Cancellation cancellation) throws Exception {
        return executeDetailed(model,apiKey,request,cancellation).value;
    }

    static ParsedResponse executeDetailed(AiModelConfig model,String apiKey, JSONObject request,
                                          Cancellation cancellation) throws Exception {
        if(model==null)throw new ClientException("请先选择 AI 模型");
        Cancellation token = cancellation == null ? new Cancellation() : cancellation;
        token.check();
        if (!model.configured()) throw new ClientException("请先填写自定义接口地址与模型名");
        requireApiKey(model,apiKey);
        if (request == null) throw new ClientException("聊天资料未准备好，请更新资料后重试");
        HttpURLConnection connection = null;
        long started=System.nanoTime();
        try {
            String body = request.toString();
            if (body == null) throw new ClientException("聊天资料暂时无法使用，请更新资料后重试");
            // Recheck here because callers may modify a request after construction.
            if(utf8Bytes(body)>MAX_REQUEST_BYTES)throw new ClientException(REQUEST_TOO_LARGE);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(model.endpoint).openConnection();
            token.attach(connection);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            // This credential is never placed in the request body, errors, or logs.
            if (apiKey != null && !apiKey.trim().isEmpty())
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setFixedLengthStreamingMode(bytes.length);
            token.check();
            try (OutputStream output = connection.getOutputStream()) {
                token.check();
                output.write(bytes);
            }
            token.check();
            int status = connection.getResponseCode();
            token.check();
            if (status < 200 || status >= 300) throw httpError(model,status);
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) {
                throw new ClientException("AI 回复过长，请重新生成");
            }
            String response;
            try (InputStream input = connection.getInputStream()) {
                response = readResponse(input, token);
            }
            token.check();
            ParsedResponse result = parseResponseDetailed(model,response);
            token.check();
            return result.withTransport(bytes.length,utf8Bytes(response),(System.nanoTime()-started)/1_000_000L);
        } catch (ClientException error) {
            token.check();
            throw error;
        } catch (SocketTimeoutException error) {
            token.check();
            throw new ClientException("AI 请求超时，请稍后重试");
        } catch (IOException error) {
            token.check();
            if (model.isCustom() && error.getMessage() != null
                    && error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("cleartext"))
                throw new ClientException("Soul 不允许此 HTTP 连接，请改用 HTTPS 接口");
            throw new ClientException("无法连接 AI 服务，请检查网络后重试");
        } catch (RuntimeException error) {
            token.check();
            throw new ClientException("AI 请求失败，请稍后重试");
        } finally {
            if (connection != null) {
                token.detach(connection);
                disconnect(connection);
            }
        }
    }

    static final class ParsedResponse {
        final JSONObject value;
        final boolean recoveredParameterSuffix;
        final long promptTokens,completionTokens,totalTokens;
        final long requestBytes,responseBytes,elapsedMillis;
        ParsedResponse(JSONObject value,boolean recovered,JSONObject usage) {
            this(value,recovered,usageCount(usage,"prompt_tokens"),usageCount(usage,"completion_tokens"),
                    usageCount(usage,"total_tokens"),-1,-1,-1);
        }
        private ParsedResponse(JSONObject value,boolean recovered,long prompt,long completion,long total,
                               long requestBytes,long responseBytes,long elapsedMillis) {
            this.value=value;this.recoveredParameterSuffix=recovered;
            this.promptTokens=prompt;this.completionTokens=completion;this.totalTokens=total;
            this.requestBytes=requestBytes;this.responseBytes=responseBytes;this.elapsedMillis=elapsedMillis;
        }
        private ParsedResponse withTransport(long requestBytes,long responseBytes,long elapsedMillis) {
            return new ParsedResponse(value,recoveredParameterSuffix,promptTokens,completionTokens,totalTokens,
                    requestBytes,responseBytes,elapsedMillis);
        }
        /** Safe for diagnostic logging: no prompt, response body, image URL, or credential. */
        String diagnostics() {
            return "request_bytes="+requestBytes+" response_text_bytes="+responseBytes+" elapsed_ms="+elapsedMillis
                    +" prompt_tokens="+promptTokens+" completion_tokens="+completionTokens+" total_tokens="+totalTokens;
        }
    }

    private static long usageCount(JSONObject usage,String key) {
        Object value=usage==null?null:usage.opt(key);
        if(!(value instanceof Number))return -1;
        double numeric=((Number)value).doubleValue();
        long count=((Number)value).longValue();
        return count>=0&&Double.isFinite(numeric)&&numeric==count?count:-1;
    }

    /** Exact for Java's UTF-8 encoder, including its '?' replacement for unmatched surrogates. */
    static long utf8Bytes(String value) {
        if(value==null)return 0;
        long bytes=0;
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(ch<0x80)bytes++;
            else if(ch<0x800)bytes+=2;
            else if(Character.isHighSurrogate(ch)&&i+1<value.length()&&Character.isLowSurrogate(value.charAt(i+1))) {
                bytes+=4;i++;
            } else if(Character.isSurrogate(ch))bytes++;
            else bytes+=3;
        }
        return bytes;
    }

    static JSONObject parseResponse(String response) throws ClientException {
        return parseResponseDetailed(null,response).value; // Legacy/default callers remain strict.
    }

    static ParsedResponse parseResponseDetailed(AiModelConfig model,String response) throws ClientException {
        try {
            JSONObject envelope = parseObject(response);
            JSONArray choices = envelope.optJSONArray("choices");
            if (choices == null || choices.length() != 1) {
                throw new ClientException("AI 回复不完整，请重新生成");
            }
            JSONObject choice = choices.getJSONObject(0);
            Object finishReason = choice.opt("finish_reason");
            if (!"stop".equals(finishReason)) {
                if ("length".equals(finishReason)) {
                    throw new ClientException("AI 回复过长，请重新生成");
                }
                throw new ClientException("AI 回复未正常完成，请重新生成");
            }
            JSONObject message = choice.getJSONObject("message");
            Object content = message.opt("content");
            if (!(content instanceof String) || ((String) content).trim().isEmpty()) {
                throw new ClientException("AI 没有返回回复，请重新生成");
            }
            String raw=(String)content;
            JSONObject usage=envelope.optJSONObject("usage");
            try { return new ParsedResponse(parseObject(raw),false,usage); }
            catch (JSONException malformed) {
                // Observed MiMo-only artifact, outside a complete unique JSON object.
                // No arbitrary extraction, edits inside JSON, or additional suffixes.
                int end=raw.length();
                while(end>0 && " \t\r\n".indexOf(raw.charAt(end-1))>=0)end--;
                String trimmed=raw.substring(0,end);
                if(model!=AiModelConfig.MIMO || raw.indexOf('\0')>=0 || !trimmed.endsWith("parameter"))
                    throw malformed;
                String prefix=trimmed.substring(0,trimmed.length()-"parameter".length());
                return new ParsedResponse(parseObject(prefix),true,usage);
            }
        } catch (JSONException error) {
            // JSONException may contain response fragments; do not propagate its cause.
            throw new ClientException("AI 回复暂时无法使用，请重新生成");
        }
    }

    private static JSONObject parseObject(String text) throws JSONException {
        JsonSyntax syntax=new JsonSyntax(text);
        syntax.object(0); syntax.space();
        if(syntax.at!=syntax.text.length()) throw new JSONException("Extra JSON content");
        return new JSONObject(text);
    }

    /** Android org.json can accept duplicate keys/non-JSON syntax. Check independently. */
    private static final class JsonSyntax {
        final String text; int at;
        JsonSyntax(String text) throws JSONException {
            if(text==null || text.indexOf('\0')>=0) bad();
            this.text=text;
        }
        void space() { while(at<text.length() && " \t\r\n".indexOf(text.charAt(at))>=0) at++; }
        void take(char c) throws JSONException {
            space(); if(at>=text.length() || text.charAt(at++)!=c) bad();
        }
        boolean next(char c) { space(); if(at<text.length() && text.charAt(at)==c){at++;return true;}return false; }
        void object(int depth) throws JSONException {
            if(depth>128) bad(); take('{');
            java.util.Set<String> keys=new java.util.HashSet<>();
            if(next('}'))return;
            do {
                space(); int start=at; string();
                String key=(String)new JSONTokener(text.substring(start,at)).nextValue();
                if(!keys.add(key))bad(); take(':'); value(depth+1);
                if(next('}'))return; take(',');
            } while(true);
        }
        void value(int depth) throws JSONException {
            if(depth>128)bad(); space(); if(at>=text.length())bad();
            char c=text.charAt(at);
            if(c=='{'){object(depth);return;}
            if(c=='['){at++;if(next(']'))return;do{value(depth+1);if(next(']'))return;take(',');}while(true);}
            if(c=='"'){string();return;}
            for(String literal:new String[]{"true","false","null"})
                if(text.startsWith(literal,at)){at+=literal.length();return;}
            java.util.regex.Matcher number=java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(text);
            number.region(at,text.length()); if(!number.lookingAt())bad(); at=number.end();
        }
        void string() throws JSONException {
            take('"');
            while(at<text.length()) {
                char c=text.charAt(at++);
                if(c=='"')return;
                if(c<0x20)bad();
                if(c=='\\') {
                    if(at>=text.length())bad(); char escaped=text.charAt(at++);
                    if(escaped=='u') {
                        for(int i=0;i<4;i++)if(at>=text.length() || "0123456789abcdefABCDEF".indexOf(text.charAt(at++))<0)bad();
                    } else if("\"\\/bfnrt".indexOf(escaped)<0)bad();
                }
            }
            bad();
        }
        static void bad() throws JSONException { throw new JSONException("Invalid JSON syntax"); }
    }

    static String readResponse(InputStream input, Cancellation cancellation)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[8192];
        int count;
        while (true) {
            cancellation.check();
            count = input.read(buffer);
            if (count == -1) break;
            if (count > MAX_RESPONSE_BYTES - output.size()) {
                throw new ClientException("AI 回复过长，请重新生成");
            }
            output.write(buffer, 0, count);
        }
        cancellation.check();
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    static ClientException httpError(int status) {
        return httpError(AiModelConfig.DEEPSEEK,status);
    }

    static ClientException httpError(AiModelConfig model,int status) {
        String service=model==null?"AI":model.serviceName();
        switch (status) {
            case 400: return new ClientException(status,"AI 未能处理本轮资料，请更新资料后重试");
            case 401: return new ClientException(status,"模型密钥无效或已失效，请重新填写");
            case 402: return new ClientException(status,service+" 余额不足，请充值后重试");
            case 403: return new ClientException(status,"模型服务拒绝访问，请检查密钥权限");
            case 429: return new ClientException(status,"请求过于频繁，请稍后重试");
            default:
                return new ClientException(status,status >= 100 && status <= 599
                        ? "AI 服务暂时不可用，请稍后重试"
                        : "AI 服务未正常响应，请稍后重试");
        }
    }

    private static void requireApiKey(AiModelConfig model,String apiKey) throws ClientException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (model.isCustom()) return;
            throw new ClientException("请先填写 "+model.serviceName()+" 模型密钥");
        }
        String key = apiKey.trim();
        if (key.length() > 4096) throw new ClientException("模型密钥格式无效，请重新填写");
        for (int index = 0; index < key.length(); index++) {
            char value = key.charAt(index);
            if (value <= 32 || value >= 127) throw new ClientException("模型密钥格式无效，请重新填写");
        }
    }

    private static void requireImageUrl(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.length() > 8192) throw new IllegalArgumentException();
            URI uri = new URI(imageUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception error) {
            throw new IllegalArgumentException("图片必须使用有效的 HTTPS 链接");
        }
    }

    private static void disconnect(HttpURLConnection connection) {
        try { connection.disconnect(); } catch (RuntimeException ignored) { }
    }

    static final class ClientException extends IOException {
        final int httpStatus;
        ClientException(String message) { this(0,message); }
        ClientException(int httpStatus,String message) { super(message);this.httpStatus=httpStatus; }
    }

    /** A cancellation token belongs to one in-flight request and cannot be reset. */
    static final class Cancellation {
        private volatile boolean cancelled;
        private HttpURLConnection active;

        void cancel() {
            HttpURLConnection connection;
            synchronized (this) {
                cancelled = true;
                connection = active;
                active = null;
            }
            if (connection != null) disconnect(connection);
        }

        boolean isCancelled() { return cancelled; }

        void check() throws ClientException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new ClientException("AI 请求已取消");
            }
        }

        synchronized void attach(HttpURLConnection connection) throws ClientException {
            check();
            if (active != null) throw new ClientException("AI 请求正在处理中");
            active = connection;
        }

        synchronized void detach(HttpURLConnection connection) {
            if (active == connection) active = null;
        }
    }
}
