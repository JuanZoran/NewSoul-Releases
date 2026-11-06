package dev.local.newsoul;

import java.util.*;
import org.json.*;

/** Strict output validation. Model output never supplies a target or send command. */
final class AiReplyRules {
    static final int MAX_TEXT = 500;
    static final int MAX_IMAGES = 4;
    static final class Candidate {
        final String id, text, reason, watch;
        Candidate(String id, String text) { this(id, text, "", ""); }
        Candidate(String id, String text, String reason, String watch) {
            this.id = id; this.text = text; this.reason = reason; this.watch = watch;
        }
        String guidance() {
            return (reason.isEmpty() ? "" : "选择理由：" + reason)
                    + (watch.isEmpty() ? "" : (reason.isEmpty() ? "" : "\n") + "观察：" + watch);
        }
    }
    static final class Result {
        final String stage, frame, summary, goal, frameGoal, action;
        final String confidence, trend, positive, unclear, negative;
        final List<Candidate> candidates;
        final List<String> questions;
        Result(JSONObject check, JSONObject operation, JSONObject cycle, List<Candidate> candidates,
                List<String> questions) throws JSONException {
            stage = check.getString("stage"); frame = check.getString("frame");
            // Confidence describes the relationship estimate, not certainty about a refusal.
            confidence = stage.equals("unknown") ? "low" : check.getString("confidence"); trend = check.getString("trend");
            // A missing user fact is the next step; speculative partner branches are premature.
            positive = questions.isEmpty() ? cycle.optString("positive", "") : "";
            unclear = questions.isEmpty() ? cycle.optString("unclear", "") : "";
            negative = questions.isEmpty() ? cycle.optString("negative", "") : "";
            summary = check.getString("summary"); goal = operation.getString("goal");
            frameGoal = operation.has("frame_goal") ? operation.getString("frame_goal") : ""; action = operation.getString("type");
            this.candidates = Collections.unmodifiableList(candidates);
            this.questions = Collections.unmodifiableList(questions);
        }
        String stageLabel() {
            switch(stage) {
                case "0-19": return "无感";
                case "20-39": return "好奇";
                case "40-59": return "兴趣";
                case "60-79": return "好感";
                case "80-100": return "依恋";
                default: return "暂无法评估";
            }
        }
        String interestLabel() {
            if (stage.equals("unknown")) return "兴趣指标 · 暂无法评估";
            return "兴趣指标 · " + stageLabel()
                    + (confidence.equals("low") ? " · 依据有限" : "");
        }
    }

    /** Recover only the complete, unambiguous action-nesting error observed in MiMo.
     * No coercion, defaults or source mutation; all semantic checks still run afterwards.
     */
    private static JSONObject normalizeLayout(JSONObject source) throws JSONException {
        JSONObject check = source.optJSONObject("check");
        String[] swallowed = {"action", "candidates", "need_user_input", "cycle"};
        boolean completeRoot = true;
        for (String key : swallowed) if (!source.has(key)) completeRoot = false;
        boolean insideCheck = false;
        for (String key : swallowed) {
            if (check == null || !check.has(key)) continue;
            Object annotation = check.opt(key);
            // Preserve canonical legacy handling of empty assessment annotations.
            // A null/empty string beside a complete root is not a second payload.
            if (completeRoot && (annotation == JSONObject.NULL || "".equals(annotation))) continue;
            insideCheck = true;
        }
        if (insideCheck) {
            for (String key : swallowed)
                if (source.has(key) || !check.has(key)) fail("响应字段层级混合或冲突，请重新生成");
            onlyKeys(source, "snapshot_id", "check");
            onlyKeys(check, "stage", "confidence", "trend", "comfort", "context", "frame", "evidence_ids", "summary",
                    "action", "candidates", "need_user_input", "cycle", "facts", "missing");
            onlyKeys(check.getJSONObject("action"), "type", "goal", "frame_goal");
            onlyKeys(check.getJSONObject("cycle"), "positive", "unclear", "negative");
            JSONArray items = check.getJSONArray("candidates");
            for (int i=0; i<items.length(); i++)
                onlyKeys(items.getJSONObject(i), "id", "text", "reason", "watch");
            JSONObject normalized = new JSONObject(source.toString());
            JSONObject copiedCheck = normalized.getJSONObject("check");
            for (String key : swallowed) normalized.put(key, copiedCheck.remove(key));
            return normalized;
        }
        JSONObject action = source.optJSONObject("action");
        String[] moved = {"candidates", "need_user_input", "cycle"};
        boolean nested = false;
        for (String key : moved) if (action != null && action.has(key)) nested = true;
        if (!nested) return source;
        for (String key : moved)
            if (source.has(key) || !action.has(key)) fail("响应字段层级混合或冲突，请重新生成");
        onlyKeys(source, "snapshot_id", "check", "action");
        onlyKeys(action, "type", "goal", "frame_goal", "candidates", "need_user_input", "cycle");
        onlyKeys(source.getJSONObject("check"), "stage", "confidence", "trend", "comfort", "context",
                "frame", "evidence_ids", "summary", "facts", "missing");
        onlyKeys(action.getJSONObject("cycle"), "positive", "unclear", "negative");
        JSONArray candidates = action.getJSONArray("candidates");
        for (int i=0; i<candidates.length(); i++)
            onlyKeys(candidates.getJSONObject(i), "id", "text", "reason", "watch");
        JSONObject normalized = new JSONObject(source.toString());
        JSONObject copiedAction = normalized.getJSONObject("action");
        for (String key : moved) normalized.put(key, copiedAction.remove(key));
        return normalized;
    }

