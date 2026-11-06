package dev.local.newsoul;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Scoped quote resolution and allowlisted event metadata; never consumes quoted snapshot bodies. */
final class AiMessageContextRules {
    private AiMessageContextRules() { }
    static final int MAX_QUOTED_MESSAGES = 12, MAX_QUOTED_CHARS = 6000;

    /** Only Soul's verified local recommendation placeholder is absent from the visible-content guard. */
    static boolean isOfficialGuide(String id, int envelopeType, int chatType, String bodyClass,
            String messageType, String from, String to, String account, String rawRecipient) {
        return "AIGC_GUIDE_MSG_ID".equals(id) && envelopeType == 1 && chatType == 35
                && "cn.soulapp.imlib.msg.chat.JsonMsg".equals(bodyClass) && "AIGC_GUIDE".equals(messageType)
                && account != null && !account.isEmpty() && !"null".equals(account)
                && rawRecipient != null && !rawRecipient.isEmpty() && !"null".equals(rawRecipient)
                && account.equals(from) && rawRecipient.equals(to);
    }

    enum VisibleRebind { SAME, GUIDE_EVICTION, REJECTED }

    /** Only the verified Soul 6.38 first-guide insertion may evict one real cache head. */
    static VisibleRebind visibleRebind(int oldRaw, int oldGuides, List<String> oldDigests,
            int newRaw, int newGuides, List<String> newDigests) {
        if (!validCacheFrame(oldRaw, oldGuides, oldDigests)
                || !validCacheFrame(newRaw, newGuides, newDigests)) return VisibleRebind.REJECTED;
        if (oldDigests.equals(newDigests)) return VisibleRebind.SAME;
        if (oldRaw != 20 || newRaw != 20 || oldGuides != 0 || newGuides != 1
                || oldDigests.size() != 20 || newDigests.size() != 19) return VisibleRebind.REJECTED;
        for (int i = 0; i < newDigests.size(); i++)
            if (!oldDigests.get(i + 1).equals(newDigests.get(i))) return VisibleRebind.REJECTED;
        return VisibleRebind.GUIDE_EVICTION;
    }

    private static boolean validCacheFrame(int raw, int guides, List<String> digests) {
        if (raw < 0 || guides < 0 || guides > raw || digests == null || digests.size() != raw - guides)
            return false;
        for (String digest : digests) if (digest == null || digest.isEmpty()) return false;
        return true;
    }

    static String messageId(String account, String session, String rawId) throws Exception {
        return "m_" + AiContextRules.digest(account + "\n" + session + "\n" + rawId).substring(0, 20);
    }

    static String recallKey(String account, String from, String to, String rawId) throws Exception {
        return AiContextRules.digest(account + "\n" + from + "\n" + to + "\n" + rawId);
    }

    static final class Quote {
        final String rawId, state;
        Quote(String rawId, String state) { this.rawId = rawId; this.state = state; }
        static Quote reference(String id, boolean deleted) {
            if (deleted) return new Quote("", "target_recalled");
            if (id == null || id.isEmpty() || id.equals("null")) return new Quote("", "unresolved");
            return new Quote(id, "pending");
        }
    }

    static Quote readQuote(Object message) {
        try {
            Object quoted = message.getClass().getField("quoteMsg").get(message);
            boolean deleted = Boolean.TRUE.equals(message.getClass().getMethod("getBooleanExt", String.class)
                    .invoke(message, "quoteMsgDeleted"));
            if (quoted == null) return deleted ? Quote.reference(null, true) : null;
            deleted |= Boolean.TRUE.equals(quoted.getClass().getMethod("getBooleanExt", String.class)
                    .invoke(quoted, "quoteMsgDeleted"));
            Object rawId = quoted.getClass().getMethod("getMsgId").invoke(quoted);
            return Quote.reference(rawId instanceof String ? (String) rawId : null, deleted);
        } catch (Exception ignored) { return new Quote("", "unresolved"); }
    }

    static final class Quotes {
        private final String account, session;
        private final Map<String, JSONObject> local = new LinkedHashMap<>();
        private final Map<String, Quote> references = new LinkedHashMap<>();
        Quotes(String account, String session) { this.account = account; this.session = session; }

        // The caller supplies only rows parsed from this scope's database query, never quoteMsg bodies.
        boolean register(String sourceAccount, String sourceSession, String rawId, JSONObject row) throws Exception {
            if (!account.equals(sourceAccount) || !session.equals(sourceSession)
                    || !messageId(account, session, rawId).equals(row.optString("id"))) return false;
            local.put(rawId, row);
            return true;
        }

        void attach(String rowId, Quote quote) { if (quote != null) references.put(rowId, quote); }

