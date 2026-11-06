package dev.local.newsoul;

import java.util.*;
final class DebugDiagnostics {
    static boolean enabled = true;
    static final List<String> events = new ArrayList<>();
    static boolean active() { return enabled; }
    static void event(String point, String facts) { if (enabled) events.add(point + ":" + facts); }
    static void registration(String point, Throwable error) { events.add(point + ":" + (error == null ? "registered" : error.getClass().getSimpleName())); }
    static void setupFact(String point, String facts) { events.add(point + ":" + facts); }
    static void failure(String point, String facts, Throwable error) { event(point, facts + "; error=" + error.getClass().getName()); }
}
