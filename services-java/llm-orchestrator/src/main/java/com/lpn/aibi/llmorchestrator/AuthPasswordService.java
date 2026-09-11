package com.lpn.aibi.llmorchestrator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class AuthPasswordService {

    private static final int LEGACY_HASH_ITERATIONS = 120_000;
    private static final int LEGACY_HASH_BITS = 256;

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    AuthPasswordService(AuthProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.password().bcryptStrength());
        this.dummyHash = encoder.encode("not-a-real-user-password");
    }

    PasswordSecret hash(String password) {
        return new PasswordSecret(encoder.encode(password), "");
    }

    boolean matches(String password, String storedHash, String storedSalt) {
        if (storedHash != null && storedHash.startsWith("$2")) {
            return encoder.matches(password == null ? "" : password, storedHash);
        }
        if (storedHash == null || storedSalt == null || storedSalt.isBlank()) {
            return false;
        }
        String actualHash = legacyHash(password, storedSalt);
        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    void consumeDummyHash(String password) {
        encoder.matches(password == null ? "" : password, dummyHash);
    }

    boolean needsUpgrade(String storedHash) {
        return storedHash == null || !storedHash.startsWith("$2") || encoder.upgradeEncoding(storedHash);
    }

    private String legacyHash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password == null ? new char[0] : password.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    LEGACY_HASH_ITERATIONS,
                    LEGACY_HASH_BITS);
            try {
                byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();
                return HexFormat.of().formatHex(hash);
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException exception) {
            return "invalid-legacy-hash";
        }
    }

    record PasswordSecret(String hash, String salt) {
    }
}
