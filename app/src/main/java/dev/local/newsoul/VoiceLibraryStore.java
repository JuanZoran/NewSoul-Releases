package dev.local.newsoul;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Account-private, durable audio copies. A metadata rename is the commit point. */
final class VoiceLibraryStore {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int MAGIC = 0x4e535631;
    private static final long MAX_LIBRARY_BYTES = 500L * 1024 * 1024;
    private static final Map<String, Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private final File root;
    private final Object lock;

    static final class StateException extends IOException {
        StateException(String message) { super(message); }
        StateException(String message, Throwable cause) { super(message, cause); }
    }

    /** A reviewed batch of known, uncommitted files; never includes committed or unknown data. */
    static final class PendingFiles {
        final int count;
        final long bytes;
        private final String directory;
        private final Map<String, String> fingerprints;
        private PendingFiles(String directory, long bytes, Map<String, String> fingerprints) {
            this.directory = directory; this.bytes = bytes; this.count = fingerprints.size();
            this.fingerprints = Collections.unmodifiableMap(fingerprints);
        }
    }

    private interface Operation<T> { T run() throws IOException; }

    private <T> T locked(Operation<T> operation) throws IOException {
        // Java's per-instance monitor cannot protect a second Session or a second process.
        synchronized (lock) {
            Path lockFile = new File(root, ".lock").toPath();
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    FileLock ignored = channel.lock()) {
                recover();
                return operation.run();
            }
        }
    }

    static final class Entry {
        final String id, note;
        final int duration;
        final long created;
        final File audio;
        Entry(String id, String note, int duration, long created, File audio) {
            this.id = id; this.note = note; this.duration = duration; this.created = created; this.audio = audio;
        }
    }

    VoiceLibraryStore(File files, String account) throws Exception {
        if (account == null || account.isEmpty()) throw new IllegalArgumentException("Missing account");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte b : hash) key.append(String.format(Locale.ROOT, "%02x", b & 255));
        root = new File(files, "newsoul-voice-library/" + key).getCanonicalFile();
        if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory())
            throw new IOException("Cannot create library");
        lock = LOCKS.computeIfAbsent(root.getPath(), ignored -> new Object());
    }

    List<Entry> list() throws IOException { return locked(this::listLocked); }

    private List<Entry> listLocked() throws IOException {
        File[] files = root.listFiles((dir, name) -> name.endsWith(".meta"));
        if (files == null) throw new IOException("Cannot read library");
        List<Entry> entries = new ArrayList<>();
        for (File file : files) {
            String id = knownId(file.getName(), ".meta");
            if (id == null) continue;
            try { entries.add(readLocked(id)); }
            catch (IllegalArgumentException | IOException corrupt) {
                // Never erase damaged records silently or replace the entire index.
                throw new IOException("语音库记录损坏，请保留数据后排查", corrupt);
            }
        }
        entries.sort(Comparator.comparingLong((Entry e) -> e.created).reversed());
        return entries;
    }

    Entry read(String id) throws IOException { return locked(() -> readLocked(id)); }

    private Entry readLocked(String id) throws IOException {
        validId(id);
        return readMetadata(new File(root, id + ".meta"), id, true);
    }

    private Entry readMetadata(File metadata, String id, boolean requireAudio) throws IOException {
        if (!Files.isRegularFile(metadata.toPath(), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing voice record");
        try (DataInputStream in = new DataInputStream(new FileInputStream(metadata))) {
            if (in.readInt() != MAGIC) throw new IOException("Unknown voice record");
            String note = in.readUTF();
            int duration = in.readInt();
            long created = in.readLong();
            File audio = new File(root, id + ".audio");
            if (duration <= 0 || note.trim().isEmpty() || note.length() > 120 || in.read() != -1)
                throw new IOException("Invalid voice record");
            if (requireAudio && (!Files.isRegularFile(audio.toPath(), LinkOption.NOFOLLOW_LINKS)
                    || audio.length() <= 0 || audio.length() > MAX_BYTES))
                throw new IOException("Missing voice file");
            return new Entry(id, note, duration, created, audio);
        }
    }

    Entry add(InputStream input, String note, int duration) throws IOException {
        String cleanNote = note(note);
        return locked(() -> addLocked(input, cleanNote, duration));
    }

    private Entry addLocked(InputStream input, String note, int duration) throws IOException {
        if (duration <= 0) throw new IllegalArgumentException("Invalid duration");
        List<Entry> entries = listLocked();
        // Include incomplete, damaged and unknown files, including nested directories.
        // Reserve the maximum audio size and metadata space while holding the writer lock.
        if (entries.size() >= 200) throw new StateException("语音库已满，请先删除部分语音");
        if (directoryBytes() > MAX_LIBRARY_BYTES - MAX_BYTES - 1024) {
            if (pendingLocked().count > 0)
                throw new StateException("语音库空间不足，请打开语音库处理未完成文件");
            throw new StateException(entries.isEmpty() ? "语音库空间不足，请保留日志后反馈"
                    : "语音库空间不足，请删除部分语音后重试");
        }
        String id = UUID.randomUUID().toString();
        File audio = new File(root, id + ".audio"), temp = new File(root, id + ".part");
        Entry entry = new Entry(id, note, duration, System.currentTimeMillis(), audio);
        boolean committed = false;
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                copyBounded(input, out);
                out.getFD().sync();
            }
            Files.move(temp.toPath(), audio.toPath(), StandardCopyOption.ATOMIC_MOVE);
            write(entry);
            committed = true;
            return entry;
        } finally {
            if (!committed) { Files.deleteIfExists(temp.toPath()); Files.deleteIfExists(audio.toPath()); }
        }
    }

    void rename(String id, String note) throws IOException {
        locked(() -> {
            Entry old = readLocked(id);
            write(new Entry(id, note(note), old.duration, old.created, old.audio));
            return null;
        });
    }

    void delete(String id) throws IOException {
        locked(() -> { deleteLocked(id); return null; });
    }

    private void deleteLocked(String id) throws IOException {
        Entry old = readLocked(id);
        // Hide the record atomically, restoring it if the audio cannot be removed.
        File meta = new File(root, id + ".meta"), tomb = new File(root, id + ".deleted");
        Files.move(meta.toPath(), tomb.toPath(), StandardCopyOption.ATOMIC_MOVE);
        try { Files.delete(old.audio.toPath()); }
        catch (IOException error) {
            Files.move(tomb.toPath(), meta.toPath(), StandardCopyOption.ATOMIC_MOVE);
            throw error;
        }
        Files.deleteIfExists(tomb.toPath());
    }

    /** Complete recoverable commits; unfinished deletion restores audio until it was removed. */
    private void recover() throws IOException {
        File[] files = root.listFiles();
        if (files == null) throw new IOException("Cannot read library");
        for (File file : files) {
            String name = file.getName(), id;
            if (name.endsWith(".deleted")) {
                id = knownId(name, ".deleted");
                if (id == null || exists(id + ".meta")) continue;
                try {
                    readMetadata(file, id, false);
                    if (!exists(id + ".audio")) Files.delete(file.toPath());
                    else {
                        readMetadata(file, id, true);
                        Files.move(file.toPath(), new File(root, id + ".meta").toPath(),
                                StandardCopyOption.ATOMIC_MOVE);
                    }
                } catch (IOException invalidOrUnavailable) {
                    // Keep uncertain files for an explicit, fingerprint-checked cleanup.
                }
            }
        }
        for (File file : files) {
            String id = knownId(file.getName(), ".meta.tmp");
            if (id == null || exists(id + ".meta") || exists(id + ".deleted")) continue;
            try {
                readMetadata(file, id, true);
                Files.move(file.toPath(), new File(root, id + ".meta").toPath(),
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException invalidOrUnavailable) {
                // An incomplete metadata write is not permission to discard its audio.
            }
        }
    }

    PendingFiles pendingFiles() throws IOException { return locked(this::pendingLocked); }

    private PendingFiles pendingLocked() throws IOException {
        File[] files = root.listFiles();
        if (files == null) throw new IOException("Cannot read library");
        Map<String, String> fingerprints = new TreeMap<>();
        long bytes = 0;
        for (File file : files) {
            String name = file.getName(), id = null;
            for (String suffix : new String[]{".audio", ".part", ".meta.tmp", ".deleted"}) {
                id = knownId(name, suffix);
                if (id != null) break;
            }
            if (id == null || !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
            // Never offer committed audio or sidecars beside an uncertain/corrupt record.
            // A valid committed record does allow disposal of its redundant temporary files.
            if (exists(id + ".meta")) {
                if (name.endsWith(".audio")) continue;
                try { readLocked(id); } catch (IOException | IllegalArgumentException corrupt) { continue; }
            }
            fingerprints.put(name, fingerprint(file));
            bytes = saturatedAdd(bytes, file.length());
        }
        return new PendingFiles(root.getPath(), bytes, fingerprints);
    }

    void discardPending(PendingFiles reviewed) throws IOException {
        locked(() -> {
            if (reviewed == null || !root.getPath().equals(reviewed.directory))
                throw new StateException("文件已变化，请返回语音库重新检查");
            PendingFiles current = pendingLocked();
            for (Map.Entry<String, String> file : reviewed.fingerprints.entrySet()) {
                if (!file.getValue().equals(current.fingerprints.get(file.getKey())))
                    throw new StateException("文件已变化，请返回语音库重新检查");
            }
            // New candidates are intentionally excluded: the user reviewed this exact batch.
            try {
                for (String name : reviewed.fingerprints.keySet()) Files.delete(new File(root, name).toPath());
            } catch (IOException failure) {
                throw new StateException("处理未完成，请返回语音库重新检查", failure);
            }
            return null;
        });
    }

    private boolean exists(String name) {
        return Files.exists(new File(root, name).toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static String knownId(String name, String suffix) {
        if (!name.endsWith(suffix)) return null;
        String id = name.substring(0, name.length() - suffix.length());
        try { validId(id); return id; } catch (IllegalArgumentException unknown) { return null; }
    }

    private static String fingerprint(File file) throws IOException {
        try {
            BasicFileAttributes stat = Files.readAttributes(file.toPath(), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[16384]; int count;
                while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
            }
            return stat.size() + ":" + stat.lastModifiedTime() + ":" + stat.fileKey()
                    + ":" + Base64.getEncoder().encodeToString(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private long directoryBytes() throws IOException {
        final long[] total = {0};
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                total[0] = saturatedAdd(total[0], attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    private static long saturatedAdd(long one, long two) {
        return Long.MAX_VALUE - one < two ? Long.MAX_VALUE : one + two;
    }

    private void write(Entry entry) throws IOException {
        File temp = new File(root, entry.id + ".meta.tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temp);
                    DataOutputStream out = new DataOutputStream(stream)) {
                out.writeInt(MAGIC); out.writeUTF(entry.note); out.writeInt(entry.duration); out.writeLong(entry.created);
                out.flush(); stream.getFD().sync();
            }
            Files.move(temp.toPath(), new File(root, entry.id + ".meta").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp.toPath()); }
    }

    static String note(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("请填写备注");
        if (text.length() > 120) throw new IllegalArgumentException("备注最多 120 字");
        return text;
    }

    static void copyBounded(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16384]; long total = 0; int count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) throw new IOException("语音文件超过 20 MB");
            out.write(buffer, 0, count);
        }
        if (total == 0) throw new IOException("语音文件为空");
    }

    /** Preserve the container suffix when handing a local copy to Soul's uploader. */
    static String extension(File audio) throws IOException {
        byte[] header = new byte[16];
        int size;
        try (InputStream in = new FileInputStream(audio)) { size = in.read(header); }
        String magic = new String(header, 0, Math.max(0, size), StandardCharsets.ISO_8859_1);
        if (magic.startsWith("#!AMR")) return ".amr";
        if (magic.startsWith("RIFF")) return ".wav";
        if (magic.startsWith("OggS")) return ".ogg";
        if (size >= 8 && magic.substring(4, 8).equals("ftyp")) return ".m4a";
        if (magic.startsWith("fLaC")) return ".flac";
        if (size >= 2 && (header[0] & 255) == 255 && (header[1] & 0xf6) == 0xf0) return ".aac";
        if (magic.startsWith("ID3") || (size >= 2 && (header[0] & 255) == 255 && (header[1] & 0xe0) == 0xe0)) return ".mp3";
        return ".audio";
    }

    private static void validId(String id) {
        if (id == null || !id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw new IllegalArgumentException("Invalid voice id");
    }
}
