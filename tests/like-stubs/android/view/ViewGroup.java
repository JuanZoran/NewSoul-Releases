package android.view;
import java.util.*;
public class ViewGroup extends View {public static class LayoutParams {public LayoutParams(int w,int h){}}public List<View> children=new ArrayList<>();public void add(View v){children.add(v);v.parent=this;}public void removeView(View v){children.remove(v);v.parent=null;}public int getChildCount(){return children.size();}public View getChildAt(int n){return n>=0&&n<children.size()?children.get(n):null;}public View findViewById(int n){if(id==n)return this;for(View c:children){View v=c.findViewById(n);if(v!=null)return v;}return null;}}
