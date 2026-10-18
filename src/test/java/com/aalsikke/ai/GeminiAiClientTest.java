package com.aalsikke.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-372: request body and reply reading for Gemini. No network: the key is blank or the methods are pure. */
class GeminiAiClientTest {

    private final GeminiAiClient client = new GeminiAiClient("", "gemini-3.6-flash", "gemini-3.5-flash-lite");

    private static final String OK_REPLY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hi\"}]}}]}";

    /** A client whose HTTP calls are scripted (model -> status code), so no network is used. 0 = no answer. */
    private static GeminiAiClient scripted(Map<String, Integer> statusByModel, List<String> called) {
        return new GeminiAiClient("test-key", "primary", "fallback") {
            @Override
            HttpResult call(String model, String body) {
                called.add(model);
                int status = statusByModel.get(model);
                return new HttpResult(status, status == 200 ? OK_REPLY : "{\"error\":{}}");
            }
        };
    }

    @Test
    void primaryOverloaded_usesTheFallbackModel() {
        List<String> called = new ArrayList<>();
        String text = scripted(Map.of("primary", 503, "fallback", 200), called).complete("hello");
        assertEquals("hi", text);
        assertEquals(List.of("primary", "fallback"), called);
    }

    @Test
    void primaryNotAnswering_usesTheFallbackModel() {
        List<String> called = new ArrayList<>();
        assertEquals("hi", scripted(Map.of("primary", 0, "fallback", 200), called).complete("hello"));
    }

    @Test
    void primaryOk_doesNotCallTheFallback() {
        List<String> called = new ArrayList<>();
        scripted(Map.of("primary", 200), called).complete("hello");
        assertEquals(List.of("primary"), called);
    }

    @Test
    void badRequest_isNotRetried() {
        List<String> called = new ArrayList<>();
        assertThrows(IllegalStateException.class,
                () -> scripted(Map.of("primary", 400), called).complete("hello"));
        assertEquals(List.of("primary"), called);
    }

    @Test
    void bothOverloaded_throws() {
        List<String> called = new ArrayList<>();
        assertThrows(IllegalStateException.class,
                () -> scripted(Map.of("primary", 503, "fallback", 503), called).complete("hello"));
    }

    @Test
    void body_containsThePromptAndAsksForJson() {
        String body = client.body("3andi exam \"bukra\"");
        assertTrue(body.contains("3andi exam \\\"bukra\\\""));
        assertTrue(body.contains("application/json"));
    }

    @Test
    void text_readsTheFirstCandidate() {
        String reply = """
                {"candidates":[{"content":{"parts":[{"text":"{\\"understood\\":true}"}],"role":"model"}}],
                 "usageMetadata":{"totalTokenCount":42}}
                """;
        assertEquals("{\"understood\":true}", client.text(reply));
    }

    @Test
    void text_withNoCandidates_throws() {
        assertThrows(IllegalStateException.class, () -> client.text("{\"candidates\":[]}"));
    }

    @Test
    void withoutApiKey_throwsWithoutCallingGoogle() {
        assertThrows(IllegalStateException.class, () -> client.complete("hello"));
    }
}