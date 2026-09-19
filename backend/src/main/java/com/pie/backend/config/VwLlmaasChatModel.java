package com.pie.backend.config;

import com.pie.backend.service.VwLlmaasService;
import com.pie.shared.dto.llmaas.ChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Adapts {@link VwLlmaasService} (VW Group LLMaaS - Cloud IDP OAuth2 +
 * {@code gpt-4o}) to Spring AI's {@link ChatModel} contract, so the app's
 * existing {@code ChatClient.Builder}-based services (ChatController,
 * AiClassificationService, AiBpmnRefinementService, etc.) can use VW LLMaaS
 * as their model with zero changes to their own code - only the
 * {@code @Primary ChatModel} wiring in {@link AiRoutingConfig} changes.
 * <p>
 * Only synchronous {@code call(Prompt)} is implemented; the application does
 * not use streaming chat responses.
 */
public class VwLlmaasChatModel implements ChatModel {

    private final VwLlmaasService llmaasService;

    public VwLlmaasChatModel(VwLlmaasService llmaasService) {
        this.llmaasService = llmaasService;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<ChatMessage> messages = prompt.getInstructions().stream()
                .map(this::toLlmaasMessage)
                .toList();

        String answer = llmaasService.chat(messages);

        Generation generation = new Generation(
                new AssistantMessage(answer),
                ChatGenerationMetadata.NULL);
        return new ChatResponse(List.of(generation));
    }

    private ChatMessage toLlmaasMessage(Message message) {
        String role = switch (message.getMessageType()) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            default -> "user";
        };
        return new ChatMessage(role, message.getText());
    }
}
