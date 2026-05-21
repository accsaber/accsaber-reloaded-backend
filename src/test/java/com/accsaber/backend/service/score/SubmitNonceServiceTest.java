package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubmitNonceServiceTest {

    private final SubmitNonceService service = new SubmitNonceService();

    @Test
    void firstConsumptionSucceeds_secondIsRejected() {
        assertThat(service.tryConsume(42L, "abc")).isTrue();
        assertThat(service.tryConsume(42L, "abc")).isFalse();
    }

    @Test
    void sameNonceDifferentUsersAreIndependent() {
        assertThat(service.tryConsume(1L, "x")).isTrue();
        assertThat(service.tryConsume(2L, "x")).isTrue();
    }

    @Test
    void blankOrNullNonceIsRejected() {
        assertThat(service.tryConsume(1L, "")).isFalse();
        assertThat(service.tryConsume(1L, "   ")).isFalse();
        assertThat(service.tryConsume(1L, null)).isFalse();
        assertThat(service.tryConsume(null, "x")).isFalse();
    }
}
