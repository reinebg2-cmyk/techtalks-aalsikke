package com.aalsikke.ai;

/** KAN-372: sends a prompt to an AI model and returns its text reply. Faked in tests. */
@FunctionalInterface
public interface AiClient {
    String complete(String prompt);
}