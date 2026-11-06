package dev.local.newsoul;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONTokener;
import org.json.JSONObject;

/** Full local timeline reads. Raw database counts also include hidden reaction rows. */
final class LocalChatHistory {
    interface Source {
        long count() throws Exception;
        List<?> allVisible() throws Exception;
        String id(Object message) throws Exception;
        // Null means equivalence is unavailable; never collapse on ID alone.
        default String content(Object message) throws Exception { return null; }
        default int type(Object message) throws Exception { return -1; }
    }

    static final class Failure extends Exception {
        final String code, counts;
        Failure(String code) { this(code, ""); }
        Failure(String code, String counts) { super(code); this.code = code; this.counts = counts; }
    }

    static final class Result {
        final List<?> messages;
        final long rawBefore, rawAfter;
        final int queriedCount, duplicateRows;
        private final Source source;
        private final Rows rows;

        Result(Source source, Rows rows, long before, long after) {
            this.source = source; this.messages = Collections.unmodifiableList(rows.messages);
            this.rows = rows; rawBefore = before; rawAfter = after;
            queriedCount = rows.queriedCount; duplicateRows = queriedCount - messages.size();
        }

        void verify(int exported) throws Exception {
            if (exported != messages.size()) throw new Failure("export_count", "visible=" + messages.size() + "; exported=" + exported);
            // Verify after consumers finish serializing: same-size replacement and timeline
            // visibility changes must also invalidate the read, even when raw counts agree.
            long before = source.count();
            checkTotals(rawAfter, before, queriedCount);
            Rows fresh = normalize(source, source.allVisible());
            checkTotals(before, source.count(), fresh.queriedCount);
            if (!rows.multiplicity.equals(fresh.multiplicity) || !rows.duplicateDigests.equals(fresh.duplicateDigests))
                throw new Failure("visible_changed", "visible_before=" + queriedCount + "; visible_after=" + fresh.queriedCount);
        }

        String facts() {
            return "raw_before=" + rawBefore + "; raw_after=" + rawAfter
                    + "; visible=" + queriedCount + "; unique=" + messages.size()
                    + "; duplicate_rows=" + duplicateRows + "; duplicate_groups=" + rows.duplicateDigests.size()
                    + "; filtered=" + (rawAfter - queriedCount);
        }
    }

    static Result read(ClassLoader loader, String sessionId) throws Exception {
        Class<?> type = loader.loadClass("cn.soulapp.imlib.database.ChatDbManager");
        Object db = type.getMethod("z").invoke(null);
        Method count = type.getMethod("A", int.class, String.class);
        Method all = type.getMethod("Z", int.class, String.class, String.class, long.class,
                int.class, int.class, List.class, boolean.class);
        return read(new Source() {
            Object gson;
            Method toJson;
            public long count() throws Exception { return ((Number) count.invoke(db, 0, sessionId)).longValue(); }
            public List<?> allVisible() throws Exception {
                // Verified host contract: no cursor and limit=-1 reads the entire visible
                // timeline on both Room and ObjectBox; it never requests cloud roaming.
                return (List<?>) all.invoke(db, 0, sessionId, null, 0L, -1, 2, null, false);
            }
            public String id(Object message) throws Exception {
                Object value = message.getClass().getMethod("getMsgId").invoke(message);
                return value instanceof String ? (String) value : null;
            }
            public String content(Object message) throws Exception {
                if (gson == null) {
                    Class<?> cls = loader.loadClass("com.google.gson.Gson");
                    gson = cls.getConstructor().newInstance(); toJson = cls.getMethod("toJson", Object.class);
                }
                // Compare the full host record, including timestamps, status, participants,
                // quote and media payloads. The canonical text is never logged or exported.
                Object parsed = new JSONTokener((String) toJson.invoke(gson, message)).nextValue();
                if (!(parsed instanceof JSONObject)) throw new IllegalStateException("Expected a full message object");
                return AiContextRules.canonical(parsed);
            }
            public int type(Object message) throws Exception {
                Object chat = message.getClass().getMethod("getChatMessage").invoke(message);
                return chat == null ? -1 : ((Number) chat.getClass().getMethod("getMsgType").invoke(chat)).intValue();
            }
        });
    }

    static Result read(Source source) throws Exception {
        long before = source.count();
        Rows rows = normalize(source, source.allVisible());
        long after = source.count();
        checkTotals(before, after, rows.queriedCount);
        return new Result(source, rows, before, after);
    }

    private static void checkTotals(long before, long after, int visible) throws Failure {
        String counts = "raw_before=" + before + "; raw_after=" + after + "; visible=" + visible;
        if (before < 0 || after < 0) throw new Failure("invalid_count", counts);
        if (before != after) throw new Failure("raw_changed", counts);
        if (visible > after) throw new Failure("visible_exceeds_raw", counts);
        // ChatDbManager.Z may turn a null backend query into an empty list. Without
        // any visible row, positive raw counts cannot establish a successful read.
        if (visible == 0 && after > 0) throw new Failure("empty_with_raw", counts);
    }

    static String failureFacts(Throwable error) {
        return error instanceof Failure ? "reason=" + ((Failure) error).code + "; " + ((Failure) error).counts
                : "reason=host_or_parse";
    }

    private static final class Rows {
        final int queriedCount;
        final List<Object> messages = new ArrayList<>();
        final Map<String, Integer> multiplicity = new LinkedHashMap<>();
        final Map<String, String> duplicateDigests = new LinkedHashMap<>();
        Rows(int queriedCount) { this.queriedCount = queriedCount; }
    }

    private static Rows normalize(Source source, List<?> messages) throws Exception {
        if (messages == null) throw new Failure("null_history");
        Rows result = new Rows(messages.size());
        Map<String, Object> first = new LinkedHashMap<>();
        for (Object message : messages) {
            if (message == null) throw new Failure("null_message");
            String id = source.id(message);
            if (id == null || id.isEmpty() || "null".equals(id)) throw new Failure("invalid_id");
            Object previous = first.get(id);
            if (previous == null) {
                first.put(id, message); result.messages.add(message); result.multiplicity.put(id, 1);
                continue;
            }
            String counts = "queried=" + messages.size() + "; unique_so_far=" + first.size()
                    + "; first_type=" + messageType(source, previous) + "; next_type=" + messageType(source, message);
            String original = result.duplicateDigests.get(id);
            if (original == null) original = contentDigest(source, previous, counts);
            if (!original.equals(contentDigest(source, message, counts)))
                throw new Failure("duplicate_id_conflict", counts);
            result.duplicateDigests.put(id, original);
            result.multiplicity.put(id, result.multiplicity.get(id) + 1);
        }
        return result;
    }

    private static String contentDigest(Source source, Object message, String counts) throws Exception {
        String content;
        try { content = source.content(message); }
        catch (Exception error) {
            Failure failure = new Failure("duplicate_compare_failed", counts);
            failure.initCause(error); throw failure;
        }
        if (content == null) throw new Failure("duplicate_id", counts);
        return AiContextRules.digest(content);
    }

    private static int messageType(Source source, Object message) {
        try { return source.type(message); } catch (Exception ignored) { return -1; }
    }

    private LocalChatHistory() {}
}
