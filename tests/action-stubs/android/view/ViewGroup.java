package android.view;
public class ViewGroup extends View {
 public final java.util.List<View> children=new java.util.ArrayList<>();
 public int getChildCount(){return children.size();}
 public View getChildAt(int i){return children.get(i);}
}
