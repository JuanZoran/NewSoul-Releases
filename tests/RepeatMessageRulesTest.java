package dev.local.newsoul;

public final class RepeatMessageRulesTest {
    private static int count;
    private static void check(boolean value) {
        count++;
        if (!value) throw new AssertionError("check " + count);
    }
    public static void main(String[] args) {
        for (int type : new int[]{1, 2, 3, 5}) {
            check(RepeatMessageRules.ordinary(1, type, 0, null, false));
            check(!RepeatMessageRules.ordinary(1, type, 1, null, false));
            check(!RepeatMessageRules.ordinary(1, type, 0, "1", false));
            check(!RepeatMessageRules.ordinary(10, type, 0, null, false));
            check(!RepeatMessageRules.ordinary(1, type, 0, null, true));
        }
        for (int type : new int[]{0, 4, 8, 12, 13, 27, 35})
            check(!RepeatMessageRules.ordinary(1, type, 0, null, false));
        check(RepeatMessageRules.forwardable(1, 4, 0, null, false));
        check(!RepeatMessageRules.forwardable(1, 4, 1, null, false));
        check(!RepeatMessageRules.forwardable(1, 4, 0, "1", false));
        check(!RepeatMessageRules.forwardable(1, 4, 0, null, true));
        check(!RepeatMessageRules.forwardable(10, 4, 0, null, false));
        check(!RepeatMessageRules.forwardable(1, 32, 0, null, false));
        check(RepeatMessageRules.sameChat("me", "you", "me", "you"));
        check(RepeatMessageRules.sameChat("me", "you", "you", "me"));
        check(!RepeatMessageRules.sameChat("me", "other", "you", "me"));
        check(!RepeatMessageRules.sameChat("other", "you", "you", "me"));
        check(!RepeatMessageRules.sameChat("", "you", "you", ""));
        check(!RepeatMessageRules.sameChat(null, "you", "you", null));
        check(RepeatMessageRules.mediaUrl("https://image.example/a.jpg?x=1"));
        check(RepeatMessageRules.mediaUrl("http://audio.example/a.mp3"));
        for (String value : new String[]{null, "", "/sdcard/a.jpg", "file:///a", "content://media/a", "https:///a", "https://user:pass@example.com/a", "https://example.com/a b"})
            check(!RepeatMessageRules.mediaUrl(value));
        System.out.println("RepeatMessageRules: " + count + " checks passed");
    }
}
