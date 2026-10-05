package com.aalsikke.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** KAN-273: random link tokens and the hash we store instead of the token itself. */
public final class VerificationTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationTokens() {}

    /** 32 random bytes, URL-safe (43 characters), so it can go straight into a link. */
    public static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as 64 lowercase hex characters. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e); // every JVM has it
        }
    }
}