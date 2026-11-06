package android.widget;
public class TextView extends android.view.View {
    public String text; public int icon;
    public TextView(android.content.Context context) { super(context); }
    public void setText(CharSequence value) { text=value.toString(); }
    public void setCompoundDrawablesWithIntrinsicBounds(int left,int top,int right,int bottom) { icon=left; }
}
