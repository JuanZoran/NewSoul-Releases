package android.app;
import android.content.*;
public class Activity extends Context {
 public boolean finishing,destroyed; public Intent intent=new Intent(),started;
 public boolean coarsePermission,finePermission;public int starts,permissionChecks;
 public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}
 public Intent getIntent(){return intent;}public void finish(){finishing=true;}
 public void startActivity(Intent i){started=i;starts++;}
 public int checkSelfPermission(String permission){permissionChecks++;return (permission.equals("android.permission.ACCESS_COARSE_LOCATION")?coarsePermission:permission.equals("android.permission.ACCESS_FINE_LOCATION")&&finePermission)?0:-1;}
}
