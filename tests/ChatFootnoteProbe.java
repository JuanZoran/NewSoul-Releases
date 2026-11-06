package dev.local.newsoul;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Isolated Android View lifecycle assertions; no activity, window, or phone input. */
public final class ChatFootnoteProbe {
    public static final class Params extends FrameLayout.LayoutParams {
        public int bottomToBottom = 0, topToBottom = -1, startToStart = -1, endToEnd = -1;
        public Params(int w, int h) { super(w,h); }
    }
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check="+checks); }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object main = thread.getMethod("systemMain").invoke(null);
        Context system = (Context) thread.getMethod("getSystemContext").invoke(main);
        Context context = system.createPackageContext("cn.soulapp.android", Context.CONTEXT_IGNORE_SECURITY);
        int id = context.getResources().getIdentifier("container","id","cn.soulapp.android");
        check(id != 0);
        for (boolean recallFirst : new boolean[]{false,true}) {
            FrameLayout root = new FrameLayout(context);
            View bubble = new View(context); bubble.setId(id); root.addView(bubble,new Params(150,60));
            if (recallFirst) check(ChatMessageFootnote.recall(root,"撤回",null));
            check(ChatMessageFootnote.time(root,"12:34",null,false));
            if (!recallFirst) check(ChatMessageFootnote.recall(root,"撤回",null));
            check(root.getChildCount()==2);
            TextView label=(TextView)root.getChildAt(1);
            check("12:34\n撤回".contentEquals(label.getText()));
            check(((Params)bubble.getLayoutParams()).bottomToBottom==-1);
            check(((Params)label.getLayoutParams()).startToStart==id);
            check(!label.isClickable() && !label.isLongClickable());
            check(ChatMessageFootnote.time(root,"13:00",null,true));
            check(root.getChildCount()==2);
            check(((Params)label.getLayoutParams()).endToEnd==id);
            check(((Params)label.getLayoutParams()).startToStart==0);
            check(ChatMessageFootnote.time(root,null,null,true));
            check("撤回".contentEquals(label.getText()));
            check(((Params)bubble.getLayoutParams()).bottomToBottom==-1);
            check(ChatMessageFootnote.recall(root,null,null));
            check(root.getChildCount()==1);
            check(((Params)bubble.getLayoutParams()).bottomToBottom==0);
            // Recycle onto an ordinary message, then remove recall while time remains.
            check(ChatMessageFootnote.time(root,"14:00",null,false));
            check(ChatMessageFootnote.recall(root,"撤回",null));
            check(ChatMessageFootnote.recall(root,null,null));
            check("14:00".contentEquals(((TextView)root.getChildAt(1)).getText()));
            check(ChatMessageFootnote.time(root,null,null,false));
            check(root.getChildCount()==1);
        }
        check(!ChatMessageFootnote.time(null,"x",null,false));
        check(!ChatMessageFootnote.time(new FrameLayout(context),"x",null,false));
        System.out.println("PASS " + checks + " footnote lifecycle checks");
        System.exit(0);
    }
}
