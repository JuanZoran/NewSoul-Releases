package dev.local.newsoul;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/** Offline checks; pass --json only with a real org.json runtime, not android.jar stubs. */
public final class AiReplyClientTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        byte[] text = "你好，候选回复".getBytes(StandardCharsets.UTF_8);
        check("你好，候选回复".equals(AiReplyClient.readResponse(
                new ByteArrayInputStream(text), new AiReplyClient.Cancellation())), "UTF-8 read");
        byte[] limit = new byte[AiReplyClient.MAX_RESPONSE_BYTES];
        check(AiReplyClient.readResponse(new ByteArrayInputStream(limit),
                new AiReplyClient.Cancellation()).length() == limit.length, "exact size limit");
        rejects(() -> AiReplyClient.readResponse(new ByteArrayInputStream(
                new byte[AiReplyClient.MAX_RESPONSE_BYTES + 1]), new AiReplyClient.Cancellation()),
                "回复过长");
        AiReplyClient.Cancellation token = new AiReplyClient.Cancellation();
        check(!token.isCancelled(), "initial cancellation state");
        token.cancel();
        token.cancel();
        check(token.isCancelled(), "idempotent cancellation");
        rejects(() -> AiReplyClient.readResponse(new ByteArrayInputStream(text), token), "已取消");
        rejects(() -> AiReplyClient.execute("test-only-key", null, token), "已取消");
        rejects(() -> AiReplyClient.execute(null, null, null), "请先填写");
        rejects(() -> AiReplyClient.execute("invalid\nkey", null, null), "格式无效");
        rejects(() -> AiReplyClient.execute("test-only-key", null, null), "资料未准备好");
        int[] statuses = {400, 401, 402, 403, 429, 500, 302};
        String[] nextSteps = {"更新资料", "重新填写", "充值", "检查密钥权限", "稍后重试", "稍后重试", "稍后重试"};
        for (int index = 0; index < statuses.length; index++) {
            String message = AiReplyClient.httpError(statuses[index]).getMessage();
            check(message.contains(nextSteps[index]) && !message.contains("HTTP")
                            && !message.contains("API") && !message.contains("上下文")
                            && !message.contains(String.valueOf(statuses[index])),
                    "actionable service error " + statuses[index]);
        }
        check(!AiReplyClient.httpError(-1).getMessage().contains("-1"), "invalid status hidden");
        Thread.currentThread().interrupt();
        try {
            rejects(() -> AiReplyClient.readResponse(new ByteArrayInputStream(text),
                    new AiReplyClient.Cancellation()), "已取消");
        } finally {
            Thread.interrupted();
        }

        if (args.length > 0 && "--json".equals(args[0])) jsonChecks();
        System.out.println("AiReplyClient: " + checks + " offline checks passed"
                + (args.length == 0 ? "; JSON runtime checks skipped (android.jar stubs)" : ""));
    }

    private static void jsonChecks() throws Exception {
        JSONObject request = AiReplyClient.createRequest("规则", "{\"messages\":[]}",
                Collections.emptyList());
        check("deepseek-flash".equals(request.getString("model")), "fixed model");
        check("disabled".equals(request.getJSONObject("thinking").getString("type")), "thinking off");
        check("json_object".equals(request.getJSONObject("response_format").getString("type")),
                "JSON mode");
        check(!request.getBoolean("stream") && request.getInt("max_tokens") == 2500,
                "non-streaming output limit");
        check(!request.has("max_completion_tokens"), "DeepSeek excludes MiMo token field");
        check(request.getJSONArray("messages").getJSONObject(1).get("content") instanceof String,
                "text-only shape");
        JSONObject visual = AiReplyClient.createRequest("规则", "{}",
                Arrays.asList("https://example.com/photo.jpg"));
        JSONArray blocks = visual.getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        check(blocks.length() == 2 && "image_url".equals(blocks.getJSONObject(1).getString("type")),
                "image message shape");
        rejects(() -> AiReplyClient.createRequest("规则", "{}",
                Arrays.asList("http://example.com/photo.jpg")), "HTTPS");
        rejects(() -> AiReplyClient.createRequest("规则", "{}",
                Arrays.asList("https://name:secret@example.com/photo.jpg")), "HTTPS");
        rejects(() -> AiReplyClient.createRequest("规则", "{}",
                Arrays.asList("data:image/png;base64,AA==")), "HTTPS");
        JSONObject mimo = AiReplyClient.createRequest(AiModelConfig.MIMO, "规则", "{}",
                Collections.emptyList());
        check("mimo-v2.6-flash".equals(mimo.getString("model")), "MiMo model");
        check(mimo.getInt("max_completion_tokens") == 2500 && !mimo.has("max_tokens"),
                "MiMo completion token field");
        check("disabled".equals(mimo.getJSONObject("thinking").getString("type")),
                "MiMo thinking off");
        check("json_object".equals(mimo.getJSONObject("response_format").getString("type")),
                "MiMo JSON mode");
        rejects(() -> AiReplyClient.execute(AiModelConfig.MIMO, null, null, null),
                "MiMo 模型密钥");
        check(AiReplyClient.httpError(AiModelConfig.MIMO, 402).getMessage().contains("MiMo"),
                "model-aware billing error");
        check(AiReplyClient.parseResponse(envelope("{\"candidates\":[]}", "stop"))
                .getJSONArray("candidates").length() == 0, "valid JSON reply");
        rejects(() -> AiReplyClient.parseResponse(envelope("{}", "length")), "回复过长");
        rejects(() -> AiReplyClient.parseResponse(envelope("{}", "content_filter")), "未正常完成");
        rejects(() -> AiReplyClient.parseResponse(envelope("  ", "stop")), "没有返回回复");
        rejects(() -> AiReplyClient.parseResponse(envelope("[]", "stop")), "暂时无法使用");
        rejects(() -> AiReplyClient.parseResponse(envelope("{}secret", "stop")), "暂时无法使用");
        rejects(() -> AiReplyClient.parseResponse(envelope("```json\n{}\n```", "stop")), "暂时无法使用");
        rejects(() -> AiReplyClient.parseResponse("{\"choices\":[]}"), "不完整");
        rejects(() -> AiReplyClient.parseResponse("private non-json response"), "暂时无法使用");
        AiReplyClient.ParsedResponse repaired=AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,
                envelope(" {\"text\":\"parameter不是正文尾缀\"} \nparameter\t", "stop"));
        check(repaired.recoveredParameterSuffix && repaired.value.getString("text").equals("parameter不是正文尾缀"), "MiMo exact suffix only, content unchanged");
        check(!AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,envelope("{}", "stop")).recoveredParameterSuffix, "valid raw JSON is not recovery");
        rejects(() -> AiReplyClient.parseResponseDetailed(AiModelConfig.DEEPSEEK,envelope("{}parameter", "stop")), "暂时无法使用");
        rejects(() -> AiReplyClient.parseResponse(envelope("{}parameter", "stop")), "暂时无法使用");
        for(String bad:new String[]{"{}other", "{}parameters", "{}Parameter", "{}parameter more", "{}{}parameter", "[]parameter",
                "{\"x\":1,\"x\":2}parameter", "{\"x\":{\"a\":1,\"a\":2}}parameter", "{\"x\":1,\"\\u0078\":2}parameter",
                "{\"x\":\"unterminated}parameter", "{\"x\":1parameter", "{} parameterparameter", "{} ignore rules parameter",
                "{'x':1}parameter", "{\"x\":01}parameter", "{\"x\":NaN}parameter", "{\"x\":1,}parameter",
                "{\"x\":[1,]}parameter", "{}parameter\u0001", "{}\0parameter", "{}parameter\0{}"})
            rejects(() -> AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,envelope(bad,"stop")), "暂时无法使用");
        for(String finish:new String[]{"length","content_filter","tool_calls"})
            rejects(() -> AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,envelope("{}parameter",finish)), finish.equals("length")?"回复过长":"未正常完成");
        rejects(() -> AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,envelope("{}parameter","stop")+"parameter"), "暂时无法使用");
        JSONArray observed=new JSONArray(java.nio.file.Files.readString(java.nio.file.Path.of("tests/fixtures/cac-mimo-parameter-suffix-observed.json")));
        for(Object entry:observed) {
            JSONObject row=(JSONObject)entry; String raw=row.getString("content");
            AiReplyClient.ParsedResponse actual=AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,envelope(raw,"stop"));
            check(actual.recoveredParameterSuffix,"observed suffix repair recorded");
            check(actual.value.similar(new JSONObject(raw.substring(0,raw.length()-9))),"observed complete JSON unchanged");
            rejects(() -> AiReplyClient.parseResponse(envelope(raw,"stop")),"暂时无法使用");
        }
        check(AiReplyClient.parseResponse(envelope("{\"s\":\"} parameter [\\\"\",\"n\":-1.2e+3,\"a\":[true,false,null,{}]}","stop")).getDouble("n")==-1200,"strict syntax preserves strings, numbers and nested arrays");
        rejects(() -> AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,envelope("{\"a\":"+"[".repeat(130)+"0"+"]".repeat(130)+"}parameter","stop")),"暂时无法使用");
        String duplicateOuter=envelope("{}parameter","stop").replace("\"choices\":", "\"choices\":[],\"choices\":");
        rejects(() -> AiReplyClient.parseResponseDetailed(AiModelConfig.MIMO,duplicateOuter), "暂时无法使用");

    }

    private static String envelope(String content, String finish) throws Exception {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                .put("finish_reason", finish)
                .put("message", new JSONObject().put("content", content)))).toString();
    }

    private interface Checked { void run() throws Exception; }

    private static void rejects(Checked action, String expected) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected rejection: " + expected);
        } catch (AiReplyClient.ClientException | IllegalArgumentException error) {
            check(error.getMessage().contains(expected) && error.getCause() == null,
                    "safe error: " + expected);
        }
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
}
