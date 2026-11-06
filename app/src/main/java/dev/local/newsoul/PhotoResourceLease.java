package dev.local.newsoul;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One worker owns the resource until saving finishes; detach only cancels its wait. */
final class PhotoResourceLease implements AutoCloseable {
    private final Future<?> future;
    private final Runnable clearTarget;
    private boolean cancelled;
    private boolean cleared;

    PhotoResourceLease(Future<?> future, Runnable clearTarget) {
        this.future = future;
        this.clearTarget = clearTarget;
    }

    Object await(long timeout, TimeUnit unit) throws Exception {
        synchronized (this) {
            if (cancelled) throw new CancellationException();
        }
        Object resource = future.get(timeout, unit);
        synchronized (this) {
            if (cancelled) throw new CancellationException();
        }
        return resource;
    }

    synchronized void cancel() {
        if (cancelled) return;
        cancelled = true;
        // Future.cancel alone cannot clear a target whose load already completed.
        future.cancel(true);
    }

    @Override public void close() {
        synchronized (this) {
            if (cleared) return;
            cleared = true;
        }
        try { cancel(); }
        finally { clearTarget.run(); }
    }
}
