package com.accsaber.backend.repository.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.MapDifficultySubscription;
import com.accsaber.backend.model.entity.notification.Notification;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MapSubscriptionQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MapDifficultySubscriptionRepository subscriptionRepository;
    @Autowired
    private ChatMessageRepository chatRepository;
    @Autowired
    private StaffUserRepository staffUserRepository;

    @Test
    @DisplayName("following twice is one row, a map_followed notice is storable, and unfollowing removes the row")
    void followingIsIdempotent() {
        User first = user(1L);
        user(2L);
        UUID difficultyId = suggestedDifficulty();

        subscriptionRepository.subscribe(difficultyId, 1L);
        subscriptionRepository.subscribe(difficultyId, 1L);
        subscriptionRepository.subscribe(difficultyId, 2L);
        entityManager.persist(Notification.builder().user(first).type(NotificationType.map_followed)
                .title("New comments on Song (Hard)").linkTo("/maps/x").build());
        entityManager.flush();

        assertThat(subscriptionRepository.findUserIds(difficultyId)).containsExactlyInAnyOrder(1L, 2L);

        subscriptionRepository.deleteById(new MapDifficultySubscription.Key(difficultyId, 1L));
        entityManager.flush();
        assertThat(subscriptionRepository.findUserIds(difficultyId)).containsExactly(2L);
    }

    @Test
    @DisplayName("a comment is stored on its difficulty, read back, and counts as recent for any other message")
    void commentsAreStoredPerDifficulty() {
        User author = user(3L);
        UUID difficultyId = suggestedDifficulty();
        entityManager.persist(ChatMessage.builder()
                .mapDifficulty(entityManager.getReference(MapDifficulty.class, difficultyId))
                .user(author).content("nice map").build());
        entityManager.flush();
        entityManager.clear();

        assertThat(chatRepository.findByMapDifficulty_IdOrderByCreatedAtDesc(difficultyId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .singleElement().satisfies(message -> {
                    assertThat(message.getContent()).isEqualTo("nice map");
                    Instant recently = Instant.now().minusSeconds(300);
                    assertThat(chatRepository.existsByMapDifficulty_IdAndCreatedAtAfterAndIdNot(difficultyId,
                            recently, message.getId())).isFalse();
                    assertThat(chatRepository.existsByMapDifficulty_IdAndCreatedAtAfterAndIdNot(difficultyId,
                            recently, UUID.randomUUID())).isTrue();
                });
    }

    @Test
    @DisplayName("only players tied to an active, accepted ranking account count as ranking team")
    void rankingTeamUserIds() {
        staff(user(4L), StaffRole.RANKING_HEAD, true);
        staff(user(5L), StaffRole.MODERATOR, true);
        staff(user(6L), StaffRole.RANKING, false);
        user(7L);
        entityManager.flush();

        assertThat(staffUserRepository.findRankingTeamUserIds(List.of(4L, 5L, 6L, 7L))).containsExactly(4L);
    }

    private void staff(User user, StaffRole role, boolean active) {
        entityManager.persist(StaffUser.builder().user(user).username("staff" + user.getId()).password("x")
                .role(role).active(active).build());
    }

    private UUID suggestedDifficulty() {
        Map map = Map.builder().songName("Song").songAuthor("Artist").songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper").build();
        entityManager.persist(map);
        MapDifficulty difficulty = MapDifficulty.builder().map(map).difficulty(Difficulty.HARD)
                .characteristic("Standard").status(MapDifficultyStatus.SUGGESTED).maxScore(1_000_000).build();
        entityManager.persist(difficulty);
        entityManager.flush();
        return difficulty.getId();
    }

    private User user(long id) {
        User user = User.builder().id(id).name("Player " + id).country("ES").build();
        entityManager.persist(user);
        return user;
    }
}
