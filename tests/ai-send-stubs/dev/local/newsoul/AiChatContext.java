package dev.local.newsoul;
import android.app.Activity;
final class AiChatContext {
    static boolean same = true, visible = true;
    static Object cache;
    static class Snapshot { String account="self", target="encrypted-peer"; }
    static class Identity { Object conversation=cache; }
    static boolean sameConversation(Activity a, Snapshot s) { return same; }
    static boolean sameVisibleConversation(Activity a, Snapshot s) { return same && visible; }
    static Identity currentIdentity(Activity a) { return new Identity(); }
}
