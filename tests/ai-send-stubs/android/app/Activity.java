package android.app;
public class Activity {
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
}
