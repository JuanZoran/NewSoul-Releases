#!/usr/bin/env python3
"""Execute HomeActivity's production lifecycle/detail methods against a virtual main loop.

Only unrelated page construction and framework activation are replaced. The lifecycle,
request, polling, dialog dismissal and failure code is read directly from HomeActivity.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
production = (root / 'app/src/main/java/dev/local/newsoul/HomeActivity.java').read_text()


def method(signature):
    start = production.index(signature)
    opening = production.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        if production[end] == '{':
            depth += 1
        elif production[end] == '}':
            depth -= 1
        end += 1
    return production[start:end]


methods = '\n'.join(method(signature) for signature in (
    '@Override public void onResume()', '@Override public void onPause()',
    '@Override public void onDestroy()', 'private void cancelDetails()', 'private void details()'))
files = {
'android/os/Looper.java': '''package android.os;
public class Looper { public static Looper getMainLooper(){return new Looper();} }''',
'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long now; public static long elapsedRealtime(){return now;} }''',
'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
static class Task { final Runnable action; final long at;
Task(Runnable r,long t){action=r;at=t;} }
private final List<Task> tasks=new ArrayList<>();
public Handler(Looper l){} public boolean post(Runnable r){return postDelayed(r,0);}
public boolean postDelayed(Runnable r,long delay){tasks.add(new Task(r,SystemClock.now+delay));return true;}
public void removeCallbacks(Runnable r){tasks.removeIf(t -> t.action==r);}
public void removeCallbacksAndMessages(Object token){tasks.clear();}
public int size(){return tasks.size();}
public Runnable first(){return tasks.get(0).action;}
public void advance(long elapsed){long until=SystemClock.now+elapsed;
for(;;){Task next=null;for(Task t:tasks)if(t.at<=until&&(next==null||t.at<next.at))next=t;
if(next==null)break;tasks.remove(next);SystemClock.now=next.at;next.action.run();}
SystemClock.now=until;}
}''',
'android/os/Bundle.java': '''package android.os;
public class Bundle { public boolean confirmed; public String report;
public boolean getBoolean(String k){return confirmed;} public String getString(String k){return report;} }''',
'android/net/Uri.java': '''package android.net; public class Uri {}''',
'android/content/Intent.java': '''package android.content;
public class Intent {public Intent(String a){} public Intent setPackage(String p){return this;}
public Intent putExtra(String k,String v){return this;} }''',
'android/widget/TextView.java': '''package android.widget;
public class TextView {private String text; public TextView(String v){text=v;}
public void setText(String v){text=v;} public String getText(){return text;} }''',
'dev/local/newsoul/SettingsUi.java': '''package dev.local.newsoul;
import android.widget.TextView;
class SettingsUi {
static MenuPanel latest;
static TextView label(Object c,String t,int size,int color){return new TextView(t);}
static void setLiveSummary(TextView view,String text){view.setText(text);}
static class MenuPanel {
Object context=this; int foreground; Body body=new Body(); FakeDialog dialog;
MenuPanel(Object a,boolean dark){latest=this;}
void show(String title,String action){dialog=new FakeDialog();}
}
static class Body {TextView result; void addView(TextView v){result=v;} }
static class FakeDialog {
interface Dismiss {void run(FakeDialog d);} Dismiss dismiss; boolean showing=true;
boolean isShowing(){return showing;} void setOnDismissListener(Dismiss value){dismiss=value;}
void dismiss(){showing=false;if(dismiss!=null)dismiss.run(this);}
}
}''',
'dev/local/newsoul/HomeActivity.java': '''package dev.local.newsoul;
import android.content.Intent; import android.os.*; import android.net.Uri;
import android.widget.TextView; import java.util.UUID;
class HomeActivity extends FakeActivity {
final Handler handler=new Handler(Looper.getMainLooper()); boolean resumed,dark; int checkGeneration;
private Runnable detailPoll; final Runnable frameworkChanged=()->{}; Uri endpoint=new Uri();
void refreshIcon(){} void check(){}
''' + methods + '''
void showDetails(){details();} boolean pending(){return detailPoll!=null;}
}
class FakeActivity {
final Provider provider=new Provider(); int broadcasts;
public void onResume(){} public void onPause(){} public void onDestroy(){}
Provider getContentResolver(){return provider;} String getPackageName(){return "test";}
void sendBroadcast(Intent intent){broadcasts++;}
}
class HomeFramework { static void observe(Runnable observer){} }
class HomeStatusProvider {static final String HOST="soul";}
class Provider {
int begins,reads; boolean unavailable; android.os.Bundle answer;
Bundle call(Uri uri,String method,String token,Bundle extras){
if(unavailable)throw new IllegalStateException("provider unavailable");
if(method.equals("begin")){begins++;answer=null;return null;}
reads++;return answer;
}
void confirm(String report){answer=new Bundle();answer.confirmed=true;answer.report=report;}
}
''',
'dev/local/newsoul/HomeLifecycleTest.java': '''package dev.local.newsoul;
public class HomeLifecycleTest {
static int checks;
static void expect(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
static String text(){return SettingsUi.latest.body.result.getText();}
static HomeActivity start(){HomeActivity a=new HomeActivity();a.onResume();a.showDetails();
expect(a.pending()&&a.provider.begins==1&&a.broadcasts==1);return a;}
public static void main(String[] args){
HomeActivity a=start(); a.provider.confirm("confirmed");a.handler.advance(200);
expect(text().equals("confirmed")&&!a.pending()&&a.handler.size()==0);
int reads=a.provider.reads;a.handler.advance(10000);expect(a.provider.reads==reads);
// A result produced while backgrounded is read immediately on return, with no new request.
a=start();a.handler.advance(50);a.onPause();expect(a.handler.size()==0&&a.pending());
a.handler.advance(1000);expect(a.provider.reads==0);a.provider.confirm("background result");
a.onResume();a.handler.advance(0);expect(text().equals("background result")&&!a.pending());
expect(a.provider.begins==1&&a.broadcasts==1);
// Returning after the original timeout must end loading immediately rather than reset its deadline.
a=start();a.onPause();a.handler.advance(HomeCheck.TIMEOUT+1);a.onResume();a.handler.advance(0);
expect(text().startsWith("尚未确认")&&!a.pending()&&a.handler.size()==0);
expect(a.provider.begins==1);
// Returning before the deadline continues polling and still respects the original window.
a=start();a.handler.advance(50);a.onPause();a.handler.advance(500);a.onResume();a.handler.advance(0);
expect(a.pending()&&a.provider.reads==1);a.handler.advance(HomeCheck.TIMEOUT);
expect(!a.pending()&&text().startsWith("尚未确认"));
// A confirmed answer from within the request window remains valid when viewed after returning late.
a=start();a.onPause();a.provider.confirm("already received");a.handler.advance(HomeCheck.TIMEOUT+1);
a.onResume();a.handler.advance(0);expect(text().equals("already received")&&!a.pending());
// Provider failures on return end polling and expose the existing next step.
a=start();a.onPause();a.provider.unavailable=true;a.onResume();a.handler.advance(0);
expect(text().startsWith("无法确认")&&!a.pending()&&a.handler.size()==0);
// Dismissal, activity destruction and replacement invalidate callbacks.
a=start();SettingsUi.latest.dialog.dismiss();expect(!a.pending()&&a.handler.size()==0);
a.onPause();a.onResume();a.handler.advance(10000);expect(a.provider.reads==0);
a=start();a.onPause();a.onDestroy();expect(!a.pending());
a=start();Runnable old=a.handler.first();SettingsUi.FakeDialog oldDialog=SettingsUi.latest.dialog;
a.showDetails();oldDialog.dismiss();old.run();expect(a.pending()&&a.provider.reads==0);
a.provider.confirm("new dialog");a.handler.advance(200);expect(text().equals("new dialog")&&!a.pending());
// Failure before scheduling a request cannot leave a resumable/stale poll.
a=new HomeActivity();a.onResume();a.provider.unavailable=true;a.showDetails();
expect(!a.pending()&&text().startsWith("无法确认")&&a.handler.size()==0);
System.out.println("Home detail production lifecycle: "+checks+" pause, resume, deadline, dismissal and failure checks passed");
}
}'''
}
with tempfile.TemporaryDirectory(prefix='newsoul-home-lifecycle-') as folder:
    base = Path(folder)
    for name, source in files.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(base / 'classes'),
                    *[str(base / name) for name in files],
                    str(root / 'app/src/main/java/dev/local/newsoul/HomeCheck.java')], check=True)
    subprocess.run(['java', '-cp', str(base / 'classes'), 'dev.local.newsoul.HomeLifecycleTest'], check=True)
