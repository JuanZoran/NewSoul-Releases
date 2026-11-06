package dev.local.newsoul;

import java.util.Locale;

/** Deterministic matching shared by the settings index and its regression checks. */
final class SettingsSearchRules {
    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
    static int score(String query, String title, String path) {
        String q=normalize(query), t=normalize(title), p=normalize(path);
        if(q.isEmpty()) return -1;
        if(t.equals(q)) return 0;
        if(t.contains(q)) return 1;
        for(String word:q.split(" ")) if(!(t+" "+p).contains(word)) return -1;
        return 2;
    }
}
