package dev.local.newsoul;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class VoiceOutgoingStoreTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static File root() throws IOException { return Files.createTempDirectory("voice-outgoing-test").toFile(); }
    static File source(File root) throws IOException {
        File source = new File(root, "source.audio");
        Files.write(source.toPath(), new byte[]{1, 2, 3});
        return source;
    }
    static void full(File root, File source) throws IOException {
        try { VoiceOutgoingStore.copy(root, source); throw new AssertionError("Expected quota failure"); }
        catch (VoiceOutgoingStore.FullException expected) {}
    }
    public static void main(String[] args) throws Exception {
        List<File> roots = new ArrayList<>();
        try {
            File r = root(); roots.add(r); File s = source(r);
            File a = VoiceOutgoingStore.copy(r, s), b = VoiceOutgoingStore.copy(r, s);
            check(!a.equals(b) && Arrays.equals(Files.readAllBytes(a.toPath()), Files.readAllBytes(s.toPath())));
            s.delete(); check(a.exists() && b.exists()); // Deleting the library source preserves messages.

            r = root(); roots.add(r); s = source(r);
            File dir = new File(r, "newsoul-voice-outgoing"); check(dir.mkdir());
            File old = new File(dir, "old.audio");
            try (RandomAccessFile f = new RandomAccessFile(old, "rw")) { f.setLength(VoiceOutgoingStore.MAX_BYTES - s.length()); }
            File edge = VoiceOutgoingStore.copy(r, s); check(edge.isFile());
            full(r, s); check(old.length() == VoiceOutgoingStore.MAX_BYTES - s.length() && dir.list().length == 2);
            // Accounting is reconstructed from disk, not a process-local counter.
            edge.delete(); check(VoiceOutgoingStore.copy(r, s).isFile());

            r = root(); roots.add(r); s = source(r); dir = new File(r, "newsoul-voice-outgoing"); check(dir.mkdir());
            for (int i = 0; i < VoiceOutgoingStore.MAX_FILES; i++) check(new File(dir, i + ".audio").createNewFile());
            full(r, s); check(dir.list().length == VoiceOutgoingStore.MAX_FILES);
            check(new File(dir, "0.audio").delete());
            final File concurrentRoot = r, concurrentSource = s;
            java.util.concurrent.atomic.AtomicInteger admitted = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<Throwable> unexpected = new java.util.concurrent.atomic.AtomicReference<>();
            Runnable send = () -> { try { VoiceOutgoingStore.copy(concurrentRoot, concurrentSource); admitted.incrementAndGet(); }
                catch (VoiceOutgoingStore.FullException expected) {} catch (Throwable error) { unexpected.set(error); } };
            Thread first = new Thread(send), second = new Thread(send);
            first.start(); second.start(); first.join(); second.join();
            check(unexpected.get() == null && admitted.get() == 1 && dir.list().length == VoiceOutgoingStore.MAX_FILES);


            r = root(); roots.add(r); s = source(r);
            try (RandomAccessFile f = new RandomAccessFile(s, "rw")) { f.setLength(VoiceLibraryStore.MAX_BYTES + 1); }
            try { VoiceOutgoingStore.copy(r, s); throw new AssertionError(); } catch (IOException expected) {}
            check(new File(r, "newsoul-voice-outgoing").list().length == 0);
            // Source length can underreport a partial copy: enforce the streaming limit and rollback.
            File growing = new File(s.getPath()) { @Override public long length() { return 1; } };
            try { VoiceOutgoingStore.copy(r, growing); throw new AssertionError(); } catch (IOException expected) {}
            check(new File(r, "newsoul-voice-outgoing").list().length == 0);
            System.out.println("Outgoing audio: unique copies, source deletion, persistent accounting, byte/count boundaries, concurrent admission, partial rollback passed");
        } finally {
            for (File r : roots) try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(r.toPath())) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); } });
            }
        }
    }
}
