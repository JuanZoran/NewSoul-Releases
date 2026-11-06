package dev.local.newsoul;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AiRequestBudgetTest {
    public static void main(String[] args) throws Exception {
        for(String text:new String[]{"", "ascii", "中文", "👍", "a\ud800b\udc00c", "\ud800\ud83d\udc4d"})
            require(AiReplyClient.utf8Bytes(text)==text.getBytes(StandardCharsets.UTF_8).length,"exact UTF-8 size including malformed surrogate");
        expectRejected(()->AiReplyClient.createRequest("s".repeat(30_000),"c".repeat(30_000),Collections.emptyList()),"combined text budget");
        byte[] image=new byte[5*1024*1024];
        image[0]=(byte)137;image[1]=80;image[2]=78;image[3]=71;image[4]=13;image[5]=10;image[6]=26;image[7]=10;
        AiReplyClient.ImageInput input=AiReplyClient.ImageInput.original(image);
        JSONObject one=AiReplyClient.createImageRequest("rules","{}",Collections.singletonList(input));
        require(AiReplyClient.utf8Bytes(one.toString())<AiReplyClient.MAX_REQUEST_BYTES,"one valid original remains supported");
        expectRejected(()->AiReplyClient.createImageRequest("rules","{}",Arrays.asList(input,input)),"aggregate original bytes cannot grow unchecked");
        JSONObject oversized=new JSONObject().put("payload","中".repeat(AiReplyClient.MAX_REQUEST_BYTES/3+1));
        // Rejection before URL creation proves this test never contacts a model endpoint.
        try {
            AiReplyClient.executeDetailed(AiModelConfig.DEEPSEEK,"test-key",oversized,new AiReplyClient.Cancellation());
            throw new AssertionError("execution must recheck modified request");
        } catch(AiReplyClient.ClientException expected) {
            require(expected.getMessage().contains("过大")&&!expected.getMessage().contains("test-key"),"execution budget fails locally without key disclosure");
        }
        JSONObject answer=new JSONObject().put("private_response_text","must-not-appear-in-diagnostics");
        JSONObject usage=new JSONObject().put("prompt_tokens",320).put("completion_tokens",42).put("total_tokens",362);
        AiReplyClient.ParsedResponse parsed=AiReplyClient.parseResponseDetailed(AiModelConfig.DEEPSEEK,envelope(answer,usage));
        require(parsed.promptTokens==320&&parsed.completionTokens==42&&parsed.totalTokens==362,"reported provider usage captured");
        require(parsed.diagnostics().contains("prompt_tokens=320")&&!parsed.diagnostics().contains("private_response")
                &&!parsed.diagnostics().contains("must-not-appear"),"numeric diagnostics only");
        parsed=AiReplyClient.parseResponseDetailed(AiModelConfig.DEEPSEEK,envelope(answer,null));
        require(parsed.promptTokens==-1&&parsed.totalTokens==-1,"missing usage is unknown, not zero");
        usage=new JSONObject().put("prompt_tokens",-1).put("completion_tokens","123").put("total_tokens",3.5);
        parsed=AiReplyClient.parseResponseDetailed(AiModelConfig.DEEPSEEK,envelope(answer,usage));
        require(parsed.promptTokens==-1&&parsed.completionTokens==-1&&parsed.totalTokens==-1,"invalid usage is unavailable");
        System.out.println("AiRequestBudgetTest: final text/base64/UTF-8 limits, local execution rejection and numeric usage diagnostics passed");
    }
    private static String envelope(JSONObject content,JSONObject usage) throws Exception {
        JSONObject envelope=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop")
                .put("message",new JSONObject().put("content",content.toString()))));
        if(usage!=null)envelope.put("usage",usage);
        return envelope.toString();
    }
    private interface Attempt {void run() throws Exception;}
    private static void expectRejected(Attempt attempt,String message) throws Exception {
        try {attempt.run();throw new AssertionError(message);} catch(IllegalArgumentException expected) { }
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
