package dev.local.newsoul;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONObject;

/** Loads the same resource entry point as Android and builds a real offline API request. */
public final class AiReplyPromptTest {
    public static void main(String[] args) throws Exception {
        String prompt=AiReplyPrompt.systemPrompt();
        // Protected builds compare the decoded result with the independent maintained original.
        try(java.io.InputStream resource=args.length == 0
                ? AiReplyPromptTest.class.getClassLoader().getResourceAsStream(AiReplyPrompt.RESOURCE)
                : java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[0]))) {
            if(resource==null||!prompt.equals(new String(resource.readAllBytes(),StandardCharsets.UTF_8)))
                throw new AssertionError("Runtime resource and loaded system prompt differ");
        }
        if(!prompt.contains("《CAC_AI聊天参考手册》")||prompt.contains("L01—L12")||prompt.contains("P09—P18"))
            throw new AssertionError("Runtime must use the new handbook, not the replaced course hierarchy");
        if(AiReplyPromptTest.class.getClassLoader().getResource("newsoul/cac_ai_v1_1.md")!=null)
            throw new AssertionError("Retired resource is still packaged");
        JSONObject context=new JSONObject().put("snapshot_id","offline-opening")
                .put("history",new JSONObject().put("status","ok").put("completeLocalRead",true)
                        .put("interaction",new AiConversationRules.HistoryState().json()))
                .put("self_profile",new JSONObject().put("source_id","self_profile").put("text",""))
                .put("homepageTags",new JSONObject().put("status","ok").put("source_id","homepageTags")
                        .put("data",new org.json.JSONArray().put(new JSONObject().put("tagName","摄影"))))
                .put("homepagePosts",new JSONObject().put("status","timeout"));
        JSONObject request=AiReplyClient.createRequest(prompt,context.toString(),Collections.emptyList());
        String system=request.getJSONArray("messages").getJSONObject(0).getString("content");
        JSONObject sent=new JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));
        if(!system.startsWith(prompt)||!"json_object".equals(request.getJSONObject("response_format").getString("type")))
            throw new AssertionError("New runtime must retain strict JSON request mode");
        if(!"opening".equals(sent.getJSONObject("history").getJSONObject("interaction").getString("state"))
                ||!sent.getJSONObject("self_profile").getString("text").isEmpty()
                ||!"timeout".equals(sent.getJSONObject("homepagePosts").getString("status")))
            throw new AssertionError("Do not replace empty user facts or partial homepage failure with invented data");
        System.out.println("AiReplyPromptTest: 5 checks passed (offline request only; no generation-quality claim)");
    }
}
