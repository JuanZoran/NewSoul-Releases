package dev.local.newsoul;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Pure, offline output-contract checks. Requires the test-only org.json runtime. */
public final class AiReplyRulesTest {
    private static final String SNAPSHOT = "snapshot-test-42";
    private static final Set<String> EVIDENCE = new HashSet<>(Arrays.asList("message-1", "post-1"));
    private static int checks;

    public static void main(String[] args) throws Exception {
        AiReplyRules.Result good = validate(valid());
        check(good.candidates.size() == 2, "normal two candidates");
        check("CONNECT".equals(good.action) && "20-39".equals(good.stage), "validated action and stage");
        check("steady".equals(good.frame) && good.questions.isEmpty(), "frame and no missing input");
        check(good.interestLabel().equals("兴趣指标 · 好奇"), "interest label without score");
        JSONObject tentative = valid(); tentative.getJSONObject("check").put("confidence", "low");
        check(validate(tentative).interestLabel().equals("兴趣指标 · 好奇 · 依据有限"), "low confidence remains visible");
        check(good.candidates.get(0).guidance().isEmpty(), "legacy response has no invented advice");
        JSONObject guided = valid();
        String replyBefore = candidate(guided, 0).getString("text");
        candidate(guided, 0).put("reason", "  接住对方主动分享  ").put("watch", "主动补充就接着聊，否则不追问");
        AiReplyRules.Candidate choice = validate(guided).candidates.get(0);
        check(choice.text.equals(replyBefore), "guidance never changes outgoing text");
        check(choice.guidance().equals("选择理由：接住对方主动分享\n观察：主动补充就接着聊，否则不追问"), "per-option advice preserved and trimmed");
        check(validate(guided).candidates.get(1).guidance().isEmpty(), "advice does not leak into another candidate");
        candidate(guided, 0).put("reason", "");
        check(validate(guided).candidates.get(0).guidance().startsWith("观察："), "watch can appear without reason");
        candidate(guided, 0).remove("watch");
        check(validate(guided).candidates.get(0).guidance().isEmpty(), "empty metadata creates no filler");
        candidate(guided, 0).put("reason", "因".repeat(80)).put("watch", "看".repeat(120));
        check(validate(guided).candidates.get(0).reason.length()==80, "guidance limits accepted");
        rejects(o -> candidate(o, 0).put("reason", "因".repeat(81)), "字段长度错误");
        rejects(o -> candidate(o, 0).put("watch", "看".repeat(121)), "字段长度错误");
        rejects(o -> candidate(o, 0).put("reason", 7), "字段类型错误");
        rejects(o -> candidate(o, 0).put("watch", JSONObject.NULL), "字段类型错误");
        rejects(o -> candidate(o, 0).put("reason", "两行\n说明"), "简短单段");
        rejects(o -> candidate(o, 0).put("watch", "控制\0字符"), "简短单段");
        JSONObject emptyCycle = valid(); emptyCycle.put("cycle", new JSONObject());
        check(validate(emptyCycle).positive.isEmpty() && validate(emptyCycle).unclear.isEmpty()
                && validate(emptyCycle).negative.isEmpty(), "optional guidance does not require invented branches");
        JSONObject partialCycle = valid(); partialCycle.getJSONObject("cycle").remove("negative");
        check(validate(partialCycle).negative.isEmpty() && !validate(partialCycle).positive.isEmpty(), "preserve supplied guidance without filling absent branches");
        rejects(o -> o.getJSONObject("cycle").put("positive", JSONObject.NULL), "字段类型错误");
        JSONObject grounded=valid();
        JSONObject fact=new JSONObject().put("claim","周日有空").put("source_id","message-1").put("quote","我周日有空");
        grounded.getJSONObject("check").put("facts",new JSONArray().put(fact)).put("missing",new JSONArray());
        JSONObject context=new JSONObject().put("profile",new JSONObject().put("source_id","message-1").put("text","我周日有空，不吃牛肉"))
                .put("post",new JSONObject().put("id","post-1").put("text","周一也行"));
        check(AiReplyRules.validateForContext(grounded,SNAPSHOT,context).candidates.size()==2,"optional facts use exact source without changing candidates");
        fact.put("quote","周一也行");
        try {AiReplyRules.validateForContext(grounded,SNAPSHOT,context);throw new AssertionError("wrong source accepted");}
        catch(JSONException expected){check(expected.getMessage().contains("事实引用"),"quote from a different source rejected");}
        fact.put("quote","message-1");
        try {AiReplyRules.validateForContext(grounded,SNAPSHOT,context);throw new AssertionError("identifier accepted as quote");}
        catch(JSONException expected){check(expected.getMessage().contains("事实引用"),"source identifier is not source prose");}
        rejects(o -> o.getJSONObject("check").put("facts",new JSONArray().put(new JSONObject().put("claim","事实").put("source_id","unknown").put("quote","原文"))),"事实来源");
        rejects(o -> o.getJSONObject("check").put("facts",JSONObject.NULL),null);
        rejects(o -> o.getJSONObject("check").put("facts",new JSONArray().put(new JSONObject().put("claim","事实").put("source_id","message-1").put("quote",true))),"字段类型");
        rejects(o -> o.getJSONObject("check").put("facts",new JSONArray().put(new JSONObject().put("claim","事实").put("source_id","message-1").put("quote","原文").put("command","send"))),null);
        rejects(o -> o.getJSONObject("check").put("missing",new JSONArray().put("a").put("b").put("c")),"缺失事实过多");
        rejects(o -> o.getJSONObject("check").put("missing",new JSONArray().put(new JSONObject())),"缺失事实格式");
        JSONObject noFrameGoal = valid();
        noFrameGoal.getJSONObject("action").remove("frame_goal");
        check(validate(noFrameGoal).frameGoal.isEmpty(), "omitted optional frame goal is empty, not a fatal error");
        rejects(o -> o.getJSONObject("action").put("frame_goal", JSONObject.NULL), "字段类型错误");
        JSONObject unknownStage = valid(); unknownStage.getJSONObject("check").put("stage", "unknown");
        check(validate(unknownStage).interestLabel().equals("兴趣指标 · 暂无法评估"), "unknown is not zero");
        unknownStage.getJSONObject("check").put("confidence", "high");
        check(validate(unknownStage).confidence.equals("low"), "unknown relationship cannot present high confidence in its estimate");
        check(unknownStage.getJSONObject("check").getString("confidence").equals("high"), "conservative display does not mutate raw evidence");
        for (String[] stage : new String[][]{{"0-19","无感"},{"40-59","兴趣"},{"60-79","好感"},{"80-100","依恋"}}) {
            JSONObject v = valid(); v.getJSONObject("check").put("stage", stage[0]);
            check(validate(v).stageLabel().equals(stage[1]), "course stage " + stage[0]);
            check(validate(v).interestLabel().equals("兴趣指标 · " + stage[1]), "all ranges use corresponding interest label");
        }
        try {
            good.candidates.clear();
            throw new AssertionError("candidate collection must be immutable");
        } catch (UnsupportedOperationException expected) { checks++; }

        rejects(o -> o.put("snapshot_id", "old-snapshot"), "快照不匹配");
        rejects(o -> o.put("snapshot_id", 42), "字段类型错误");
        rejects(o -> o.getJSONObject("check").put("evidence_ids", new JSONArray().put("unknown")),
                "不存在的依据");
        rejects(o -> o.getJSONObject("check").put("evidence_ids", new JSONArray().put(12)),
                "不存在的依据");
        rejects(o -> o.getJSONObject("check").put("evidence_ids", repeated("message-1", 25)),
                "依据数量异常");

        for (String action : new String[] {"WAIT", "STOP"}) {
            rejects(o -> o.getJSONObject("action").put("type", action), "状态矛盾");
            JSONObject value = valid();
            value.getJSONObject("action").put("type", action);
            value.put("candidates", new JSONArray());
            check(validate(value).candidates.isEmpty(), action + " with no candidates");
        }
        rejects(o -> o.put("need_user_input", new JSONArray().put("你周末什么时间方便？")), "状态矛盾");
        JSONObject missingFacts = valid();
        missingFacts.put("need_user_input", new JSONArray().put("你周末什么时间方便？"));
        missingFacts.put("candidates", new JSONArray());
        check(validate(missingFacts).questions.size() == 1, "missing fact blocks candidates");
        missingFacts.getJSONObject("cycle").put("positive","对方可能会回答");
        check(validate(missingFacts).positive.isEmpty() && validate(missingFacts).unclear.isEmpty() && validate(missingFacts).negative.isEmpty(), "missing user facts hide premature partner predictions");
        check(missingFacts.getJSONObject("cycle").getString("positive").equals("对方可能会回答"), "raw guidance preserved for audit");
        rejects(o -> o.put("candidates", new JSONArray()), "未提供候选");
        JSONObject quietBoundary=valid();
        quietBoundary.getJSONObject("action").put("type","BOUNDARY");
        quietBoundary.put("candidates",new JSONArray());
        check(validate(quietBoundary).candidates.isEmpty(),"boundary guidance may recommend no outgoing message");

        JSONObject limit = valid();
        candidate(limit, 0).put("text", "好".repeat(500));
        check(validate(limit).candidates.get(0).text.length() == 500, "exact candidate text limit");
        rejects(o -> candidate(o, 0).put("text", "好".repeat(501)), "字段长度错误");
        rejects(o -> candidate(o, 1).put("id", "a"), "候选重复");
        rejects(o -> candidate(o, 1).put("text", "  这家店看起来不错，你最推荐哪道菜？  "), "候选重复");
        rejects(o -> candidate(o, 0).put("text", "```json\n{}\n```"), "内容格式异常");
        rejects(o -> candidate(o, 0).put("text", "回复\0内容"), "内容格式异常");
        for (String text : new String[]{"这家店挺不错~", "周末一起逛逛～", "好呀\\~", "今天不错\\n明天聊", "明天聊\\"})
            rejects(o -> candidate(o, 0).put("text", text), "装饰符号或转义残留");
        for (String text : new String[]{
                "您和对放目前是纯匹配状态，还没有任何聊天？还是线上已有对话但是未同步？",
                "你和对方目前是匹配状态吗？", "请提供最近的聊天记录", "建议你这样回复：明天去试试"})
            rejects(o -> candidate(o, 0).put("text", text), "助手补问或说明");
        for (String text : new String[]{
                "你喝咖啡更挑豆子还是挑店里的氛围？", "这个 AI 展览你也去看了吗？", "可直接发送给我，我看看",
                "翻到我们的聊天记录，发现第一次聊的也是这家店", "你们周末一般去哪骑车？",
                "发你链接 https://example.com/~zoran?a=1&b=2", "看看 https://example.com/%7Euser",
                "下午 3~5 点我都在", "大概 3～5 公里", "照片在 C:\\Users\\Zoran\\Pictures",
                "共享目录是 \\\\server\\photos", "保存在 ~/Pictures 里", "这里的“\\n”表示换行",
                "正则里用 `\\d+` 表示数字", "这个“~”符号叫波浪号", "今天聊得挺开心\n明天再给你看看照片",
                "找人聊天吗", "沿河走走确实挺舒服\n你一般傍晚去吗",
                "你说的是周六，还是周日？", "好呀\n那就周日\n具体时间再商量"}) {
            JSONObject value=valid();candidate(value,0).put("text",text);
            check(text.equals(validate(new JSONObject(value.toString())).candidates.get(0).text),"preserve legitimate content: "+text);
        }
        rejects(o -> candidate(o, 0).put("text", "看这里 https://example.com/~zoran 好呀~"), "装饰符号或转义残留");
        rejects(o -> candidate(o, 0).put("text", "   "), "字段长度错误");
        rejects(o -> candidate(o, 0).put("text", 123), "字段类型错误");
        rejects(o -> candidate(o, 0).put("text", JSONObject.NULL), "字段类型错误");
        rejects(o -> candidate(o, 0).put("id", 123), "字段类型错误");
        rejects(o -> candidate(o, 0).put("id", "x".repeat(41)), "字段长度错误");
        rejects(o -> o.getJSONArray("candidates").put(new JSONObject().put("id", "c").put("text", "三"))
                .put(new JSONObject().put("id", "d").put("text", "四")), "数量超过限制");
        rejects(o -> o.getJSONArray("candidates").put(0, "plain text"), null);

        for (String field : new String[] {"stage", "confidence", "trend", "comfort", "context", "frame"}) {
            rejects(o -> o.getJSONObject("check").put(field, "unsupported"), "状态不可识别");
            rejects(o -> o.getJSONObject("check").put(field, 123), "字段类型错误");
        }
        rejects(o -> o.getJSONObject("action").put("type", "SEND"), "状态不可识别");
        rejects(o -> o.getJSONObject("action").put("type", 123), "字段类型错误");
        rejects(o -> o.getJSONObject("check").put("summary", new JSONObject()), "字段类型错误");
        rejects(o -> o.getJSONObject("action").put("goal", new JSONArray()), "字段类型错误");
        rejects(o -> o.getJSONObject("action").put("frame_goal", false), "字段类型错误");
        for (String field : new String[] {"positive", "unclear", "negative"}) {
            rejects(o -> o.getJSONObject("cycle").put(field, true), "字段类型错误");
        }
        rejects(o -> o.put("need_user_input", repeated("问题", 6)), "补充问题过多");
        rejects(o -> o.put("need_user_input", new JSONArray().put(42)), "补充问题格式错误");
        rejects(o -> o.put("need_user_input", new JSONArray().put("   ")), "补充问题格式错误");
        rejects(o -> o.put("need_user_input", new JSONArray().put("问".repeat(501))), "补充问题格式错误");

        JSONObject sources = new JSONObject().put("messages", new JSONArray()
                .put(new JSONObject().put("id", "message-1").put("unrelated", "not-an-id")))
                .put("post", new JSONObject().put("source_id", "post-1"))
                .put("nested", new JSONArray().put(new JSONArray()
                        .put(new JSONObject().put("id", "nested-id"))))
                .put("id", 999);
        check(AiReplyRules.evidenceIds(sources).equals(
                new HashSet<>(Arrays.asList("message-1", "post-1", "nested-id"))),
                "only string id/source_id collected recursively");
        check(AiReplyRules.evidenceIds(new JSONArray()).isEmpty(), "empty evidence collection");
        System.out.println("AiReplyRules: " + checks + " offline checks passed");
    }

