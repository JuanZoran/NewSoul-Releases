package dev.local.newsoul;

import org.json.JSONObject;

/** Chronological full-history fixtures; no real conversation data or host calls. */
public final class AiConversationRulesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        AiConversationRules.HistoryState empty = new AiConversationRules.HistoryState();
        state(empty, "opening");
        empty.record(1, 27, "other", 2, "system");
        // Soul 6.35.0 ImMessage.createNotifyReceiveMsg stores NotifyMsg as envelope type 3.
        empty.record(3, -1, "other", 2, "system");
        state(empty, "opening");
        eq(2, empty.json().getInt("systemCount"));
        eq(0, empty.json().getInt("interpersonalCount"));

        AiConversationRules.HistoryState waiting = new AiConversationRules.HistoryState();
        waiting.record(1, 1, "self", 4, "text");
        state(waiting, "awaiting_reply");
        for (int i=0; i<150; i++) waiting.record(3, -1, "other", 2, "system");
        waiting.record(1, 27, "other", 2, "system");
        // An export of only the last 120 rows would hide the sent message, but must not reset it.
        state(waiting, "awaiting_reply");
        eq(1, waiting.json().getInt("selfSentCount"));
        eq(151, waiting.json().getInt("systemCount"));
        waiting.record(1, 1, "other", 2, "text");
        state(waiting, "reply");
        eq(1, waiting.json().getInt("otherCount"));

        for (long status : new long[]{0,1,2,5,-1,99}) {
            AiConversationRules.HistoryState unconfirmed = new AiConversationRules.HistoryState();
            unconfirmed.record(1, 1, "self", status, "text");
            unconfirmed.record(1, 27, "other", 2, "system");
            state(unconfirmed, "outgoing_unconfirmed");
            eq(0, unconfirmed.json().getInt("selfSentCount"));
        }
        for (long delivered : new long[]{3,4}) {
            AiConversationRules.HistoryState state = new AiConversationRules.HistoryState();
            state.record(1, 99, "other", 2, "unsupported");
            state.record(1, 1, "other", 2, "text");
            state.record(1, 1, "self", delivered, "text");
            state(state, "awaiting_reply");
            eq(1, state.json().getInt("selfSentCount"));
            eq(0, state.json().getInt("selfUnconfirmedCount"));
        }
        for (String kind : new String[]{"image", "unsupported", "withheld"}) {
            AiConversationRules.HistoryState media = new AiConversationRules.HistoryState();
            media.record(1, 99, "other", 2, kind);
            state(media, "incoming_unparsed");
            eq(1, media.json().getInt("interpersonalCount"));
            eq(1, media.json().getInt("unparsedInterpersonalCount"));
            media.record(1, 99, "self", 4, kind);
            state(media, "awaiting_reply");
        }

        AiConversationRules.HistoryState sticker = new AiConversationRules.HistoryState();
        sticker.record(1,8,"other",2,"sticker");
        state(sticker,"incoming_sticker");
        sticker.record(1,1,"self",4,"text");
        state(sticker,"awaiting_reply");

        AiConversationRules.HistoryState unknown = new AiConversationRules.HistoryState();
        unknown.record(1, 1, "system_or_unknown", 2, "text");
        state(unknown, "uncertain_history");
        eq(0, unknown.json().getInt("interpersonalCount"));
        unknown.record(3, -1, "other", 2, "system");
        state(unknown, "uncertain_history");
        unknown.record(1, 1, "other", 2, "text");
        state(unknown, "reply");
        unknown.record(4, -1, "other", 2, "unsupported");
        state(unknown, "uncertain_history");
        unknown.record(1, -1, "other", 2, "withheld");
        state(unknown, "uncertain_history");
        eq(3, unknown.json().getInt("unknownCount"));
        eq("complete_local_history_before_export_limits", unknown.json().getString("scope"));
        System.out.println("AiConversationRulesTest: " + checks + " checks passed");
    }
    private static void state(AiConversationRules.HistoryState state,String expected) throws Exception {
        eq(expected,state.json().getString("state"));
    }
    private static void eq(Object expected,Object actual) {
        if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);
        checks++;
    }
}
