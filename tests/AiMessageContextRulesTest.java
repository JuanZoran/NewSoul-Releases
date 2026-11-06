package dev.local.newsoul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real collection rules exercised with synthetic local rows; no host/API calls. */
public final class AiMessageContextRulesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        resolvesLocalOldTarget();
        readsOnlyQuoteIdentity();
        refusesForeignScopeAndSnapshots();
        respectsHiddenRecalledAndFailedTargets();
        deduplicatesAndReconciles();
        enforcesQuoteBudget();
        classifiesOnlyKnownEvents();
        excludesOnlyExactOfficialGuide();
        rebindsOnlyVerifiedGuideEviction();
        System.out.println("AiMessageContextRulesTest: " + checks + " checks passed");
    }

    private static void rebindsOnlyVerifiedGuideEviction() throws Exception {
        List<String> original = new ArrayList<>();
        for (int i = 0; i < 20; i++) original.add(AiContextRules.digest("real message " + i));
        List<String> suffix = new ArrayList<>(original.subList(1, 20));
        eq(AiMessageContextRules.VisibleRebind.SAME,
                AiMessageContextRules.visibleRebind(20, 0, original, 20, 0, new ArrayList<>(original)));
        eq(AiMessageContextRules.VisibleRebind.GUIDE_EVICTION,
                AiMessageContextRules.visibleRebind(20, 0, original, 20, 1, suffix));
        // Below the cache limit the guide can appear/disappear without changing any real message.
        eq(AiMessageContextRules.VisibleRebind.SAME,
                AiMessageContextRules.visibleRebind(19, 0, suffix, 20, 1, suffix));
        eq(AiMessageContextRules.VisibleRebind.SAME,
                AiMessageContextRules.visibleRebind(20, 1, suffix, 19, 0, suffix));
        eq(AiMessageContextRules.VisibleRebind.SAME,
                AiMessageContextRules.visibleRebind(20, 1, suffix, 20, 1, suffix));
        eq(AiMessageContextRules.VisibleRebind.SAME,
                AiMessageContextRules.visibleRebind(0, 0, Collections.emptyList(), 1, 1, Collections.emptyList()));

        // A real message may enter memory before DB persistence. It cannot become a new baseline.
        List<String> notPersisted = new ArrayList<>(suffix);
        notPersisted.add(AiContextRules.digest("new message not yet in DB"));
        rejected(20, 0, original, 20, 0, notPersisted);
        notPersisted.remove(0);
        rejected(20, 0, original, 20, 1, notPersisted);
        List<String> added = new ArrayList<>(original);
        added.add(AiContextRules.digest("additional message not yet in DB"));
        rejected(20, 0, original, 21, 0, added);

        // A change at any retained position is rejected even during a genuine guide insertion.
        for (String change : new String[]{"edited content", "status", "quote target", "recall"}) {
            for (int i = 0; i < suffix.size(); i++) {
                List<String> modified = new ArrayList<>(suffix);
                modified.set(i, AiContextRules.digest(change + " not yet in DB " + i));
                rejected(20, 0, original, 20, 1, modified);
                List<String> fullModified = new ArrayList<>(original);
                fullModified.set(i + 1, modified.get(i));
                rejected(20, 0, original, 20, 0, fullModified);
            }
        }
        List<String> reordered = new ArrayList<>(suffix);
        Collections.swap(reordered, 4, 5);
        rejected(20, 0, original, 20, 1, reordered);
        rejected(20, 0, original, 20, 1, new ArrayList<>(original.subList(0, 19)));
        List<String> middleMissing = new ArrayList<>(original);
        middleMissing.remove(10);
        rejected(20, 0, original, 20, 1, middleMissing);
        List<String> twoMissing = new ArrayList<>(original.subList(2, 20));
        rejected(20, 0, original, 20, 2, twoMissing);
        rejected(20, 0, original, 19, 1, twoMissing);
        rejected(20, 0, original, 19, 0, suffix); // No new verified guide.
        rejected(21, 1, original, 20, 1, suffix); // Existing guide cannot explain eviction.
        rejected(20, 1, suffix, 20, 2, twoMissing);
        rejected(21, 0, added, 21, 1, original); // Do not generalize the verified 20-entry host rule.
        rejected(19, 0, suffix, 19, 1, twoMissing);
        rejected(20, 0, original, 19, 1, suffix); // Raw count and captured list disagree.
        rejected(19, 0, original, 20, 1, suffix);
        rejected(20, -1, original, 20, 1, suffix);
        rejected(-1, 0, original, 20, 1, suffix);
        rejected(20, 21, original, 20, 1, suffix);
        rejected(20, 0, null, 20, 1, suffix);
        rejected(20, 0, original, 20, 1, null);
        List<String> invalid = new ArrayList<>(original);
        invalid.set(0, null);
        rejected(20, 0, invalid, 20, 0, invalid);
        invalid.set(0, "");
        rejected(20, 0, invalid, 20, 0, invalid);
    }

    private static void rejected(int oldRaw, int oldGuides, List<String> oldDigests,
            int newRaw, int newGuides, List<String> newDigests) {
        eq(AiMessageContextRules.VisibleRebind.REJECTED,
                AiMessageContextRules.visibleRebind(oldRaw, oldGuides, oldDigests, newRaw, newGuides, newDigests));
    }

    private static void excludesOnlyExactOfficialGuide() {
        String id = "AIGC_GUIDE_MSG_ID", body = "cn.soulapp.imlib.msg.chat.JsonMsg", type = "AIGC_GUIDE";
        check(AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "account", "recipient", "account", "recipient"));
        for (String otherId : new String[]{"message-1", "AIGC_GUIDE_MSG_ID_1", "ACTIVE_GUIDE_MSG_ID", "aigc_guide_msg_id", "", null})
            check(!AiMessageContextRules.isOfficialGuide(otherId, 1, 35, body, type, "account", "recipient", "account", "recipient"));
        for (int envelope : new int[]{0, 2, 3, 10, -1})
            check(!AiMessageContextRules.isOfficialGuide(id, envelope, 35, body, type, "account", "recipient", "account", "recipient"));
        for (int chat : new int[]{1, 9, 27, 32, -1})
            check(!AiMessageContextRules.isOfficialGuide(id, 1, chat, body, type, "account", "recipient", "account", "recipient"));
        for (String otherBody : new String[]{"cn.soulapp.imlib.msg.chat.TextMsg", body + "$Subclass", "JsonMsg", "", null})
            check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, otherBody, type, "account", "recipient", "account", "recipient"));
        // Genuine JSON chat events, tips and unrecognised types retain every content/status guard.
        for (String otherType : new String[]{"gift_notify", "poke_it", "little_tip", "AIGC_GUIDE_SENT", "aigc_guide", "", null})
            check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, otherType, "account", "recipient", "account", "recipient"));
        check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "recipient", "account", "account", "recipient"));
        check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "another-account", "recipient", "account", "recipient"));
        check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "account", "another-recipient", "account", "recipient"));
        check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "account", "recipient", "other-account", "recipient"));
        // The raw message recipient cannot be matched to snapshot.target's encrypted ID.
        check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "account", "recipient", "account", "encrypted-recipient"));
        for (String invalid : new String[]{null, "", "null"}) {
            check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, invalid, "recipient", invalid, "recipient"));
            check(!AiMessageContextRules.isOfficialGuide(id, 1, 35, body, type, "account", invalid, "account", invalid));
        }
    }

    public static final class HostMessage {
        public HostMessage quoteMsg;
        final String id; boolean deleted;
        HostMessage(String id) { this.id = id; }
        public String getMsgId() { return id; }
        public boolean getBooleanExt(String key) { if (!"quoteMsgDeleted".equals(key)) throw new AssertionError(key); return deleted; }
        public Object getChatMessage() { throw new AssertionError("Embedded quote body must never be read"); }
    }

    private static void readsOnlyQuoteIdentity() {
        HostMessage source = new HostMessage("reply");
        check(AiMessageContextRules.readQuote(source) == null);
        source.quoteMsg = new HostMessage("old");
        eq("old", AiMessageContextRules.readQuote(source).rawId);
        source.quoteMsg.deleted = true;
        eq("target_recalled", AiMessageContextRules.readQuote(source).state);
        source.quoteMsg.deleted = false; source.deleted = true;
        eq("target_recalled", AiMessageContextRules.readQuote(source).state);
        source.quoteMsg = null;
        eq("target_recalled", AiMessageContextRules.readQuote(source).state);
        eq("unresolved", AiMessageContextRules.readQuote(new Object()).state);
    }

    private static void resolvesLocalOldTarget() throws Exception {
        AiMessageContextRules.Quotes resolver = new AiMessageContextRules.Quotes("account", "chat");
        JSONObject old = row("old", "text", "周六上午十点在公园见");
        check(resolver.register("account", "chat", "old", old));
        JSONObject reply = row("reply", "text", "这个时间可以");
        resolver.attach(reply.getString("id"), AiMessageContextRules.Quote.reference("old", false));
        JSONArray messages = new JSONArray().put(reply), quoted = resolver.enrich(messages);
        eq("resolved", reply.getJSONObject("quote").getString("status"));
        eq(old.getString("id"), reply.getJSONObject("quote").getString("targetId"));
        eq(1, quoted.length()); eq(old.getString("text"), quoted.getJSONObject(0).getString("text"));
        check(!quoted.toString().contains("\"old\""));
        check(!reply.getJSONObject("quote").toString().contains("account"));
        JSONObject history = new JSONObject().put("messages", messages).put("quotedMessages", quoted);
        AiMessageContextRules.reconcileQuotes(history);
        eq(1, history.getJSONArray("quotedMessages").length());
    }

    private static void refusesForeignScopeAndSnapshots() throws Exception {
        AiMessageContextRules.Quotes resolver = new AiMessageContextRules.Quotes("account", "chat");
        JSONObject foreign = row("foreign", "text", "外部聊天正文不应出现");
        check(!resolver.register("account", "another_chat", "foreign", foreign));
        check(!resolver.register("another_account", "chat", "foreign", foreign));
        JSONObject mismatchedId = row("other", "text", "标识不匹配");
        check(!resolver.register("account", "chat", "foreign", mismatchedId));
        JSONObject reply = row("reply", "text", "收到");
        resolver.attach(reply.getString("id"), AiMessageContextRules.Quote.reference("foreign", false));
        eq(0, resolver.enrich(new JSONArray().put(reply)).length());
        eq("target_not_available", reply.getJSONObject("quote").getString("status"));
        check(!reply.getJSONObject("quote").has("targetId"));
        check(!reply.toString().contains("外部聊天"));
        // An embedded quoted snapshot is deliberately not an input to the resolver at all.
        eq("unresolved", AiMessageContextRules.Quote.reference(null, false).state);
        eq("target_recalled", AiMessageContextRules.Quote.reference("old", true).state);
        eq("", AiMessageContextRules.Quote.reference("old", true).rawId);
    }

    private static void respectsHiddenRecalledAndFailedTargets() throws Exception {
        for (String kind : new String[]{"withheld", "recall", "unsupported", "system"}) {
            AiMessageContextRules.Quotes resolver = new AiMessageContextRules.Quotes("account", "chat");
            JSONObject target = row("old", kind, "即使原始保留正文存在也不可补出");
            resolver.register("account", "chat", "old", target);
            JSONObject reply = row("reply", "text", "收到");
            resolver.attach(reply.getString("id"), AiMessageContextRules.Quote.reference("old", false));
            JSONArray quotes = resolver.enrich(new JSONArray().put(reply));
            eq(0, quotes.length());
            String expected = kind.equals("system") ? "unsupported" : kind.equals("recall") ? "recalled" : kind;
            eq("target_" + expected, reply.getJSONObject("quote").getString("status"));
            check(!reply.toString().contains("保留正文"));
        }
        AiMessageContextRules.Quotes resolver = new AiMessageContextRules.Quotes("account", "chat");
        JSONObject failed = row("old", "text", "未发出的草稿").put("sender", "self").put("status", 5);
        resolver.register("account", "chat", "old", failed);
        JSONObject reply = row("reply", "text", "收到");
        resolver.attach(reply.getString("id"), AiMessageContextRules.Quote.reference("old", false));
        eq(0, resolver.enrich(new JSONArray().put(reply)).length());
        eq("target_unconfirmed", reply.getJSONObject("quote").getString("status"));
        eq(AiContextRules.digest("account\nfrom\nto\nraw"), AiMessageContextRules.recallKey("account", "from", "to", "raw"));
        check(!AiMessageContextRules.recallKey("account", "from", "to", "raw")
                .equals(AiMessageContextRules.recallKey("account", "to", "from", "raw")));
    }

    private static void deduplicatesAndReconciles() throws Exception {
        AiMessageContextRules.Quotes resolver = new AiMessageContextRules.Quotes("account", "chat");
        JSONObject target = row("old", "image", "[图片画面未提供]")
                .put("mediaIds", new JSONArray().put("image"))
                .put("url", "https://img.soulapp.cn/private.jpg").put("secret", "not_exported");
        resolver.register("account", "chat", "old", target);
        JSONArray rows = new JSONArray();
        for (int i = 0; i < 2; i++) {
            JSONObject reply = row("reply" + i, "text", "看到了"); rows.put(reply);
            resolver.attach(reply.getString("id"), AiMessageContextRules.Quote.reference("old", false));
        }
        JSONArray quotes = resolver.enrich(rows); eq(1, quotes.length());
        check(!quotes.toString().contains("https")); check(!quotes.toString().contains("mediaIds"));
        check(!quotes.toString().contains("secret")); eq("not_provided", quotes.getJSONObject(0).getString("contentStatus"));
        JSONObject history = new JSONObject().put("messages", rows).put("quotedMessages", quotes);
        history.put("quotedMessages", new JSONArray()); AiMessageContextRules.reconcileQuotes(history);
        eq("budget_excluded", rows.getJSONObject(0).getJSONObject("quote").getString("status"));
        eq("budget_excluded", rows.getJSONObject(1).getJSONObject("quote").getString("status"));
        rows.put(target); quotes = resolver.enrich(rows); eq(0, quotes.length());
        eq("resolved", rows.getJSONObject(0).getJSONObject("quote").getString("status"));
        history.put("messages", new JSONArray()).put("quotedMessages", new JSONArray().put(target));
        AiMessageContextRules.reconcileQuotes(history); eq(0, history.getJSONArray("quotedMessages").length());
    }

    private static void enforcesQuoteBudget() throws Exception {
        AiMessageContextRules.Quotes resolver = new AiMessageContextRules.Quotes("account", "chat");
        JSONArray rows = new JSONArray();
        for (int i = 0; i < 20; i++) {
            JSONObject target = row("old" + i, "text", "字".repeat(2000));
            resolver.register("account", "chat", "old" + i, target);
            JSONObject reply = row("reply" + i, "text", "收到"); rows.put(reply);
            resolver.attach(reply.getString("id"), AiMessageContextRules.Quote.reference("old" + i, false));
        }
        JSONArray retained = resolver.enrich(rows);
        check(retained.length() <= 12); check(retained.toString().length() <= 6000 + 13);
        eq("resolved", rows.getJSONObject(19).getJSONObject("quote").getString("status"));
        eq("budget_excluded", rows.getJSONObject(0).getJSONObject("quote").getString("status"));
        eq(1000, retained.getJSONObject(0).getString("text").length());
        check(retained.getJSONObject(0).getBoolean("textTruncated"));
    }

    private static void classifiesOnlyKnownEvents() throws Exception {
        String prefix = "cn.soulapp.imlib.msg.chat.";
        JSONObject untrusted = new JSONObject().put("url", "https://img.soulapp.cn/audio")
                .put("word", "不应自动上传的转写").put("latitude", 12.3).put("address", "私人地址")
                .put("content", "不能直接导出的JSON").put("messageType", "pat_it");
        eq("audio", AiMessageContextRules.event(5, prefix + "AudioMsg", untrusted).getString("type"));
        eq("video", AiMessageContextRules.event(4, prefix + "VideoMsg", untrusted).getString("type"));
        eq("location", AiMessageContextRules.event(33, prefix + "PositionMsg", untrusted).getString("type"));
        eq("call", AiMessageContextRules.event(39, "", untrusted).getString("type"));
        eq("recall", AiMessageContextRules.event(9, "", untrusted).getString("type"));
        JSONObject event = AiMessageContextRules.event(35, prefix + "JsonMsg", untrusted);
        eq("interaction", event.getString("type")); eq("pat_it", event.getString("subType"));
        for (String key : new String[]{"url", "word", "latitude", "address", "content"}) check(!event.has(key));
        check(AiMessageContextRules.event(5, prefix + "StringMsg", untrusted) == null);
        check(AiMessageContextRules.event(999, prefix + "JsonMsg", untrusted) == null);
        untrusted.put("messageType", "unknown_future_payload");
        check(AiMessageContextRules.event(35, prefix + "JsonMsg", untrusted) == null);
    }

    private static JSONObject row(String id, String kind, String text) throws Exception {
        return new JSONObject().put("id", AiMessageContextRules.messageId("account", "chat", id))
                .put("sender", "other").put("type", 1).put("envelopeType", 1).put("status", 2)
                .put("time", 1770000000000L).put("kind", kind).put("text", text);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + (checks + 1)); checks++; }
    private static void eq(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual)); }
}
