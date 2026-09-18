package com.pie.backend.service;

import com.pie.backend.config.VwLlmaasProperties;
import com.pie.backend.exception.LlmaasApiException;
import com.pie.backend.exception.LlmaasAuthException;
import com.pie.shared.dto.llmaas.ChatCompletionChoice;
import com.pie.shared.dto.llmaas.ChatCompletionRequest;
import com.pie.shared.dto.llmaas.ChatCompletionResponse;
import com.pie.shared.dto.llmaas.ChatMessage;
import com.pie.shared.dto.llmaas.EmbeddingApiData;
import com.pie.shared.dto.llmaas.EmbeddingApiRequest;
import com.pie.shared.dto.llmaas.EmbeddingApiResponse;
import com.pie.shared.dto.llmaas.EmbeddingResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * Reusable service for VW Group LLMaaS operations. Equivalent of the Python
 * {@code init_llmaas()} / {@code get_text_answer()} / {@code get_embedding()}
 * helpers: authenticates via {@link VwCloudIdpTokenService}, then calls the
 * LLMaaS chat completions / embeddings endpoints with the required
 * {@code Authorization} and {@code X-LLM-API-CLIENT-ID} headers.
 */
@Service
public class VwLlmaasService {

    private static final Logger log = LoggerFactory.getLogger(VwLlmaasService.class);
    private static final String LLM_API_CLIENT_ID_HEADER = "X-LLM-API-CLIENT-ID";

    private final WebClient llmaasWebClient;
    private final VwCloudIdpTokenService tokenService;
    private final VwLlmaasProperties properties;

    public VwLlmaasService(WebClient vwLlmaasWebClient,
                            VwCloudIdpTokenService tokenService,
                            VwLlmaasProperties properties) {
        this.llmaasWebClient = vwLlmaasWebClient;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * Sends a single-turn prompt to the configured chat model and returns the
     * assistant's answer text.
     */
    public String chat(String prompt) {
        return chat(List.of(ChatMessage.user(prompt)));
    }

    /**
     * Sends a full conversation (system/user/assistant turns) to the
     * configured chat model and returns the assistant's answer text. This is
     * the overload used by the Spring AI {@code ChatModel} adapter so that
     * system prompts and multi-turn history are preserved.
     */
    public String chat(List<ChatMessage> messages) {
        requireApiKey();
        VwLlmaasProperties.Api api = properties.getApi();

        ChatCompletionRequest request = new ChatCompletionRequest(
                api.getChatModel(),
                messages,
                0.0,
                false,
                2048);

        ChatCompletionResponse response = executeWithAuth(webClient -> webClient.post()
                .uri(api.getChatCompletionsPath())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(ChatCompletionResponse.class)
                .block());

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmaasApiException("VW LLMaaS returned no chat completion choices", null);
        }

        ChatCompletionChoice choice = response.choices().get(0);
        ChatMessage message = choice.message();
        if (message == null || !StringUtils.hasText(message.content())) {
            throw new LlmaasApiException("VW LLMaaS returned an empty chat completion message", null);
        }
        return message.content();
    }

    /**
     * Requests an embedding vector for the given input text.
     */
    public EmbeddingResponseDTO embed(String input) {
        requireApiKey();
        VwLlmaasProperties.Api api = properties.getApi();

        EmbeddingApiRequest request = new EmbeddingApiRequest(api.getEmbeddingModel(), input, "float");

        EmbeddingApiResponse response = executeWithAuth(webClient -> webClient.post()
                .uri(api.getEmbeddingsPath())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toApiException)
                .bodyToMono(EmbeddingApiResponse.class)
                .block());

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new LlmaasApiException("VW LLMaaS returned no embedding data", null);
        }

        EmbeddingApiData data = response.data().get(0);
        List<Double> embedding = data.embedding();
        return new EmbeddingResponseDTO(embedding, response.model(), embedding == null ? 0 : embedding.size());
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.getApi().getApiKey())) {
            throw new LlmaasAuthException(
                    "VW LLMaaS API key is not configured. Set the VW_LLM_API_KEY environment variable.");
        }
    }

    /**
     * Attaches the bearer token (from Cloud IDP) and the LLMaaS client-id
     * header to every outbound call, translating low-level WebClient
     * connectivity failures into {@link LlmaasApiException}.
     */
    private <T> T executeWithAuth(Function<WebClient, T> call) {
        String accessToken = tokenService.getToken();
        try {
            return call.apply(llmaasWebClient.mutate()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .defaultHeader(LLM_API_CLIENT_ID_HEADER, "Bearer " + properties.getApi().getApiKey())
                    .build());
        } catch (LlmaasApiException | LlmaasAuthException ex) {
            throw ex;
        } catch (WebClientRequestException ex) {
            log.warn("VW LLMaaS request could not be sent: {}", ex.getMessage());
            throw new LlmaasApiException("Unable to reach VW LLMaaS API", null, ex);
        } catch (Exception ex) {
            log.error("Unexpected error while calling VW LLMaaS API", ex);
            throw new LlmaasApiException("Unexpected error while calling VW LLMaaS API", null, ex);
        }
    }

    private Mono<? extends Throwable> toApiException(ClientResponse clientResponse) {
        HttpStatusCode status = clientResponse.statusCode();
        return clientResponse.createException()
                .map((WebClientResponseException ex) -> {
                    // Log the upstream response body at debug level for diagnosis; never
                    // logged at warn/error to avoid leaking any sensitive echoed data by default.
                    log.warn("VW LLMaaS API responded with status {}", status.value());
                    log.debug("VW LLMaaS API error response body: {}", ex.getResponseBodyAsString());
                    return new LlmaasApiException("VW LLMaaS API responded with status " + status.value(), status, ex);
                });
    }
}