    private static AiReplyRules.Result validate(JSONObject value) throws JSONException {
        return AiReplyRules.validate(value, SNAPSHOT, EVIDENCE);
    }

    private static JSONObject candidate(JSONObject value, int index) throws JSONException {
        return value.getJSONArray("candidates").getJSONObject(index);
    }

    private static JSONArray repeated(String text, int count) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < count; i++) result.put(text);
        return result;
    }

    private static JSONObject valid() throws JSONException {
        return new JSONObject()
                .put("snapshot_id", SNAPSHOT)
                .put("check", new JSONObject()
                        .put("stage", "20-39").put("confidence", "medium").put("trend", "stable")
                        .put("comfort", "comfortable").put("context", "ordinary").put("frame", "steady")
                        .put("summary", "双方围绕美食交流，当前可以自然延续话题。")
                        .put("evidence_ids", new JSONArray().put("message-1").put("post-1")))
                .put("action", new JSONObject().put("type", "CONNECT")
                        .put("goal", "延续共同话题").put("frame_goal", "有兴趣且保持自然"))
                .put("candidates", new JSONArray()
                        .put(new JSONObject().put("id", "a").put("text", "这家店看起来不错，你最推荐哪道菜？"))
                        .put(new JSONObject().put("id", "b").put("text", "这份菜单挺有意思，我会先试招牌菜。")))
                .put("need_user_input", new JSONArray())
                .put("cycle", new JSONObject().put("positive", "延续共同体验")
                        .put("unclear", "依据后续消息再判断").put("negative", "降低强度"));
    }

    private interface Mutation { void apply(JSONObject value) throws Exception; }

    private static void rejects(Mutation mutation, String message) throws Exception {
        JSONObject value = valid();
        mutation.apply(value);
        try {
            validate(value);
            throw new AssertionError("Expected rejected output: " + message);
        } catch (JSONException expected) {
            check(message == null || expected.getMessage().contains(message),
                    "rejection reason: " + message);
        }
    }

    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks++;
    }
}
