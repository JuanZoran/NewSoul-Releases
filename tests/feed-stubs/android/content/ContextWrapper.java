package android.content;
public class ContextWrapper extends Context {public Context base;public ContextWrapper(Context c){base=c;}public Context getBaseContext(){return base;}}