    private static void onlyKeys(JSONObject object, String... allowed) throws JSONException {
        Set<String> keys = new HashSet<>(Arrays.asList(allowed));
        for (Iterator<String> it=object.keys(); it.hasNext();)
            if (!keys.contains(it.next())) fail("响应包含未知字段，不能恢复层级");
    }

    static Result validate(JSONObject value, String snapshotId, Set<String> evidenceIds) throws JSONException {
        value = normalizeLayout(value);
        if (!snapshotId.equals(text(value, "snapshot_id", 128, false))) fail("快照不匹配，请重新生成");
        JSONObject check = value.getJSONObject("check"), action = value.getJSONObject("action");
        oneOf(check, "stage", "unknown", "0-19", "20-39", "40-59", "60-79", "80-100");
        oneOf(check, "confidence", "low", "medium", "high");
        oneOf(check, "trend", "up", "stable", "down", "unknown");
        oneOf(check, "comfort", "comfortable", "concern", "unknown");
        oneOf(check, "context", "ordinary", "romantic", "sexual", "unknown");
        oneOf(check, "frame", "steady", "approval_seeking", "defensive", "overbearing", "unknown");
        text(check, "summary", 800, false);
        JSONArray evidence = check.getJSONArray("evidence_ids");
        if (evidence.length() > 24) fail("依据数量异常");
        for (int i=0;i<evidence.length();i++) {
            Object id = evidence.get(i);
            if (!(id instanceof String) || !evidenceIds.contains(id)) fail("模型引用了不存在的依据");
        }
        validateGrounding(check,evidenceIds);
        String type = oneOf(action, "type", "CLARIFY", "SHARE", "CONNECT", "RELATE", "FLIRT",
                "REPAIR", "BOUNDARY", "INVITE", "WECHAT", "LOGISTICS", "WAIT", "STOP");
        text(action, "goal", 300, false);
        if (action.has("frame_goal")) text(action, "frame_goal", 300, true);
        JSONObject cycle = value.getJSONObject("cycle");
        for (String field : new String[]{"positive", "unclear", "negative"})
            if (cycle.has(field)) text(cycle, field, 500, true);
        List<String> questions = new ArrayList<>();
        JSONArray asks = value.getJSONArray("need_user_input");
        if (asks.length()>5) fail("补充问题过多");
        for (int i=0;i<asks.length();i++) {
            Object ask = asks.get(i);
            if (!(ask instanceof String) || ((String)ask).trim().isEmpty() || ((String)ask).length()>500)
                fail("补充问题格式错误");
            questions.add(((String)ask).trim());
        }
        JSONArray list = value.getJSONArray("candidates");
        if (list.length()>3) fail("候选数量超过限制");
        if ((type.equals("WAIT") || type.equals("STOP") || !questions.isEmpty()) && list.length()!=0)
            fail("候选与等待、停止或缺失资料状态矛盾");
        if (!type.equals("WAIT") && !type.equals("STOP") && !type.equals("BOUNDARY")
                && questions.isEmpty() && list.length()==0)
            fail("模型未提供候选或所需资料");
        List<Candidate> candidates = new ArrayList<>(); Set<String> ids = new HashSet<>(), texts = new HashSet<>();
        for(int i=0;i<list.length();i++) {
            JSONObject item=list.getJSONObject(i);
            String id=text(item,"id",40,false), reply=text(item,"text",MAX_TEXT,false).trim();
            if(!ids.add(id) || !texts.add(reply)) fail("候选重复");
            if(reply.indexOf('\0')>=0 || reply.contains("```")) fail("候选内容格式异常");
            AiCandidateTextRules.validate(reply);
            candidates.add(new Candidate(id, reply, optionalGuidance(item, "reason", 80), optionalGuidance(item, "watch", 120)));
        }
        return new Result(check, action, cycle, candidates, questions);
    }

