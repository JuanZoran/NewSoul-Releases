package dev.local.newsoul;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class VoiceLibraryStoreTest {
    private static int checks;
    private interface Operation { void run() throws Exception; }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static void reject(Operation action) throws Exception {
        try { action.run(); throw new AssertionError("Unexpected success"); }
        catch (IOException | IllegalArgumentException expected) { checks++; }
    }
    private static ByteArrayInputStream sample() { return new ByteArrayInputStream("#!AMR\nvoice-data".getBytes()); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("voice-store-test-");
        try {
            VoiceLibraryStore one = new VoiceLibraryStore(root.toFile(), "account-one");
            VoiceLibraryStore two = new VoiceLibraryStore(root.toFile(), "account-two");
            check(one.list().isEmpty());
            VoiceLibraryStore.Entry saved = one.add(sample(), "  我的备注 😊  ", 3);
            check(saved.note.equals("我的备注 😊") && saved.duration == 3);
            check(Arrays.equals(Files.readAllBytes(saved.audio.toPath()), sample().readAllBytes()));
            check(two.list().isEmpty());
            reject(() -> two.read(saved.id));
            VoiceLibraryStore reopened = new VoiceLibraryStore(root.toFile(), "account-one");
            check(reopened.list().size() == 1 && reopened.read(saved.id).note.equals(saved.note));
            one.rename(saved.id, "新备注");
            check(reopened.read(saved.id).note.equals("新备注"));
            check(reopened.read(saved.id).created == saved.created);
            reject(() -> one.rename(saved.id, " "));
            reject(() -> one.rename(saved.id, "a".repeat(121)));
            check(reopened.read(saved.id).note.equals("新备注"));
            reject(() -> one.read("../../etc/passwd"));
            reject(() -> one.delete("../"));
            reject(() -> one.add(sample(), "空时长", 0));
            reject(() -> one.add(new ByteArrayInputStream(new byte[0]), "空文件", 1));
            reject(() -> one.add(new InputStream() {
                int reads;
                public int read() throws IOException { if (reads++ < 16) return 1; throw new IOException("interrupted"); }
            }, "中断下载", 1));
            reject(() -> one.add(new InputStream() {
                long left = VoiceLibraryStore.MAX_BYTES + 1;
                public int read() { return left-- > 0 ? 0 : -1; }
                public int read(byte[] data, int offset, int length) {
                    if (left <= 0) return -1;
                    int count = (int) Math.min(left, length); left -= count; return count;
                }
            }, "过大文件", 1));
            check(one.list().size() == 1);
            try (var paths = Files.walk(root)) {
                check(paths.noneMatch(p -> p.toString().endsWith(".part") || p.toString().endsWith(".tmp")));
            }
            check(VoiceLibraryStore.extension(saved.audio).equals(".amr"));
            File mp3 = root.resolve("format-test").toFile();
            Files.write(mp3.toPath(), "ID3fake".getBytes()); check(VoiceLibraryStore.extension(mp3).equals(".mp3"));
            Files.write(mp3.toPath(), new byte[]{(byte)255, (byte)241, 0}); check(VoiceLibraryStore.extension(mp3).equals(".aac"));
            Files.write(mp3.toPath(), new byte[]{(byte)255, (byte)251, 0}); check(VoiceLibraryStore.extension(mp3).equals(".mp3"));
            Files.write(mp3.toPath(), new byte[]{0,0,0,24,'f','t','y','p'}); check(VoiceLibraryStore.extension(mp3).equals(".m4a"));
            File outgoing = root.resolve("sent-copy.amr").toFile();
            Files.copy(saved.audio.toPath(), outgoing.toPath());
            one.delete(saved.id);
            check(one.list().isEmpty() && !saved.audio.exists());
            check(outgoing.isFile() && outgoing.length() > 0);
            reject(() -> one.read(saved.id));
            reject(() -> new VoiceLibraryStore(root.toFile(), ""));
            VoiceLibraryStore.Entry corrupted = one.add(sample(), "保留损坏记录", 1);
            Path meta = corrupted.audio.toPath().resolveSibling(corrupted.id + ".meta");
            Files.write(meta, new byte[]{0});
            reject(one::list);
            check(Files.exists(meta) && corrupted.audio.exists());
            System.out.println("VoiceLibraryStore: " + checks + " checks passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
