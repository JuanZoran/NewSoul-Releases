package dev.local.newsoul;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Local account-scoped index. Atomic replacement never discards an unreadable index. */
final class ProfileBookmarkStore {
    private static final Object LOCK = new Object();
    private static final int MAGIC = 0x4e535031;
    static final int LIMIT = 1000;
    private final File file;
    static final class Entry {
        final String id, name;
        final long created;
        Entry(String id, String name, long created) { this.id=id; this.name=name; this.created=created; }
    }
    ProfileBookmarkStore(File files, String account) throws Exception {
        if (account == null || account.trim().isEmpty()) throw new IllegalArgumentException("请先登录 Soul");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte b : digest) key.append(String.format(Locale.ROOT, "%02x", b & 255));
        file = new File(new File(files, "newsoul-profile-bookmarks"), key + ".bin");
    }
    List<Entry> list() throws IOException { synchronized (LOCK) { return read(); } }
    boolean contains(String id) throws IOException {
        synchronized (LOCK) { for (Entry e : read()) if (e.id.equals(id)) return true; return false; }
    }
    void put(String id, String name) throws IOException {
        validateId(id);
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > 120) throw new IllegalArgumentException("昵称不可用");
        synchronized (LOCK) {
            List<Entry> entries = read();
            for (int i=0; i<entries.size(); i++) if (entries.get(i).id.equals(id)) {
                entries.set(i, new Entry(id, name, entries.get(i).created)); write(entries); return;
            }
            if (entries.size() >= LIMIT) throw new IOException("插眼列表已满，请先移除部分记录");
            entries.add(0, new Entry(id, name, System.currentTimeMillis())); write(entries);
        }
    }
    void remove(String id) throws IOException {
        validateId(id);
        synchronized (LOCK) {
            List<Entry> entries = read();
            if (entries.removeIf(e -> e.id.equals(id))) write(entries);
        }
    }
    private List<Entry> read() throws IOException {
        List<Entry> entries = new ArrayList<>();
        if (!file.exists()) return entries;
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != MAGIC) throw new IOException("插眼记录格式异常");
            int count = in.readInt();
            if (count < 0 || count > LIMIT) throw new IOException("插眼记录数量异常");
            Set<String> ids = new HashSet<>();
            for (int i=0; i<count; i++) {
                String id=in.readUTF(), name=in.readUTF(); long created=in.readLong();
                try { validateId(id); } catch (IllegalArgumentException error) { throw new IOException("插眼记录损坏", error); }
                if (name.trim().isEmpty() || name.length()>120 || created<0 || !ids.add(id))
                    throw new IOException("插眼记录损坏");
                entries.add(new Entry(id,name,created));
            }
            if (in.read() != -1) throw new IOException("插眼记录格式异常");
        }
        return entries;
    }
    private void write(List<Entry> entries) throws IOException {
        File parent=file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建插眼列表");
        File temp=new File(parent,file.getName()+".tmp");
        try {
            try (FileOutputStream stream=new FileOutputStream(temp); DataOutputStream out=new DataOutputStream(stream)) {
                out.writeInt(MAGIC); out.writeInt(entries.size());
                for (Entry e:entries) { out.writeUTF(e.id); out.writeUTF(e.name); out.writeLong(e.created); }
                out.flush(); stream.getFD().sync();
            }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp.toPath()); }
    }
    private static void validateId(String id) {
        if (id==null || id.trim().isEmpty() || id.length()>512 || !id.equals(id.trim()))
            throw new IllegalArgumentException("主页标识不可用");
    }
}
