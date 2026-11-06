package dev.local.newsoul;
import java.util.List;
public final class AiSendRulesTest {
    public static void main(String[] args) {
        check("找人聊天吗", List.of("找人聊天吗"));
        check("这条街挺舒服\n你拍的是哪一段", List.of("这条街挺舒服", "你拍的是哪一段"));
        check("第一段\r\n\r\n第二段\r第三段", List.of("第一段", "第二段", "第三段"));
        check("  好呀\n \n 明天见  ", List.of("好呀", "明天见"));
        check("周六，还是周日？", List.of("周六，还是周日？"));
        check("https://example.com/a/b", List.of("https://example.com/a/b"));
        check("这条街看起来挺舒服的。你拍的是哪一段？", List.of("这条街看起来挺舒服的。","你拍的是哪一段？"));
        check("我也会在书店待很久，看到喜欢的书就走不动，你通常看哪类？", List.of("我也会在书店待很久，看到喜欢的书就走不动，你通常看哪类？"));
        check("如果周六下雨，就改天再去",List.of("如果周六下雨，就改天再去"));
        check("好呀！明天见",List.of("好呀！明天见"));
        check("好久没来这条街了。这里变化真的挺大。你最近常来这边吗？",List.of("好久没来这条街了。","这里变化真的挺大。","你最近常来这边吗？"));
        check("第一件事情说完了。第二件事情也说完了。第三件事情说完了。第四件事情先留在这段。",List.of("第一件事情说完了。","第二件事情也说完了。","第三件事情说完了。第四件事情先留在这段。"));
        check("票价是3.14元，12:30见。到了再联系我",List.of("票价是3.14元，12:30见。","到了再联系我"));
        check("看看https://example.com/a?x=1.2&y=wow!再决定",List.of("看看https://example.com/a?x=1.2&y=wow!再决定"));
        check("我的邮箱是a.b@example.com。发过来就好了",List.of("我的邮箱是a.b@example.com。","发过来就好了"));
        check("她说“这条街变了。你认得吗？”我还没回答",List.of("她说“这条街变了。你认得吗？”我还没回答"));
        check("这本书叫《今天没有下雨。我们一起出门吧》",List.of("这本书叫《今天没有下雨。我们一起出门吧》"));
        check("她说 'What a day! Maybe tomorrow'",List.of("她说 'What a day! Maybe tomorrow'"));
        check("先说一句（别着急。慢慢想）再决定",List.of("先说一句（别着急。慢慢想）再决定"));
        check("这也太好笑了吧！👨‍👩‍👧‍👦你怎么想到的",List.of("这也太好笑了吧！👨‍👩‍👧‍👦你怎么想到的"));
        check("这条街挺舒服的！？你拍的是哪一段",List.of("这条街挺舒服的！？","你拍的是哪一段"));
        check("这条街挺舒服的\\n你拍的是哪一段",List.of("这条街挺舒服的\\n你拍的是哪一段"));
        if(!AiSendRules.explicitParts("这条街看起来挺舒服的。你拍的是哪一段？\n第二张卡片").equals(List.of("这条街看起来挺舒服的。你拍的是哪一段？","第二张卡片")))throw new AssertionError("Edited card regrouping must be preserved");
        check("好".repeat(500), List.of("好".repeat(500)));
        for(String invalid : new String[]{null,"","\n \r\n","好".repeat(501)}) {
            try { AiSendRules.parts(invalid); throw new AssertionError("accepted invalid input"); }
            catch(IllegalArgumentException expected) { }
        }
        System.out.println("AiSendRulesTest: 28 checks passed");
    }
    private static void check(String input,List<String> expected) {
        if(!expected.equals(AiSendRules.parts(input)))throw new AssertionError("Wrong segments");
    }
}
