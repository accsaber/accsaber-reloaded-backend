package com.accsaber.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.accsaber.backend.websocket.IngestionLeaderLock;

@Tag("integration")
@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "accsaber.jwt.secret=integration-test-signing-secret-that-is-long-enough-for-hmac-sha256",
        "accsaber.service.api-key=integration-test-service-key",
        "accsaber.platforms.beatleader.websocket-url=",
        "accsaber.platforms.scoresaber.websocket-url=",
        "accsaber.backfill.on-startup=false"
})
class ApplicationContextTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private IngestionLeaderLock leaderLock;

    @Test
    @DisplayName("the production profile wires a complete application context")
    void contextLoads() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }

    @Test
    @DisplayName("score ingestion leadership can be taken against a real database")
    void leadershipIsObtainable() {
        assertThat(leaderLock.acquire())
                .as("a lone instance must win the advisory lock, or it would ingest nothing")
                .isTrue();
    }
}
