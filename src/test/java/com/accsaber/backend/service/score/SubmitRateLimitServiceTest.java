package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubmitRateLimitServiceTest {

    private final SubmitRateLimitService service = new SubmitRateLimitService();

    @Test
    void firstAcquireSucceeds_secondWithinWindowFails() {
        assertThat(service.tryAcquire(42L)).isTrue();
        assertThat(service.tryAcquire(42L)).isFalse();
    }

    @Test
    void differentUsersAreIndependent() {
        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(2L)).isTrue();
    }

    @Test
    void nullUserIdIsRejected() {
        assertThat(service.tryAcquire(null)).isFalse();
    }
}
