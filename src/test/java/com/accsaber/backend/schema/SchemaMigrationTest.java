package com.accsaber.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("every migration on disk applies cleanly")
    void everyMigrationApplies() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*.sql");

        Number applied = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL'")
                .getSingleResult();

        assertThat(files).isNotEmpty();
        assertThat(applied.intValue()).isEqualTo(files.length);
    }
}
