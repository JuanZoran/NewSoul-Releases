package dev.local.newsoul;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** APK and rule-scoped cache. No user preferences or account data are stored here. */
final class HostSymbolCache {
    static String fingerprint(List<File> apks, String revision) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, revision);
        List<File> sorted = new ArrayList<>(apks);
        sorted.sort(Comparator.comparing(File::getAbsolutePath));
        for (File apk : sorted) {
            update(digest, apk.getAbsolutePath());
            update(digest, apk.length() + ":" + apk.lastModified());
            try (ZipFile zip = new ZipFile(apk)) {
                List<String> entries = new ArrayList<>();
                zip.stream().filter(e -> e.getName().matches("classes[0-9]*\\.dex"))
                        .forEach(e -> entries.add(e.getName() + ":" + e.getSize() + ":" + e.getCrc()));
                Collections.sort(entries);
                for (String entry : entries) update(digest, entry);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
    }

    static Properties read(File file, String fingerprint) {
        Properties result = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            if (file.length() > 64 * 1024) return result;
            result.load(in);
            if (!fingerprint.equals(result.getProperty("fingerprint"))) result.clear();
        } catch (IOException | IllegalArgumentException ignored) { result.clear(); }
        return result;
    }

    static void write(File file, String fingerprint, Properties values) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Host cache directory unavailable");
        Properties saved = new Properties(); saved.putAll(values); saved.setProperty("fingerprint", fingerprint);
        File temporary = File.createTempFile("symbols-", ".tmp", parent);
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                saved.store(out, "New Soul host symbols"); out.getFD().sync();
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    static String unique(List<String> values) {
        // A duplicate across two DEX sources is ambiguous too: do not choose by load order.
        if (values.size() != 1) throw new IllegalStateException("Host candidates=" + values.size());
        return values.get(0);
    }
}
