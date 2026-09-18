package com.pie.shared.dto.llmaas;

/**
 * A single message in an OpenAI-compatible chat completion conversation.
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
