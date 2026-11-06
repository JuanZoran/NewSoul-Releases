package dev.local.newsoul;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.*;
import javax.crypto.spec.*;

/** NSDBG001: authenticated header + RSA-OAEP wrapped random AES key + AES-256-GCM. */
final class DebugEnvelope {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final byte[] MAGIC = "NSDBG001".getBytes(StandardCharsets.US_ASCII);
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    static byte[] seal(byte[] plain, PublicKey recipient) throws GeneralSecurityException, IOException {
        if (plain.length > MAX_BYTES) throw new IOException("Size limit");
        byte[] key = new byte[32], nonce = new byte[12];
        SecureRandom random = new SecureRandom(); random.nextBytes(key); random.nextBytes(nonce);
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, recipient, OAEP);
            byte[] wrapped = rsa.doFinal(key);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(bytes);
            header.write(MAGIC); header.writeInt(wrapped.length); header.write(wrapped); header.write(nonce);
            byte[] aad = bytes.toByteArray();
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            aes.updateAAD(aad); bytes.write(aes.doFinal(plain));
            return bytes.toByteArray();
        } finally { java.util.Arrays.fill(key, (byte) 0); }
    }

    static byte[] open(byte[] sealed, PrivateKey recipient) throws GeneralSecurityException, IOException {
        if (sealed.length > MAX_BYTES + 2048) throw new IOException("Size limit");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(sealed));
        byte[] magic = new byte[8]; in.readFully(magic);
        if (!MessageDigest.isEqual(MAGIC, magic)) throw new IOException("Unknown format");
        int size = in.readInt();
        if (size < 256 || size > 1024 || sealed.length < 12 + size + 12 + 16) throw new IOException("Invalid header");
        byte[] wrapped = new byte[size], nonce = new byte[12]; in.readFully(wrapped); in.readFully(nonce);
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE, recipient, OAEP);
        byte[] key = rsa.doFinal(wrapped);
        try {
            if (key.length != 32) throw new GeneralSecurityException("Invalid key");
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            int offset = 24 + size;
            aes.updateAAD(sealed, 0, offset);
            return aes.doFinal(sealed, offset, sealed.length - offset);
        } finally { java.util.Arrays.fill(key, (byte) 0); }
    }
}
