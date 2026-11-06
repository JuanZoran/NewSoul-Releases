package dev.local.newsoul;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Request-time facts only. Raw host timestamps and frozen snapshot identity stay intact. */
final class AiTimingRules {
    private AiTimingRules() { }

    static void enrich(JSONObject context, long now) throws JSONException {
        TimeZone zone = TimeZone.getDefault();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
        format.setTimeZone(zone);
        JSONObject timing = new JSONObject().put("requestedAt", now)
                .put("requestedAtText", format.format(new Date(now))).put("timeZone", zone.getID())
                .put("durationUnit", "milliseconds");
        context.put("timing", timing);
        JSONObject history = context.optJSONObject("history");
        if (history == null) return;
        JSONArray messages = history.optJSONArray("messages");
        JSONObject previous = null;
        long previousTime = 0;
        if (messages != null) for (int i = 0; i < messages.length(); i++) {
            JSONObject row = messages.optJSONObject(i);
            if (row == null) continue;
            long time = timestamp(row.optLong("time"), now);
            JSONObject facts = new JSONObject().put("status", time > 0 ? "ok" : "unknown_or_future");
            row.put("timing", facts);
            if (time > 0) facts.put("sentAt", time).put("sentAtText", format.format(new Date(time)))
                    .put("elapsedMs", now - time);
            // System notices never reset the interpersonal clock. Unknown events do.
            String sender = row.optString("sender");
            if ("system".equals(sender)) continue;
            boolean interpersonal = row.optInt("envelopeType", -1) == 1 && row.optInt("type", -1) >= 0
                    && ("self".equals(sender) || "other".equals(sender));
            if (!interpersonal) { previous = null; previousTime = 0; continue; }
            // Sending/failed outgoing rows are not a successful conversational turn.
            if ("self".equals(sender) && !AiMessageStatus.sent(row.optLong("status"))) {
                previous = null; previousTime = 0; continue;
            }
            if (previous != null && previousTime > 0 && time >= previousTime) {
                facts.put("gapFromPreviousInterpersonalMs", time - previousTime)
                        .put("previousMessageId", previous.optString("id"));
                if (!sender.equals(previous.optString("sender")))
                    facts.put("gapFromOppositeMessageMs", time - previousTime);
            }
            previous = row; previousTime = time;
        }
        JSONObject interaction = history.optJSONObject("interaction");
        if (interaction == null) return;
        long latest = timestamp(interaction.optLong("latestTime"), now);
        timing.put("latestInterpersonalTimeStatus", latest > 0 ? "ok" : "unknown_or_future");
        if (latest == 0) return;
        String state = interaction.optString("state");
        if ("uncertain_history".equals(state) || "opening".equals(state)) return;
        timing.put("latestInterpersonalAtText", format.format(new Date(latest)))
                .put("latestInterpersonalElapsedMs", now - latest);
        if ("awaiting_reply".equals(state)) timing.put("waitingFor", "other")
                .put("unansweredSinceLatestMs", now - latest);
        else if ("reply".equals(state) || "incoming_sticker".equals(state) || "incoming_unparsed".equals(state))
            timing.put("waitingFor", "self").put("unansweredSinceLatestMs", now - latest);
    }

    static long timestamp(long raw, long now) {
        // Accept modern Unix seconds or milliseconds; placeholders/invalid clocks remain unknown.
        long time = raw >= 946684800L && raw < 100000000000L ? raw * 1000L : raw;
        return time >= 946684800000L && time <= now ? time : 0;
    }
}
