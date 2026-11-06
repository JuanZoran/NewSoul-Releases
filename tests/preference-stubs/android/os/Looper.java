package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static final Thread OWNER = Thread.currentThread();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return Thread.currentThread() == OWNER ? MAIN : null; }
}
