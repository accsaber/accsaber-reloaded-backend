package com.accsaber.backend.service.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;

class SteamOpenIdClientTest {

    @Test
    void rejectsAssertionIssuedForAnotherSite() {
        OauthProperties properties = new OauthProperties();
        properties.getSteam().setReturnTo("https://api.accsaber.com/v1/auth/steam/callback");
        SteamOpenIdClient client = new SteamOpenIdClient(properties);

        Map<String, String> params = Map.of("openid.return_to", "https://beatkhana.com/auth/steam/return");

        assertThatThrownBy(() -> client.verifyAndExtractSteamId(params, "state"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not issued for this sign in");
    }
}
