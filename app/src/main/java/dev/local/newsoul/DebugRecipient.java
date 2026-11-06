package dev.local.newsoul;

/** Public encryption key only. Private key is held outside the repository. */
final class DebugRecipient {
    static java.security.PublicKey key() throws Exception {
        return java.security.KeyFactory.getInstance("RSA").generatePublic(
                new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(
                "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAqrDmgObzyP43bK71v6HgV0OBDB6+HIfkXftRMhNOIwfWbfaNZRR0/HCNnI2QSn+bFWyGHUdu7fh67Cy9n0hCnhDQh36aa3nq7+jogQqSEquEqJHqyFIfxi+4/HRJXyWAhs39M6sKyS4fJGK2xKP6gjN3oDzsunro5+ldPx9hfy/J38zm5u7qIhx5s37KKn9JoE58czyAoGp6a476JcrqUf1z5F+dhRiFa/Q8Cs/Sa1U26OxSZ+EOSauQj19rZBaXPtHh7AAXyakRJfznNPHbaH9OWg5loOB0nWHLyb/RYhlmdx/WTb+n2MiYOI2EkY4g2NanTLW4/Hydi6tM4gVTzs1xfqjdhHaI52iOXEvLdEnnOlk06ha0hFbfULxfDS4usDDs3J5G70kte+qrMmeqrcEmygaZnIUqKbVW6Qx6WIP8yVdNY3sBh1PwHy3LnXdsCFQbdhFocSSjZqvx5AUVi0Z74E7WAQsvQioFR3oyuGjJdxaXF3PX5bE0gVX80wzXAgMBAAE=")));
    }
}
