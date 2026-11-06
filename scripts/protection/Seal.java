import java.nio.file.*;
import java.util.*;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Build-only encoder; keys here obfuscate shipped content, not server-held secrets. */
public final class Seal {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32]; random.nextBytes(key);
        Files.write(Path.of(args[2]), key);
        List<String> output = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(new byte[]{78,83,72,49});
            byte[] encrypted = cipher.doFinal(Base64.getDecoder().decode(line));
            byte[] result = new byte[16 + encrypted.length];
            System.arraycopy(new byte[]{78,83,72,49},0,result,0,4);
            System.arraycopy(nonce,0,result,4,12);
            System.arraycopy(encrypted,0,result,16,encrypted.length);
            output.add(Base64.getEncoder().encodeToString(result));
        }
        Files.write(Path.of(args[1]), output);
    }
}
