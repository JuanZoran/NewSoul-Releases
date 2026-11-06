package android.app;
public class Activity extends android.content.Context {
 public boolean finishing,destroyed,failStart;
 public int starts;public android.content.Intent lastIntent;
 public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}
 public void startActivity(android.content.Intent intent){if(failStart)throw new IllegalStateException();starts++;lastIntent=intent;}
}
