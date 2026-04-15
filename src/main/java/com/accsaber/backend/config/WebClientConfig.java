package com.accsaber.backend.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.service.infra.MetricsService;

import io.micrometer.core.instrument.Counter;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final PlatformProperties properties;
    private final MetricsService metricsService;

    @Bean(name = "beatLeaderWebClient")
    public WebClient beatLeaderWebClient() {
        return buildWebClient(properties.getBeatleader(), metricsService.getOutboundBeatLeader());
    }

    @Bean(name = "scoreSaberWebClient")
    public WebClient scoreSaberWebClient() {
        return buildWebClient(properties.getScoresaber(), metricsService.getOutboundScoreSaber());
    }

    @Bean(name = "beatSaverWebClient")
    public WebClient beatSaverWebClient() {
        return buildWebClient(properties.getBeatsaver(), metricsService.getOutboundBeatSaver());
    }

    private WebClient buildWebClient(PlatformProperties.PlatformConfig config, Counter outboundCounter) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getTimeoutMs())
                .responseTimeout(Duration.ofMillis(config.getTimeoutMs()));

        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(countOutbound(outboundCounter))
                .filter(logRequest())
                .build();
    }

    private ExchangeFilterFunction countOutbound(Counter counter) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            counter.increment();
            return reactor.core.publisher.Mono.just(request);
        });
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("External API request: {} {}", request.method(), request.url());
            return reactor.core.publisher.Mono.just(request);
        });
    }
}
