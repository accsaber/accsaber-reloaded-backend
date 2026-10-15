package com.accsaber.backend.service.oauth;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;

final class OauthTokenExchange {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private OauthTokenExchange() {
    }

    static OauthTokenResponse exchange(WebClient webClient, OauthProperties.ProviderConfig config, String code,
            String provider) {
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
                .timeout(HTTP_TIMEOUT)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException(provider + " token exchange failed"));
    }
}
