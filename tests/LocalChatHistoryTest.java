package dev.local.newsoul;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

public final class LocalChatHistoryTest {
    static class Source implements LocalChatHistory.Source {
        long total;
        List<?> visible;
        Source(long total, String... ids) { this.total = total; visible = new ArrayList<>(Arrays.asList(ids)); }
        public long count() { return total; }
        public List<?> allVisible() { return visible; }
        public String id(Object message) { return (String) message; }
    }
    static final class JsonSource extends Source {
        JsonSource(JSONObject... rows) { super(rows.length); visible = Arrays.asList(rows); }
        @Override public String id(Object message) { return ((JSONObject) message).optString("msgId"); }
        @Override public String content(Object message) throws Exception { return AiContextRules.canonical(message); }
        @Override public int type(Object message) { return ((JSONObject) message).optInt("type", -1); }
    }
    interface Action { void run() throws Exception; }
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError(); }
    static void fails(String reason, Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + reason); }
        catch (LocalChatHistory.Failure error) { check(reason.equals(error.code)); }
    }

    public static void main(String[] args) throws Exception {
        LocalChatHistory.read(new Source(0)).verify(0);
        LocalChatHistory.read(new Source(2, "a", "b")).verify(2);
        Source reaction = new Source(3, "a", "b");
        LocalChatHistory.Result result = LocalChatHistory.read(reaction);
        result.verify(2);
        check(result.facts().contains("filtered=1"));
        // A swallowed backend failure and an all-hidden timeline both look empty.
        // Preserve failure rather than exporting a fabricated empty conversation.
        fails("empty_with_raw", () -> LocalChatHistory.read(new Source(2)));
        fails("invalid_count", () -> LocalChatHistory.read(new Source(-1)));
        fails("visible_exceeds_raw", () -> LocalChatHistory.read(new Source(0, "a")));
        fails("duplicate_id", () -> LocalChatHistory.read(new Source(2, "a", "a")));
        fails("invalid_id", () -> LocalChatHistory.read(new Source(1, "")));
        fails("invalid_id", () -> LocalChatHistory.read(new Source(1, "null")));
        Source nulls = new Source(1); nulls.visible = Arrays.asList((Object) null);
        fails("null_message", () -> LocalChatHistory.read(nulls));
        nulls.visible = null;
        fails("null_history", () -> LocalChatHistory.read(nulls));
        fails("export_count", () -> result.verify(1));
        reaction.total++;
        fails("raw_changed", () -> result.verify(2));
        reaction.total -= 2;
        fails("raw_changed", () -> result.verify(2));
        reaction.total = 3;
        reaction.visible = Arrays.asList("a", "c");
        fails("visible_changed", () -> result.verify(2));
        reaction.visible = Arrays.asList("b", "a");
        result.verify(2); // Host ordering changes do not change the set of visible records.
        check("a".equals(result.messages.get(0))); // Caller owns a stable copy of the first result.
        reaction.visible = Arrays.asList("a");
        fails("visible_changed", () -> result.verify(2));
        reaction.visible = Arrays.asList("a", "b", "c");
        fails("visible_changed", () -> result.verify(2));
        Source duringRead = new Source(1, "a") {
            @Override public List<?> allVisible() { total++; return visible; }
        };
        fails("raw_changed", () -> LocalChatHistory.read(duringRead));
        Source duringVerify = new Source(1, "a");
        LocalChatHistory.Result pending = LocalChatHistory.read(duringVerify);
        duringVerify.visible = new java.util.AbstractList<Object>() {
            public int size() { return 1; }
            public Object get(int i) { duringVerify.total++; return "a"; }
        };
        fails("raw_changed", () -> pending.verify(1));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) many.add("id" + i);
        Source longHistory = new Source(251); longHistory.visible = many;
        LocalChatHistory.read(longHistory).verify(250);
        check(LocalChatHistory.failureFacts(new IllegalStateException("private chat text"))
                .equals("reason=host_or_parse"));
        check(!LocalChatHistory.failureFacts(new LocalChatHistory.Failure("duplicate_id"))
                .contains("private"));
        LocalChatHistory.Result reflected = LocalChatHistory.read(LocalChatHistoryTest.class.getClassLoader(), "local-session");
        reflected.verify(1);
        check(reflected.rawBefore == 2 && reflected.messages.size() == 1);
        check(cn.soulapp.imlib.database.ChatDbManager.queries == 2);
        JSONObject first = new JSONObject().put("msgId", "private-message-id").put("from", "self")
                .put("to", "peer").put("type", 1).put("localTime", 100).put("serverTime", 200)
                .put("status", 4).put("body", new JSONObject().put("text", "private message text"));
        JSONObject copy = new JSONObject(first.toString());
        JsonSource duplicated = new JsonSource(first, copy);
        LocalChatHistory.Result collapsed = LocalChatHistory.read(duplicated);
        check(collapsed.messages.size() == 1 && collapsed.queriedCount == 2 && collapsed.duplicateRows == 1);
        check(collapsed.facts().contains("filtered=0") && collapsed.facts().contains("duplicate_rows=1"));
        collapsed.verify(1);
        check(!collapsed.facts().contains("private"));
        fails("export_count", () -> collapsed.verify(2));
        // Timestamp, participants, status, quote and media are part of full-record identity.
        for (String key : Arrays.asList("from", "to", "localTime", "serverTime", "status", "body", "quote", "media")) {
            JSONObject changed = new JSONObject(first.toString()).put(key, "different");
            fails("duplicate_id_conflict", () -> LocalChatHistory.read(new JsonSource(first, changed)));
        }
        try {
            LocalChatHistory.read(new JsonSource(first, new JSONObject(first.toString()).put("type", 27)));
            throw new AssertionError("Conflicting types accepted");
        } catch (LocalChatHistory.Failure error) {
            String facts = LocalChatHistory.failureFacts(error);
            check(facts.contains("first_type=1") && facts.contains("next_type=27"));
            check(!facts.contains("private-message-id") && !facts.contains("private message text"));
        }
        // Equal keys/values in a different insertion order are an exact copy.
        JSONObject reordered = new JSONObject().put("body", new JSONObject().put("text", "private message text"))
                .put("status", 4).put("serverTime", 200).put("localTime", 100).put("type", 1)
                .put("to", "peer").put("from", "self").put("msgId", "private-message-id");
        duplicated.visible = Arrays.asList(reordered, copy);
        collapsed.verify(1);
        // Same raw count but different duplicate multiplicity is still a change.
        JSONObject another = new JSONObject(first.toString()).put("msgId", "another");
        duplicated.visible = Arrays.asList(first, another);
        fails("visible_changed", () -> collapsed.verify(1));
        // Replacing both copies with a different body must also invalidate the snapshot.
        JSONObject changed = new JSONObject(first.toString()).put("body", new JSONObject().put("text", "changed"));
        duplicated.visible = Arrays.asList(changed, new JSONObject(changed.toString()));
        fails("visible_changed", () -> collapsed.verify(1));
        JsonSource tripled = new JsonSource(first, copy, new JSONObject(first.toString()));
        LocalChatHistory.Result three = LocalChatHistory.read(tripled);
        three.verify(1); check(three.duplicateRows == 2 && three.queriedCount == 3);
        Source unreadable = new Source(2, "a", "a") {
            @Override public String content(Object message) { throw new IllegalStateException("private text"); }
        };
        fails("duplicate_compare_failed", () -> LocalChatHistory.read(unreadable));
        System.out.println("LocalChatHistory: " + checks + " checks passed");
    }
}
