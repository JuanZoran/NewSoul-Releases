#!/usr/bin/env python3
"""Run the real select/loaded methods with deterministic worker/main queues."""
from pathlib import Path
import subprocess
import tempfile
source = Path('app/src/main/java/dev/local/newsoul/DonationUi.java').read_text()
methods = source[source.index('        void select(int index)'):source.index('        void saveAndOpen()')]
harness = r'''
import java.util.*;
public class DonationLifecycleProbe {
 static final String[] RESOURCES={"wechat","alipay"}, NAMES={"微信","支付宝"};
 static class Queue { ArrayDeque<Runnable> tasks=new ArrayDeque<>(); void execute(Runnable r){tasks.add(r);} void post(Runnable r){execute(r);} void drain(){while(!tasks.isEmpty()) tasks.remove().run();} }
 static Queue IO=new Queue(), MAIN=new Queue();
 static class Bitmap { boolean recycled; void recycle(){recycled=true;} }
 static class DonationImages { static int failure; static List<Bitmap> created=new ArrayList<>(); static Bitmap preview(int i) { if(failure==1) throw new IllegalStateException(); if(failure==2) throw new OutOfMemoryError(); Bitmap b=new Bitmap();created.add(b);return b;} }
 static class DebugDiagnostics { static int failures; static void failure(String a,String b,Throwable t){failures++;} }
 static class android { static class animation { static class ValueAnimator { static boolean areAnimatorsEnabled(){return true;} } } static class util { static class Log { static void w(String a,String b,Throwable t){} } } }
 static class View { static final int GONE=8,VISIBLE=0,INVISIBLE=4; }
 static class TextView { String text=""; int visibility; void setVisibility(int i){visibility=i;} void setEnabled(boolean b){} void setText(String s){text=s;} }
 static class Animator { void cancel(){} Animator alpha(float f){return this;} Animator setDuration(int d){return this;} void start(){} }
 static class Code extends TextView { Animator animate(){return new Animator();} void setAlpha(float f){} Bitmap bitmap; Object getDrawable(){return bitmap;} void setImageDrawable(Object o){bitmap=null;} void setImageBitmap(Bitmap b){bitmap=b;} void setContentDescription(String s){} }
 static class Choices { void selectValue(String s,String[] all,boolean b){} }
 static class AlertDialog { static final int BUTTON_NEGATIVE=-2; }
 static class Dialog { TextView button=new TextView(); TextView getButton(int i){return button;} }
 static class Panel { Dialog dialog=new Dialog(); }
 static class Page {
 boolean saving, loading, open=true; int requested, selected=-1,generation; String status;
 Panel panel=new Panel(); Code code=new Code(); Choices choices=new Choices();
 boolean visible(){return open;} void message(String s){status=s;}
 METHODS
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void drain(){IO.drain();MAIN.drain();}
 public static void main(String[] args){
  Page p=new Page(); p.select(0);int first=p.generation;p.select(0);check(p.generation==first);p.select(1);drain();
  check(p.code.visibility==View.VISIBLE && p.panel.dialog.button.visibility==View.VISIBLE);
  check(p.selected==1 && DonationImages.created.get(0).recycled && !p.code.bitmap.recycled);
  p.select(0);p.open=false;p.generation++;drain();check(DonationImages.created.get(2).recycled);
  p.open=true;p.loading=false;DonationImages.failure=1;p.select(1);drain();check(p.selected==-1 && p.panel.dialog.button.text.equals("重新加载"));
  DonationImages.failure=0;p.select(p.requested);drain();check(p.selected==1 && p.status.isEmpty());
  DonationImages.failure=2;p.select(0);drain();check(p.selected==-1 && DebugDiagnostics.failures==2);
  p.saving=true;int before=p.generation;p.select(1);check(p.generation==before && IO.tasks.isEmpty());
  System.out.println("PASS: latest selection, dismissed-page recycle, failure, retry, OOM, saving guard");
 }
}
'''.replace('METHODS', methods)
with tempfile.TemporaryDirectory() as directory:
    path=Path(directory)/'DonationLifecycleProbe.java'
    path.write_text(harness)
    subprocess.run(['javac','-encoding','UTF-8','-d',directory,str(path)],check=True)
    subprocess.run(['java','-cp',directory,'DonationLifecycleProbe'],check=True)
