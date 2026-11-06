package android.content;
public class Context {
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return dev.local.newsoul.AccountHttpLifecycleTest.preferences(name);
    }
}
