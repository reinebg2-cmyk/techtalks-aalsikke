package com.aalsikke.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * KAN-372: calls Google's Gemini generateContent API over HTTPS (same pattern as BrevoMailTransport).
 * Key from GEMINI_API_KEY; model from GEMINI_MODEL. Without a key every call fails fast
 * and quick-add shows its friendly error, so the rest of the app is unaffected.
 */
@Component
public class GeminiAiClient implements AiClient {

    static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String apiKey;
    private final String model;
    private final String fallbackModel;

    public GeminiAiClient(@Value("${app.ai.gemini-api-key:}") String apiKey,
                          @Value("${app.ai.gemini-model:gemini-3.6-flash}") String model,
                          @Value("${app.ai.gemini-fallback-model:gemini-3.5-flash-lite}") String fallbackModel) {
        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set");
        }
        String body = body(prompt);
        HttpResult result = call(model, body);

        // Overloaded, rate-limited or no answer: try once more with the lighter model
        if (retryable(result.status()) && fallbackModel != null && !fallbackModel.isBlank()
                && !fallbackModel.equals(model)) {
            result = call(fallbackModel, body);
        }

        if (result.status() / 100 != 2) {
            throw new IllegalStateException(result.status() == 0
                    ? "Gemini API not reachable"
                    : "Gemini API returned " + result.status());
        }
        return text(result.body());
    }

    /** One HTTPS call to one model. Status 0 = no answer (timeout or network error). Overridden in tests. */
    HttpResult call(String model, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + model + ":generateContent"))
                .timeout(TIMEOUT)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        } catch (IOException e) {
            return new HttpResult(0, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResult(0, "interrupted");
        }
    }

    /** 0 = no answer, 429 = rate limit, 500/503/504 = Google-side trouble. A 400 is our mistake: never retried. */
    static boolean retryable(int status) {
        return status == 0 || status == 429 || status == 500 || status == 503 || status == 504;
    }

    record HttpResult(int status, String body) { }

    /** Request body: the prompt, and ask for a plain JSON reply with no creativity. */
    String body(String prompt) {
        return jsonMapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0)));
    }

    /** The text of the first candidate's first part. */
    String text(String responseJson) {
        GeminiReply reply = jsonMapper.readValue(responseJson, GeminiReply.class);
        if (reply.candidates() == null || reply.candidates().isEmpty()
                || reply.candidates().get(0).content() == null
                || reply.candidates().get(0).content().parts() == null
                || reply.candidates().get(0).content().parts().isEmpty()
                || reply.candidates().get(0).content().parts().get(0).text() == null) {
            throw new IllegalStateException("Gemini reply has no text");
        }
        return reply.candidates().get(0).content().parts().get(0).text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiReply(List<Candidate> candidates) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) { }
}