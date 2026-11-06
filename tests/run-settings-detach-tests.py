#!/usr/bin/env python3
"""Run production cleanup methods with ViewGroup's cached-array detach traversal.
This is a JVM lifecycle regression, not Android rendering/device acceptance.
Pass a historical SettingsSearch.java to confirm the regression catches the old bug.
"""
from pathlib import Path
import subprocess
import sys
source = Path(sys.argv[1] if len(sys.argv) > 1 else
              'app/src/main/java/dev/local/newsoul/SettingsSearch.java').read_text()
a = source.index('    private void stopGuide()')
b = source.index('    private final class Spotlight', a)
methods = source[a:b]
import tempfile
_test_dir = tempfile.TemporaryDirectory(prefix='newsoul-settings-detach-')
out = Path(_test_dir.name)
out.mkdir(parents=True, exist_ok=True)
harness = r'''
import java.util.*;
class SettingsDetachTest {
 boolean disposed, guiding; int generation, guideCategory=-1, resets, restored;
 List<Runnable> pending=new ArrayList<>();
 View header=new View(), searchOverlay=new View(), floatingPanel=new View(); Spotlight spotlight;
 ValueAnimator transition; Object imeLayout=new Object(); Panel panel=new Panel(); Host host=new Host();
 void resetLift(){resets++;}
 void restoreWindow(){if(!disposed)restored++;}
 static class Observer { int removed; void removeOnGlobalLayoutListener(Object o){removed++;} }
 static class View {
  ViewGroup parent; Runnable onDetach; boolean detached; int callbacks;
  Observer observer=new Observer(); Object getParent(){return parent;}
  void removeCallbacks(Runnable r){callbacks++;}
  Observer getViewTreeObserver(){return observer;}
  void detach(){if(onDetach!=null)onDetach.run();detached=true;}
 }
 static class ViewGroup extends View {
  View[] children=new View[8]; int count;
  void add(View v){children[count++]=v;v.parent=this;}
  void removeView(View v){for(int i=0;i<count;i++)if(children[i]==v){
   System.arraycopy(children,i+1,children,i,count-i-1);children[--count]=null;v.parent=null;return;
  }}
  // Android ViewGroup.dispatchDetachedFromWindow snapshots the count, not the array.
  @Override void detach(){int n=count;View[] array=children;
   for(int i=0;i<n;i++)array[i].detach();super.detach();}
 }
 static class Spotlight extends View {}
 static class Window {ViewGroup decor=new ViewGroup(); ViewGroup getDecorView(){return decor;}}
 static class Dialog {Window window=new Window();Window getWindow(){return window;}}
 static class Appearance {int clears;void clearSearchBackdrop(View view){clears++;}}
 static class Panel {Dialog dialog=new Dialog(); Appearance appearance=new Appearance();}
 static class Host {int calls;void lens(int c,boolean b){calls++;}}
 class ValueAnimator {boolean cancelled;void cancel(){cancelled=true;
  if(transition==this)throw new AssertionError("cancel could finish a morph during detach");}}
''' + methods + r'''
 static int checks;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  for(boolean guide:new boolean[]{false,true})for(boolean animate:new boolean[]{false,true}){
   SettingsDetachTest s=new SettingsDetachTest();ViewGroup root=s.panel.dialog.window.decor;
   ViewGroup content=new ViewGroup();root.add(content);content.add(s.header);root.add(s.searchOverlay);
   s.header.onDetach=s::dispose;
   Spotlight spot=new Spotlight();
   if(guide){s.guiding=true;s.guideCategory=2;s.spotlight=spot;root.add(spot);s.pending.add(()->{});}
   ValueAnimator anim=s.new ValueAnimator();if(animate)s.transition=anim;
   root.detach();
   check(s.disposed&&root.detached&&s.header.detached&&s.searchOverlay.detached);
   check(s.panel.appearance.clears==1);
   check(!guide||spot.detached);check(s.spotlight==null&&s.pending.isEmpty()&&!s.guiding);
   check(s.host.calls==0&&s.restored==0);check(!animate||anim.cancelled);
   check(s.transition==null&&root.observer.removed==1);
   s.dispose();check(root.observer.removed==1&&s.resets==1);
  }
  SettingsDetachTest live=new SettingsDetachTest();live.guiding=true;live.guideCategory=1;
  Spotlight spot=new Spotlight();live.spotlight=spot;live.panel.dialog.window.decor.add(spot);
  live.stopGuide();check(spot.parent==null&&live.host.calls==1&&live.restored==1);
  System.out.println("Settings detach: "+checks+" lifecycle checks passed");
 }
}
'''
java = out/'SettingsDetachTest.java'
java.write_text(harness)
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(out), str(java)], check=True)
subprocess.run(['java', '-cp', str(out), 'SettingsDetachTest'], check=True)
