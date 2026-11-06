#!/usr/bin/env python3
"""Execute actual photo transaction and voice send methods with failure-injection fakes."""
from pathlib import Path
import subprocess
import tempfile

photo = Path('app/src/main/java/dev/local/newsoul/PrivacyPhotoControls.java').read_text()
photo = photo[photo.index('    private static Uri saveBitmap('):photo.index('    private static Method findMethod(')]
photo_test = r'''
package dev.local.newsoul;
import java.io.*;
class PhotoTransactionTest {
 static class Uri {}
 static class ContentValues { void put(String k,Object v){} void clear(){} }
 static class MediaStore {static class Images {static class Media {static String DISPLAY_NAME="name",MIME_TYPE="mime",RELATIVE_PATH="path",IS_PENDING="pending";static Uri EXTERNAL_CONTENT_URI=new Uri();}}}
 static class Bitmap {static class CompressFormat {static Object PNG=new Object();} boolean ok=true; boolean compress(Object f,int q,OutputStream o){return ok;}}
 static class Context {ContentResolver resolver;ContentResolver getContentResolver(){return resolver;}}
 static class ContentResolver {int deletes,updates;String mode;boolean closed;
  Uri insert(Uri u,ContentValues v){return mode.equals("insert-null")?null:new Uri();}
  OutputStream openOutputStream(Uri u,String m)throws IOException {
   if(mode.equals("open-throw"))throw new IOException("open");
   if(mode.equals("open-null"))return null;
   return new ByteArrayOutputStream(){public void close()throws IOException {closed=true;if(mode.equals("close"))throw new IOException("close");}};
  }
  int delete(Uri u,String a,String[] b){deletes++;if(mode.equals("rollback"))throw new IllegalStateException("rollback");return 1;}
  int update(Uri u,ContentValues v,String a,String[] b){if(!closed)throw new AssertionError("Publish before close");updates++;if(mode.equals("publish"))throw new IllegalStateException("publish");return mode.equals("zero")?0:1;}
 }
''' + photo + r'''
 public static void main(String[] args)throws Exception {
  for(String mode:new String[]{"ok","insert-null","open-throw","open-null","compress","close","publish","zero","rollback"}){
   Context c=new Context();c.resolver=new ContentResolver();c.resolver.mode=mode;Bitmap b=new Bitmap();b.ok=!mode.equals("compress")&&!mode.equals("rollback");
   try {Uri u=saveBitmap(c,b);if(!mode.equals("ok")&&!mode.equals("insert-null"))throw new AssertionError("Expected failure: "+mode);if((u==null)!=mode.equals("insert-null"))throw new AssertionError();}
   catch(Exception e){if(mode.equals("ok")||mode.equals("insert-null"))throw e;if(mode.equals("rollback")&&(e.getSuppressed().length!=1||!e.getMessage().contains("compression")))throw new AssertionError("Preserve primary failure");}
   if(c.resolver.deletes!=(mode.equals("ok")||mode.equals("insert-null")?0:1))throw new AssertionError("Rollback: "+mode);
   if(mode.equals("close")&&c.resolver.updates!=0)throw new AssertionError();
  }
  System.out.println("Photo transaction: success, null insert/output, open/compress/close/publish/zero-update failures and rollback failure passed");
 }
}
'''
voice = Path('app/src/main/java/dev/local/newsoul/VoiceLibraryControls.java').read_text()
voice = voice[voice.index('        void send(VoiceLibraryStore.Entry entry)'):voice.index('        void play(VoiceLibraryStore.Entry entry)')]
voice_test=r'''
package dev.local.newsoul;
import java.io.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;
class VoiceSubmissionTest {
 static class Queue {List<Runnable> queue=new ArrayList<>();boolean accepting=true;void execute(Runnable r){queue.add(r);}boolean post(Runnable r){if(!accepting)return false;queue.add(r);return true;}void drain(){while(!queue.isEmpty())queue.remove(0).run();}}
 static class Activity {File cache;File getCacheDir(){return cache;}}
 static class Host {boolean throwsAfterQueued;int calls;public void send(String path,int duration,Object ignored){calls++;if(!new File(path).isFile())throw new AssertionError();if(throwsAfterQueued)throw new IllegalStateException("ambiguous submission");}}
 static class Log {static void i(String a,String b){}}
 static void toast(Activity a,String b){} static void failure(Activity a,String b,Throwable e){}
 static class Fragment {Host Q; Fragment(Host host){Q=host;}}
 static class Chat {final Field sender=Fragment.class.getDeclaredField("Q"); Chat()throws Exception{sender.setAccessible(true);}}
 boolean busy,closed,valid=true;Activity activity=new Activity();VoiceLibraryStore store;Object fragment;Chat chat=new Chat();Method sendLocal=Host.class.getMethod("send",String.class,int.class,Object.class);Queue main=new Queue(),io=new Queue();
 VoiceSubmissionTest()throws Exception{}
 void validate(){if(!valid)throw new IllegalStateException("stale session");}void stop(){}void close(){closed=true;}
''' + voice + r'''
 public static void main(String[] args)throws Exception {
  for(String mode:new String[]{"success","ambiguous","stale","post-rejected"}){
   Path root=Files.createTempDirectory("voice-submit-test");
   try {
    VoiceSubmissionTest t=new VoiceSubmissionTest();t.activity.cache=root.toFile();t.store=new VoiceLibraryStore(root.toFile(),"owner");
    VoiceLibraryStore.Entry e=t.store.add(new ByteArrayInputStream(new byte[]{1,2,3}),"test",1);Host host=new Host();host.throwsAfterQueued=mode.equals("ambiguous");t.fragment=new Fragment(host);
    t.send(e);if(mode.equals("stale"))t.valid=false;if(mode.equals("post-rejected"))t.main.accepting=false;t.io.drain();t.main.drain();
    File[] copies=new File(root.toFile(),"newsoul-voice-outgoing").listFiles();boolean submitted=mode.equals("success")||mode.equals("ambiguous");
    if(copies.length!=(submitted?1:0)||host.calls!=(submitted?1:0))throw new AssertionError(mode);
    if(submitted){t.store.delete(e.id);if(!copies[0].isFile())throw new AssertionError("Library delete affected message");}
   } finally {try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException x){throw new UncheckedIOException(x);}});}}
  }
  System.out.println("Voice submission: success/ambiguous retain, stale session/post rejection remove, library deletion preserves queued audio passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='newsoul-media-transaction-') as temp:
    temp = Path(temp)
    photo_path = temp/'PhotoTransactionTest.java'; photo_path.write_text(photo_test)
    voice_path = temp/'VoiceSubmissionTest.java'; voice_path.write_text(voice_test)
    subprocess.run(['javac','-encoding','UTF-8','-d',str(temp),str(photo_path),str(voice_path),
                    'app/src/main/java/dev/local/newsoul/VoiceLibraryStore.java',
                    'app/src/main/java/dev/local/newsoul/VoiceOutgoingStore.java','tests/VoiceOutgoingStoreTest.java'],check=True)
    for name in ['PhotoTransactionTest','VoiceOutgoingStoreTest','VoiceSubmissionTest']:
        subprocess.run(['java','-cp',str(temp),'dev.local.newsoul.'+name],check=True)