        JSONArray enrich(JSONArray messages) throws Exception {
            Set<String> visible = ids(messages);
            Map<String, JSONObject> retained = new LinkedHashMap<>();
            int chars = 0;
            // Most recent references win when the quote-only budget is exhausted.
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject row = messages.getJSONObject(i);
                Quote reference = references.get(row.optString("id"));
                if (reference == null) continue;
                JSONObject quote = new JSONObject().put("status", reference.state);
                row.put("quote", quote);
                if (!"pending".equals(reference.state)) continue;
                JSONObject target = local.get(reference.rawId);
                if (target == null) { quote.put("status", "target_not_available"); continue; }
                String targetId = target.getString("id");
                quote.put("targetId", targetId);
                String blocked = blockedTarget(target);
                if (blocked != null) { quote.put("status", blocked); continue; }
                if (visible.contains(targetId) || retained.containsKey(targetId)) {
                    quote.put("status", "resolved"); continue;
                }
                JSONObject excerpt = excerpt(target);
                int size = excerpt.toString().length();
                if (retained.size() >= MAX_QUOTED_MESSAGES || chars + size > MAX_QUOTED_CHARS) {
                    quote.put("status", "budget_excluded"); continue;
                }
                retained.put(targetId, excerpt); chars += size;
                quote.put("status", "resolved");
            }
            JSONArray quoted = new JSONArray();
            for (JSONObject row : retained.values()) quoted.put(row);
            return quoted;
        }
    }

    private static String blockedTarget(JSONObject row) {
        String kind = row.optString("kind");
        if ("recall".equals(kind) || row.optInt("type", -1) == 9) return "target_recalled";
        if ("withheld".equals(kind)) return "target_withheld";
        if (row.optInt("envelopeType", -1) != 1 || "system".equals(kind) || "unsupported".equals(kind)
                || !("self".equals(row.optString("sender")) || "other".equals(row.optString("sender"))))
            return "target_unsupported";
        if ("self".equals(row.optString("sender")) && !AiMessageStatus.sent(row.optLong("status")))
            return "target_unconfirmed";
        return null;
    }

    private static JSONObject excerpt(JSONObject row) throws Exception {
        JSONObject result = new JSONObject().put("retainedFor", "quoted_message");
        for (String key : new String[]{"id", "sender", "type", "envelopeType", "time", "status", "kind"})
            if (row.has(key)) result.put(key, row.get(key));
        String text = row.optString("text");
        result.put("text", AiContextRules.cleanText(text, 1000));
        if (text.length() > 1000 || row.optBoolean("textTruncated")) result.put("textTruncated", true);
        // No media IDs, embedded quote bodies, post details, URLs, or location fields are copied.
        if (!"text".equals(row.optString("kind")) || row.has("sharedPost"))
            result.put("contentStatus", "not_provided");
        return result;
    }

    /** Call after request selection/truncation so a missing target never remains 'resolved'. */
    static void reconcileQuotes(JSONObject history) throws JSONException {
        JSONArray messages = history.optJSONArray("messages");
        if (messages == null) return;
        Set<String> visible = ids(messages), wanted = new HashSet<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject quote = messages.getJSONObject(i).optJSONObject("quote");
            if (quote != null && "resolved".equals(quote.optString("status"))) wanted.add(quote.optString("targetId"));
        }
        JSONArray previous = history.optJSONArray("quotedMessages"), kept = new JSONArray();
        Set<String> retained = new HashSet<>();
        if (previous != null) for (int i = 0; i < previous.length(); i++) {
            JSONObject target = previous.getJSONObject(i); String id = target.optString("id");
            if (wanted.contains(id) && !visible.contains(id) && blockedTarget(target) == null && retained.add(id)) kept.put(target);
        }
        history.put("quotedMessages", kept);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject quote = messages.getJSONObject(i).optJSONObject("quote");
            if (quote != null && "resolved".equals(quote.optString("status"))
                    && !visible.contains(quote.optString("targetId")) && !retained.contains(quote.optString("targetId")))
                quote.put("status", "budget_excluded");
        }
    }

    private static Set<String> ids(JSONArray rows) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null) result.add(row.optString("id")); }
        return result;
    }

    static JSONObject event(int type, String cls, JSONObject body) throws Exception {
        String event;
        if (type == 5 && cls.equals("cn.soulapp.imlib.msg.chat.AudioMsg")) event = "audio";
        else if (type == 4 && cls.equals("cn.soulapp.imlib.msg.chat.VideoMsg")) event = "video";
        else if (type == 39) event = "call";
        else if (type == 33 && cls.equals("cn.soulapp.imlib.msg.chat.PositionMsg")) event = "location";
        else if (type == 9) event = "recall";
        else if ((type == 12 || type == 13) && cls.equals("cn.soulapp.imlib.msg.chat.DiceFingerMsg"))
            event = type == 12 ? "finger_guess" : "dice";
        else if (type == 15) event = "soulmate_card";
        else if (type == 18) event = "shared_background_invitation";
        else if (type == 35 && cls.equals("cn.soulapp.imlib.msg.chat.JsonMsg") && body != null) {
            String subType = body.optString("messageType");
            switch (subType) {
                case "clapping_head": case "pat_it": case "knead_face_image": case "poke_it":
                case "water_gun": case "cat_paw": case "popup_it": case "HI_EXPRESSION":
                case "gift_notify": case "guard_prop_gift": case "Invate_ChatRoom":
                case "Invite_Group_Chat": case "BIBI_TRIGGER": case "invite_follow":
                case "follow_remind_text_msg": case "share_link":
                    return new JSONObject().put("type", "interaction").put("subType", subType).put("contentStatus", "not_provided");
                default: return null;
            }
        } else return null;
        return new JSONObject().put("type", event).put("contentStatus", "not_provided");
    }

    static String eventText(String type) {
        switch (type) {
            case "audio": return "[语音消息，声音与转写未提供]";
            case "video": return "[视频消息，画面与声音未提供]";
            case "call": return "[通话事件，通话内容与结果未知]";
            case "location": return "[位置消息，坐标与地址未提供]";
            case "recall": return "[撤回事件，原内容未提供]";
            default: return "[互动消息，具体内容未提供]";
        }
    }
}
