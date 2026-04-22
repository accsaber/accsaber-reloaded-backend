package com.accsaber.backend.service.oauth;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Component
public class BeatLeaderOauthClient {

    private final OauthProperties.ProviderConfig config;
    private final WebClient webClient;

    public BeatLeaderOauthClient(OauthProperties oauthProperties) {
        this.config = oauthProperties.getBeatleader();
        this.webClient = WebClient.builder().build();
    }

    public String buildAuthorizeUrl(String state) {
        return config.getAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + config.getClientId()
                + "&scope=" + config.getScope()
                + "&redirect_uri=" + UriUtils.encode(config.getRedirectUri(), StandardCharsets.UTF_8)
                + "&state=" + UriUtils.encode(state, StandardCharsets.UTF_8);
    }

    public BeatLeaderIdentity exchangeCode(String code) {
        OauthTokenResponse token = exchange(code);
        return webClient.get()
                .uri(config.getUserInfoUrl())
                .header("Authorization", "Bearer " + token.getAccessToken())
                .retrieve()
                .bodyToMono(BeatLeaderIdentity.class)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException("BeatLeader identity lookup failed"));
    }

    private OauthTokenResponse exchange(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", config.getRedirectUri());
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());

        return webClient.post()
                .uri(config.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(OauthTokenResponse.class)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException("BeatLeader token exchange failed"));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BeatLeaderIdentity {
        private String id;
        private String name;
        private String avatar;
    }
}
