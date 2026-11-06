package dev.local.newsoul;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Literal substring matching, never regular expressions. */
final class FeedKeywordRules {
    final List<String> words;
    FeedKeywordRules(String text) {
        if (text == null) text = "";
        if (text.length() > 12000) throw new IllegalArgumentException("屏蔽词内容过长");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String word = line.trim().toLowerCase(Locale.ROOT);
            if (word.isEmpty()) continue;
            if (word.length() > 100) throw new IllegalArgumentException("每个屏蔽词最多100个字符");
            unique.add(word);
        }
        if (unique.size() > 100) throw new IllegalArgumentException("最多保存100个屏蔽词");
        words = java.util.Collections.unmodifiableList(new ArrayList<>(unique));
    }
    boolean blocks(String text) {
        if (text == null || text.isEmpty()) return false;
        String folded = text.toLowerCase(Locale.ROOT);
        for (String word : words) if (folded.contains(word)) return true;
        return false;
    }
}
