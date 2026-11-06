#!/usr/bin/env python3
"""Exercise the production footer's ownership/restoration with small JVM Android stubs."""
from pathlib import Path
import subprocess
import tempfile
_test_dir = tempfile.TemporaryDirectory(prefix='newsoul-chat-footnote-')
out = Path(_test_dir.name)
stubs = {
'android/content/Context.java': '''package android.content; public class Context { public static final int CONTEXT_IGNORE_SECURITY=2; public android.content.res.Resources getResources(){return new android.content.res.Resources();} public Context createPackageContext(String n,int f){return this;} }''',
'android/content/res/Resources.java': '''package android.content.res; public class Resources { public int getIdentifier(String n,String t,String p){return 1;} public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();} }''',
'android/content/res/ColorStateList.java': '''package android.content.res; public class ColorStateList {}''',
'android/util/DisplayMetrics.java': '''package android.util; public class DisplayMetrics {public float density=1;}''',
'android/util/Log.java': '''package android.util; public class Log {public static int w(String t,String m,Throwable e){throw new AssertionError(m,e);} }''',
'android/os/Looper.java': '''package android.os; public class Looper {public static void prepareMainLooper(){} }''',
'android/app/ActivityThread.java': '''package android.app; public class ActivityThread {public static ActivityThread systemMain(){return new ActivityThread();} public android.content.Context getSystemContext(){return new android.content.Context();} }''',
'android/view/Gravity.java': '''package android.view; public class Gravity {public static final int START=1,END=2;}''',
'android/view/View.java': '''package android.view;
public class View { public static final int IMPORTANT_FOR_ACCESSIBILITY_YES=1; private static int counter=10;
private int id; private final android.content.Context context; ViewGroup parent; ViewGroup.LayoutParams params;
public View(android.content.Context c){context=c;} public android.content.Context getContext(){return context;}
public android.content.res.Resources getResources(){return context.getResources();} public static int generateViewId(){return counter++;}
public void setId(int v){id=v;} public int getId(){return id;} public View findViewById(int v){return v==id?this:null;}
public ViewGroup getParent(){return parent;} public ViewGroup.LayoutParams getLayoutParams(){return params;}
public void setLayoutParams(ViewGroup.LayoutParams p){params=p;} public void setImportantForAccessibility(int v){}
public void setClickable(boolean v){} public void setLongClickable(boolean v){} public void setFocusable(boolean v){}
public boolean isClickable(){return false;} public boolean isLongClickable(){return false;}}
''',
'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View {
public static class LayoutParams {public LayoutParams(int w,int h){}}
public static class MarginLayoutParams extends LayoutParams {public int topMargin,bottomMargin; public MarginLayoutParams(int w,int h){super(w,h);}}
private final java.util.List<View> children=new java.util.ArrayList<>(); public ViewGroup(android.content.Context c){super(c);}
public void addView(View v,LayoutParams p){children.add(v);v.parent=this;v.params=p;} public void removeView(View v){children.remove(v);v.parent=null;}
public View findViewById(int id){View self=super.findViewById(id);if(self!=null)return self;for(View v:children){View found=v.findViewById(id);if(found!=null)return found;}return null;}
public int getChildCount(){return children.size();} public View getChildAt(int i){return children.get(i);} }
''',
'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout extends android.view.ViewGroup {public FrameLayout(android.content.Context c){super(c);} public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {public LayoutParams(int w,int h){super(w,h);}}}''',
'android/widget/TextView.java': '''package android.widget; public class TextView extends android.view.View {private CharSequence text; public TextView(android.content.Context c){super(c);} public void setTextSize(int v){} public void setTextColor(android.content.res.ColorStateList c){} public void setText(CharSequence v){text=v;} public CharSequence getText(){return text;} public void setGravity(int v){}}'''
}
paths=[]
for name,content in stubs.items():
    path=out/'stubs'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content);paths.append(str(path))
classes=out/'classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),*paths,'app/src/main/java/dev/local/newsoul/ChatMessageFootnote.java','tests/ChatFootnoteProbe.java'],check=True)
subprocess.run(['java','-cp',str(classes),'dev.local.newsoul.ChatFootnoteProbe'],check=True)
