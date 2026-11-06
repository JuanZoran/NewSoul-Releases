package dev.local.newsoul;

/** A response is valid only for this request and this installed module build. */
final class HomeCheck {
    static final String[] VERSIONS = {"4.88.0", "6.37.0", "6.38.0", "6.39.0"};
    static final long TIMEOUT = 8000;
    private String token;
    private long started, code;
    private boolean answered;
    synchronized void begin(String token, long now, long code) {
        this.token = token; started = now; this.code = code; answered = false;
    }
    synchronized boolean valid(String token, long now, long code) {
        return !answered && this.token != null && this.token.equals(token)
                && now >= started && now - started <= TIMEOUT && this.code == code;
    }
    synchronized boolean accept(String token, long now, long code) {
        if (!valid(token, now, code)) return false;
        answered = true;
        return true;
    }
    /** Current version and its neighbors; clamp at either end instead of leaving empty slots. */
    static int windowStart(int count, int selected, int visible) {
        if (count <= 0 || visible <= 0 || selected < 0 || selected >= count) return 0;
        int slots = Math.min(4, visible);
        return Math.max(0, Math.min(selected - (slots - 1) / 2, count - slots));
    }
    static boolean supported(String version) {
        for (String item : VERSIONS) if (item.equals(version)) return true;
        return false;
    }
}
