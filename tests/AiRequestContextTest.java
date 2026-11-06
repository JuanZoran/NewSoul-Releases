package dev.local.newsoul;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AiRequestContextTest {
    public static void main(String[] args) throws Exception {
        sceneSelection();
        selectedSourcesAndQuotes();
        completeBudget();
        latestStateAndTiming();
        rewriteRules();
        System.out.println("AiRequestContextTest: scene selection, preserved inputs/quotes, total character budget and rewrite contract passed");
    }

    private static void sceneSelection() throws Exception {
        JSONObject original=context("REPLY","reply",80,8,30);
        String before=original.toString();
        JSONObject reply=AiRequestContext.prepare("rules",original);
        require(messages(reply).length()==80&&posts(reply).length()==2,"reply prioritizes dialogue without discarding affordable older boundaries");
        require("m0".equals(messages(reply).getJSONObject(0).getString("id")),"older dialogue retained without budget pressure");
        require(before.equals(original.toString()),"snapshot must remain immutable");
        require(reply.getJSONObject("requestSelection").getInt("historyOmittedCount")==0,"omission count");
        require(reply.getJSONObject("history").getJSONObject("interaction").toString().equals(
                original.getJSONObject("history").getJSONObject("interaction").toString()),"complete local interaction retained");
        JSONObject opening=AiRequestContext.prepare("rules",context("REPLY","opening",80,8,30));
        require(messages(opening).length()==8&&posts(opening).length()==6,"opening prioritizes homepage");
        require("opening".equals(opening.getJSONObject("requestSelection").getString("scene")),"opening selection metadata");
        JSONObject rewrite=AiRequestContext.prepare("rules",context("REWRITE","reply",80,8,30));
        require(messages(rewrite).length()==12&&posts(rewrite).length()==0,"rewrite focuses on draft and necessary recent context");
        for(String key:new String[]{"self_profile","current_intent","current_draft","selected_images","snapshot_id"})
            require(rewrite.get(key).toString().equals(original.get(key).toString()),"preserved "+key);
    }

    private static void selectedSourcesAndQuotes() throws Exception {
        JSONObject original=context("REWRITE","reply",80,8,30);
        original.getJSONArray("selected_images").put(new JSONObject().put("id","selected_old"));
        messages(original).getJSONObject(0).put("sharedPost",new JSONObject().put("detail",
                new JSONObject().put("mediaIds",new JSONArray().put("selected_old"))));
        posts(original).getJSONObject(7).put("mediaIds",new JSONArray().put("selected_old"));
        messages(original).getJSONObject(79).put("quote",new JSONObject().put("status","resolved").put("targetId","m1"));
        JSONObject result=AiRequestContext.prepare("rules",original);
        require(hasId(messages(result),"m0")&&hasId(messages(result),"m1"),"selected image source and quote target survive oldest pruning");
        require(hasId(posts(result),"p7"),"selected homepage source survives pruning");
        require("resolved".equals(messages(result).getJSONObject(messages(result).length()-1).getJSONObject("quote").getString("status")),"retained quote remains resolved");

        JSONObject external=context("REWRITE","reply",70,0,30);
        messages(external).getJSONObject(69).put("quote",new JSONObject().put("status","resolved").put("targetId","q1"));
        messages(external).getJSONObject(0).put("quote",new JSONObject().put("status","resolved").put("targetId","orphan"));
        external.getJSONObject("history").put("quotedMessages",new JSONArray().put(row("q1",20)).put(row("orphan",20)));
        JSONObject selected=AiRequestContext.prepare("rules",external);
        JSONArray quoted=selected.getJSONObject("history").getJSONArray("quotedMessages");
        require(quoted.length()==1&&hasId(quoted,"q1"),"quote excerpts from removed references are omitted");

        JSONObject constrained=context("REWRITE","reply",1,0,10);
        messages(constrained).getJSONObject(0).put("quote",new JSONObject().put("status","resolved").put("targetId","too_big"));
        constrained.getJSONObject("history").put("quotedMessages",new JSONArray().put(row("too_big",6000)));
        constrained.put("current_draft","d".repeat(42_000));
        JSONObject budgeted=AiRequestContext.prepare("rules",constrained);
        require(budgeted.getJSONObject("history").getJSONArray("quotedMessages").length()==0,"quote target lower priority than user draft");
        require("budget_excluded".equals(messages(budgeted).getJSONObject(0).getJSONObject("quote").getString("status")),"budgeted quote cannot dangle as resolved");
    }

    private static void completeBudget() throws Exception {
        JSONObject large=context("REPLY","reply",48,2,900);
        large.put("self_profile",new JSONObject().put("source_id","self_profile").put("text","p".repeat(7000)))
                .put("current_draft","d".repeat(5000)).put("current_intent","i".repeat(1500));
        String system="s".repeat(8000);
        JSONObject result=AiRequestContext.prepare(system,large);
        require(AiRequestContext.inputChars(system,result.toString())<=AiRequestContext.MAX_INPUT_CHARS,"final budget includes system and all final fields");
        require(messages(result).length()<48,"combined system/profile/intent/draft causes old row pruning");
        require(result.getJSONObject("self_profile").getString("text").length()==7000&&result.getString("current_draft").length()==5000,"user semantics never silently truncated");
        JSONObject huge=context("REWRITE","reply",1,0,10);
        huge.put("current_draft","private-draft".repeat(5000));
        JSONObject hugeDraft=huge;
        expectRejected(()->AiRequestContext.prepare("rules",hugeDraft),"oversized indispensable draft fails");
        huge=context("REPLY","reply",1,0,10);
        huge.put("self_profile",new JSONObject().put("text","private-profile".repeat(5000)));
        JSONObject finalHuge=huge;
        expectRejected(()->AiRequestContext.prepare("rules",finalHuge),"oversized profile fails");
        expectRejected(()->AiRequestContext.prepare("custom-rules".repeat(5000),context("REPLY","reply",1,0,10)),"custom system included in budget");
    }

    private static void latestStateAndTiming() throws Exception {
        JSONObject data=context("REWRITE","awaiting_reply",60,0,30);
        JSONObject history=data.getJSONObject("history");
        history.put("latestState",new JSONObject().put("state","outgoing_unconfirmed").put("status",5));
        for(int i=1;i<60;i++)messages(data).getJSONObject(i).put("timing",new JSONObject().put("previousMessageId","m"+(i-1))
                .put("gapFromPreviousInterpersonalMs",60000).put("gapFromOppositeMessageMs",60000).put("elapsedMs",100000));
        JSONObject selected=AiRequestContext.prepare("rules",data);
        require(selected.getJSONObject("history").getJSONObject("latestState").toString().equals(history.getJSONObject("latestState").toString()),"latest state validation fields retained");
        JSONObject first=messages(selected).getJSONObject(0).getJSONObject("timing");
        require(!first.has("previousMessageId")&&!first.has("gapFromPreviousInterpersonalMs")&&!first.has("gapFromOppositeMessageMs")&&first.has("elapsedMs"),"omitted predecessor not presented as supplied timing evidence");
        require(messages(selected).getJSONObject(1).getJSONObject("timing").has("previousMessageId"),"retained predecessor timing preserved");
        JSONObject crowded=context("REWRITE","reply",12,0,8000);
        for(int i=4;i<12;i++)messages(crowded).getJSONObject(i).put("sender","system");
        JSONObject reduced=AiRequestContext.prepare("rules",crowded);
        require(hasId(messages(reduced),"m3")&&hasId(messages(reduced),"m11"),"latest interpersonal and latest event survive oversized system notices");
    }

    private static void rewriteRules() throws Exception {
        String base=AiReplyPrompt.systemPrompt();
        String rewrite=AiWritingRules.systemRules(base,AiWritingRules.Mode.REWRITE,AiWritingRules.Style.CONSIDERATE);
        String reply=AiWritingRules.systemRules(base,AiWritingRules.Mode.REPLY,AiWritingRules.Style.DEFAULT);
        require(rewrite.length()<reply.length(),"bundled rewrite is leaner");
        for(String required:new String[]{"check.facts","逐字","WAIT/STOP","history.interaction","selected_images","严格 JSON 契约","年龄不明","醉酒","否定、时间、数字"})
            require(rewrite.contains(required),"retained rewrite constraint "+required);
        String custom=base+"\n用户自定义规则：保留所有额外要求";
        require(AiWritingRules.systemRules(custom,AiWritingRules.Mode.REWRITE,AiWritingRules.Style.CONSIDERATE).startsWith(custom+"\n"),"custom base cannot be compacted by a shared header");
        System.out.println("Bundled request rules: reply="+reply.length()+" chars rewrite="+rewrite.length()+" chars (not token estimates)");
    }

    private static JSONObject context(String mode,String state,int count,int postCount,int textChars) throws Exception {
        JSONArray rows=new JSONArray(),posts=new JSONArray();
        for(int i=0;i<count;i++)rows.put(row("m"+i,textChars));
        for(int i=0;i<postCount;i++)posts.put(new JSONObject().put("id","p"+i).put("content","x".repeat(textChars)));
        return new JSONObject().put("snapshot_id","test-snapshot").put("writing_mode",mode)
                .put("self_profile",new JSONObject().put("source_id","self_profile").put("text","真实用户资料"))
                .put("current_intent","已知目标").put("current_draft","明天不行\n周六可以")
                .put("selected_images",new JSONArray())
                .put("history",new JSONObject().put("status","ok").put("messages",rows).put("includedCount",count)
                        .put("eligibleCount",count).put("omittedOlderCount",0).put("completeLocalRead",true)
                        .put("interaction",new JSONObject().put("state",state).put("interpersonalCount",count).put("latestEvent","other")))
                .put("homepagePosts",new JSONObject().put("status","ok").put("data",new JSONObject().put("posts",posts).put("includedCount",postCount)));
    }
    private static JSONObject row(String id,int textChars) throws Exception {
        return new JSONObject().put("id",id).put("sender","other").put("envelopeType",1).put("type",1)
                .put("status",2).put("kind","text").put("text","x".repeat(textChars));
    }
    private static JSONArray messages(JSONObject context) throws Exception {return context.getJSONObject("history").getJSONArray("messages");}
    private static JSONArray posts(JSONObject context) throws Exception {return context.getJSONObject("homepagePosts").getJSONObject("data").getJSONArray("posts");}
    private static boolean hasId(JSONArray rows,String id) {for(int i=0;i<rows.length();i++)if(id.equals(rows.optJSONObject(i).optString("id")))return true;return false;}
    private interface Attempt {void run() throws Exception;}
    private static void expectRejected(Attempt attempt,String message) throws Exception {
        try {attempt.run();throw new AssertionError(message);} catch(IllegalArgumentException expected) {
            require(!expected.getMessage().contains("private-"),"rejection must not expose user content");
        }
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
