package dev.local.newsoul;
public final class SettingsSearchRulesTest {
    static int count;
    static void check(boolean value,String message) {count++; if(!value)throw new AssertionError(message);}
    static void matches(String query,String title,String path) {
        check(SettingsSearchRules.score(query,title,path)>=0,"expected match: "+query);
    }
    static void rejects(String query,String title,String path) {
        check(SettingsSearchRules.score(query,title,path)<0,"unexpected match: "+query);
    }
    public static void main(String[] args) {
        rejects("", "时间", "聊天");
        rejects("  ", "时间", "聊天");
        rejects(null,"时间","聊天");
        rejects("广场 撤回", "聊天防撤回", "聊天 · 消息撤回");
        rejects("[", "时间", "聊天");
        matches("广告", "启动广告", "广告 · 启动与浮层");
        matches(" soul ", "SOUL", "其他");
        matches("配置", "允许迁移", "其他 · 配置迁移");
        matches("聊天\n 时间","自定义时间格式","聊天 · 消息时间");
        matches("时间 聊天","自定义时间格式","聊天 · 消息时间");
        // Ranking is the contract; the numeric score encoding may change.
        int exact=SettingsSearchRules.score("撤回","撤回","其他");
        int title=SettingsSearchRules.score("撤回","聊天防撤回","其他");
        int path=SettingsSearchRules.score("撤回","允许迁移","聊天 · 撤回");
        check(exact>=0&&title>=0&&path>=0,"all ranked entries match");
        check(exact<title&&title<path,"exact title precedes title substring, then path-only match");
        check(SettingsSearchRules.score(" SOUL ","soul","其他")
                ==SettingsSearchRules.score("soul","SOUL","新增分类"),"case and unrelated path preserve exact-match rank");
        System.out.println("SettingsSearchRules: "+count+" checks passed");
    }
}
