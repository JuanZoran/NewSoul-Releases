package dev.local.newsoul;

import java.util.Collections;
import org.json.*;

/** Synthetic conversations and authored responses; no model calls or quality scoring. */
public final class AiCacScenariosTest {
    static int checks;
    public static void main(String[] args) throws Exception {
        scenario("cold", "reply", "", "我：做什么工作？\n对方：上班\n我：喜欢什么？\n对方：还好", "WAIT", "已连续单向提问，冷淡原因未知", "先停下来，不再堆话题", "", "");
        scenario("positive", "reply", "我确实也喜欢旧书店", "对方：我爱逛旧书店，你呢", "CONNECT", "主动分享并回问，支持继续聊，未证明恋爱意愿", "回应共同兴趣", "我也喜欢逛旧书店", "");
        scenario("ambiguous", "reply", "想明天约出来", "对方：哈哈以后有机会吧", "WAIT", "没有确认时间，不能当同意", "先不落实明天的安排", "", "");
        scenario("invite", "reply", "想一起去，不知道哪天有空", "对方：那家面馆我也想试", "INVITE", "共同活动有连接点，见面意愿还未确认", "先问共同意愿，不虚构日程", "一起去试试？", "");
        scenario("reschedule", "reply", "周日有空", "对方：周六不行，周日中午可以", "LOGISTICS", "给出了替代日期，不是拒绝见面", "确认周日安排", "好，那就周日中午", "");
        scenario("decline-meeting", "reply", "", "对方：暂时不想见面，可以在这里聊", "CONNECT", "拒绝见面但允许普通交流", "停止邀约，尊重普通聊天范围", "好，就在这里聊", "");
        scenario("stop", "reply", "用幽默挽回", "对方：别再联系我", "STOP", "明确要求停止联系", "停止联系，不补最后一句", "", "");
        scenario("missing", "incoming_unparsed", "", "对方：[未提供语音内容]", "CLARIFY", "无法读取语音，不能猜测", "只补充必要内容", "", "这条语音说了什么？");
        scenario("awaiting", "awaiting_reply", "", "我：周末一起吃饭？", "WAIT", "已发出且尚无新回复", "等待，不催促", "", "");

        scenario("short-direct-answer", "reply", "", "我：下班了吗\n对方：嗯，你呢", "CLARIFY", "短答之后主动回问，并非没有待答事项", "先核对用户真实近况", "", "你现在下班了吗？");
        scenario("short-logistics", "reply", "我已经订好周日中午的位子", "我：周日中午可以吗\n对方：可以，订好了吗", "LOGISTICS", "简短接受并问安排", "回答已知订位事实", "订好了，周日中午见", "");
        scenario("cold-but-question", "reply", "我在城西上班", "我：周末喜欢做什么\n对方：没啥，你在哪上班", "CLARIFY", "投入不高但有明确待答问题", "直接回答，不用新问题换取投入", "在城西", "");
        scenario("busy-ending", "reply", "", "对方：先忙了，晚点聊", "WAIT", "明确暂时结束本轮", "先停下，不再询问忙什么", "", "");
        scenario("ordinary-short", "reply", "刚聊完一段彼此都觉得有趣的经历", "对方：哈哈，也是", "CONNECT", "正常收尾短答，不必诊断冷淡", "简单接住即可，不延长采访", "哈哈", "");
        scenario("preference-unknown", "reply", "", "对方：牛肉面很好吃，你也喜欢吃面吗", "CLARIFY", "对方主动反问，用户口味尚未知", "确认一个必要事实后直接回答", "", "你自己喜欢吃面吗？喜欢、一般或不喜欢都可以。");
        scenario("preference-known", "reply", "喜欢面，但不吃牛肉", "对方：牛肉面很好吃，你也喜欢吃面吗", "CLARIFY", "已有明确口味，直接回应", "回答真实偏好，不重复问已答信息", "喜欢吃面，不过我不吃牛肉", "");
        scenario("share-no-personal-question", "reply", "", "对方：这家牛肉面很好吃", "CONNECT", "对方分享评价，无待确认的用户事实", "自然接住已知内容", "看来这家的牛肉面不错", "");
        scenario("boundary-time-elapsed", "reply", "想再邀约，已经过了三天", "之前对方：暂时不想见面，可以在这里聊。现在对方：今天那部电影挺好笑", "CONNECT", "新话题不改变原有见面边界", "只承接允许的线上交流", "是哪一段把你逗笑了", "");
        scenario("boundary-new-consent", "reply", "我周日中午有空", "之前对方：暂时不见面。现在对方：我改主意了，周日中午一起去吃面吧", "LOGISTICS", "对方主动明确改变见面意愿，用户日程已知", "核对双方同意的具体安排", "好，周日中午一起去", "");
        JSONObject fixture = new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of("tests/fixtures/cac-mimo-cold-observed.json")));
        JSONObject observed = fixture.getJSONObject("response");
        AiReplyRules.Result actual = AiReplyRules.validate(observed, "synthetic-cold", java.util.Set.of("m1", "m2", "m3", "m4"));
        if (!actual.frameGoal.isEmpty() || actual.candidates.size()!=2 || !"CONNECT".equals(actual.action))
            throw new AssertionError("Observed response must parse unchanged, including omitted optional frame_goal");
        if (actual.action.equals(fixture.getJSONObject("review").getString("expected_action")))
            throw new AssertionError("Do not relabel the observed model failure as a successful WAIT response");
        checks += 2; // Parser compatibility and preservation of a known semantic failure, not a model quality pass.
        // User-run 1.38.0 real outputs: record transport/parse separately from semantics.
        JSONObject batch = new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of("tests/fixtures/cac-mimo-batch-1.38.0-observed.json")));
        for (Object rowObject : batch.getJSONArray("requests")) {
            JSONObject row = (JSONObject) rowObject;
            JSONObject output = new JSONObject(row.getString("content"));
            if (row.getString("name").equals("cold")) {
                String rawBefore = output.toString();
                AiReplyRules.Result recovered = AiReplyRules.validate(output, "synthetic-cold", java.util.Set.of("m1", "m2", "m3", "m4"));
                if (!recovered.action.equals("WAIT") || !recovered.candidates.isEmpty() || !rawBefore.equals(output.toString()))
                    throw new AssertionError("Recover only shape, preserve observed WAIT and immutable source");
            } else {
                AiReplyRules.Result parsed = AiReplyRules.validate(output, output.getString("snapshot_id"), java.util.Set.of("m1", "m2"));
                if (parsed.candidates.size() != 2) throw new AssertionError("Preserve raw candidates; parsing is not factual approval");
            }
            checks++;
        }
        JSONObject newBatch = new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of("tests/fixtures/cac-mimo-batch-1.38.1-observed.json")));
        for (Object rowObject : newBatch.getJSONArray("requests")) {
            JSONObject row = (JSONObject) rowObject;
            JSONObject output = new JSONObject(row.getString("content"));
            // These new responses already have the canonical layout, without recovery.
            for (String key : new String[]{"candidates", "need_user_input", "cycle"})
                if (!output.has(key) || output.getJSONObject("action").has(key)) throw new AssertionError("canonical layout changed");
            AiReplyRules.Result parsed = AiReplyRules.validate(output, output.getString("snapshot_id"), java.util.Set.of("m1", "m2", "m3", "m4"));
            String name = row.getString("name");
            if (name.equals("cold") && (!parsed.action.equals("WAIT") || !parsed.candidates.isEmpty()))
                throw new AssertionError("observed cold decision changed");
            if (name.equals("short_engaged") && (!parsed.action.equals("CLARIFY") || !parsed.candidates.isEmpty() || parsed.questions.size()!=1))
                throw new AssertionError("observed clarification changed");
            if (name.equals("boundary") && (!parsed.action.equals("BOUNDARY") || parsed.candidates.size()!=2))
                throw new AssertionError("observed boundary response changed");
            checks++;
        }
        JSONObject context = context("awaiting_reply", "", "我：你好");
        JSONObject wrong = answer("CONNECT", "刚才我在看你的主页", "", "说明", "目标");
        // Before: syntax-only validator accepted a fresh opener while awaiting a reply.
        AiReplyRules.validate(wrong, "fixture", AiReplyRules.evidenceIds(context)); checks++;
        reject(wrong, context);
        reject(answer("BOUNDARY", "", "", "边界", "不发话"), context); // Silent guidance cannot bypass awaiting-state gate.
        JSONObject wait = answer("WAIT", "", "", "等待", "留出空间");
        AiReplyRules.validateForContext(wait, "fixture", context); checks++;
        reject(answer("CLARIFY", "", "之前语音是什么？", "说明", "目标"), context);
        context.put("current_intent", "刚才把周日写成周六，需要纠正");
        AiReplyRules.validateForContext(answer("REPAIR", "刚才说错了，是周日", "", "说明", "纠正"), "fixture", context); checks++;
        context.getJSONObject("history").getJSONObject("interaction").put("state", "outgoing_unconfirmed");
        reject(wrong, context); // Explicit intent cannot permit duplicate unconfirmed sends.
        context.put("writing_mode", "REWRITE");
        AiReplyRules.validateForContext(wrong, "fixture", context); checks++;
        wrong.put("snapshot_id", "stale"); reject(wrong, context); // Rewrite retains normal validation.
        JSONObject truncated = context("reply", "", "对方：你好");
        truncated.getJSONObject("history").put("truncated", true).put("omittedOlderCount", 140);
        AiReplyRules.validateForContext(answer("CONNECT", "你好", "", "较早记录未提供", "普通接话"), "fixture", truncated); checks++;
        JSONObject unknown = context("reply", "", "对方：你好");
        unknown.getJSONObject("history").getJSONObject("interaction").put("state", "uncertain_history");
        AiReplyRules.validateForContext(answer("CLARIFY", "", "最后一条是什么内容？", "记录类型不明", "核对必要内容"), "fixture", unknown); checks++;
        // Known limit: a free-text intent is an exception signal, NOT proof of a legitimate correction.
        JSONObject intentException = context("awaiting_reply", "再帮我说一句", "我：你好");
        AiReplyRules.validateForContext(answer("CONNECT", "今天怎么样", "", "说明", "目标"), "fixture", intentException); checks++;
        System.out.println("AiCacScenariosTest: " + checks + " checks passed; synthetic contracts and observed response replay, no new model generation");
    }
    static JSONObject context(String state, String intent, String chat) {
        JSONArray rows = new JSONArray();
        AiConversationRules.HistoryState interaction = new AiConversationRules.HistoryState();
        for (String line : chat.split("\\n")) {
            boolean self = line.startsWith("我：");
            String text = line.substring(line.indexOf('：') + 1);
            interaction.record(1, state.equals("incoming_unparsed") ? 4 : 1, self ? "self" : "other",
                    self ? 4 : 2, state.equals("incoming_unparsed") ? "unsupported" : "text");
            rows.put(new JSONObject().put("id", "m" + (rows.length() + 1)).put("sender", self ? "self" : "other")
                    .put("kind", state.equals("incoming_unparsed") ? "unsupported" : "text")
                    .put("status", self ? 4 : 2).put("text", text));
        }
        if (!state.equals(interaction.json().getString("state"))) throw new AssertionError("fixture state differs from production");
        return new JSONObject().put("snapshot_id", "fixture").put("writing_mode", "REPLY").put("current_intent", intent)
                .put("history", new JSONObject().put("completeLocalRead", true).put("truncated", false)
                    .put("interaction", interaction.json())
                    .put("messages", rows));
    }
    static JSONObject answer(String action, String text, String question, String summary, String goal) {
        return new JSONObject().put("snapshot_id", "fixture")
                .put("check", new JSONObject().put("stage", "unknown").put("confidence", "low").put("trend", "unknown")
                    .put("comfort", "unknown").put("context", "ordinary").put("frame", "steady")
                    .put("evidence_ids", new JSONArray().put("m1")).put("summary", summary))
                .put("action", new JSONObject().put("type", action).put("goal", goal).put("frame_goal", ""))
                .put("candidates", text.isEmpty() ? new JSONArray() : new JSONArray().put(new JSONObject().put("id", "a").put("text", text)))
                .put("need_user_input", question.isEmpty() ? new JSONArray() : new JSONArray().put(question))
                .put("cycle", new JSONObject().put("positive", "").put("unclear", "").put("negative", ""));
    }
    static void scenario(String name, String state, String intent, String chat, String action, String summary,
                         String goal, String text, String question) throws Exception {
        JSONObject context = context(state, intent, chat);
        for (AiModelConfig model : new AiModelConfig[]{AiModelConfig.DEEPSEEK, AiModelConfig.MIMO}) {
            String rules = AiWritingRules.systemRules(AiReplyPrompt.systemPrompt(), AiWritingRules.Mode.REPLY, AiWritingRules.Style.DEFAULT);
            JSONObject request = AiReplyClient.createRequest(model, rules, context.toString(), Collections.emptyList());
            JSONObject delivered = new JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));
            if (!context.toString().equals(delivered.toString()))
                throw new AssertionError(name + " context lost");
            AiReplyRules.Result result = AiReplyRules.validateForContext(answer(action, text, question, summary, goal), "fixture", delivered);
            if (!action.equals(result.action) || !summary.equals(result.summary) || !goal.equals(result.goal)) throw new AssertionError(name);
            checks++;
        }
    }
    static void reject(JSONObject answer, JSONObject context) throws Exception {
        try { AiReplyRules.validateForContext(answer, "fixture", context); }
        catch (JSONException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
}
