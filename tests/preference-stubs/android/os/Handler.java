package android.os;
public final class Handler {
    private static final java.util.Queue<Runnable> QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public Handler(Looper ignored) {}
    public boolean post(Runnable run) { QUEUE.add(run); return true; }
    public static void drain() { for (Runnable run; (run = QUEUE.poll()) != null;) run.run(); }
}
