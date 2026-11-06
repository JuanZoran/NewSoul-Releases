package dev.local.newsoul;

import org.json.JSONException;
import org.json.JSONObject;

/** Full local-history facts, calculated before export limits; never infers interest or consent. */
final class AiConversationRules {
    private AiConversationRules() { }

    static final class HistoryState {
        private int interpersonal, systems, unknown, unparsed, sent, received, unconfirmed;
        private String latest = "none";
        private String latestKind = "";
        private long latestTime;

        // Call in the same chronological order as the history export, including omitted rows.
        void record(int envelopeType, int chatType, String sender, long status, String kind) {
            record(envelopeType, chatType, sender, status, kind, 0);
        }

        void record(int envelopeType, int chatType, String sender, long status, String kind, long time) {
            if (chatType == 27 || envelopeType == 3) {
                // Soul 6.35.0: chat type 27 is the profile card; envelope type 3 is NotifyMsg.
                systems++;
                return;
            }
            if (envelopeType != 1 || chatType < 0
                    || (!"self".equals(sender) && !"other".equals(sender))) {
                latestTime = time;
                unknown++;
                latest = "unknown";
                latestKind = "unsupported";
                return;
            }
            latestTime = time;
            interpersonal++;
            latestKind = kind;
            if (!"text".equals(kind)) unparsed++;
            if ("self".equals(sender)) {
                if (AiMessageStatus.sent(status)) { sent++; latest = "self_sent"; }
                else { unconfirmed++; latest = "self_unconfirmed"; }
            } else {
                received++;
                latest = "other";
            }
        }

        JSONObject json() throws JSONException {
            String state;
            if (interpersonal == 0 && unknown == 0) state = "opening";
            else if ("unknown".equals(latest)) state = "uncertain_history";
            else if ("self_sent".equals(latest)) state = "awaiting_reply";
            else if ("self_unconfirmed".equals(latest)) state = "outgoing_unconfirmed";
            else if ("other".equals(latest)) state = "text".equals(latestKind) ? "reply" : "sticker".equals(latestKind) ? "incoming_sticker" : "incoming_unparsed";
            else state = "uncertain_history";
            return new JSONObject().put("state", state).put("scope", "complete_local_history_before_export_limits")
                    .put("interpersonalCount", interpersonal).put("systemCount", systems)
                    .put("unknownCount", unknown).put("unparsedInterpersonalCount", unparsed)
                    .put("selfSentCount", sent).put("otherCount", received)
                    .put("selfUnconfirmedCount", unconfirmed).put("latestEvent", latest)
                    .put("latestKind", latestKind).put("latestTime", latestTime);
        }
    }
}
