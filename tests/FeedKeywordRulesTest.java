package dev.local.newsoul;

public final class FeedKeywordRulesTest {
    private static int count;
    private static void check(boolean value) { count++; if (!value) throw new AssertionError("check " + count); }
    public static void main(String[] args) {
        FeedKeywordRules rules = new FeedKeywordRules("  广告  \r\n兼职\nAD\nad\n\n.*\nC++\n");
        check(rules.words.size() == 5);
        for (String text : new String[]{"这是广告帖", "招兼职", "An AD here", "literal .* pattern", "学习C++"}) check(rules.blocks(text));
        for (String text : new String[]{null, "", "普通帖子", "nothing matches", "C语言"}) check(!rules.blocks(text));
        check(!new FeedKeywordRules("").blocks("任何帖子"));
        check(!new FeedKeywordRules(" \n\r\n").blocks("任何帖子"));
        check(!new FeedKeywordRules(null).blocks("任何帖子"));
        check(new FeedKeywordRules("一 个词").blocks("这是一个 一 个词 测试"));
        check(!new FeedKeywordRules("一 个词").blocks("这是一个词"));
        check(new FeedKeywordRules("emoji😊").blocks("EMOJI😊"));
        check(new FeedKeywordRules("词\u2028话题").blocks("新话题"));
        check(new FeedKeywordRules("x".repeat(100)).words.size()==1);
        reject("x".repeat(101)); reject("x".repeat(12001));
        StringBuilder many = new StringBuilder();
        for(int i=0;i<100;i++) many.append("词").append(i).append('\n');
        check(new FeedKeywordRules(many.toString()).words.size()==100);
        reject(many.append("超限").toString());
        check(new FeedKeywordRules("ad\n".repeat(101)).words.size()==1);
        System.out.println("FeedKeywordRules: " + count + " checks passed");
    }
    private static void reject(String raw) {
        try { new FeedKeywordRules(raw); throw new AssertionError("accepted invalid list"); }
        catch(IllegalArgumentException expected) { count++; }
    }
}
