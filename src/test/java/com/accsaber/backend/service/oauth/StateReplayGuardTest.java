package com.accsaber.backend.service.oauth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.config.OauthProperties;
import com.accsaber.backend.exception.UnauthorizedException;

class StateReplayGuardTest {

    private final StateReplayGuard guard = new StateReplayGuard(new OauthProperties());

    @Test
    void aStateCanBeConsumedOnce() {
        assertThatCode(() -> guard.consume("jti-1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.consume("jti-1")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void differentStatesDoNotCollide() {
        guard.consume("jti-a");

        assertThatCode(() -> guard.consume("jti-b")).doesNotThrowAnyException();
    }
}
