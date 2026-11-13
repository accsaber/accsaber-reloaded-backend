package com.accsaber.backend.repository.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelation;
import com.accsaber.backend.model.entity.user.UserRelationType;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRelationMergeQueryTest {

    private static final Long PRIMARY = 76561190000000011L;
    private static final Long SECONDARY = 76561190000000012L;
    private static final Long FAN = 76561190000000013L;
    private static final Long IDOL = 76561190000000014L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRelationRepository relationRepository;

    @BeforeEach
    void seedUsers() {
        for (Long id : new Long[] { PRIMARY, SECONDARY, FAN, IDOL }) {
            entityManager.persist(User.builder().id(id).name("Player" + id).country("ES").build());
        }
    }

    @Test
    void movesRelationsOwnedByAndPointingAtTheSecondary() {
        relation(FAN, SECONDARY, true);
        relation(SECONDARY, IDOL, true);

        merge();

        assertThat(follows(FAN, PRIMARY)).isTrue();
        assertThat(follows(PRIMARY, IDOL)).isTrue();
        assertThat(follows(FAN, SECONDARY)).isFalse();
        assertThat(follows(SECONDARY, IDOL)).isFalse();
    }

    @Test
    void followingBothAccountsLeavesOneActiveFollow() {
        relation(FAN, PRIMARY, true);
        relation(FAN, SECONDARY, true);

        merge();

        assertThat(follows(FAN, PRIMARY)).isTrue();
        assertThat(relationRepository.countActiveByType(FAN)).singleElement()
                .satisfies(row -> assertThat(row.getOutgoing()).isEqualTo(1));
    }

    @Test
    void activeSecondaryFollowRevivesInactivePrimaryFollow() {
        relation(FAN, PRIMARY, false);
        relation(FAN, SECONDARY, true);

        merge();

        assertThat(follows(FAN, PRIMARY)).isTrue();
    }

    @Test
    void inactiveSecondaryFollowDoesNotMove() {
        relation(FAN, SECONDARY, false);

        merge();

        assertThat(follows(FAN, PRIMARY)).isFalse();
    }

    @Test
    void relationBetweenTheTwoAccountsIsDropped() {
        relation(SECONDARY, PRIMARY, true);

        merge();

        assertThat(relationRepository.countActiveByType(SECONDARY)).isEmpty();
        assertThat(relationRepository.countActiveByType(PRIMARY)).isEmpty();
    }

    @Test
    void countsOutgoingAndIncomingPerTypeAndSkipsInactive() {
        relation(PRIMARY, IDOL, true);
        relation(FAN, PRIMARY, true);
        relation(SECONDARY, PRIMARY, true);
        relation(IDOL, PRIMARY, false);
        entityManager.flush();

        assertThat(relationRepository.countActiveByType(PRIMARY)).singleElement().satisfies(row -> {
            assertThat(row.getType()).isEqualTo(UserRelationType.follower);
            assertThat(row.getOutgoing()).isEqualTo(1);
            assertThat(row.getIncoming()).isEqualTo(2);
        });
    }

    private void relation(Long userId, Long targetUserId, boolean active) {
        entityManager.persist(UserRelation.builder()
                .user(entityManager.getReference(User.class, userId))
                .targetUser(entityManager.getReference(User.class, targetUserId))
                .type(UserRelationType.follower)
                .active(active)
                .build());
    }

    private void merge() {
        entityManager.flush();
        relationRepository.moveActiveRelations(SECONDARY, PRIMARY);
        entityManager.clear();
    }

    private boolean follows(Long userId, Long targetUserId) {
        return relationRepository.existsByUser_IdAndTargetUser_IdAndTypeAndActiveTrue(
                userId, targetUserId, UserRelationType.follower);
    }
}
