package com.accsaber.backend.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.service.infra.MetricsService;

import io.micrometer.core.instrument.Counter;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private static final int LARGE_BUFFER_BYTES = 25 * 1024 * 1024;

    private final PlatformProperties properties;
    private final CriteriaCheckerProperties criteriaCheckerProperties;
    private final ComplexityModelProperties complexityModelProperties;
    private final MetricsService metricsService;

    @Bean(name = "beatLeaderWebClient")
    public WebClient beatLeaderWebClient() {
        return buildPlatformClient(properties.getBeatleader(), metricsService.getOutboundBeatLeader());
    }

    @Bean(name = "scoreSaberWebClient")
    public WebClient scoreSaberWebClient() {
        return buildPlatformClient(properties.getScoresaber(), metricsService.getOutboundScoreSaber());
    }

    @Bean(name = "beatSaverWebClient")
    public WebClient beatSaverWebClient() {
        return buildPlatformClient(properties.getBeatsaver(), metricsService.getOutboundBeatSaver());
    }

    @Bean(name = "gitHubWebClient")
    public WebClient gitHubWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30))
                .followRedirect(true);
        return builder("https://api.github.com", httpClient).build();
    }

    @Bean(name = "criteriaCheckerWebClient")
    public WebClient criteriaCheckerWebClient() {
        return builder(criteriaCheckerProperties.getBaseUrl(), timeouts(criteriaCheckerProperties.getTimeoutMs()))
                .build();
    }

    @Bean(name = "complexityModelWebClient")
    public WebClient complexityModelWebClient() {
        return builder(complexityModelProperties.getBaseUrl(), timeouts(complexityModelProperties.getTimeoutMs()))
                .build();
    }

    private WebClient buildPlatformClient(PlatformProperties.PlatformConfig config, Counter outboundCounter) {
        return builder(config.getBaseUrl(), timeouts(config.getTimeoutMs()))
                .filters(filters -> filters.addFirst(countOutbound(outboundCounter)))
                .build();
    }

    private static HttpClient timeouts(int timeoutMs) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .responseTimeout(Duration.ofMillis(timeoutMs));
    }

    private WebClient.Builder builder(String baseUrl, HttpClient httpClient) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(LARGE_BUFFER_BYTES))
                        .build())
                .filter(logRequest());
    }

    private ExchangeFilterFunction countOutbound(Counter counter) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            counter.increment();
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("External API request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }
}
