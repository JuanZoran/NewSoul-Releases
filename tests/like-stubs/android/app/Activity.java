package android.app;
import android.content.Context;import android.view.*;
public class Activity extends Context {public Window window=new Window();public boolean focus=true,finishing,destroyed;public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}public boolean hasWindowFocus(){return focus;}public Window getWindow(){return window;}public View findViewById(int id){return window.decor.findViewById(id);}public void runOnUiThread(Runnable r){r.run();}}
