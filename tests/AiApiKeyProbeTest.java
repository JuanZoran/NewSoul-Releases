package dev.local.newsoul;

import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AiApiKeyProbeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for(AiModelConfig model:new AiModelConfig[]{AiModelConfig.DEEPSEEK,AiModelConfig.MIMO, AiModelConfig.custom("http://127.0.0.1:1234/v1", "local-model")}) {
            JSONObject probe=AiApiKeyProbe.request(model);
            require(model.id.equals(probe.getString("model")),"test the row's model");
            require(probe.getInt(model.outputTokenField)==64,"small model-specific output limit");
            require(!probe.has(model==AiModelConfig.MIMO?"max_tokens":"max_completion_tokens"),"no other provider token field");
            require(!probe.getBoolean("stream"),"non-streaming protocol");
            require(model.isCustom() ? !probe.has("thinking") : "disabled".equals(probe.getJSONObject("thinking").getString("type")), "vendor thinking field only sent to existing providers");
            require("json_object".equals(probe.getJSONObject("response_format").getString("type")),"normal JSON mode exercised");
            JSONArray messages=probe.getJSONArray("messages");
            require(messages.length()==2&&messages.getJSONObject(0).getString("content").contains("{\"ok\":true}"),"fixed explicit expected JSON");
            require("{}".equals(messages.getJSONObject(1).getString("content")),"fixed user payload with no chat context");
            require(probe.toString().length()<600&&!probe.has("tools")&&!probe.has("user"),"small request has no user metadata or tools");
            JSONObject chat=AiReplyClient.createRequest(model,"normal rules","{}",Collections.emptyList());
            require(chat.getInt(model.outputTokenField)==2500,"chat output budget remains unchanged");
            AiApiKeyProbe.Result success=AiApiKeyProbe.response(parsed(model,new JSONObject().put("ok",true)));
            require(success.passed()&&"连接正常".equals(success.message),"normal typed marker passes");
            for(JSONObject wrong:new JSONObject[]{new JSONObject(),new JSONObject().put("ok",false),
                    new JSONObject().put("ok","true"),new JSONObject().put("ok",1),
                    new JSONObject().put("ok",true).put("other","private-response")}) {
                AiApiKeyProbe.Result result=AiApiKeyProbe.response(parsed(model,wrong));
                require(!result.passed()&&result.status==AiApiKeyProbe.Status.UNCONFIRMED,"incorrect marker is not success or invalid credential");
                require(!result.message.contains("private-response")&&!result.diagnostics.contains("private-response"),"response text never surfaced");
            }
            failure(model,401,AiApiKeyProbe.Status.AUTHENTICATION_FAILED);
            failure(model,402,AiApiKeyProbe.Status.INSUFFICIENT_BALANCE);
            failure(model,403,AiApiKeyProbe.Status.ACCESS_DENIED);
            failure(model,429,AiApiKeyProbe.Status.LIMITED);
            failure(model,404,AiApiKeyProbe.Status.MODEL_OR_ENDPOINT_UNAVAILABLE);
            failure(model,400,AiApiKeyProbe.Status.REQUEST_REJECTED);
            failure(model,422,AiApiKeyProbe.Status.REQUEST_REJECTED);
            failure(model,500,AiApiKeyProbe.Status.UNCONFIRMED);
            failure(model,503,AiApiKeyProbe.Status.UNCONFIRMED);
            failure(model,302,AiApiKeyProbe.Status.UNCONFIRMED);
            String[][] safeMessages={
                {"AI 请求超时，请稍后重试","测试超时，请重试"},
                {"无法连接 AI 服务，请检查网络后重试","无法连接 AI 服务，请检查网络后重试"},
                {"AI 请求失败，请稍后重试","测试未完成，请稍后重试"},
                {"AI 回复不完整，请重新生成","服务返回格式异常，请重试"},
                {"AI 回复过长，请重新生成","服务返回格式异常，请重试"},
                {"AI 回复未正常完成，请重新生成","服务返回格式异常，请重试"},
                {"AI 没有返回回复，请重新生成","服务返回格式异常，请重试"},
                {"AI 回复暂时无法使用，请重新生成","服务返回格式异常，请重试"}
            };
            for(String[] safe:safeMessages) {
                AiReplyClient.ClientException error=new AiReplyClient.ClientException(safe[0]);
                AiApiKeyProbe.Result result=AiApiKeyProbe.failure(model,error);
                require(result.status==AiApiKeyProbe.Status.UNCONFIRMED&&safe[1].equals(result.message),"network/timeout/format use accurate test actions without implying invalid key");
                require(safe[0].equals(error.getMessage()),"original chat error wording remains unchanged");
            }
            AiReplyClient.Cancellation cancel=new AiReplyClient.Cancellation();cancel.cancel();
            AiApiKeyProbe.Result cancelled=AiApiKeyProbe.run(model,"offline-unused-key",cancel);
            require(cancelled.status==AiApiKeyProbe.Status.CANCELLED&&!cancelled.passed(),"cancelled probe exits before constructing a connection");
        }
        require(AiApiKeyProbe.failure(AiModelConfig.MIMO,AiReplyClient.httpError(AiModelConfig.MIMO,401)).message.contains("按量计费"),"MiMo endpoint/key type mismatch stays actionable");
        require(AiApiKeyProbe.failure(AiModelConfig.MIMO,AiReplyClient.httpError(AiModelConfig.MIMO,429)).message.contains("额度"),"MiMo 429 preserves rate/quota ambiguity");
        require(AiReplyClient.httpError(429).getMessage().equals("请求过于频繁，请稍后重试"),"existing chat error wording preserved");
        try {
            AiReplyClient.parseResponseDetailed(AiModelConfig.DEEPSEEK,envelope("not-json-private-body"));
            throw new AssertionError("malformed response must fail");
        } catch(AiReplyClient.ClientException error) {
            AiApiKeyProbe.Result failed=AiApiKeyProbe.failure(AiModelConfig.DEEPSEEK,error);
            require(failed.status==AiApiKeyProbe.Status.UNCONFIRMED&&"服务返回格式异常，请重试".equals(failed.message),"malformed content stays safe and uses an available test action");
        }
        System.out.println("AiApiKeyProbeTest: "+checks+" offline checks passed; no API request");
    }
    private static void failure(AiModelConfig model,int status,AiApiKeyProbe.Status expected) {
        AiReplyClient.ClientException error=AiReplyClient.httpError(model,status);
        require(error.httpStatus==status,"transport retains HTTP status without raw body");
        AiApiKeyProbe.Result result=AiApiKeyProbe.failure(model,error);
        require(result.status==expected&&!result.passed()&&result.httpStatus==status,"failure classification "+status);
    }
    private static AiReplyClient.ParsedResponse parsed(AiModelConfig model,JSONObject body) throws Exception {
        return AiReplyClient.parseResponseDetailed(model,envelope(body.toString()));
    }
    private static String envelope(String content) throws Exception {
        return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop")
                .put("message",new JSONObject().put("content",content)))).toString();
    }
    private static void require(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
}
