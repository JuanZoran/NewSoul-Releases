package dev.local.newsoul;

import java.io.*;
import java.security.PublicKey;
import java.util.*;
import java.util.zip.*;

/** IO-worker only. Every on-disk diagnostic payload is already encrypted. */
final class DebugStore {
    static final long LIMIT = 4 * 1024 * 1024;
    private final File directory;
    private final PublicKey key;
    DebugStore(File directory, PublicKey key) throws IOException {
        this.directory = directory; this.key = key;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Storage unavailable");
        for (File file : files()) if (file.getName().endsWith(".tmp")) file.delete();
    }
    private File[] files() { File[] files = directory.listFiles(); return files == null ? new File[0] : files; }
    long size() { long size = 0; for (File file : files()) size += file.length(); return size; }
    boolean append(byte[] data) throws Exception {
        byte[] encrypted = DebugEnvelope.seal(data, key);
        if (size() + encrypted.length > LIMIT) return false;
        File target = new File(directory, String.format(Locale.ROOT, "%013d-%s.chunk", System.currentTimeMillis(), UUID.randomUUID()));
        File temporary = new File(directory, target.getName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(encrypted); out.getFD().sync();
            }
            if (!temporary.renameTo(target)) throw new IOException("Storage rename failed");
        } finally { temporary.delete(); }
        return true;
    }
    byte[] export() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        File[] files = files(); Arrays.sort(files, Comparator.comparing(File::getName));
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (File file : files) {
                if (!file.getName().endsWith(".chunk")) continue;
                if (file.length() > LIMIT || bytes.size() > LIMIT + 512 * 1024) throw new IOException("Size limit");
                zip.putNextEntry(new ZipEntry(file.getName()));
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
                }
                zip.closeEntry(); count++;
            }
        }
        if (count == 0) throw new IOException("No records");
        // The outer authenticated envelope also prevents undetected chunk deletion/reordering.
        return DebugEnvelope.seal(bytes.toByteArray(), key);
    }
    void clear() throws IOException {
        for (File file : files()) if (!file.delete()) throw new IOException("Clear failed");
    }
}
