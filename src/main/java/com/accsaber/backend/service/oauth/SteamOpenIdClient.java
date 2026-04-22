package com.accsaber.backend.service.oauth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Component
public class SteamOpenIdClient {

    private static final String OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final Pattern CLAIMED_ID_PATTERN = Pattern
            .compile("^https://steamcommunity\\.com/openid/id/(\\d+)$");
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final OauthProperties.SteamConfig config;
    private final WebClient webClient;

    public SteamOpenIdClient(OauthProperties oauthProperties) {
        this.config = oauthProperties.getSteam();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(HTTP_TIMEOUT);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public String buildAuthorizeUrl(String returnTo) {
        return OPENID_ENDPOINT
                + "?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0"
                + "&openid.mode=checkid_setup"
                + "&openid.return_to=" + UriUtils.encode(returnTo, StandardCharsets.UTF_8)
                + "&openid.realm=" + UriUtils.encode(config.getRealm(), StandardCharsets.UTF_8)
                + "&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"
                + "&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select";
    }

    public Long verifyAndExtractSteamId(Map<String, String> openidParams) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        openidParams.forEach(form::add);
        form.set("openid.mode", "check_authentication");

        String body = webClient.post()
                .uri(OPENID_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(HTTP_TIMEOUT)
                .blockOptional()
                .orElseThrow(() -> new UnauthorizedException("Steam OpenID verification failed"));

        boolean valid = body.lines()
                .map(String::trim)
                .anyMatch("is_valid:true"::equals);
        if (!valid) {
            throw new UnauthorizedException("Steam OpenID signature invalid");
        }

        String claimedId = openidParams.get("openid.claimed_id");
        if (claimedId == null) {
            throw new UnauthorizedException("Steam OpenID response missing claimed_id");
        }
        Matcher matcher = CLAIMED_ID_PATTERN.matcher(claimedId);
        if (!matcher.matches()) {
            throw new UnauthorizedException("Unexpected Steam claimed_id format");
        }
        return Long.parseLong(matcher.group(1));
    }
}
