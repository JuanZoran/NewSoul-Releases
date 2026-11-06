package dev.local.newsoul;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class PhotoResourceLeaseTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    static void expect(Class<? extends Throwable> type, PhotoResourceLease lease) throws Exception {
        try { lease.await(15, TimeUnit.MILLISECONDS); throw new AssertionError("expected " + type); }
        catch (Exception error) { check(type.isInstance(error)); }
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger clears = new AtomicInteger();
        CompletableFuture<String> success = CompletableFuture.completedFuture("bitmap");
        PhotoResourceLease a = new PhotoResourceLease(success, clears::incrementAndGet);
        check("bitmap".equals(a.await(1, TimeUnit.SECONDS)));
        // Detach during compression: a completed future cannot cancel; resource stays
        // owned until the worker closes after its save, including exceptional saves.
        a.cancel(); check(clears.get() == 0); check(!success.isCancelled());
        a.close(); a.close(); check(clears.get() == 1);
        CompletableFuture<String> failure = new CompletableFuture<>();
        failure.completeExceptionally(new IllegalStateException("host load failed"));
        PhotoResourceLease b = new PhotoResourceLease(failure, clears::incrementAndGet);
        try { expect(ExecutionException.class, b); } finally { b.close(); }
        check(clears.get() == 2);
        CompletableFuture<String> timeout = new CompletableFuture<>();
        PhotoResourceLease c = new PhotoResourceLease(timeout, clears::incrementAndGet);
        try { expect(TimeoutException.class, c); } finally { c.close(); }
        check(timeout.isCancelled() && clears.get() == 3);
        CountDownLatch waiting = new CountDownLatch(1);
        CompletableFuture<String> destroyed = new CompletableFuture<String>() {
            @Override public String get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                waiting.countDown();
                return super.get(timeout, unit);
            }
        };
        PhotoResourceLease d = new PhotoResourceLease(destroyed, clears::incrementAndGet);
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { d.await(5, TimeUnit.SECONDS); throw new AssertionError("destroy should cancel"); }
            catch (CancellationException expected) { }
            catch (Throwable error) { workerFailure.set(error); }
            finally { d.close(); }
        });
        worker.start(); waiting.await(); d.cancel(); worker.join(1000);
        check(!worker.isAlive() && destroyed.isCancelled() && clears.get() == 4);
        check(workerFailure.get() == null);
        PhotoResourceLease e = new PhotoResourceLease(CompletableFuture.completedFuture("bitmap"), clears::incrementAndGet);
        try {
            e.await(1, TimeUnit.SECONDS);
            throw new IllegalStateException("compression failed");
        } catch (IllegalStateException expected) { } finally { e.close(); }
        check(clears.get() == 5);
        System.out.println("Photo resource lease: " + checks + " checks passed");
    }
}
