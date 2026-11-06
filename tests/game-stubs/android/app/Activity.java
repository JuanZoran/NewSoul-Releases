package android.app;
public class Activity extends android.content.Context {
 public boolean finishing,destroyed;public Object fragment;
 public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}
 public static ClassLoader hostLoader;public ClassLoader getClassLoader(){return hostLoader==null?getClass().getClassLoader():hostLoader;}public androidx.fragment.app.Fragment y(){return (androidx.fragment.app.Fragment)fragment;}
}
