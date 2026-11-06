package dev.local.newsoul;

import java.io.*;
import java.util.UUID;

/** Durable directory accounting: queued audio stays available for playback and retries. */
final class VoiceOutgoingStore {
    static final int MAX_FILES = 2048;
    static final long MAX_BYTES = 500L * 1024 * 1024;

    static final class FullException extends IOException {
        FullException() { super("发送缓存已满，请使用 Soul 录音发送"); }
    }

    // Serializes admission and copy across all chat sessions in this process.
    static synchronized File copy(File cache, File source) throws IOException {
        File directory = new File(cache, "newsoul-voice-outgoing");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create outgoing audio");
        File[] retained = directory.listFiles();
        if (retained == null) throw new IOException("Cannot inspect outgoing audio");
        long available = MAX_BYTES;
        for (File file : retained) {
            // Fail closed on unexpected directory contents; never delete message-owned files.
            if (!file.isFile() || file.length() > available) throw new FullException();
            available -= file.length();
        }
        long size = source.length();
        if (!source.isFile() || size <= 0 || size > VoiceLibraryStore.MAX_BYTES)
            throw new IOException("Invalid outgoing audio");
        if (retained.length >= MAX_FILES || size > available) throw new FullException();
        File target = new File(directory, UUID.randomUUID() + VoiceLibraryStore.extension(source));
        try {
            try (InputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                long bytes = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    bytes += read;
                    // The source may change during copying; enforce both limits as bytes arrive.
                    if (bytes > available || bytes > VoiceLibraryStore.MAX_BYTES)
                        throw new IOException("Outgoing audio exceeds available storage");
                    out.write(buffer, 0, read);
                }
                if (bytes == 0) throw new IOException("Empty outgoing audio");
                out.getFD().sync();
            }
            return target;
        } catch (IOException | RuntimeException | Error error) {
            if (target.exists() && !target.delete())
                error.addSuppressed(new IOException("Cannot remove incomplete outgoing audio"));
            throw error;
        }
    }
}
