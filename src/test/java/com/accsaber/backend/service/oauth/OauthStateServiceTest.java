package com.accsaber.backend.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.service.oauth.DiscordOauthClient.DiscordIdentity;
import com.accsaber.backend.service.oauth.OauthStateService.StateClaims;

class OauthStateServiceTest {

    private static final String RETURN_TO = "https://accsaber.com/login/finish";

    private OauthStateService stateService;

    @BeforeEach
    void setUp() {
        OauthProperties properties = new OauthProperties();
        stateService = new OauthStateService(properties, new StateReplayGuard(properties));
        ReflectionTestUtils.setField(stateService, "secret",
                Base64.getEncoder().encodeToString(new byte[64]));
    }

    @Test
    void sameBrowserCompletesTheFlow() {
        String binding = stateService.newBrowserBinding();
        String state = stateService.createState(new StateClaims("discord", RETURN_TO, 7L, null, binding));

        StateClaims claims = stateService.parseState(state, "discord", binding);

        assertThat(claims.linkUserId()).isEqualTo(7L);
        assertThat(claims.returnTo()).isEqualTo(RETURN_TO);
    }

    @Test
    void callbackInAnotherBrowserIsRejected() {
        String state = stateService.createState(
                new StateClaims("discord", RETURN_TO, 7L, null, stateService.newBrowserBinding()));

        assertThatThrownBy(() -> stateService.parseState(state, "discord", stateService.newBrowserBinding()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> stateService.parseState(state, "discord", null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void pendingLinkTokenOnlyContinuesInTheBrowserThatEarnedIt() {
        String attacker = stateService.newBrowserBinding();
        String pendingLinkToken = stateService.createPendingLinkToken(discordIdentity(),
                new StateClaims("discord", RETURN_TO, null, null, attacker));

        assertThat(stateService.createState(
                new StateClaims("beatleader", RETURN_TO, null, pendingLinkToken, attacker))).isNotBlank();
        assertThatThrownBy(() -> stateService.createState(new StateClaims(
                "beatleader", RETURN_TO, null, pendingLinkToken, stateService.newBrowserBinding())))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static DiscordIdentity discordIdentity() {
        DiscordIdentity identity = new DiscordIdentity();
        identity.setId("123");
        identity.setUsername("attacker");
        return identity;
    }
}