    /** Cross-check deterministic local facts, without guessing intent from message keywords.
     * Explicit user context may describe a correction or an outstanding question; that
     * exception remains a model judgment. Draft rewriting is not a new reply decision.
     */
    static Result validateForContext(JSONObject value, String snapshotId, JSONObject context) throws JSONException {
        Result result = validate(value, snapshotId, evidenceIds(context));
        JSONObject check=value.getJSONObject("check");
        if(check.has("facts")) {
            JSONArray facts=check.getJSONArray("facts");
            for(int i=0;i<facts.length();i++) {
                JSONObject fact=facts.getJSONObject(i);
                if(!sourceContains(context,fact.getString("source_id"),fact.getString("quote")))
                    fail("事实引用与本轮资料不符，请重新生成");
            }
        }
        if ("REWRITE".equals(context.optString("writing_mode"))) return result;
        JSONObject history = context.optJSONObject("history");
        JSONObject interaction = history == null ? null : history.optJSONObject("interaction");
        String state = interaction == null ? "" : interaction.optString("state");
        boolean unconfirmed = "outgoing_unconfirmed".equals(state);
        boolean awaiting = "awaiting_reply".equals(state)
                && context.optString("current_intent").trim().isEmpty();
        if ((unconfirmed || awaiting) && (!result.candidates.isEmpty() || !result.questions.isEmpty()
                || !("WAIT".equals(result.action) || "STOP".equals(result.action)))) {
            fail(unconfirmed ? "上一条消息状态尚未确认，请先核对，不重复补发"
                    : "当前正在等待对方回复，本轮结果与聊天状态不符，请重新生成");
        }
        return result;
    }

    /** Optional provenance is auditable evidence, not a proof of semantic entailment. */
    private static void validateGrounding(JSONObject check,Set<String> evidenceIds) throws JSONException {
        if(check.has("facts")) {
            JSONArray facts=check.getJSONArray("facts");
            if(facts.length()>3)fail("事实依据过多");
            for(int i=0;i<facts.length();i++) {
                JSONObject fact=facts.getJSONObject(i);
                onlyKeys(fact,"claim","source_id","quote");
                text(fact,"claim",300,false); text(fact,"quote",500,false);
                if(!evidenceIds.contains(text(fact,"source_id",128,false)))fail("事实来源不存在");
            }
        }
        if(check.has("missing")) {
            JSONArray missing=check.getJSONArray("missing");
            if(missing.length()>2)fail("缺失事实过多");
            for(int i=0;i<missing.length();i++) {
                Object item=missing.get(i);
                if(!(item instanceof String) || ((String)item).trim().isEmpty() || ((String)item).length()>300)
                    fail("缺失事实格式错误");
            }
        }
    }
    private static boolean sourceContains(Object data,String id,String quote) throws JSONException {
        if(data instanceof JSONObject) {
            JSONObject object=(JSONObject)data;
            if((id.equals(object.opt("id")) || id.equals(object.opt("source_id"))) && containsText(object,quote))return true;
            for(Iterator<String> it=object.keys();it.hasNext();)if(sourceContains(object.get(it.next()),id,quote))return true;
        } else if(data instanceof JSONArray) {
            JSONArray array=(JSONArray)data;for(int i=0;i<array.length();i++)if(sourceContains(array.get(i),id,quote))return true;
        }
        return false;
    }
    private static boolean containsText(Object data,String quote) throws JSONException {
        if(data instanceof String)return ((String)data).contains(quote);
        if(data instanceof JSONObject) {
            JSONObject object=(JSONObject)data;
            for(Iterator<String> it=object.keys();it.hasNext();) {
                String key=it.next();
                if(!key.equals("id") && !key.equals("source_id") && containsText(object.get(key),quote))return true;
            }
        } else if(data instanceof JSONArray) {
            JSONArray array=(JSONArray)data;for(int i=0;i<array.length();i++)if(containsText(array.get(i),quote))return true;
        }
        return false;
    }

    static Set<String> evidenceIds(Object data) throws JSONException {
        Set<String> ids = new HashSet<>(); collectIds(data, ids); return ids;
    }
    private static void collectIds(Object data, Set<String> ids) throws JSONException {
        if(data instanceof JSONObject) {
            JSONObject o=(JSONObject)data;
            for(Iterator<String> it=o.keys();it.hasNext();) {
                String key=it.next();Object v=o.get(key);
                if ((key.equals("id") || key.equals("source_id")) && v instanceof String) ids.add((String)v);
                else if(v instanceof JSONArray || v instanceof JSONObject) collectIds(v,ids);
            }
        } else if(data instanceof JSONArray) {
            JSONArray a=(JSONArray)data;for(int i=0;i<a.length();i++)collectIds(a.get(i),ids);
        }
    }
    private static String optionalGuidance(JSONObject item, String key, int max) throws JSONException {
        if (!item.has(key)) return ""; // Older model responses and previews remain valid.
        String value = text(item, key, max, true).trim();
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0)
            fail("候选说明须为简短单段：" + key);
        return value;
    }

    private static String text(JSONObject o,String key,int max,boolean empty) throws JSONException {
        Object value=o.get(key);
        if(!(value instanceof String)) {fail("输出字段类型错误："+key);return "";}
        String s=(String)value;
        if(s.length()>max || (!empty && s.trim().isEmpty())) fail("输出字段长度错误："+key);
        return s;
    }
    private static String oneOf(JSONObject o,String key,String...values) throws JSONException {
        String s=text(o,key,80,false);
        if(!Arrays.asList(values).contains(s)) fail("输出状态不可识别："+key);
        return s;
    }
    private static void fail(String message) throws JSONException {throw new JSONException(message);}
}
