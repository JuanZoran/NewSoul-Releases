package dev.local.newsoul;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ProfileBookmarkStoreTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt action) throws Exception {
        checks++; try {action.run();} catch(IOException|IllegalArgumentException expected){return;}
        throw new AssertionError("invalid operation accepted");
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("profile-bookmarks-test").toFile();
        ProfileBookmarkStore a=new ProfileBookmarkStore(root,"account-a");
        check(a.list().isEmpty(),"new account not empty");
        a.put("opaque/id=+", "昵称甲");
        long created=a.list().get(0).created;
        a.put("second", "昵称乙");
        a.put("opaque/id=+", "新昵称甲");
        check(a.list().size()==2,"duplicate bookmark");
        check(a.list().get(0).id.equals("second"),"duplicate changed ordering");
        check(a.list().get(1).name.equals("新昵称甲"),"name not updated");
        check(a.list().get(1).created==created,"creation time changed");
        check(new ProfileBookmarkStore(root,"account-a").contains("opaque/id=+"),"restart lost data");
        check(new ProfileBookmarkStore(root,"account-b").list().isEmpty(),"account isolation failed");
        rejects(() -> new ProfileBookmarkStore(root,""));
        rejects(() -> a.put("", "name")); rejects(() -> a.put("id", " "));
        a.remove("second"); a.remove("missing");
        check(a.list().size()==1 && !a.contains("second"),"remove failed");
        ExecutorService pool=Executors.newFixedThreadPool(4);
        List<Future<?>> futures=new ArrayList<>();
        for(int i=0;i<40;i++){ final int n=i; futures.add(pool.submit(() -> {
            try {new ProfileBookmarkStore(root,"account-a").put("parallel-"+n,"name"+n);}
            catch(Exception e){throw new RuntimeException(e);}
        }));}
        for(Future<?> f:futures)f.get(); pool.shutdown();
        check(a.list().size()==41,"concurrent instances lost updates");
        for(File file:new File(root,"newsoul-profile-bookmarks").listFiles()) {
            check(!file.getName().endsWith(".tmp"),"temporary file left behind");
        }
        File index=new File(root,"newsoul-profile-bookmarks").listFiles()[0];
        byte[] corrupt=new byte[]{0,1,2,3}; Files.write(index.toPath(),corrupt);
        rejects(() -> a.list()); rejects(() -> a.put("third","three")); rejects(() -> a.remove("parallel-0"));
        check(Arrays.equals(corrupt,Files.readAllBytes(index.toPath())),"damaged index overwritten");
        File blocked=Files.createTempDirectory("profile-bookmark-write-failure").toFile();
        Files.write(new File(blocked,"newsoul-profile-bookmarks").toPath(),new byte[]{1});
        rejects(() -> new ProfileBookmarkStore(blocked,"a").put("id","name"));
        System.out.println("ProfileBookmarkStore: "+checks+" checks passed");
    }
}
