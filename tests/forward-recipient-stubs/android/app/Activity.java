package android.app;
public class Activity extends android.content.Context {
 public boolean finishing,destroyed; public int starts,finishes;
 public android.content.Intent intent;
 public boolean isFinishing(){return finishing;} public boolean isDestroyed(){return destroyed;}
 public void startActivity(android.content.Intent value){starts++;intent=value;}
 public android.content.Intent getIntent(){return intent;}
 public void finish(){finishing=true;finishes++;}
}
