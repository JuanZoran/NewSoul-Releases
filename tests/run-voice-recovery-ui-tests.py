#!/usr/bin/env python3
"""Exercise the production background/error UI with real storage snapshots and observable widgets."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/dev/local/newsoul/VoiceLibraryControls.java').read_text()
methods = source[source.index('        void background('):source.index('        void send(')]
harness = r'''
package dev.local.newsoul;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class VoiceRecoveryUiTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 interface Work{void run()throws Exception;}
 interface Click{void run(TextView view);}
 static class TextView{
  String text="";boolean enabled=true;Click click;
  void setText(String next){text=next;}void setEnabled(boolean next){enabled=next;}
  void setOnClickListener(Click next){click=next;}
  void performClick(){if(enabled&&click!=null)click.run(this);}
 }
 static class Body{
  List<TextView> children=new ArrayList<>();
  void addView(TextView view){children.add(view);}void removeView(TextView view){children.remove(view);}
 }
 static class AlertDialog{static final int BUTTON_POSITIVE=1,BUTTON_NEGATIVE=-1;}
 static class Dialog{
  TextView positive=new TextView(),negative=new TextView();
  TextView getButton(int which){return which==1?positive:negative;}
 }
 static class SettingsUi{
  static class MenuPanel{Object context=new Object();Body body=new Body();Dialog dialog=new Dialog();int secondary,foreground;}
  static TextView label(Object context,String text,int size,int color){TextView view=new TextView();view.setText(text);return view;}
  static void setLiveSummary(TextView view,String text){view.setText(text);}
 }
 static class Log{static void w(String tag,String message,Throwable error){}}
 static class Queue{
  List<Runnable> tasks=new ArrayList<>();
  void execute(Runnable task){tasks.add(task);}boolean post(Runnable task){tasks.add(task);return true;}
  void drain(){while(!tasks.isEmpty())tasks.remove(0).run();}
 }
 final Queue io=new Queue(),main=new Queue();
 final Object activity=new Object();
 SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel();
 VoiceLibraryStore store;
 boolean busy,writing,closed;
 int attempts,scans,toasts;
 String lastToast;
 void validate(){if(closed)throw new IllegalStateException("closed");}
 void failure(Object activity,String message,Throwable error){toasts++;lastToast=message;}
 void drain(){io.drain();main.drain();}
 void library()throws Exception{
  scans++;
  VoiceLibraryStore.PendingFiles fresh=store.pendingFiles();
  panel=new SettingsUi.MenuPanel();
  if(fresh.count>0){
   panel.dialog.positive.setText("删除");
   panel.dialog.positive.setOnClickListener(view->background(()->{attempts++;store.discardPending(fresh);},this::library));
  }
 }
 void reviewed(VoiceLibraryStore.PendingFiles snapshot){
  panel.dialog.positive.setText("删除");
  panel.dialog.positive.setOnClickListener(view->background(()->{attempts++;store.discardPending(snapshot);},this::library));
 }
''' + methods + r'''
 public static void main(String[] ignored)throws Exception{
  Path files=Files.createTempDirectory("voice-recovery-ui-test-");
  try{
   VoiceRecoveryUiTest ui=new VoiceRecoveryUiTest();ui.store=new VoiceLibraryStore(files.toFile(),"owner");
   VoiceLibraryStore.Entry valid=ui.store.add(new ByteArrayInputStream(new byte[]{1,2,3}),"保留",1);
   Path orphan=valid.audio.toPath().resolveSibling(UUID.randomUUID()+".part");Files.write(orphan,new byte[]{1,2,3});
   VoiceLibraryStore.PendingFiles snapshot=ui.store.pendingFiles();
   ui.reviewed(snapshot);Files.write(orphan,new byte[]{3,2,1});
   SettingsUi.MenuPanel failedPanel=ui.panel;TextView oldButton=failedPanel.dialog.positive;
   oldButton.performClick();check(ui.busy&&!oldButton.enabled,"work disables deletion while running");ui.drain();
   check(ui.attempts==1&&Files.exists(orphan),"stale snapshot did not delete file");
   check(oldButton.text.equals("重新检查")&&oldButton.enabled,"stale retry replaced by actionable rescan");
   check(failedPanel.body.children.size()==1&&failedPanel.body.children.get(0).text.contains("文件已变化"),"failure is persistent in current panel");
   check(ui.toasts==0,"actionable storage state does not rely on an expiring toast");
   oldButton.performClick();
   check(ui.scans==1&&ui.attempts==1&&ui.panel!=failedPanel,"rescan navigates to a fresh snapshot without repeating failed work");
   oldButton.performClick();check(ui.scans==1,"detached button cannot start a duplicate scan");
   ui.panel.dialog.positive.performClick();ui.drain();
   check(ui.attempts==2&&ui.scans==2&&!Files.exists(orphan),"new confirmation uses refreshed snapshot and succeeds");
   check(ui.store.read(valid.id).audio.isFile(),"valid record survives the full recovery flow");

   VoiceRecoveryUiTest ordinary=new VoiceRecoveryUiTest();int[] attempts={0},done={0};
   ordinary.panel.dialog.positive.setOnClickListener(view->ordinary.background(()->{
    if(attempts[0]++==0)throw new IOException("temporary IO failure");
   },()->done[0]++));
   ordinary.panel.dialog.positive.performClick();ordinary.drain();
   check(ordinary.toasts==1&&ordinary.panel.dialog.positive.text.equals("重试"),"ordinary recoverable failure retains original retry behavior");
   ordinary.panel.dialog.positive.performClick();ordinary.drain();
   check(attempts[0]==2&&done[0]==1,"ordinary retry still calls original operation");

   VoiceRecoveryUiTest stale=new VoiceRecoveryUiTest();SettingsUi.MenuPanel abandoned=stale.panel;
   stale.background(()->{throw new VoiceLibraryStore.StateException("old failure");},()->{});
   stale.panel=new SettingsUi.MenuPanel();stale.drain();
   check(stale.panel.body.children.isEmpty()&&abandoned.dialog.positive.text.equals("处理中…"),"completion does not update a replaced panel");
   System.out.println("VoiceRecoveryUi: "+checks+" checks passed");
  }finally{try(var paths=Files.walk(files)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
'''
with tempfile.TemporaryDirectory(prefix='newsoul-voice-recovery-ui-') as output:
    output = Path(output)
    java = output / 'VoiceRecoveryUiTest.java'
    java.write_text(harness)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(output), str(java),
                    str(root / 'app/src/main/java/dev/local/newsoul/VoiceLibraryStore.java')], check=True)
    subprocess.run(['java', '-cp', str(output), 'dev.local.newsoul.VoiceRecoveryUiTest'], check=True)
