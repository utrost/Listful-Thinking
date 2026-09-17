package app.listful.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class TokenHashing {
    private static final String PREFIX = "sha256:";

    private TokenHashing() {
    }

    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }

    public static boolean isSha256Hash(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
