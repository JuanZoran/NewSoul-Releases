package dev.local.newsoul;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real store, real file locks and child JVM termination; no simulated store implementation. */
public final class VoiceLibraryRecoveryTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    interface Operation { void run() throws Exception; }
    static void rejects(Operation operation) throws Exception {
        try { operation.run(); throw new AssertionError("Unexpected successful operation"); }
        catch (IOException expected) { checks++; }
    }
    static ByteArrayInputStream sample() { return new ByteArrayInputStream("#!AMR\nvoice-data".getBytes()); }
    static VoiceLibraryStore store(Path base, String account) throws Exception { return new VoiceLibraryStore(base.toFile(), account); }
    static Path directory(VoiceLibraryStore store) throws Exception {
        var field = VoiceLibraryStore.class.getDeclaredField("root"); field.setAccessible(true);
        return ((File) field.get(store)).toPath();
    }
    static Path file(Path root, String id, String suffix) { return root.resolve(id + suffix); }
    static Process child(String action, Path base) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), VoiceLibraryRecoveryTest.class.getName(),
                action, base.toString()).redirectErrorStream(true).start();
    }
    static void waitFile(Path file, Process process) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(file) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(5);
        check(Files.exists(file), "child reached the writing phase");
    }
    static void childMode(String action, Path base) throws Exception {
        VoiceLibraryStore target = store(base, "process");
        if (action.equals("list")) { check(target.list().size() == 1, "other process sees completed commit"); return; }
        target.add(new InputStream() {
            boolean written;
            @Override public int read() { throw new AssertionError("bulk reader expected"); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (!written) {
                    written = true; Arrays.fill(bytes, offset, offset + 16, (byte) 1); return 16;
                }
                if (action.equals("crash")) Runtime.getRuntime().halt(73);
                Files.writeString(base.resolve("writer-ready"), "ready");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(base.resolve("writer-release")) && System.nanoTime() < deadline) {
                    try { Thread.sleep(5); } catch (InterruptedException error) { throw new IOException(error); }
                }
                if (!Files.exists(base.resolve("writer-release"))) throw new IOException("writer deadline");
                return -1;
            }
        }, "进行中的保存", 3);
    }
    static void recovery(Path base) throws Exception {
        VoiceLibraryStore target = store(base, "recovery"); Path root = directory(target);
        VoiceLibraryStore.Entry saved = target.add(sample(), "保留原备注", 3);
        Path meta = file(root, saved.id, ".meta"), temp = file(root, saved.id, ".meta.tmp");
        Files.move(meta, temp); // completed metadata write, killed just before its commit rename
        check(store(base, "recovery").list().get(0).note.equals("保留原备注"), "recover completed metadata transaction");
        check(Files.exists(meta) && !Files.exists(temp), "recovered record has one authoritative metadata file");
        Path tomb = file(root, saved.id, ".deleted");
        Files.move(meta, tomb); // interrupted after hide, before removing audio
        check(target.list().size() == 1 && Files.exists(meta), "rollback deletion while audio still exists");
        Files.move(meta, tomb); Files.delete(saved.audio.toPath());
        check(target.list().isEmpty() && !Files.exists(tomb), "finish deletion whose audio was already removed");

        VoiceLibraryStore.Entry kept = target.add(sample(), "已保存", 2);
        Files.write(file(root, kept.id, ".meta.tmp"), new byte[]{0});
        Files.write(file(root, kept.id, ".part"), new byte[]{1});
        String orphan = UUID.randomUUID().toString();
        Files.write(file(root, orphan, ".audio"), new byte[]{2,3,4});
        Files.write(file(root, orphan, ".meta.tmp"), new byte[]{0});
        Files.write(root.resolve("not-a-uuid.audio"), new byte[]{5});
        Files.write(root.resolve("readme.txt"), new byte[]{6});
        Files.write(root.resolve("unknown.meta"), new byte[]{7});
        VoiceLibraryStore.PendingFiles pending = target.pendingFiles();
        check(pending.count == 4 && pending.bytes == 6, "only known incomplete files included");
        check(target.list().size() == 1 && target.read(kept.id).note.equals("已保存"), "incomplete sidecars do not replace committed metadata");
        target.discardPending(pending);
        check(target.pendingFiles().count == 0 && target.list().size() == 1, "cleanup preserves committed record and audio");
        check(Files.exists(root.resolve("not-a-uuid.audio")) && Files.exists(root.resolve("readme.txt"))
                && Files.exists(root.resolve("unknown.meta")), "unknown files preserved");

        String protectedId = UUID.randomUUID().toString();
        Files.write(file(root, protectedId, ".meta"), new byte[]{0});
        Files.write(file(root, protectedId, ".audio"), new byte[]{9});
        Files.write(file(root, protectedId, ".meta.tmp"), new byte[]{0});
        check(target.pendingFiles().count == 0, "uncertain existing metadata protects its sidecars");
        rejects(target::list);
        check(Files.exists(file(root, protectedId, ".audio")), "corruption never silently erases audio");
    }
    static void snapshots(Path base) throws Exception {
        VoiceLibraryStore target = store(base, "snapshot"); Path root = directory(target);
        String id = UUID.randomUUID().toString(); Path part = file(root, id, ".part");
        Files.write(part, new byte[]{1,2,3}); FileTime stamp = Files.getLastModifiedTime(part);
        VoiceLibraryStore.PendingFiles old = target.pendingFiles();
        Files.write(part, new byte[]{3,2,1}); Files.setLastModifiedTime(part, stamp);
        rejects(() -> target.discardPending(old));
        check(Files.exists(part), "same length and timestamp cannot conceal changed content");
        VoiceLibraryStore.PendingFiles reviewed = target.pendingFiles();
        String later = UUID.randomUUID().toString(); Path newPart = file(root, later, ".part");
        Files.write(newPart, new byte[]{4});
        target.discardPending(reviewed);
        check(!Files.exists(part) && Files.exists(newPart), "later unreviewed files are not removed");
        VoiceLibraryStore.PendingFiles another = target.pendingFiles();
        rejects(() -> store(base, "another-account").discardPending(another));
        check(Files.exists(newPart), "cross-account snapshot refused");

        VoiceLibraryStore.Entry valid = target.add(sample(), "重新完成", 2);
        Path validMeta = file(root, valid.id, ".meta"), parked = base.resolve("parked-meta");
        Files.move(validMeta, parked);
        VoiceLibraryStore.PendingFiles beforeRecovery = target.pendingFiles();
        Files.move(parked, validMeta);
        rejects(() -> target.discardPending(beforeRecovery));
        check(target.read(valid.id).audio.isFile(), "later committed metadata protects formerly orphaned audio");

        Path outside = base.resolve("outside"); Files.write(outside, new byte[]{8});
        Path link = file(root, UUID.randomUUID().toString(), ".audio");
        Files.createSymbolicLink(link, outside);
        VoiceLibraryStore.PendingFiles noLinks = target.pendingFiles();
        target.discardPending(noLinks);
        check(Files.exists(outside) && Files.isSymbolicLink(link), "symlink and target excluded from cleanup");
    }
    static void quota(Path base) throws Exception {
        VoiceLibraryStore target = store(base, "quota"); Path root = directory(target);
        for (int i = 0; i < 26; i++) {
            try (RandomAccessFile audio = new RandomAccessFile(file(root, UUID.randomUUID().toString(), ".audio").toFile(), "rw")) {
                audio.setLength(VoiceLibraryStore.MAX_BYTES);
            }
        }
        rejects(() -> target.add(sample(), "不能绕过容量", 1));
        VoiceLibraryStore.PendingFiles leftovers = target.pendingFiles();
        check(leftovers.count == 26 && leftovers.bytes == 26 * VoiceLibraryStore.MAX_BYTES, "all orphan bytes counted");
        target.discardPending(leftovers);
        check(target.add(sample(), "清理后可保存", 1).audio.isFile(), "explicit cleanup frees quota");
        Path nested = root.resolve("unknown-directory"); Files.createDirectory(nested);
        Path unknown = nested.resolve("unknown.bin");
        try (RandomAccessFile file = new RandomAccessFile(unknown.toFile(), "rw")) { file.setLength(500L * 1024 * 1024); }
        rejects(() -> target.add(sample(), "未知文件也计量", 1));
        check(target.pendingFiles().count == 0 && Files.exists(unknown), "unknown nested bytes count without cleanup permission");
    }
    static void threads(Path base) throws Exception {
        VoiceLibraryStore one = store(base, "threads"), two = store(base, "threads");
        CountDownLatch writing = new CountDownLatch(1), release = new CountDownLatch(1), scanning = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = workers.submit(() -> {
                try { one.add(new InputStream() {
                    boolean first = true;
                    public int read() { throw new AssertionError(); }
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        if (first) { first = false; bytes[offset] = 1; return 1; }
                        writing.countDown();
                        try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                        catch (InterruptedException error) { throw new IOException(error); }
                        return -1;
                    }
                }, "写入中", 1); } catch (Throwable error) { failure.set(error); }
            });
            check(writing.await(5, TimeUnit.SECONDS), "first instance is writing");
            Future<VoiceLibraryStore.PendingFiles> reader = workers.submit(() -> { scanning.countDown(); return two.pendingFiles(); });
            check(scanning.await(5, TimeUnit.SECONDS), "second instance began scanning");
            try { reader.get(100, TimeUnit.MILLISECONDS); throw new AssertionError("scanner did not wait for writer"); }
            catch (TimeoutException expected) { checks++; }
            release.countDown(); writer.get(5, TimeUnit.SECONDS);
            check(reader.get(5, TimeUnit.SECONDS).count == 0 && two.list().size() == 1 && failure.get() == null,
                    "other instance cannot offer or remove active partial audio");
        } finally { release.countDown(); workers.shutdownNow(); }
    }
    static void concurrentAdmission(Path base) throws Exception {
        VoiceLibraryStore one = store(base, "admission"), two = store(base, "admission");
        VoiceLibraryStore.Entry seed = one.add(sample(), "已有语音", 1); Path root = directory(one);
        for (int index = 1; index < 199; index++) {
            String id = UUID.randomUUID().toString();
            Files.copy(seed.audio.toPath(), file(root, id, ".audio"));
            Files.copy(file(root, seed.id, ".meta"), file(root, id, ".meta"));
        }
        ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (VoiceLibraryStore target : new VoiceLibraryStore[]{one, two}) results.add(workers.submit(() -> {
                start.await();
                try { target.add(sample(), "最后一个名额", 1); return true; }
                catch (VoiceLibraryStore.StateException full) { return false; }
            }));
            start.countDown(); int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(5, TimeUnit.SECONDS)) accepted++;
            check(accepted == 1 && one.list().size() == 200, "concurrent instances cannot exceed entry cap");
        } finally { start.countDown(); workers.shutdownNow(); }
    }
    static void processes(Path base) throws Exception {
        Path crashed = base.resolve("crashed"); Files.createDirectory(crashed);
        Process crash = child("crash", crashed);
        check(crash.waitFor(10, TimeUnit.SECONDS) && crash.exitValue() == 73, "writer JVM halted without finally cleanup");
        VoiceLibraryStore restarted = store(crashed, "process");
        VoiceLibraryStore.PendingFiles residual = restarted.pendingFiles();
        check(restarted.list().isEmpty() && residual.count == 1 && residual.bytes == 16, "crashed part preserved and manageable after restart");
        restarted.discardPending(residual);
        check(restarted.pendingFiles().count == 0, "reviewed crash residual removed");
        Path live = base.resolve("live"); Files.createDirectory(live);
        Process writer = child("block", live), reader = null;
        try {
            waitFile(live.resolve("writer-ready"), writer);
            reader = child("list", live);
            check(!reader.waitFor(200, TimeUnit.MILLISECONDS), "cross-process reader waits on active write lock");
            Files.writeString(live.resolve("writer-release"), "release");
            check(writer.waitFor(10, TimeUnit.SECONDS) && writer.exitValue() == 0, "first process completes commit");
            check(reader.waitFor(10, TimeUnit.SECONDS) && reader.exitValue() == 0, "second process reads completed data");
        } finally { writer.destroyForcibly(); if (reader != null) reader.destroyForcibly(); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2) { childMode(args[0], Path.of(args[1])); return; }
        Path base = Files.createTempDirectory("voice-recovery-test-");
        try {
            recovery(base); snapshots(base); quota(base); threads(base); concurrentAdmission(base); processes(base);
            System.out.println("VoiceLibraryRecovery: " + checks + " checks passed");
        } finally {
            try (var paths = Files.walk(base)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
