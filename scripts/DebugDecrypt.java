package dev.local.newsoul;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.zip.*;

/** Developer-side only. Not part of the APK. Requires Java 17, no third-party libraries. */
public final class DebugDecrypt {
    static byte[] decrypt(byte[] file, PrivateKey key) throws Exception {
        byte[] archive = DebugEnvelope.open(file, key);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 10000 || entry.isDirectory() || !entry.getName().matches("[0-9]{13}-[a-f0-9-]+\\.chunk"))
                    throw new IOException("Invalid record entry");
                byte[] chunk = zip.readNBytes(2 * 1024 * 1024 + 1);
                if (chunk.length > 2 * 1024 * 1024) throw new IOException("Chunk size limit");
                byte[] plain = DebugEnvelope.open(chunk, key);
                if ((long) output.size() + plain.length > 32 * 1024 * 1024) throw new IOException("Decoded size limit");
                output.write(plain); zip.closeEntry();
            }
        }
        if (count == 0) throw new IOException("No diagnostic records");
        return output.toByteArray();
    }
    public static void main(String[] args) {
        try {
            if (args.length < 2 || args.length > 3) throw new IllegalArgumentException(
                    "Usage: scripts/debug-decrypt.sh input.nsdebug output.jsonl [private-key.pk8]");
            Path input = Path.of(args[0]), output = Path.of(args[1]);
            Path keyFile = args.length == 3 ? Path.of(args[2]) : Path.of(System.getProperty("user.home"),
                    ".local/share/newsoul/debug-keys/recipient.pk8");
            if (Files.size(input) > DebugEnvelope.MAX_BYTES + 2048 || Files.size(keyFile) > 16384)
                throw new IOException("Input size limit");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyFile)));
            byte[] plain = decrypt(Files.readAllBytes(input), key);
            // Verify the entire artifact before creating any plaintext output; never overwrite an existing report.
            try (OutputStream stream = java.nio.channels.Channels.newOutputStream(Files.newByteChannel(output,
                    java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))))) {
                stream.write(plain);
            }
            System.out.println("Decoded " + plain.length + " bytes to " + output.toAbsolutePath());
        } catch (Exception error) {
            System.err.println("Unable to decode: " + error.getClass().getSimpleName()
                    + ". Check file integrity, matching private key, and a new output path.");
            System.exit(1);
        }
    }
}
