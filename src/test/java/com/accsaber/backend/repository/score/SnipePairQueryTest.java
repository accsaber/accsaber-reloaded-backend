package com.accsaber.backend.repository.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.service.snipe.SnipeQuery;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnipePairQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final Long SNIPER_ID = 76561190000000011L;
    private static final Long TARGET_ID = 76561190000000012L;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ScoreRepository scoreRepository;

    private Category trueAcc;
    private User sniper;
    private User target;
    private UUID closeGap;
    private UUID wideGap;
    private UUID tightestGap;

    @BeforeEach
    void seed() {
        trueAcc = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        sniper = persistUser(SNIPER_ID, "Sniper");
        target = persistUser(TARGET_ID, "Target");

        closeGap = persistPair(trueAcc, sniper, target, 1_000_000, 940_000, 480.0, 5, 950_000, 500.0, 1);
        wideGap = persistPair(trueAcc, sniper, target, 1_000_000, 900_000, 400.0, 30, 990_000, 600.0, 2);
        tightestGap = persistPair(trueAcc, sniper, target, 2_000_000, 1_890_000, 690.0, 4, 1_900_000, 700.0, 3);
        entityManager.flush();
    }

    static Stream<Arguments> orderings() {
        return Stream.of(
                Arguments.of("the default order is the smallest accuracy gap first",
                        SnipeSort.GAP, null, List.of("tightest", "close", "wide")),
                Arguments.of("flipping the direction puts the biggest accuracy gap on top",
                        SnipeSort.GAP, Sort.Direction.DESC, List.of("wide", "close", "tightest")),
                Arguments.of("AP_GAP leads with the map holding the most AP to take back",
                        SnipeSort.AP_GAP, null, List.of("wide", "close", "tightest")),
                Arguments.of("TARGET_AP leads with the target's best map",
                        SnipeSort.TARGET_AP, null, List.of("tightest", "wide", "close")),
                Arguments.of("YOUR_AP leads with the sniper's own best map",
                        SnipeSort.YOUR_AP, null, List.of("tightest", "close", "wide")),
                Arguments.of("RANK_GAP leads with the widest leaderboard distance",
                        SnipeSort.RANK_GAP, null, List.of("wide", "close", "tightest")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("orderings")
    void sortOrdersThePairs(String description, SnipeSort sort, Sort.Direction direction, List<String> expected) {
        java.util.Map<String, UUID> seeded = java.util.Map.of(
                "tightest", tightestGap, "close", closeGap, "wide", wideGap);

        assertThat(orderedBy(sort, direction))
                .containsExactlyElementsOf(expected.stream().map(seeded::get).toList());
    }

    @Test
    @DisplayName("a paged request keeps both the ordering and the unsorted count query")
    void pagedRequestKeepsOrderingAndCount() {
        SnipeQuery query = new SnipeQuery(SNIPER_ID, TARGET_ID, null, SnipeSort.AP_GAP, null, null);
        var page = scoreRepository.findSnipePairs(SNIPER_ID, TARGET_ID, null, false, true, false,
                PageRequest.of(0, 2, query.toSort()));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(difficultyIds(page.getContent())).containsExactly(wideGap, closeGap);
    }

    @Test
    @DisplayName("a difficulty with no max score sorts last either way round rather than hijacking the top")
    void unknownMaxScoreSortsLastInBothDirections() {
        UUID unknownMaxScore = persistPair(trueAcc, sniper, target, null, 900_000, 450.0, 6, 950_000, 470.0, 2);
        entityManager.flush();

        assertThat(orderedBy(SnipeSort.GAP, null)).containsExactly(tightestGap, closeGap, wideGap, unknownMaxScore);
        assertThat(orderedBy(SnipeSort.GAP, Sort.Direction.DESC))
                .containsExactly(wideGap, closeGap, tightestGap, unknownMaxScore);
    }

    private List<UUID> orderedBy(SnipeSort sort, Sort.Direction direction) {
        return orderedBy(sort, direction, SnipeUnplayed.EXCLUDE);
    }

    private List<UUID> orderedBy(SnipeSort sort, Sort.Direction direction, SnipeUnplayed unplayed) {
        SnipeQuery query = new SnipeQuery(SNIPER_ID, TARGET_ID, null, sort, direction, unplayed);
        return difficultyIds(scoreRepository
                .findSnipePairs(SNIPER_ID, TARGET_ID, null, false, unplayed.allowsPlayed(), unplayed.allowsUnplayed(),
                        Pageable.unpaged(query.toSort()))
                .getContent());
    }

    @Test
    @DisplayName("a map only the target has played is left out by default")
    void unplayedMapIsHiddenUnlessAskedFor() {
        UUID unplayed = persistTargetOnly(trueAcc, target, 1_000_000, 960_000, 520.0, 2);
        entityManager.flush();

        assertThat(orderedBy(SnipeSort.TARGET_AP, null)).doesNotContain(unplayed);
    }

    @Test
    @DisplayName("including unplayed maps adds them alongside the ones you have played")
    void includingUnplayedAddsThemToTheList() {
        UUID unplayed = persistTargetOnly(trueAcc, target, 1_000_000, 960_000, 520.0, 2);
        entityManager.flush();

        assertThat(orderedBy(SnipeSort.TARGET_AP, null, SnipeUnplayed.INCLUDE))
                .containsExactlyInAnyOrder(tightestGap, wideGap, closeGap, unplayed);
    }

    @Test
    @DisplayName("only unplayed leaves nothing but the maps you have never touched")
    void onlyUnplayedDropsEverythingYouHavePlayed() {
        UUID unplayed = persistTargetOnly(trueAcc, target, 1_000_000, 960_000, 520.0, 2);
        entityManager.flush();

        assertThat(orderedBy(SnipeSort.TARGET_AP, null, SnipeUnplayed.ONLY)).containsExactly(unplayed);
    }

    @Test
    @DisplayName("unplayed maps sort last rather than hijacking the top of a sniper-side order")
    void unplayedMapsSortLastOnSniperSideOrders() {
        UUID unplayed = persistTargetOnly(trueAcc, target, 1_000_000, 960_000, 520.0, 2);
        entityManager.flush();

        assertThat(orderedBy(SnipeSort.YOUR_AP, null, SnipeUnplayed.INCLUDE))
                .containsExactly(tightestGap, closeGap, wideGap, unplayed);
    }

    @Test
    @DisplayName("a map where you are already ahead stays out even when unplayed maps are included")
    void mapsYouAlreadyLeadStayOut() {
        UUID alreadyAhead = persistPair(trueAcc, sniper, target, 1_000_000, 980_000, 700.0, 1, 900_000, 400.0, 40);
        entityManager.flush();

        assertThat(orderedBy(SnipeSort.TARGET_AP, null, SnipeUnplayed.INCLUDE)).doesNotContain(alreadyAhead);
    }

    private UUID persistTargetOnly(Category category, User target, Integer maxScore, int targetScore, double targetAp,
            int targetRank) {
        MapDifficulty difficulty = persistDifficulty(category, maxScore);
        persistScore(target, difficulty, targetScore, targetAp, targetRank);
        return difficulty.getId();
    }

    private List<UUID> difficultyIds(List<Object[]> rows) {
        return rows.stream().map(row -> ((Score) row[0]).getMapDifficulty().getId()).toList();
    }

    private User persistUser(Long id, String name) {
        User user = User.builder().id(id).name(name).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private UUID persistPair(Category category, User sniper, User target, Integer maxScore,
            int sniperScore, double sniperAp, int sniperRank, int targetScore, double targetAp, int targetRank) {
        MapDifficulty difficulty = persistDifficulty(category, maxScore);
        persistScore(sniper, difficulty, sniperScore, sniperAp, sniperRank);
        persistScore(target, difficulty, targetScore, targetAp, targetRank);
        return difficulty.getId();
    }

    private MapDifficulty persistDifficulty(Category category, Integer maxScore) {
        Map map = Map.builder()
                .songName("Song")
                .songAuthor("Author")
                .songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper")
                .build();
        entityManager.persist(map);

        MapDifficulty difficulty = MapDifficulty.builder()
                .map(map)
                .category(category)
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(MapDifficultyStatus.RANKED)
                .maxScore(maxScore)
                .build();
        entityManager.persist(difficulty);
        return difficulty;
    }

    private void persistScore(User user, MapDifficulty difficulty, int value, double ap, int rank) {
        entityManager.persist(Score.builder()
                .user(user)
                .mapDifficulty(difficulty)
                .score(value)
                .scoreNoMods(value)
                .rank(rank)
                .rankWhenSet(rank)
                .ap(ap)
                .weightedAp(ap)
                .active(true)
                .build());
    }
}
