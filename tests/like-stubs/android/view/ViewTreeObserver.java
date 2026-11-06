package android.view;
public class ViewTreeObserver {public interface OnPreDrawListener{boolean onPreDraw();}public boolean isAlive(){return true;}public void removeOnPreDrawListener(OnPreDrawListener l){}public void addOnPreDrawListener(OnPreDrawListener l){}}
