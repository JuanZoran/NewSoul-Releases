package android.view;
public class View {
 public interface OnClickListener {void onClick(View v);}
 private OnClickListener listener;
 public boolean attached=true;
 public boolean isAttachedToWindow(){return attached;}
 public android.content.Context getContext(){return new android.content.Context();}
 public void setOnClickListener(OnClickListener l){listener=l;}
 public boolean performClick(){if(listener==null)return false;listener.onClick(this);return true;}
}
