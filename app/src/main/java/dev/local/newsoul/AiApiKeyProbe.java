package dev.local.newsoul;

import java.util.Collections;
import org.json.JSONException;
import org.json.JSONObject;

/** Explicit, fixed-content connectivity check; never collects chat data or saves a credential. */
final class AiApiKeyProbe {
    static final int MAX_OUTPUT_TOKENS=64;
    private static final String PROMPT="This is an API connectivity test. Return only the JSON object {\"ok\":true}.";

    enum Status {
        PASSED, AUTHENTICATION_FAILED, INSUFFICIENT_BALANCE, ACCESS_DENIED, LIMITED,
        MODEL_OR_ENDPOINT_UNAVAILABLE, REQUEST_REJECTED, UNCONFIRMED, CANCELLED
    }

    static final class Result {
        final Status status;
        final String message;
        final String diagnostics;
        final int httpStatus;
        Result(Status status,String message,int httpStatus,String diagnostics) {
            this.status=status;this.message=message;this.httpStatus=httpStatus;this.diagnostics=diagnostics;
        }
        boolean passed() {return status==Status.PASSED;}
    }

    private AiApiKeyProbe() { }

    /** Uses the normal protocol flags and model-specific token field, without changing chat defaults. */
    static JSONObject request(AiModelConfig model) throws JSONException {
        return AiReplyClient.createRequest(model,PROMPT,"{}",Collections.emptyList())
                .put(model.outputTokenField,MAX_OUTPUT_TOKENS);
    }

    /** Run off MAIN after an explicit test tap. One request, no automatic retries. */
    static Result run(AiModelConfig model,String apiKey,AiReplyClient.Cancellation cancellation) {
        AiReplyClient.Cancellation token=cancellation==null?new AiReplyClient.Cancellation():cancellation;
        try {
            token.check();
            AiReplyClient.ParsedResponse response=AiReplyClient.executeDetailed(model,apiKey,request(model),token);
            token.check();
            return response(response);
        } catch(AiReplyClient.ClientException error) {
            if(token.isCancelled()||Thread.currentThread().isInterrupted())
                return new Result(Status.CANCELLED,"测试已取消",0,"");
            return failure(model,error);
        } catch(Exception error) {
            // Exception text may contain request fragments. Never surface it here.
            return new Result(Status.UNCONFIRMED,"测试未完成，请稍后重试",0,"");
        }
    }

    static Result response(AiReplyClient.ParsedResponse response) {
        JSONObject value=response==null?null:response.value;
        if(value!=null&&value.length()==1&&Boolean.TRUE.equals(value.opt("ok")))
            return new Result(Status.PASSED,"连接正常",200,response.diagnostics());
        return new Result(Status.UNCONFIRMED,"已收到服务响应，但测试结果异常，请重试",200,
                response==null?"":response.diagnostics());
    }

    static Result failure(AiModelConfig model,AiReplyClient.ClientException error) {
        int status=error.httpStatus;
        Status result;
        String message=testMessage(error.getMessage());
        switch(status) {
            case 401:
                result=Status.AUTHENTICATION_FAILED;
                if(model==AiModelConfig.MIMO)message="密钥认证失败，请确认使用按量计费密钥";
                break;
            case 402: result=Status.INSUFFICIENT_BALANCE;break;
            case 403:
                result=Status.ACCESS_DENIED;
                if(model==AiModelConfig.MIMO)message="服务拒绝访问，请检查密钥权限或服务支持地区";
                break;
            case 429:
                result=Status.LIMITED;
                if(model==AiModelConfig.MIMO)message="请求过于频繁或额度已用尽，请检查额度或稍后重试";
                break;
            case 404:
                result=Status.MODEL_OR_ENDPOINT_UNAVAILABLE;
                message="当前模型或接口不可用，请稍后重试或切换模型";
                break;
            case 400: case 422:
                result=Status.REQUEST_REJECTED;
                message="测试请求未被模型接受，请稍后重试或切换模型";
                break;
            default: result=Status.UNCONFIRMED;
        }
        return new Result(result,message,status,status==0?"":"http_status="+status);
    }

    /** Keep transport diagnostics safe while using actions that exist on the test control. */
    private static String testMessage(String message) {
        if(message==null)return "测试未完成，请稍后重试";
        switch(message) {
            case "AI 请求超时，请稍后重试": return "测试超时，请重试";
            case "AI 回复不完整，请重新生成":
            case "AI 回复过长，请重新生成":
            case "AI 回复未正常完成，请重新生成":
            case "AI 没有返回回复，请重新生成":
            case "AI 回复暂时无法使用，请重新生成":
                return "服务返回格式异常，请重试";
            case "AI 请求失败，请稍后重试": return "测试未完成，请稍后重试";
            default: return message;
        }
    }
}
