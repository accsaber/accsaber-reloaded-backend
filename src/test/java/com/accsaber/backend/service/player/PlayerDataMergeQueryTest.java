package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

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

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.PlayersMergedEvent;
import com.accsaber.backend.repository.item.EssenceTransactionRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.EssenceLedgerService;
import com.accsaber.backend.service.item.ItemTransferService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlayerDataMergeQueryTest {

    private static final Long PRIMARY = 76561190000000021L;
    private static final Long SECONDARY = 76561190000000022L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EssenceTransactionRepository essenceTransactionRepository;

    private final ItemTransferService itemTransferService = mock(ItemTransferService.class);
    private PlayerDataMergeService service;

    @BeforeEach
    void seedUsers() {
        service = new PlayerDataMergeService(itemTransferService,
                new EssenceLedgerService(userRepository, essenceTransactionRepository), entityManager);
        entityManager.persist(User.builder().id(PRIMARY).name("Primary").country("ES").build());
        entityManager.persist(User.builder().id(SECONDARY).name("Secondary").country("ES").build());
        entityManager.flush();
    }

    @Test
    void essenceMovesWithLedgerRows() {
        run("UPDATE users SET item_essence = 40 WHERE id = ?1", SECONDARY);
        run("UPDATE users SET item_essence = 2 WHERE id = ?1", PRIMARY);

        merge();

        assertThat(single("SELECT item_essence FROM users WHERE id = ?1", PRIMARY)).isEqualTo(42L);
        assertThat(single("SELECT item_essence FROM users WHERE id = ?1", SECONDARY)).isEqualTo(0L);
        assertThat(single("SELECT amount FROM essence_transactions WHERE user_id = ?1", PRIMARY)).isEqualTo(40L);
        assertThat(single("SELECT amount FROM essence_transactions WHERE user_id = ?1", SECONDARY)).isEqualTo(-40L);
    }

    @Test
    void completedMilestoneReplacesUnfinishedOneAndCompletedStaysOnPrimary() {
        UUID replaced = milestone();
        UUID kept = milestone();
        UUID movedOnly = milestone();
        milestoneLink(PRIMARY, replaced, false);
        milestoneLink(SECONDARY, replaced, true);
        milestoneLink(PRIMARY, kept, true);
        milestoneLink(SECONDARY, kept, false);
        milestoneLink(SECONDARY, movedOnly, true);

        merge();

        assertThat(completed(replaced)).isEqualTo(true);
        assertThat(completed(kept)).isEqualTo(true);
        assertThat(completed(movedOnly)).isEqualTo(true);
    }

    @Test
    void completedCampaignBeatsInProgressAndAuthorshipMoves() {
        UUID first = campaign();
        UUID second = campaign();
        userCampaign(PRIMARY, first, "in_progress");
        userCampaign(SECONDARY, first, "completed");
        userCampaign(SECONDARY, second, "in_progress");

        merge();

        assertThat(single("SELECT status FROM user_campaigns WHERE user_id = ?1 AND campaign_id = ?2 AND active",
                PRIMARY, first)).isEqualTo("completed");
        assertThat(single("SELECT COUNT(*) FROM user_campaigns WHERE user_id = ?1 AND campaign_id = ?2 AND active",
                PRIMARY, second)).isEqualTo(1L);
        assertThat(single("SELECT COUNT(*) FROM campaigns WHERE creator_id = ?1", SECONDARY)).isEqualTo(0L);
    }

    @Test
    void supporterAccountMovesUnlessPrimaryHasOne() {
        run("INSERT INTO supporter_accounts (user_id, balance_cents) VALUES (?1, 500)", SECONDARY);

        merge();

        assertThat(single("SELECT balance_cents FROM supporter_accounts WHERE user_id = ?1", PRIMARY)).isEqualTo(500);
    }

    @Test
    void reservedEssenceRefusesTheMerge() {
        run("UPDATE users SET reserved_essence = 5 WHERE id = ?1", SECONDARY);

        assertThatThrownBy(this::merge).isInstanceOf(ConflictException.class);

        verify(itemTransferService, never()).moveAll(SECONDARY, PRIMARY);
    }

    private void merge() {
        service.onPlayersMerged(new PlayersMergedEvent(PRIMARY, SECONDARY));
    }

    private UUID milestone() {
        MilestoneSet set = MilestoneSet.builder().title("Set " + UUID.randomUUID()).setBonusXp(0.0).build();
        entityManager.persist(set);
        Milestone milestone = Milestone.builder()
                .milestoneSet(set)
                .title("Milestone")
                .type("milestone")
                .status(MilestoneStatus.ACTIVE)
                .xp(100.0)
                .targetValue(1.0)
                .active(true)
                .build();
        entityManager.persist(milestone);
        entityManager.flush();
        return milestone.getId();
    }

    private UUID campaign() {
        return (UUID) single("INSERT INTO campaigns (creator_id, name, slug) VALUES (?1, ?2, ?2) RETURNING id",
                SECONDARY, "c" + UUID.randomUUID());
    }

    private void userCampaign(Long userId, UUID campaignId, String status) {
        run("INSERT INTO user_campaigns (user_id, campaign_id, status) VALUES (?1, ?2, ?3)", userId, campaignId, status);
    }

    private void milestoneLink(Long userId, UUID milestoneId, boolean completed) {
        run("INSERT INTO user_milestone_links (user_id, milestone_id, completed) VALUES (?1, ?2, ?3)",
                userId, milestoneId, completed);
    }

    private Object completed(UUID milestoneId) {
        return single("SELECT completed FROM user_milestone_links WHERE user_id = ?1 AND milestone_id = ?2",
                PRIMARY, milestoneId);
    }

    private void run(String sql, Object... params) {
        query(sql, params).executeUpdate();
    }

    private Object single(String sql, Object... params) {
        return query(sql, params).getSingleResult();
    }

    private Query query(String sql, Object... params) {
        Query query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return query;
    }
}
