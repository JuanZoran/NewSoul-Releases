package dev.local.newsoul;

/** Synchronous long-press lifetime; independent of optional diagnostic recording. */
final class ChatMenuContext implements AutoCloseable {
    private static final ThreadLocal<ChatMenuContext> CURRENT = new ThreadLocal<>();
    final Object fragment, message, anchor;
    private final ChatMenuContext previous;
    private boolean rendered, closed;

    private ChatMenuContext(Object fragment, Object message, Object anchor) {
        this.fragment = fragment; this.message = message; this.anchor = anchor;
        previous = CURRENT.get(); CURRENT.set(this);
    }
    static ChatMenuContext enter(Object fragment, Object message, Object anchor) {
        return new ChatMenuContext(fragment, message, anchor);
    }
    static ChatMenuContext takeRenderer() {
        ChatMenuContext current = CURRENT.get();
        if (current == null || current.rendered || current.closed) return null;
        current.rendered = true;
        return current;
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
