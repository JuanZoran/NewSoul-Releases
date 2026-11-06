#!/usr/bin/env python3
"""Extract actual dock cleanup and privacy UI guard; run cached-array teardown and lease tests."""
from pathlib import Path
import subprocess
import tempfile

source = Path('app/src/main/java/dev/local/newsoul/AiReplyDock.java').read_text()
methods = source[source.index('    private void release(boolean notify)'):source.index('    private static LinearLayout findInputBar')].replace('AiReplyDock', 'DockLifecycleTest')
head = r'''
import java.util.*;import java.lang.ref.*;
class DockLifecycleTest {
 boolean attached=true,closed; WeakReference<Activity> activity=new WeakReference<>(null);
 static Map<Activity,WeakReference<DockLifecycleTest>> ACTIVE=new WeakHashMap<>();
 static class Activity{}
 static class Main{List<Runnable> queue=new ArrayList<>();void removeCallbacks(Object r){}void post(Runnable r){queue.add(r);}void drain(){while(!queue.isEmpty())queue.remove(0).run();}}
 static Main MAIN=new Main();
 static class Observer{boolean isAlive(){return true;}void removeOnGlobalLayoutListener(Object x){}void removeOnWindowFocusChangeListener(Object x){}}
 Observer observer;Object attachment,deferredCheck,layout,windowFocus,originalContentParams;
 Runnable unregisterBack,onClose;int restores;void restoreOfficialOpening(){restores++;}
 V inputBar=new V(),frame=new V(),content=new V();
 static class V{
  V parent;V[] children=new V[8];int count;Runnable detach;boolean detached;
  Object getParent(){return parent;}void add(V v){children[count++]=v;v.parent=this;}
  void removeOnAttachStateChangeListener(Object a){}void setLayoutParams(Object a){}
  void removeView(V v){for(int i=0;i<count;i++)if(children[i]==v){System.arraycopy(children,i+1,children,i,count-i-1);children[--count]=null;v.parent=null;return;}}
  // Match Android ViewGroup's cached count and shared children array during teardown.
  void dispatch(){if(detach!=null)detach.run();detached=true;int n=count;V[] c=children;for(int i=0;i<n;i++)c[i].dispatch();}
 }
'''
body = r'''
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  for(boolean parentRemoval:new boolean[]{false,true}){
   DockLifecycleTest d=new DockLifecycleTest();Activity owner=new Activity();d.activity=new WeakReference<>(owner);
   ACTIVE.put(owner,new WeakReference<>(d));int[] calls={0};d.onClose=()->{calls[0]++;check(d.frame.getParent()==null);};
   d.inputBar.add(d.frame);d.frame.add(d.content);V sibling=new V();d.inputBar.add(sibling);
   d.frame.detach=()->d.release(true,true);
   d.inputBar.dispatch();
   check(d.closed&&!d.attached&&sibling.detached&&d.content.detached);
   check(d.inputBar.count==2&&d.frame.count==1&&calls[0]==0&&d.restores==0);
   check(ACTIVE.get(owner).get()==d); // No replacement can race deferred restoration.
   d.release(true,true);d.release(true);
   if(parentRemoval)d.inputBar.removeView(d.frame);
   MAIN.drain();
   check(d.inputBar.count==1&&d.frame.count==0&&calls[0]==1&&d.restores==1);
   check(!ACTIVE.containsKey(owner));
  }
  DockLifecycleTest explicit=new DockLifecycleTest();explicit.inputBar.add(explicit.frame);explicit.frame.add(explicit.content);
  int[] calls={0};explicit.onClose=()->calls[0]++;explicit.release(true);explicit.release(true);
  check(explicit.frame.getParent()==null&&explicit.content.getParent()==null&&calls[0]==1&&MAIN.queue.isEmpty());
  DockLifecycleTest failed=new DockLifecycleTest();failed.onClose=()->{throw new AssertionError();};failed.release(false);
  System.out.println("Dock lifecycle: detach with following sibling, reentry, external removal, explicit close and attach failure passed");
 }
}
'''
privacy = Path('app/src/main/java/dev/local/newsoul/PrivacyPhotoControls.java').read_text()
a=privacy.index('    private static boolean photoUiAlive(')
b=privacy.index('    private static void clearPhotoTarget', a)
guard=privacy[a:b]
ui=r'''
import java.lang.ref.WeakReference;
class PrivacyUiGuardTest {
 static class Activity {boolean finishing,destroyed;boolean isFinishing(){return finishing;} boolean isDestroyed(){return destroyed;}}
 static class TextView {boolean attached=true;boolean isAttachedToWindow(){return attached;}}
''' + guard + r'''
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){Activity a=new Activity();TextView b=new TextView();
 WeakReference<Activity> owner=new WeakReference<>(a);WeakReference<TextView> button=new WeakReference<>(b);
 check(photoUiAlive(owner,button));a.finishing=true;check(!photoUiAlive(owner,button));a.finishing=false;
 a.destroyed=true;check(!photoUiAlive(owner,button));a.destroyed=false;
 b.attached=false;check(!photoUiAlive(owner,button));b.attached=true;
 owner.clear();check(!photoUiAlive(owner,button));owner=new WeakReference<>(a);button.clear();check(!photoUiAlive(owner,button));
 System.out.println("Privacy UI lifecycle: live, finishing, destroyed, detached and weak reference release passed");}
}
'''
with tempfile.TemporaryDirectory(prefix='newsoul-photo-dock-') as directory:
    out=Path(directory)
    dock=out/'DockLifecycleTest.java';dock.write_text(head+methods+body)
    ui_file=out/'PrivacyUiGuardTest.java';ui_file.write_text(ui)
    subprocess.run(['javac','-encoding','UTF-8','-d',directory,str(dock),str(ui_file),
        'app/src/main/java/dev/local/newsoul/PhotoResourceLease.java','tests/PhotoResourceLeaseTest.java'],check=True)
    for name in ['DockLifecycleTest','PrivacyUiGuardTest','dev.local.newsoul.PhotoResourceLeaseTest']:
        subprocess.run(['java','-cp',directory,name],check=True)
