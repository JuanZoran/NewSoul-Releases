package dev.local.newsoul;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** One formatter and validation policy for the editor, imports and live messages. */
final class ChatTimeRules {
    static final String DEFAULT = "yyyy-MM-dd HH:mm:ss";
    static final int MAX_PATTERN = 64;
    static String format(String pattern, long millis, Locale locale, TimeZone zone) {
        if (pattern == null || pattern.trim().isEmpty() || pattern.length() > MAX_PATTERN)
            throw new IllegalArgumentException("请输入 1–64 个字符的时间格式");
        boolean quoted = false, field = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (Character.isISOControl(c)) throw new IllegalArgumentException("时间格式不能包含换行或控制字符");
            if (c == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') { i++; continue; }
                quoted = !quoted;
            } else if (!quoted && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                if ("yMdEHhmsazZ".indexOf(c) < 0) throw new IllegalArgumentException("不支持格式字符：" + c);
                field = true;
            }
        }
        if (quoted) throw new IllegalArgumentException("单引号需要成对使用");
        if (!field) throw new IllegalArgumentException("请至少包含一种日期或时间符号");
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
        formatter.setTimeZone(zone);
        String result = formatter.format(new Date(millis));
        if (result.length() > 96) throw new IllegalArgumentException("显示结果过长，请缩短格式");
        return result;
    }
    static String format(String pattern, long millis) {
        return format(pattern, millis, Locale.getDefault(), TimeZone.getDefault());
    }
    static boolean valid(String pattern) {
        try { format(pattern, 1791000000000L); return true; }
        catch (IllegalArgumentException error) { return false; }
    }
}
