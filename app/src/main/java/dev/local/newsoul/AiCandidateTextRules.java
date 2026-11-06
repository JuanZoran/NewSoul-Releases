package dev.local.newsoul;

import java.util.regex.Pattern;
import org.json.JSONException;

/** Reject recognizable output leaks without silently rewriting text or banning ordinary topics. */
final class AiCandidateTextRules {
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>\"“”，。！？；]+");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])(?:[a-z]:\\\\|\\\\\\\\[^\\\\\\s]+\\\\)[^\\s，。！？；\"“”<>]+");
    private static final Pattern HOME_PATH = Pattern.compile("(?<!\\S)~/[^\\s，。！？；\"“”<>]+");
    private static final Pattern NUMBER_RANGE = Pattern.compile("[0-9]+(?:\\.[0-9]+)?\\s*[~～]\\s*[0-9]+(?:\\.[0-9]+)?");
    // Preserve explicit discussion of literal symbols / escaped code, not quoted Chinese prose.
    private static final Pattern SYMBOL_LITERAL = Pattern.compile(
            "[\"“‘'`][A-Za-z0-9~～\\\\.*+?^${}()\\[\\]/:_= -]{1,80}[\"”’'`]");
    private static final Pattern ASSISTANT_PREFIX = Pattern.compile(
            "^(?:推荐回复|建议回复|可直接发送|你可以这样回复|建议你这样(?:回复|说))[：:]");
    private static final Pattern REQUEST_CONTEXT = Pattern.compile(
            "^(?:请(?:先)?|麻烦(?:先)?)(?:提供|补充|上传|粘贴)(?:一下|一些|最近|当前|你们的|双方的|相关|完整|更多|的|\\s){0,8}(?:聊天记录|聊天截图|对方资料)");
    private static final Pattern MATCH_SYNC_QUESTION = Pattern.compile(
            "^(?:请问[，,\\s]*)?(?:你|您)(?:和|与)对[方放][\\s\\S]{0,100}(?:匹配状态|未同步|尚未同步)[\\s\\S]{0,100}[?？]");

    static void validate(String text) throws JSONException {
        if (ASSISTANT_PREFIX.matcher(text).find() || REQUEST_CONTEXT.matcher(text).find()
                || MATCH_SYNC_QUESTION.matcher(text).find())
            throw new JSONException("候选含助手补问或说明，请重新生成");
        // Keep links, ranges, literal paths and semantic paragraph boundaries intact.
        // Punctuation is a generation style choice, never mechanically removed here.
        String prose = URL.matcher(text).replaceAll(" ");
        prose = WINDOWS_PATH.matcher(prose).replaceAll(" ");
        prose = HOME_PATH.matcher(prose).replaceAll(" ");
        prose = NUMBER_RANGE.matcher(prose).replaceAll(" ");
        prose = SYMBOL_LITERAL.matcher(prose).replaceAll(" ");
        if (prose.indexOf('~') >= 0 || prose.indexOf('～') >= 0 || prose.indexOf('\\') >= 0)
            throw new JSONException("候选含装饰符号或转义残留，请重新生成");
    }
}
