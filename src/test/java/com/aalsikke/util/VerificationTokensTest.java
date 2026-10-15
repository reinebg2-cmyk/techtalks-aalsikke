package com.aalsikke.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-273: tokens in verification links and the hash stored instead of them. */
class VerificationTokensTest {

    @Test
    void newTokenIsUrlSafeAnd43Characters() {
        String token = VerificationTokens.newRawToken();
        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "only URL-safe characters: " + token);
    }

    @Test
    void newTokensAreDifferentEachTime() {
        assertNotEquals(VerificationTokens.newRawToken(), VerificationTokens.newRawToken());
    }

    @Test
    void sha256HexMatchesKnownValue() {
        // Standard SHA-256 test vector for "abc"
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                VerificationTokens.sha256Hex("abc"));
    }

    @Test
    void sameTokenAlwaysGivesSameHash() {
        String token = VerificationTokens.newRawToken();
        assertEquals(VerificationTokens.sha256Hex(token), VerificationTokens.sha256Hex(token));
        assertEquals(64, VerificationTokens.sha256Hex(token).length());
    }
}