package cn.soulapp.imlib.database;

import java.util.List;

/** Enforces the full, local-only host query contract rather than accepting any arguments. */
public final class ChatDbManager {
    public static int queries;
    public static ChatDbManager z() { return new ChatDbManager(); }
    public long A(int type, String session) {
        if (type != 0 || !"local-session".equals(session)) throw new AssertionError("Wrong count scope");
        return 2; // One visible message and one hidden emoji reaction.
    }
    public List<?> Z(int type, String session, String cursor, long time, int limit,
            int direction, List<?> types, boolean reverse) {
        if (type != 0 || !"local-session".equals(session) || cursor != null || time != 0L
                || limit != -1 || direction != 2 || types != null || reverse)
            throw new AssertionError("Expected unlimited local timeline query");
        queries++;
        return List.of(new Message());
    }
    public static final class Message {
        public String getMsgId() { return "visible"; }
    }
}
