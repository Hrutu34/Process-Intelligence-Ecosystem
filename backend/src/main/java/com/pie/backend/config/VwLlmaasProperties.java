package com.pie.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code vw.llm.*} configuration tree used to talk to the VW Group
 * LLMaaS platform (Cloud IDP OAuth2 client-credentials + LLMaaS REST API).
 * <p>
 * Values are sourced from application-*.yml, which in turn resolve from the
 * {@code VW_LLM_CLIENT_ID}, {@code VW_LLM_CLIENT_SECRET} and
 * {@code VW_LLM_API_KEY} environment variables. No secret ever has a
 * non-empty default baked into source control.
 */
@ConfigurationProperties(prefix = "vw.llm")
public class VwLlmaasProperties {

    private final Idp idp = new Idp();
    private final Api api = new Api();
    private final Timeout timeout = new Timeout();

    public Idp getIdp() {
        return idp;
    }

    public Api getApi() {
        return api;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    /** Cloud IDP OAuth2 client-credentials settings. */
    public static class Idp {
        private String tokenUri = "https://idp.cloud.vwgroup.com/auth/realms/kums-mfa/protocol/openid-connect/token";
        private String clientId;
        private String clientSecret;

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    /** LLMaaS API settings. */
    public static class Api {
        private String baseUrl = "https://llmapi.ai.vwgroup.com";
        private String apiKey;
        private String chatModel = "gpt-4o";
        private String embeddingModel = "text-embedding-3-large";
        private String chatCompletionsPath = "/chat/completions";
        private String embeddingsPath = "/embeddings";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public String getChatCompletionsPath() {
            return chatCompletionsPath;
        }

        public void setChatCompletionsPath(String chatCompletionsPath) {
            this.chatCompletionsPath = chatCompletionsPath;
        }

        public String getEmbeddingsPath() {
            return embeddingsPath;
        }

        public void setEmbeddingsPath(String embeddingsPath) {
            this.embeddingsPath = embeddingsPath;
        }
    }

    /** Network timeouts applied to both the IDP and LLMaaS WebClients. */
    public static class Timeout {
        private int connectMs = 5000;
        private int readMs = 30000;

        public int getConnectMs() {
            return connectMs;
        }

        public void setConnectMs(int connectMs) {
            this.connectMs = connectMs;
        }

        public int getReadMs() {
            return readMs;
        }

        public void setReadMs(int readMs) {
            this.readMs = readMs;
        }
    }
}
