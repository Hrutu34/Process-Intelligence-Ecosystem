package com.pie.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * WebClient beans dedicated to VW Group LLMaaS integration:
 * <ul>
 *     <li>{@code vwIdpWebClient} - talks to Cloud IDP to obtain OAuth2 tokens.</li>
 *     <li>{@code vwLlmaasWebClient} - talks to the LLMaaS API (chat completions, embeddings).</li>
 * </ul>
 * Both clients have explicit connect/read timeouts so a slow or unresponsive
 * upstream never hangs a request thread indefinitely.
 */
@Configuration
@EnableConfigurationProperties(VwLlmaasProperties.class)
public class VwLlmaasWebClientConfig {

    @Bean
    public WebClient vwIdpWebClient(VwLlmaasProperties properties) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient(properties)))
                .build();
    }

    @Bean
    public WebClient vwLlmaasWebClient(VwLlmaasProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient(properties)))
                .build();
    }

    private HttpClient httpClient(VwLlmaasProperties properties) {
        VwLlmaasProperties.Timeout timeout = properties.getTimeout();
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout.getConnectMs())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(timeout.getReadMs(), TimeUnit.MILLISECONDS)));
    }
}
