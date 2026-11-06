package dev.local.newsoul;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Worker failures and timeouts must fail the main test process. */
final class TestWorkers {
    static FutureTask<Void> start(Runnable action) {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Thread thread = new Thread(task, "test-worker");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    static void await(Future<?> task) throws Exception {
        task.get(5, TimeUnit.SECONDS);
    }

    private TestWorkers() {}
}
