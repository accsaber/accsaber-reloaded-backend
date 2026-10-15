package com.accsaber.backend.service.oauth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.netty.channel.ChannelOption;
import lombok.Data;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class BeatLeaderOauthClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String SIGNIN_URL = "https://api.beatleader.com/signin";
    private static final String MOD_USER_INFO_URL = "https://api.beatleader.com/user/modinterface";

    private final OauthProperties.ProviderConfig config;
    private final WebClient webClient;

    public BeatLeaderOauthClient(OauthProperties oauthProperties) {
        this.config = oauthProperties.getBeatleader();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(HTTP_TIMEOUT);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public String buildAuthorizeUrl(String state) {
        return config.getAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + config.getClientId()
                + "&scope=" + UriUtils.encode(config.getScope(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + UriUtils.encode(config.getRedirectUri(), StandardCharsets.UTF_8)
                + "&state=" + UriUtils.encode(state, StandardCharsets.UTF_8);
    }

    public BeatLeaderIdentity exchangeCode(String code) {
        OauthTokenResponse token = OauthTokenExchange.exchange(webClient, config, code, "BeatLeader");
        return webClient.get()
                .uri(config.getUserInfoUrl())
                .header("Authorization", "Bearer " + token.getAccessToken())
                .retrieve()
                .bodyToMono(BeatLeaderIdentity.class)
                .timeout(HTTP_TIMEOUT)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException("BeatLeader identity lookup failed"));
    }

    public BeatLeaderIdentity verifyTicketAndFetchIdentity(String provider, String ticket) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ticket", ticket);
        form.add("provider", provider);
        form.add("returnUrl", "/");

        String cookieHeader = webClient.post()
                .uri(SIGNIN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(this::extractSessionCookie)
                .timeout(HTTP_TIMEOUT)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException("BeatLeader ticket verification failed"));

        return webClient.get()
                .uri(MOD_USER_INFO_URL)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .retrieve()
                .bodyToMono(BeatLeaderIdentity.class)
                .timeout(HTTP_TIMEOUT)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException("BeatLeader identity lookup failed"));
    }

    private Mono<String> extractSessionCookie(ClientResponse response) {
        if (!response.statusCode().is2xxSuccessful()) {
            return response.releaseBody().then(Mono.error(new UnauthorizedException("BeatLeader rejected ticket")));
        }
        String header = response.cookies().values().stream()
                .flatMap(List::stream)
                .map(c -> c.getName() + "=" + c.getValue())
                .collect(Collectors.joining("; "));
        if (header.isEmpty()) {
            return response.releaseBody()
                    .then(Mono.error(new UnauthorizedException("BeatLeader signin returned no session cookie")));
        }
        return response.releaseBody().thenReturn(header);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BeatLeaderIdentity {
        private String id;
        private String name;
        private String avatar;
    }
}
