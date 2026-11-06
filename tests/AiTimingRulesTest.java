package dev.local.newsoul;

import org.json.JSONArray;
import org.json.JSONObject;

public final class AiTimingRulesTest {
    private static final long NOW = 1790906400000L;
    private static int checks;
    public static void main(String[] args) throws Exception {
        AiConversationRules.HistoryState state = new AiConversationRules.HistoryState();
        state.record(1, 1, "other", 2, "text", NOW - 180000);
        state.record(1, 1, "self", 4, "text", NOW - 120000);
        state.record(3, -1, "system", 2, "system", NOW - 1000);
        JSONArray rows = new JSONArray().put(row("other", 2, NOW - 180000))
                .put(new JSONObject().put("sender", "system").put("time", NOW - 150000))
                .put(row("self", 4, (NOW - 120000) / 1000));
        JSONObject context = context(state, rows);
        context.put("capturedAt", NOW - 60000).put("snapshot_id", "frozen");
        AiTimingRules.enrich(context, NOW);
        eq(60000L, rows.getJSONObject(2).getJSONObject("timing").getLong("gapFromOppositeMessageMs"));
        eq(120000L, context.getJSONObject("timing").getLong("unansweredSinceLatestMs"));
        eq("other", context.getJSONObject("timing").getString("waitingFor"));
        eq(NOW - 60000, context.getLong("capturedAt"));
        eq("frozen", context.getString("snapshot_id"));
        for (AiModelConfig model : new AiModelConfig[]{AiModelConfig.DEEPSEEK, AiModelConfig.MIMO}) {
            JSONObject textRequest = AiReplyClient.createRequest(model, "规则", context.toString(), java.util.Collections.emptyList());
            JSONObject textContext = new JSONObject(textRequest.getJSONArray("messages").getJSONObject(1).getString("content"));
            eq(120000L, textContext.getJSONObject("timing").getLong("unansweredSinceLatestMs"));
            JSONObject imageRequest = AiReplyClient.createImageRequest(model, "规则", context.toString(),
                    java.util.Collections.singletonList(AiReplyClient.ImageInput.remote("https://example.com/image.jpg")));
            JSONObject imageContext = new JSONObject(imageRequest.getJSONArray("messages").getJSONObject(1)
                    .getJSONArray("content").getJSONObject(0).getString("text"));
            eq("other", imageContext.getJSONObject("timing").getString("waitingFor"));
        }
        AiTimingRules.enrich(context, NOW + 60000);
        eq(180000L, context.getJSONObject("timing").getLong("unansweredSinceLatestMs"));

        state.record(1, 8, "other", 2, "sticker", NOW - 10000);
        // Full-history latest time survives an empty/truncated export.
        context = context(state, new JSONArray());
        AiTimingRules.enrich(context, NOW);
        eq("self", context.getJSONObject("timing").getString("waitingFor"));
        eq(10000L, context.getJSONObject("timing").getLong("unansweredSinceLatestMs"));
        state.record(1, 1, "self", 5, "text", NOW - 5000);
        context = context(state, new JSONArray()); AiTimingRules.enrich(context, NOW);
        eq(false, context.getJSONObject("timing").has("waitingFor"));

        for (long invalid : new long[]{0, 1, -1, NOW + 1, Long.MAX_VALUE}) {
            state = new AiConversationRules.HistoryState();
            state.record(1, 1, "self", 4, "text", invalid);
            context = context(state, new JSONArray().put(row("self", 4, invalid)));
            AiTimingRules.enrich(context, NOW);
            eq(false, context.getJSONObject("timing").has("unansweredSinceLatestMs"));
            eq(false, context.getJSONObject("history").getJSONArray("messages").getJSONObject(0)
                    .getJSONObject("timing").has("elapsedMs"));
        }
        rows = new JSONArray().put(row("other", 2, NOW - 30000))
                .put(row("self", 5, NOW - 20000)).put(row("other", 2, NOW - 10000));
        context = context(state, rows); AiTimingRules.enrich(context, NOW);
        eq(false, rows.getJSONObject(1).getJSONObject("timing").has("gapFromOppositeMessageMs"));
        eq(false, rows.getJSONObject(2).getJSONObject("timing").has("gapFromPreviousInterpersonalMs"));
        System.out.println("AiTimingRulesTest: " + checks + " checks passed");
    }
    private static JSONObject row(String sender, long status, long time) throws Exception {
        return new JSONObject().put("id", sender + time).put("sender", sender).put("status", status)
                .put("time", time).put("envelopeType", 1).put("type", 1);
    }
    private static JSONObject context(AiConversationRules.HistoryState state, JSONArray rows) throws Exception {
        return new JSONObject().put("history", new JSONObject().put("interaction", state.json()).put("messages", rows));
    }
    private static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
        checks++;
    }
}
