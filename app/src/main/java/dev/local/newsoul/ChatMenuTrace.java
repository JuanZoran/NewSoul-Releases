package dev.local.newsoul;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local attempt IDs; no account, message or content retained. */
final class ChatMenuTrace {
    private static final AtomicLong NEXT = new AtomicLong();
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();
    private static final ArrayDeque<Binding> LISTS = new ArrayDeque<>();
    static final class Trace {
        final long id = NEXT.incrementAndGet();
        int menu, show, render;
        String summary() { return "menu_hits=" + menu + "; show_hits=" + show + "; render_hits=" + render; }
    }
    private static final class Binding {
        final WeakReference<Object> list;
        final Trace trace;
        Binding(Object list, Trace trace) { this.list = new WeakReference<>(list); this.trace = trace; }
    }
    static final class Scope implements AutoCloseable {
        final Trace trace;
        private final Trace previous;
        private boolean closed;
        Scope(Trace trace, Trace previous) { this.trace = trace; this.previous = previous; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
    static Scope enter(boolean newAttempt, Object list) {
        Trace previous = CURRENT.get(), trace = newAttempt ? null : previous;
        if (!newAttempt && trace == null && list != null) {
            synchronized (LISTS) {
                for (Iterator<Binding> it = LISTS.iterator(); it.hasNext();) {
                    Binding binding = it.next(); Object value = binding.list.get();
                    if (value == null) it.remove();
                    else if (value == list) trace = binding.trace;
                }
            }
        }
        if (trace == null) trace = new Trace();
        CURRENT.set(trace);
        return new Scope(trace, previous);
    }
    static Trace current() { return CURRENT.get(); }
    static void remember(Object list) {
        Trace trace = CURRENT.get();
        if (trace == null || list == null) return;
        synchronized (LISTS) {
            LISTS.removeIf(binding -> binding.list.get() == null || binding.list.get() == list);
            while (LISTS.size() >= 64) LISTS.removeFirst();
            LISTS.addLast(new Binding(list, trace));
        }
    }
}
