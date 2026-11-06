package android.view;
import android.content.*;
import android.content.res.*;
public class View {
 public interface OnLongClickListener{boolean onLongClick(View v);}
 public interface OnClickListener{void onClick(View v);}
 public OnLongClickListener longClick;public OnClickListener click;public boolean attached=true,longClickable;public View child;
 public boolean isAttachedToWindow(){return attached;}public Context getContext(){return new Context();}
 public Resources getResources(){return new Resources();}public View findViewById(int id){return child;}
 public void setOnLongClickListener(OnLongClickListener l){longClick=l;longClickable=l!=null;}
 public void setLongClickable(boolean b){longClickable=b;}
 public void setOnClickListener(OnClickListener l){click=l;}
 public boolean performLongClick(){return longClick!=null&&longClick.onLongClick(this);}
 public void performClick(){if(click!=null)click.onClick(this);}
}
