package com.accsaber.backend.repository.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaffDeletionQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private StaffUserRepository staffUserRepository;

    private StaffUser departing;
    private StaffUser remaining;
    private MapDifficulty difficulty;
    private Batch batch;

    @BeforeEach
    void seed() {
        departing = persistStaff("departing");
        remaining = persistStaff("remaining");

        batch = Batch.builder().name("September").createdBy(departing).build();
        entityManager.persist(batch);

        Map map = Map.builder()
                .songName("Song")
                .songAuthor("Author")
                .songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper")
                .build();
        entityManager.persist(map);

        difficulty = MapDifficulty.builder()
                .map(map)
                .category(entityManager
                        .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true",
                                Category.class)
                        .getSingleResult())
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(MapDifficultyStatus.QUEUE)
                .maxScore(1_000_000)
                .createdBy(departing.getId())
                .lastUpdatedBy(remaining.getId())
                .build();
        entityManager.persist(difficulty);

        persistVote(departing);
        persistVote(remaining);
        entityManager.flush();
    }

    @Test
    @DisplayName("scrubbing lets the account be deleted and leaves other staff attributions alone")
    void detachesReferencesSoTheAccountCanBeDeleted() {
        staffUserRepository.detachStaffReferences(departing.getId());
        staffUserRepository.delete(departing);
        entityManager.flush();
        entityManager.clear();

        assertThat(staffUserRepository.findById(departing.getId())).isEmpty();
        assertThat(entityManager
                .createNativeQuery("SELECT staff_id FROM staff_map_votes WHERE map_difficulty_id = :id", UUID.class)
                .setParameter("id", difficulty.getId())
                .getResultList())
                .containsExactly(remaining.getId());
        assertThat(entityManager.find(Batch.class, batch.getId()).getCreatedBy()).isNull();

        MapDifficulty scrubbed = entityManager.find(MapDifficulty.class, difficulty.getId());
        assertThat(scrubbed.getCreatedBy()).isNull();
        assertThat(scrubbed.getLastUpdatedBy()).isEqualTo(remaining.getId());
    }

    @Test
    @DisplayName("authored news marks the account as undeletable")
    void newsAuthorshipBlocksDeletion() {
        assertThat(staffUserRepository.hasAuthoredRecords(departing.getId())).isFalse();

        entityManager.createNativeQuery("""
                INSERT INTO news (staff_user_id, title, slug, content)
                VALUES (:id, 'Release', 'release', 'Maps')
                """)
                .setParameter("id", departing.getId())
                .executeUpdate();

        assertThat(staffUserRepository.hasAuthoredRecords(departing.getId())).isTrue();
        assertThat(staffUserRepository.hasAuthoredRecords(remaining.getId())).isFalse();
    }

    private StaffUser persistStaff(String username) {
        StaffUser staff = StaffUser.builder()
                .username(username)
                .password("hashed")
                .role(StaffRole.RANKING)
                .build();
        entityManager.persist(staff);
        return staff;
    }

    private void persistVote(StaffUser staff) {
        entityManager.persist(StaffMapVote.builder()
                .mapDifficulty(difficulty)
                .staffId(staff.getId())
                .vote(VoteType.UPVOTE)
                .type(MapVoteAction.RANK)
                .build());
    }
}
