package dev.local.newsoul;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class DebugDiagnosticsTest {
    static int checks;
    interface Checked { void run() throws Exception; }
    static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    static void rejected(Checked action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
        KeyPair key = generator.generateKeyPair(), wrong = generator.generateKeyPair();
        byte[] plain = "{\"point\":\"repeat.menu.skip\",\"facts\":\"missing_repeat_icon\"}\n".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = DebugEnvelope.seal(plain, key.getPublic());
        check(Arrays.equals(plain, DebugEnvelope.open(sealed, key.getPrivate())), "roundtrip");
        check(!Arrays.equals(sealed, DebugEnvelope.seal(plain, key.getPublic())), "fresh key and nonce");
        rejected(() -> DebugEnvelope.open(sealed, wrong.getPrivate()), "wrong key");
        for (int position : new int[]{0, 8, 12, 396, sealed.length - 1}) {
            byte[] corrupt = sealed.clone(); corrupt[position] ^= 1;
            rejected(() -> DebugEnvelope.open(corrupt, key.getPrivate()), "tampering " + position);
        }
        rejected(() -> DebugEnvelope.open(Arrays.copyOf(sealed, sealed.length - 1), key.getPrivate()), "truncation");
        rejected(() -> DebugEnvelope.open(Arrays.copyOf(sealed, sealed.length + 1), key.getPrivate()), "trailing bytes");
        rejected(() -> DebugEnvelope.open(new byte[0], key.getPrivate()), "empty input");
        Path root = Files.createTempDirectory("newsoul-debug-test");
        try {
            DebugStore store = new DebugStore(root.toFile(), key.getPublic());
            check(store.append(plain), "first append");
            byte[] exported = store.export();
            check(Arrays.equals(plain, DebugDecrypt.decrypt(exported, key.getPrivate())), "export decrypt");
            rejected(() -> DebugDecrypt.decrypt(exported, wrong.getPrivate()), "export wrong key");
            try (var files = Files.list(root)) {
                for (Path path : files.toList()) {
                    byte[] data = Files.readAllBytes(path);
                    check(!new String(data, StandardCharsets.ISO_8859_1).contains("repeat.menu.skip"), "no plaintext on disk");
                    check(Arrays.equals(plain, DebugEnvelope.open(data, key.getPrivate())), "encrypted disk roundtrip");
                }
            }
            DebugStore restarted = new DebugStore(root.toFile(), key.getPublic());
            check(Arrays.equals(plain, DebugDecrypt.decrypt(restarted.export(), key.getPrivate())), "restart preserves records");
            check(restarted.append(plain), "append after restart");
            check(DebugDecrypt.decrypt(restarted.export(), key.getPrivate()).length == 2 * plain.length, "both process records");
            restarted.clear(); check(restarted.size() == 0, "clear");
            rejected(restarted::export, "empty export");
            byte[] big = new byte[1024 * 1024]; new SecureRandom().nextBytes(big);
            check(restarted.append(big) && restarted.append(big) && restarted.append(big), "under cap");
            check(!restarted.append(big) && restarted.size() <= DebugStore.LIMIT, "hard storage cap");
            check(DebugDecrypt.decrypt(restarted.export(), key.getPrivate()).length == big.length * 3, "near cap export");
        } finally {
            try (var files = Files.list(root)) { for (Path path : files.toList()) Files.delete(path); }
            Files.delete(root);
        }
        if (args.length == 1) {
            PrivateKey recipient = KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(args[0]))));
            check(Arrays.equals(plain, DebugEnvelope.open(DebugEnvelope.seal(plain, DebugRecipient.key()), recipient)), "production recipient matches private key");
        }
        System.out.println("Debug diagnostics: " + checks + " checks passed");
    }
}
