package dev.local.newsoul;

import java.io.InterruptedIOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One request, including queue time. Cancelling owns the actual host Call, not just its result. */
final class HostRequestScope implements AutoCloseable {
    private enum Stop {
        CANCELLED("请求已取消"), TIMEOUT("请求超时"), INVALIDATED("请求状态已变化");
        final String message;
        Stop(String message) { this.message = message; }
    }
    private static final ScheduledThreadPoolExecutor WATCHDOG = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "newsoul-request-deadlines");
        thread.setDaemon(true);
        return thread;
    });
    static { WATCHDOG.setRemoveOnCancelPolicy(true); }

    private final long deadline;
    private final BooleanSupplier enabled;
    private BooleanSupplier current = () -> true;
    private Runnable cancelCall;
    private ScheduledFuture<?> watchdog;
    private Stop failure;
    private boolean closed, attached;

    HostRequestScope(long timeoutMillis) { this(timeoutMillis, () -> true); }

    HostRequestScope(long timeoutMillis, BooleanSupplier enabled) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis");
        this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        this.enabled = enabled;
    }

    /** Attach before execute; cancellation before attachment still cancels without sending. */
    void attach(Runnable cancel, BooleanSupplier current) throws InterruptedIOException {
        Runnable pending = null;
        synchronized (this) {
            if (attached || closed) throw new IllegalStateException("Request scope already used");
            attached = true;
            this.current = current;
            if (failure == null) cancelCall = cancel;
            else pending = cancel;
            if (failure == null) watchdog = WATCHDOG.scheduleAtFixedRate(this::refreshCancellation, 0, 100, TimeUnit.MILLISECONDS);
        }
        invokeCancel(pending);
        check();
    }

    void check() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) cancel();
        refreshCancellation();
        synchronized (this) {
            if (failure != null) throw new InterruptedIOException(failure.message);
            if (closed) throw new InterruptedIOException("请求已结束");
        }
    }

    /** Also used before queued work has a Call or watchdog. Invalid state takes precedence over timeout. */
    void refreshCancellation() {
        BooleanSupplier guard;
        synchronized (this) {
            if (closed || failure != null) return;
            guard = current;
        }
        boolean valid;
        try { valid = enabled.getAsBoolean() && guard.getAsBoolean(); }
        catch (RuntimeException unavailable) { valid = false; }
        if (!valid) cancel(Stop.INVALIDATED);
        else if (System.nanoTime() - deadline >= 0) cancel(Stop.TIMEOUT);
    }

    void cancel() { cancel(Stop.CANCELLED); }

    synchronized boolean isCancelled() { return failure != null; }
    synchronized boolean timedOut() { return failure == Stop.TIMEOUT; }

    private void cancel(Stop reason) {
        Runnable action;
        synchronized (this) {
            if (closed || failure != null) return;
            failure = reason;
            action = cancelCall;
            cancelCall = null;
            if (watchdog != null) watchdog.cancel(false);
        }
        invokeCancel(action);
    }

    private static void invokeCancel(Runnable action) {
        if (action != null) action.run();
    }

    @Override public synchronized void close() {
        closed = true;
        cancelCall = null;
        if (watchdog != null) watchdog.cancel(false);
    }
}
