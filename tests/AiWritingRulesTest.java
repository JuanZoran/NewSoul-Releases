package dev.local.newsoul;

import java.util.Arrays;
import java.util.HashSet;

public final class AiWritingRulesTest {
    public static void main(String[] args) {
        for (AiWritingRules.Mode mode : AiWritingRules.Mode.values()) {
            AiWritingRules.Style[] styles=AiWritingRules.styles(mode);
            require(styles.length==9 && new HashSet<>(Arrays.asList(styles)).size()==9,"unique nine styles");
            for (AiWritingRules.Style style:styles) {
                String prompt=AiWritingRules.systemRules("BASE_CONTRACT",mode,style);
                require(prompt.startsWith("BASE_CONTRACT\n"),"preserve original contract");
                require(prompt.contains(style.label)&&prompt.contains(style.instruction),"selected style delivered");
                require(prompt.contains("WAIT/STOP")&&prompt.contains("CAC"),"retain analysis constraints");
                if(mode==AiWritingRules.Mode.REWRITE)
                    require(prompt.contains("只改写 current_draft")&&prompt.contains("只返回一个候选")&&prompt.contains("否定、时间、数字")&&prompt.contains("不得把改写后的草稿视为已发送"),"rewrite boundaries");
            }
        }
        require(AiWritingRules.styles(AiWritingRules.Mode.REPLY)[0]==AiWritingRules.Style.DEFAULT,"default reply");
        require(!Arrays.asList(AiWritingRules.styles(AiWritingRules.Mode.REWRITE)).contains(AiWritingRules.Style.DEFAULT),"rewrite styles");
        require(AiWritingRules.canApplyRewrite("明天不行\n周六可以", "明天不行\n周六可以"),"unchanged draft accepted");
        require(!AiWritingRules.canApplyRewrite("明天不行", "明天可以"),"new edit preserved");
        require(!AiWritingRules.canApplyRewrite("原话", ""),"cleared draft preserved");
        require(!AiWritingRules.canApplyRewrite("原话", "原话 "),"even whitespace edit preserved");
        require(!AiWritingRules.canApplyRewrite(null,null),"missing draft rejected");
        require(!AiWritingRules.canApplyRewrite(" \n", " \n"),"empty draft rejected");
        System.out.println("AiWritingRulesTest: reply/rewrite style contracts and stale draft protection passed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
