package dev.local.newsoul;
public final class ChatRecallRulesTest {
    public static void main(String[] args) {
        check(ChatRecallRules.incoming("me", "friend", "me"), "incoming private");
        check(!ChatRecallRules.incoming("me", "me", "friend"), "own recall passes");
        check(!ChatRecallRules.incoming("otherAccount", "friend", "me"), "account isolation");
        check(!ChatRecallRules.incoming("me", "friend", "group"), "group destination excluded");
        check(!ChatRecallRules.incoming("", "friend", ""), "logged out");
        check(!ChatRecallRules.incoming(null, "friend", "me"), "missing account");
        check(!ChatRecallRules.incoming("me", null, "me"), "missing sender");
        check(!ChatRecallRules.incoming("me", "", "me"), "empty sender");
        check(!ChatRecallRules.incoming("me", "friend", null), "missing destination");
        System.out.println("ChatRecallRules: 9 checks passed");
    }
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
