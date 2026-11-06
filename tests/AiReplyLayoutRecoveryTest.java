package dev.local.newsoul;
import org.json.*;
import java.nio.file.*;
import java.util.Set;

/** Real output replay plus adversarial layout mutations; no model calls. */
public final class AiReplyLayoutRecoveryTest {
    static int checks;
    static JSONObject original;
    interface Mutation { void apply(JSONObject o) throws Exception; }
    public static void main(String[] args) throws Exception {
        JSONObject fixture = new JSONObject(Files.readString(Path.of("tests/fixtures/cac-mimo-batch-1.38.0-observed.json")));
        original = new JSONObject(fixture.getJSONArray("requests").getJSONObject(0).getString("content"));
        JSONObject rootCheck = copy();
        JSONObject nestedAction = rootCheck.getJSONObject("action");
        for (String key : new String[]{"candidates", "need_user_input", "cycle"}) rootCheck.put(key, nestedAction.remove(key));
        for (String key : new String[]{"action", "candidates", "need_user_input", "cycle"}) rootCheck.getJSONObject("check").put(key, rootCheck.remove(key));
        JSONObject canonical = new JSONObject(rootCheck.toString());
        for (String key : new String[]{"action", "candidates", "need_user_input", "cycle"}) canonical.put(key, canonical.getJSONObject("check").remove(key));
        canonical.getJSONObject("check").put("action", JSONObject.NULL);
        require(parse(canonical).action.equals("WAIT"), "null annotation beside complete root is harmless");
        canonical.getJSONObject("check").put("action", "");
        require(parse(canonical).action.equals("WAIT"), "empty annotation beside complete root is harmless");
        canonical.getJSONObject("check").put("action", new JSONObject().put("type","CONNECT")); rejectValue(canonical);
        String checkBefore = rootCheck.toString();
        require(parse(rootCheck).action.equals("WAIT"), "complete check wrapper recovered");
        require(rootCheck.toString().equals(checkBefore), "check wrapper evidence unmodified");
        for (String key : new String[]{"action", "candidates", "need_user_input", "cycle"}) {
            JSONObject mixed = new JSONObject(checkBefore);
            mixed.put(key, mixed.getJSONObject("check").remove(key)); rejectValue(mixed);
            JSONObject duplicate = new JSONObject(checkBefore);
            duplicate.put(key, duplicate.getJSONObject("check").get(key)); rejectValue(duplicate);
        }
        JSONObject unknownCheck = new JSONObject(checkBefore); unknownCheck.getJSONObject("check").put("send", true); rejectValue(unknownCheck);
        JSONObject badEvidence = new JSONObject(checkBefore); badEvidence.getJSONObject("check").put("evidence_ids", new JSONArray().put("invented")); rejectValue(badEvidence);
        JSONObject before = copy();
        AiReplyRules.Result recovered = parse(before);
        require(recovered.action.equals("WAIT") && recovered.candidates.isEmpty(), "real WAIT recovered");
        require(before.toString().equals(original.toString()), "no mutation of evidence");
        require(recovered.positive.equals(original.getJSONObject("action").getJSONObject("cycle").getString("positive")), "cycle preserved");
        for (String field : new String[]{"candidates", "need_user_input", "cycle"}) {
            rejects(o -> o.put(field, o.getJSONObject("action").get(field))); // duplicate, even identical
            rejects(o -> o.put(field, o.getJSONObject("action").remove(field))); // mixed
            rejects(o -> o.getJSONObject("action").remove(field)); // incomplete
            rejects(o -> o.getJSONObject("action").put(field, JSONObject.NULL));
        }
        rejects(o -> o.put("target", "other-contact"));
        rejects(o -> o.getJSONObject("action").put("send", true));
        rejects(o -> o.getJSONObject("check").put("instruction", "send"));
        rejects(o -> o.getJSONObject("action").getJSONObject("cycle").put("timer", 60));
        rejects(o -> o.getJSONObject("action").put("candidates", new JSONArray().put(
                new JSONObject().put("id", "a").put("text", "再问一句")))); // WAIT invariant remains
        rejects(o -> o.getJSONObject("check").put("evidence_ids", new JSONArray().put("invented")));
        rejects(o -> o.put("snapshot_id", "stale"));
        rejects(o -> o.getJSONObject("action").put("type", "SEND"));
        JSONObject reply = copy();
        reply.getJSONObject("action").put("type", "CONNECT").put("candidates", new JSONArray().put(
                new JSONObject().put("id", "a").put("text", "好，就在这里聊").put("reason", "接受当前范围")));
        require(parse(reply).candidates.get(0).text.equals("好，就在这里聊"), "canonical checks accept safe nested reply");
        JSONObject unknown = new JSONObject(reply.toString());
        unknown.getJSONObject("action").getJSONArray("candidates").getJSONObject(0).put("target", "injected");
        rejectValue(unknown);
        JSONObject context = new JSONObject().put("writing_mode", "REPLY").put("current_intent", "")
                .put("history", new JSONObject().put("interaction", new JSONObject().put("state", "awaiting_reply"))
                .put("messages", new JSONArray().put(new JSONObject().put("id", "m2")).put(new JSONObject().put("id", "m4"))));
        try { AiReplyRules.validateForContext(reply, "synthetic-cold", context); throw new AssertionError("context gate bypassed"); }
        catch (JSONException expected) { checks++; }
        context.getJSONObject("history").getJSONObject("interaction").put("state", "outgoing_unconfirmed");
        try { AiReplyRules.validateForContext(reply, "synthetic-cold", context); throw new AssertionError("unconfirmed gate bypassed"); }
        catch (JSONException expected) { checks++; }
        require(AiReplyRules.validateForContext(copy(), "synthetic-cold", context).action.equals("WAIT"), "WAIT safe across context gate");
        System.out.println("AiReplyLayoutRecoveryTest: " + checks + " checks passed");
    }
    static JSONObject copy() { return new JSONObject(original.toString()); }
    static AiReplyRules.Result parse(JSONObject o) { return AiReplyRules.validate(o, "synthetic-cold", Set.of("m1", "m2", "m3", "m4")); }
    static void rejects(Mutation mutation) throws Exception { JSONObject o=copy(); mutation.apply(o); rejectValue(o); }
    static void rejectValue(JSONObject o) { try { parse(o); throw new AssertionError("accepted invalid layout: " + o); } catch (JSONException expected) { checks++; } }
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
