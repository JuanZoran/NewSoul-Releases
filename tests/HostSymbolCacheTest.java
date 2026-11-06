package dev.local.newsoul;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class HostSymbolCacheTest {
    private static int count;
    private static void check(boolean ok) { count++; if (!ok) throw new AssertionError("Check " + count); }
    private static void apk(File file, byte payload) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("classes.dex")); zip.write(new byte[]{payload}); zip.closeEntry();
        }
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("host-cache-test");
        try {
            File base = root.resolve("base.apk").toFile(), split = root.resolve("split.apk").toFile();
            apk(base, (byte) 1); apk(split, (byte) 2);
            String first = HostSymbolCache.fingerprint(List.of(base, split), "rules1");
            check(first.equals(HostSymbolCache.fingerprint(List.of(split, base), "rules1")));
            check(!first.equals(HostSymbolCache.fingerprint(List.of(base, split), "rules2")));
            check(!first.equals(HostSymbolCache.fingerprint(List.of(base), "rules1")));
            long length=split.length(), time=split.lastModified();
            apk(split, (byte) 3); split.setLastModified(time);
            check(split.length()==length);
            check(!first.equals(HostSymbolCache.fingerprint(List.of(base, split), "rules1")));
            Properties values = new Properties(); values.setProperty("follow", "LExample;->a()V");
            File cache=root.resolve("cache/symbols.properties").toFile();
            HostSymbolCache.write(cache, first, values);
            check(HostSymbolCache.read(cache, first).getProperty("follow").equals("LExample;->a()V"));
            check(HostSymbolCache.read(cache, "other-host").isEmpty());
            check(values.getProperty("fingerprint")==null);
            Files.writeString(cache.toPath(), "fingerprint="+first+"\nbroken=\\uBAD!\n");
            check(HostSymbolCache.read(cache, first).isEmpty());
            check(HostSymbolCache.unique(List.of("a")).equals("a"));
            for (List<String> candidates : List.of(Collections.<String>emptyList(), List.of("a", "b"), List.of("a", "a"))) {
                try { HostSymbolCache.unique(candidates); throw new AssertionError("Accepted ambiguous result"); }
                catch (IllegalStateException expected) { count++; }
            }
            System.out.println(count + " host-cache checks passed");
        } finally {
            try (var paths=Files.walk(root)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p); }
        }
    }
}
