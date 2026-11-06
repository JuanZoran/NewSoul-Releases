package android.widget;
public class TextView extends android.view.View {
 public CharSequence text; public boolean enabled; public float alpha;
 public void setText(CharSequence value){text=value;}
 public void setEnabled(boolean value){enabled=value;}
 public void setAlpha(float value){alpha=value;}
}
