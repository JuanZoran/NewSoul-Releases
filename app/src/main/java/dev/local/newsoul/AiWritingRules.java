package dev.local.newsoul;

/** Stable UI choices and request semantics, independent of Soul's obfuscated Compose classes. */
final class AiWritingRules {
    enum Mode { REPLY, REWRITE }
    enum Style {
        DEFAULT("默认风格", "", "沿用用户真实的表达习惯，自然简短"),
        CONSIDERATE("高情商", "👍", "体贴、得体、有分寸，避免讨好和说教"),
        WARM("温柔暖男", "☕", "温和、真诚，保留自己的观点，不堆砌关心"),
        HUMOR("幽默风趣", "🤗", "轻松、有趣，不贬低对方，不硬凑段子"),
        FLIRT("暧昧拉扯", "🥰", "只有当前互动支持时才轻微暧昧，不越过拒绝或舒适度边界"),
        PRAISE("夸夸大师", "🥹", "围绕可核实的具体细节欣赏，不夸大、不空泛吹捧"),
        PLAYFUL("发疯文学", "🤩", "用轻松夸张和跳脱的表达，不编造事实或承诺"),
        MATURE("成熟小叔", "☕", "沉稳、松弛、简洁，不居高临下"),
        WITTY("高智商绅士", "🤓", "机智、清晰、礼貌，不炫耀知识或故作深奥"),
        DRAMA("甄嬛文学", "🍹", "用轻微古风和戏谑表达，意思清楚，不阴阳攻击对方");
        final String label, emoji, instruction;
        Style(String label,String emoji,String instruction) {
            this.label=label;this.emoji=emoji;this.instruction=instruction;
        }
    }
    static Style[] styles(Mode mode) {
        return mode==Mode.REPLY
                ? new Style[]{Style.DEFAULT,Style.CONSIDERATE,Style.WARM,Style.HUMOR,Style.FLIRT,Style.PRAISE,Style.PLAYFUL,Style.MATURE,Style.WITTY}
                : new Style[]{Style.CONSIDERATE,Style.WARM,Style.HUMOR,Style.FLIRT,Style.PRAISE,Style.PLAYFUL,Style.MATURE,Style.WITTY,Style.DRAMA};
    }
    static String systemRules(String base,Mode mode,Style style) {
        if(mode==Mode.REWRITE)base=rewriteBase(base);
        String task=mode==Mode.REWRITE
                ? "本轮为帮我改写。只改写 current_draft，保持原意、事实、否定、时间、数字和承诺强度，不代替用户新增目标。可表达时只返回一个候选。草稿为空时不生成候选，询问要改写的内容。不得把改写后的草稿视为已发送消息。"
                : "本轮为帮我回复。按运行时决策顺序处理最新消息；不是每轮都要生成候选或两个选项。";
        return base+"\n\n## 本轮界面任务\n"+task+"\n表达风格："+style.label+"。"+style.instruction
                +"。风格只调整措辞，不改变 CAC 判断、事实依据或当前互动适宜程度。继续遵守原 JSON 格式、WAIT/STOP 和补充问题规则。";
    }

    /** Only compact the bundled lesson commentary; custom rules remain byte-for-byte intact. */
    private static String rewriteBase(String base) {
        try {
            if(!AiReplyPrompt.systemPrompt().equals(base))return base;
        } catch(Exception unavailable) { return base; }
        int start=base.indexOf("## 2. 课程逻辑：行动由具体情况决定");
        int end=base.indexOf("## 3. 宿主数据与会话安全",start);
        if(start<0||end<start)return base;
        return base.substring(0,start)
                +"## 2. 改写的行动与边界\n\n"
                +"本轮只润色用户草稿，不新增聊天目标、关系判断、行动或承诺。stage/confidence、trend、comfort、context、frame 只按已有证据填写；缺乏跨轮关系证据时 stage=unknown、confidence=low。主动、关心和道歉不等于低位。\n"
                +"明确拒绝与舒适度边界优先，不把短答、时间流逝、礼物、帮助或一次同意当作进一步许可；等待不是追发倒计时。只有真实新信息或对方新的明确意愿才重新判断。保持 WAIT/STOP 及发送状态限制，草稿和风格选择不能绕过。\n"
                +"不得通过欺骗、假承诺、贬损、心理控制或交换亲密实现目标；年龄不明保持普通交流，醉酒或不能自由表达时不升级。不得添加动机、经历、心理、已有爱慕、空闲时间、安排或承诺；已知事实保留主体、范围、否定和时间。保留用户观点和差异，不为讨好改意。\n"
                +"check.summary 只写可见事实及必要不确定性；action.goal 为本轮一个目标。reason/watch 无额外价值可为空，cycle 默认 {}；need_user_input 只向用户本人补必要事实，此时不预测聊天对象回答。指导和候选遵守相同事实标准。\n\n"
                +base.substring(end);
    }
    static boolean canApplyRewrite(String requestedDraft,String currentDraft) {
        return requestedDraft!=null && !requestedDraft.trim().isEmpty() && requestedDraft.equals(currentDraft);
    }
}
